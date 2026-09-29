#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SyncAI", __VA_ARGS__)

static std::mutex g_mutex;
static llama_model *g_model = nullptr;
static std::string g_last_diagnostics = "No inference request recorded yet.";
static std::string g_last_load_error = "";

static std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

static void emit(JNIEnv *env, jobject callback, jmethodID tokenMethod, const std::string &text) {
    if (text.empty()) return;
    jstring value = env->NewStringUTF(text.c_str());
    env->CallVoidMethod(callback, tokenMethod, value);
    env->DeleteLocalRef(value);
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
    params.use_mmap = true;
    params.use_mlock = false;
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

extern "C"
JNIEXPORT void JNICALL
Java_com_sam_syncai_GgufNative_nativeGenerate(
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

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID tokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID completeMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
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

    std::vector<llama_chat_message> messages;
    messages.reserve(count);
    for (size_t i = 0; i < roleStrings.size(); ++i) {
        messages.push_back({roleStrings[i].c_str(), textStrings[i].c_str()});
    }

    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    if (!tmpl) tmpl = "{{ messages }}";

    llama_context_params ctxParams = llama_context_default_params();
    const int32_t trainedCtx = llama_model_n_ctx_train(g_model);
    const uint32_t contextSize = static_cast<uint32_t>(
            std::min<int32_t>(2048, std::max<int32_t>(1024, trainedCtx)));
    ctxParams.n_ctx = contextSize;
    ctxParams.n_batch = 256;
    ctxParams.n_ubatch = 256;
    const int cpuCount = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
    const int threads = std::max(2, std::min(6, cpuCount - 1));
    ctxParams.n_threads = threads;
    ctxParams.n_threads_batch = threads;

    llama_context *ctx = llama_init_from_model(g_model, ctxParams);
    if (!ctx) {
        const std::string message = "Could not create the inference context.";
        set_error_diagnostics(startedMs, "context", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    std::vector<char> formatted(std::max<size_t>(4096, contextSize * 4));
    int32_t formattedSize = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true, formatted.data(), formatted.size());

    if (formattedSize < 0) {
        llama_free(ctx);
        const std::string message = "The model's chat template could not be applied.";
        set_error_diagnostics(startedMs, "prompt", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    if (formattedSize > static_cast<int32_t>(formatted.size())) {
        formatted.resize(formattedSize + 1);
        formattedSize = llama_chat_apply_template(
                tmpl, messages.data(), messages.size(), true, formatted.data(), formatted.size());
    }

    if (formattedSize < 0) {
        llama_free(ctx);
        const std::string message = "The formatted prompt exceeded the model context.";
        set_error_diagnostics(startedMs, "prompt", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

    std::string prompt(formatted.data(), formattedSize);
    const auto *vocab = llama_model_get_vocab(g_model);

    const int promptCount = -llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            nullptr, 0, true, true);
    if (promptCount <= 0 || promptCount >= static_cast<int>(contextSize)) {
        llama_free(ctx);
        const std::string message = "Prompt is too large for the selected context window.";
        set_error_diagnostics(startedMs, "tokenization", message);
        jstring value = env->NewStringUTF(message.c_str());
        env->CallVoidMethod(callback, errorMethod, value);
        env->DeleteLocalRef(value);
        return;
    }

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

    std::string pendingUtf8;
    const int generationLimit = std::max(1, std::min(128, static_cast<int>(maxTokens)));
    int generated = 0;

    for (; generated < generationLimit; ++generated) {
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
            emit(env, callback, tokenMethod, pendingUtf8.substr(0, emitLen));
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

    if (!pendingUtf8.empty()) emit(env, callback, tokenMethod, pendingUtf8);
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

    env->CallVoidMethod(callback, completeMethod);
}
