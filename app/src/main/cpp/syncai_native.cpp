#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <exception>
#include <new>
#include <mutex>
#include <unistd.h>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SyncAI", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SyncAI", __VA_ARGS__)

static std::string process_memory_snapshot() {
    std::ifstream status("/proc/self/status");
    std::string line, result;
    while (std::getline(status, line)) {
        if (line.rfind("VmRSS:", 0) == 0 || line.rfind("VmSize:", 0) == 0
                || line.rfind("VmHWM:", 0) == 0) {
            if (!result.empty()) result += " ";
            result += line;
        }
    }
    return result.empty() ? "process memory unavailable" : result;
}

static std::mutex g_mutex;
static llama_model *g_model = nullptr;
static std::string g_last_diagnostics = "No inference request recorded yet.";
static std::string g_last_load_error = "";

static void append_utf8(std::string &out, uint32_t cp) {
    if (cp <= 0x7F) {
        out.push_back(static_cast<char>(cp));
    } else if (cp <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp <= 0xFFFF) {
        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

static std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (!value) return {};
    const jsize length = env->GetStringLength(value);
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length
                && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        append_utf8(result, cp);
    }
    env->ReleaseStringChars(value, chars);
    return result;
}

static jstring utf8_to_jstring(JNIEnv *env, const std::string &value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    size_t i = 0;
    while (i < value.size()) {
        const unsigned char first = static_cast<unsigned char>(value[i]);
        uint32_t cp = 0xFFFD;
        size_t count = 1;
        if (first <= 0x7F) {
            cp = first;
        } else if ((first & 0xE0) == 0xC0 && i + 1 < value.size()) {
            const unsigned char b1 = static_cast<unsigned char>(value[i + 1]);
            if ((b1 & 0xC0) == 0x80) {
                cp = ((first & 0x1F) << 6) | (b1 & 0x3F);
                count = cp >= 0x80 ? 2 : 1;
                if (count == 1) cp = 0xFFFD;
            }
        } else if ((first & 0xF0) == 0xE0 && i + 2 < value.size()) {
            const unsigned char b1 = static_cast<unsigned char>(value[i + 1]);
            const unsigned char b2 = static_cast<unsigned char>(value[i + 2]);
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80) {
                uint32_t candidate = ((first & 0x0F) << 12)
                        | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
                if (candidate >= 0x800 && !(candidate >= 0xD800 && candidate <= 0xDFFF)) {
                    cp = candidate;
                    count = 3;
                }
            }
        } else if ((first & 0xF8) == 0xF0 && i + 3 < value.size()) {
            const unsigned char b1 = static_cast<unsigned char>(value[i + 1]);
            const unsigned char b2 = static_cast<unsigned char>(value[i + 2]);
            const unsigned char b3 = static_cast<unsigned char>(value[i + 3]);
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80
                    && (b3 & 0xC0) == 0x80) {
                uint32_t candidate = ((first & 0x07) << 18)
                        | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
                if (candidate >= 0x10000 && candidate <= 0x10FFFF) {
                    cp = candidate;
                    count = 4;
                }
            }
        }
        i += count;
        if (cp <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(cp));
        } else {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return env->NewString(utf16.empty() ? nullptr : utf16.data(),
                          static_cast<jsize>(utf16.size()));
}

static bool emit(JNIEnv *env, jobject callback, jmethodID tokenMethod, const std::string &text) {
    if (text.empty()) return true;
    jstring value = utf8_to_jstring(env, text);
    if (!value) {
        LOGE("JNI token conversion failed");
        return false;
    }
    env->CallVoidMethod(callback, tokenMethod, value);
    env->DeleteLocalRef(value);
    if (env->ExceptionCheck()) {
        LOGE("JNI token callback raised an exception; aborting generation safely");
        return false;
    }
    return true;
}

static std::string model_info_text_locked() {
    if (!g_model) return "No model loaded.";

    char desc[256] = {};
    llama_model_desc(g_model, desc, sizeof(desc));

    const double sizeGiB = static_cast<double>(llama_model_size(g_model)) /
            1024.0 / 1024.0 / 1024.0;
    const double paramsB = static_cast<double>(llama_model_n_params(g_model)) / 1e9;
    const int32_t trainCtx = llama_model_n_ctx_train(g_model);

    char info[768];
    snprintf(info, sizeof(info),
             "Architecture: %s\nParameters: %.2fB\nTensor size: %.2f GiB\n"
             "Training context: %d\nBackend: CPU",
             desc, paramsB, sizeGiB, trainCtx);
    return info;
}

static void set_error_diagnostics(
        long long startedMs,
        const char *stage,
        const std::string &message) {
    long long total = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count()) - startedMs;
    g_last_diagnostics = "Stage: " + std::string(stage) +
            "\nTotal: " + std::to_string(total) + " ms" +
            "\nError: " + message;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sam_syncai_GgufNative_nativeLoad(JNIEnv *env, jclass, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!path) return 2;

    g_last_load_error.clear();
    llama_log_set([](enum ggml_log_level level, const char * text, void *) {
        if (level >= GGML_LOG_LEVEL_ERROR && text) {
            g_last_load_error += text;
            LOGE("%s", text);
        }
    }, nullptr);

    // Current llama.cpp can load CPU backends dynamically; explicitly load them
    // before model initialization so Android builds do not end up with zero backends.
    ggml_backend_load_all();
    llama_backend_init();

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    std::string modelPath = jstring_to_string(env, path);

    std::ifstream probe(modelPath, std::ios::binary);
    if (!probe) {
        g_last_load_error = "Could not open the imported model file.";
        return 3;
    }
    char magic[4] = {};
    probe.read(magic, sizeof(magic));
    if (!probe || std::string(magic, 4) != "GGUF") {
        g_last_load_error = "The selected file does not have a valid GGUF header.";
        return 4;
    }

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.check_tensors = true;

    g_model = llama_model_load_from_file(modelPath.c_str(), params);
    if (!g_model) {
        if (g_last_load_error.empty()) {
            g_last_load_error = "llama.cpp rejected the GGUF model without reporting a detailed error.";
        }
        LOGE("Failed to load GGUF model: %s -- %s", modelPath.c_str(), g_last_load_error.c_str());
        return 1;
    }

    g_last_load_error.clear();
    g_last_diagnostics = "Model loaded. No inference request recorded yet.";
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sam_syncai_GgufNative_nativeUnload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    g_last_diagnostics = "No model loaded.";
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sam_syncai_GgufNative_nativeInfo(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(model_info_text_locked().c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sam_syncai_GgufNative_nativeDiagnostics(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::string result = g_model
            ? model_info_text_locked() + "\n\nLAST REQUEST\n" + g_last_diagnostics
            : (g_last_load_error.empty()
                ? "No model loaded."
                : "No model loaded.\n\nLAST LOAD ERROR\n" + g_last_load_error);
    return env->NewStringUTF(result.c_str());
}

static void native_generate_impl(
        JNIEnv *env, jclass,
        jobjectArray roles,
        jobjectArray texts,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);
    const long long startedMs = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count());
    LOGI("GENERATION_NATIVE_START pid=%d %s", getpid(), process_memory_snapshot().c_str());

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID tokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID completeMethod = env->GetMethodID(
            callbackClass, "onComplete", "(Ljava/lang/String;)V");
    jmethodID errorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");

    if (!g_model) {
        const std::string message = "No GGUF model is loaded.";
        set_error_diagnostics(startedMs, "load", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    const jsize count = env->GetArrayLength(roles);
    std::vector<std::string> roleStrings;
    std::vector<std::string> textStrings;
    roleStrings.reserve(count);
    textStrings.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto text = static_cast<jstring>(env->GetObjectArrayElement(texts, i));
        roleStrings.push_back(jstring_to_string(env, role));
        textStrings.push_back(jstring_to_string(env, text));
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(text);
    }

    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    if (!tmpl) tmpl = "{{ messages }}";

    llama_context_params ctxParams = llama_context_default_params();
    const int32_t trainedCtx = llama_model_n_ctx_train(g_model);
    // A modest 3072-token context gives code tasks more room while avoiding
    // the much larger KV cache of 4096+ on 4 GB phones. Re-check device memory
    // before raising this further.
    const uint32_t contextSize = static_cast<uint32_t>(
            std::min<int32_t>(3072, std::max<int32_t>(1024, trainedCtx)));
    ctxParams.n_ctx = contextSize;
    // The prompt is prefilled as one logical batch and can exceed 256 tokens.
    // Keep n_batch large enough for the bounded context while retaining a small
    // physical micro-batch to limit temporary memory on 4 GB devices.
    ctxParams.n_batch = contextSize;
    ctxParams.n_ubatch = 256;
    LOGI("GENERATION_CONTEXT context=%u trained=%d maxTokens=%d %s",
         contextSize, trainedCtx, static_cast<int>(maxTokens), process_memory_snapshot().c_str());
    const int cpuCount = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
    const int threads = std::max(2, std::min(6, cpuCount - 1));
    ctxParams.n_threads = threads;
    ctxParams.n_threads_batch = threads;

    LOGI("GENERATION_CONTEXT_CREATE_BEGIN context=%u promptMessages=%zu %s",
         contextSize, roleStrings.size(), process_memory_snapshot().c_str());
    llama_context *ctx = llama_init_from_model(g_model, ctxParams);
    if (!ctx) {
        LOGE("GENERATION_CONTEXT_CREATE_FAILED context=%u %s",
             contextSize, process_memory_snapshot().c_str());
        const std::string message = "Could not create the inference context.";
        set_error_diagnostics(startedMs, "context", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    std::vector<char> formatted(std::max<size_t>(4096, contextSize * 4));
    std::vector<llama_chat_message> contextMessages;

    auto formatPrompt = [&]() -> int32_t {
        contextMessages.clear();
        contextMessages.reserve(roleStrings.size());
        for (size_t i = 0; i < roleStrings.size(); ++i) {
            contextMessages.push_back({
                    roleStrings[i].c_str(),
                    textStrings[i].c_str()
            });
        }

        int32_t size = llama_chat_apply_template(
                tmpl, contextMessages.data(), contextMessages.size(), true,
                formatted.data(), formatted.size());

        if (size > static_cast<int32_t>(formatted.size())) {
            formatted.resize(static_cast<size_t>(size) + 1);
            size = llama_chat_apply_template(
                    tmpl, contextMessages.data(), contextMessages.size(), true,
                    formatted.data(), formatted.size());
        }
        return size;
    };

    const int requestedGeneration = std::max(1, std::min(1024, static_cast<int>(maxTokens)));
    const int reservedGeneration = std::min(requestedGeneration, 1024);
    const int promptBudget = std::max(
            1, static_cast<int>(contextSize) - reservedGeneration - 4);

    int32_t formattedSize = formatPrompt();

    if (formattedSize < 0) {
        llama_free(ctx);
        const std::string message = "The model's chat template could not be applied.";
        set_error_diagnostics(startedMs, "prompt", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    const auto *vocab = llama_model_get_vocab(g_model);
    int promptCount = -llama_tokenize(
            vocab, formatted.data(), formattedSize,
            nullptr, 0, true, true);

    // Keep enough room for the requested response. Older versions only checked
    // that the prompt itself fit, then could decode past the context window during
    // generation and terminate the app inside llama.cpp.
    while (promptCount > promptBudget && roleStrings.size() > 1) {
        // Preserve the system prompt and discard the oldest conversation turn.
        roleStrings.erase(roleStrings.begin() + 1);
        textStrings.erase(textStrings.begin() + 1);

        formattedSize = formatPrompt();
        if (formattedSize < 0) {
            llama_free(ctx);
            const std::string message = "The model's chat template could not be applied.";
            set_error_diagnostics(startedMs, "prompt", message);
            jstring value = env->NewStringUTF(message.c_str());
            env->CallVoidMethod(callback, errorMethod, value);
            env->DeleteLocalRef(value);
            return;
        }

        promptCount = -llama_tokenize(
                vocab, formatted.data(), formattedSize,
                nullptr, 0, true, true);
    }

    LOGI("GENERATION_PROMPT_READY promptTokens=%d context=%u %s",
         promptCount, contextSize, process_memory_snapshot().c_str());

    if (promptCount <= 0 || promptCount + reservedGeneration >= static_cast<int>(contextSize)) {
        llama_free(ctx);
        const std::string message = "Prompt is too large for the selected context window.";
        set_error_diagnostics(startedMs, "tokenization", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    std::string prompt(formatted.data(), formattedSize);

    std::vector<llama_token> tokens(promptCount);
    if (llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), tokens.size(), true, true) < 0) {
        llama_free(ctx);
        const std::string message = "Tokenization failed.";
        set_error_diagnostics(startedMs, "tokenization", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    llama_sampler *sampler =
            llama_sampler_chain_init(llama_sampler_chain_default_params());
    const float safeTemp = std::max(0.05f, std::min(2.0f, temperature));
    const float safeTopP = std::max(0.05f, std::min(1.0f, topP));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(safeTopP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(safeTemp));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const long long promptStartMs = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count());
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        const std::string message = "Model evaluation failed during prompt processing.";
        set_error_diagnostics(startedMs, "prompt-eval", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    const long long promptMs = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count()) - promptStartMs;
    const long long generationStartMs = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count());
    LOGI("GENERATION_DECODE_BEGIN promptMs=%lld %s", promptMs, process_memory_snapshot().c_str());

    std::string pendingUtf8;
    const int generationLimit = std::max(
            1,
            std::min(
                    1024,
                    std::min(
                            static_cast<int>(maxTokens),
                            static_cast<int>(contextSize) - promptCount - 1)));
    int generated = 0;

    for (; generated < generationLimit; ++generated) {
        if (generated > 0 && generated % 32 == 0) {
            LOGI("GENERATION_PROGRESS generated=%d/%d %s",
                 generated, generationLimit, process_memory_snapshot().c_str());
        }
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char piece[256];
        int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n < 0) {
            std::vector<char> larger(static_cast<size_t>(-n));
            n = llama_token_to_piece(vocab, token, larger.data(), larger.size(), 0, true);
            if (n > 0) pendingUtf8.append(larger.data(), n);
        } else if (n > 0) {
            pendingUtf8.append(piece, n);
        }

        if (!pendingUtf8.empty()) {
            size_t emitLen = pendingUtf8.size();
            while (emitLen > 0) {
                const unsigned char c = static_cast<unsigned char>(pendingUtf8[emitLen - 1]);
                if ((c & 0xC0) != 0x80) break;
                --emitLen;
            }
            if (emitLen == 0 && pendingUtf8.size() < 4) continue;
            if (emitLen == 0) emitLen = pendingUtf8.size();
            if (!emit(env, callback, tokenMethod, pendingUtf8.substr(0, emitLen))) {
                llama_sampler_free(sampler);
                llama_free(ctx);
                set_error_diagnostics(startedMs, "jni-callback", "Token callback failed; see Android logcat.");
                return;
            }
            pendingUtf8.erase(0, emitLen);
        }

        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) {
            llama_sampler_free(sampler);
            llama_free(ctx);
            const std::string message = "Model evaluation failed during generation.";
            set_error_diagnostics(startedMs, "generation", message);
            jstring value = env->NewStringUTF(message.c_str());
            env->CallVoidMethod(callback, errorMethod, value);
            env->DeleteLocalRef(value);
            return;
        }
    }

    if (!pendingUtf8.empty() && !emit(env, callback, tokenMethod, pendingUtf8)) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        set_error_diagnostics(startedMs, "jni-callback", "Final token callback failed; see Android logcat.");
        return;
    }
    llama_sampler_free(sampler);
    llama_free(ctx);

    const long long generationMs = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count()) -
            generationStartMs;
    const long long totalMs = static_cast<long long>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count()) -
            startedMs;
    const double tokensPerSecond = generationMs > 0
            ? (static_cast<double>(generated) * 1000.0 / generationMs) : 0.0;

    char diagnostics[1024];
    snprintf(diagnostics, sizeof(diagnostics),
             "Prompt tokens: %d\nContext: %u\nThreads: %d\n"
             "Prompt eval: %lld ms\nGeneration: %lld ms\n"
             "Generated tokens: %d\nTokens/sec: %.2f\nTotal inference: %lld ms",
             promptCount, contextSize, threads, promptMs, generationMs,
             generated, tokensPerSecond, totalMs);
    g_last_diagnostics = diagnostics;
    LOGI("GENERATION_NATIVE_COMPLETE generated=%d totalMs=%lld %s",
         generated, totalMs, process_memory_snapshot().c_str());

    jstring diagnosticsValue = env->NewStringUTF(g_last_diagnostics.c_str());
    env->CallVoidMethod(callback, completeMethod, diagnosticsValue);
    env->DeleteLocalRef(diagnosticsValue);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_sam_syncai_GgufNative_nativeGenerate(
        JNIEnv *env, jclass clazz,
        jobjectArray roles,
        jobjectArray texts,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jobject callback) {
    try {
        native_generate_impl(env, clazz, roles, texts, maxTokens, temperature, topP, callback);
    } catch (const std::bad_alloc &) {
        LOGE("GENERATION_CPP_ALLOCATION_FAILURE %s", process_memory_snapshot().c_str());
        if (!env->ExceptionCheck() && callback) {
            jclass callbackClass = env->GetObjectClass(callback);
            jmethodID errorMethod = callbackClass
                    ? env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V") : nullptr;
            if (errorMethod) {
                jstring message = env->NewStringUTF(
                        "Native inference ran out of memory while preparing or generating the response.");
                if (message) {
                    env->CallVoidMethod(callback, errorMethod, message);
                    env->DeleteLocalRef(message);
                }
            }
            if (callbackClass) env->DeleteLocalRef(callbackClass);
        }
    } catch (const std::exception &error) {
        LOGE("GENERATION_CPP_EXCEPTION message=%s %s",
             error.what(), process_memory_snapshot().c_str());
        if (!env->ExceptionCheck() && callback) {
            jclass callbackClass = env->GetObjectClass(callback);
            jmethodID errorMethod = callbackClass
                    ? env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V") : nullptr;
            if (errorMethod) {
                jstring message = env->NewStringUTF("Native inference failed safely. Export logs for details.");
                if (message) {
                    env->CallVoidMethod(callback, errorMethod, message);
                    env->DeleteLocalRef(message);
                }
            }
            if (callbackClass) env->DeleteLocalRef(callbackClass);
        }
    } catch (...) {
        LOGE("GENERATION_CPP_UNKNOWN_EXCEPTION %s", process_memory_snapshot().c_str());
        if (!env->ExceptionCheck() && callback) {
            jclass callbackClass = env->GetObjectClass(callback);
            jmethodID errorMethod = callbackClass
                    ? env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V") : nullptr;
            if (errorMethod) {
                jstring message = env->NewStringUTF("Native inference failed unexpectedly. Export logs for details.");
                if (message) {
                    env->CallVoidMethod(callback, errorMethod, message);
                    env->DeleteLocalRef(message);
                }
            }
            if (callbackClass) env->DeleteLocalRef(callbackClass);
        }
    }
}

