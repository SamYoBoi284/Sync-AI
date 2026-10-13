#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include <unistd.h>
#include <cstdio>
#include <thread>
#include <chrono>

#include "llama.h"
#include "ggml-backend.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SyncAI", __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static std::string g_last_error;
static double g_last_prompt_ms = 0.0;
static double g_last_generation_ms = 0.0;
static int g_last_generated_tokens = 0;
static int g_last_prompt_tokens = 0;
static int g_last_context_size = 0;
static int g_last_threads = 0;
static double g_last_tokens_per_second = 0.0;

static void syncai_llama_log(ggml_log_level level, const char * text, void *) {
    if (!text) return;
    __android_log_print(level >= GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO,
                        "SyncAI/llama.cpp", "%s", text);
    if (level >= GGML_LOG_LEVEL_ERROR) {
        g_last_error.append(text);
    }
}

static std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

static void emit(JNIEnv * env, jobject callback, jmethodID tokenMethod, const std::string & text) {
    if (!text.empty()) {
        jstring value = env->NewStringUTF(text.c_str());
        env->CallVoidMethod(callback, tokenMethod, value);
        env->DeleteLocalRef(value);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sam_syncai_GgufNative_nativeLoad(JNIEnv * env, jclass, jstring path, jstring nativeLibDir) {
    (void) nativeLibDir;
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!path) {
        g_last_error = "No model path was provided.";
        return 2;
    }

    g_last_error.clear();
    llama_log_set(syncai_llama_log, nullptr);

    std::string nativeDir = jstring_to_string(env, nativeLibDir);
    if (!nativeDir.empty()) {
        // Load the best CPU backend variant for the device at runtime.
        ggml_backend_load_all_from_path(nativeDir.c_str());
    }
    llama_backend_init();

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    std::string modelPath = jstring_to_string(env, path);
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(modelPath.c_str(), params);
    if (!g_model) {
        if (g_last_error.empty()) {
            g_last_error = "llama.cpp returned a model-load failure without an error log. Check the model format, file integrity, available RAM, and backend initialization.";
        }
        LOGE("Failed to load GGUF model: %s", modelPath.c_str());
        return 1;
    }

    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sam_syncai_GgufNative_nativeLastError(JNIEnv * env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_last_error.c_str());
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
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sam_syncai_GgufNative_nativeInfo(JNIEnv * env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return env->NewStringUTF("No model loaded.");

    char desc[256] = {};
    llama_model_desc(g_model, desc, sizeof(desc));

    const double sizeGiB = static_cast<double>(llama_model_size(g_model)) /
                           1024.0 / 1024.0 / 1024.0;
    const double paramsB = static_cast<double>(llama_model_n_params(g_model)) / 1e9;
    const int32_t trainCtx = llama_model_n_ctx_train(g_model);

    char info[1024];
    snprintf(info, sizeof(info),
             "Architecture: %s\n"
             "Parameters: %.2fB\n"
             "Tensor size: %.2f GiB\n"
             "Training context: %d\n"
             "Backend: CPU\n"
             "Last context size: %d\n"
             "Last prompt tokens: %d\n"
             "Last CPU threads: %d\n"
             "Last prompt eval: %.1f ms\n"
             "Last generation: %.1f ms\n"
             "Last generated tokens: %d\n"
             "Last generation speed: %.2f tok/s",
             desc, paramsB, sizeGiB, trainCtx,
             g_last_context_size, g_last_prompt_tokens, g_last_threads,
             g_last_prompt_ms, g_last_generation_ms, g_last_generated_tokens,
             g_last_tokens_per_second);
    return env->NewStringUTF(info);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sam_syncai_GgufNative_nativeGenerate(
        JNIEnv * env, jclass,
        jobjectArray roles,
        jobjectArray texts,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID tokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID completeMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
    jmethodID errorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");

    if (!g_model) {
        jstring message = env->NewStringUTF("No GGUF model is loaded.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
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
        messages.push_back({
            roleStrings[i].c_str(),
            textStrings[i].c_str()
        });
    }

    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    if (!tmpl) tmpl = "{{ messages }}";

    llama_context_params ctxParams = llama_context_default_params();
    const int32_t trainedCtx = llama_model_n_ctx_train(g_model);
    const uint32_t contextSize = static_cast<uint32_t>(
        std::min<int32_t>(1024, std::max<int32_t>(768, trainedCtx))
    );

    // The prompt is submitted as one batch. Keep n_batch large enough for
    // the complete prompt so llama_decode never receives more tokens than
    // the context's configured batch capacity.
    ctxParams.n_ctx = contextSize;
    ctxParams.n_batch = contextSize;
    ctxParams.n_ubatch = contextSize;

    const int cpuCount = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
    const int threads = std::max(2, std::min(8, cpuCount));
    ctxParams.n_threads = threads;
    ctxParams.n_threads_batch = threads;

    llama_context * ctx = llama_init_from_model(g_model, ctxParams);
    if (!ctx) {
        jstring message = env->NewStringUTF("Could not create the inference context.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
        return;
    }

    std::vector<char> formatted(std::max<size_t>(4096, contextSize * 4));
    int32_t formattedSize = llama_chat_apply_template(
        tmpl, messages.data(), messages.size(), true, formatted.data(), formatted.size());

    if (formattedSize < 0) {
        llama_free(ctx);
        jstring message = env->NewStringUTF("The model's chat template could not be applied.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
        return;
    }

    if (formattedSize > static_cast<int32_t>(formatted.size())) {
        formatted.resize(formattedSize + 1);
        formattedSize = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true, formatted.data(), formatted.size());
    }

    if (formattedSize < 0) {
        llama_free(ctx);
        jstring message = env->NewStringUTF("The formatted prompt exceeded the model context.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
        return;
    }

    std::string prompt(formatted.data(), formattedSize);
    const auto * vocab = llama_model_get_vocab(g_model);

    const int promptCount = -llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        nullptr, 0, true, true);

    g_last_context_size = static_cast<int>(contextSize);
    g_last_threads = threads;

    const int generationLimit = std::max(1, std::min(64, static_cast<int>(maxTokens)));
    if (promptCount <= 0 || promptCount >= static_cast<int>(contextSize) ||
        promptCount + generationLimit >= static_cast<int>(contextSize)) {
        llama_free(ctx);
        jstring message = env->NewStringUTF(
                "The prompt is too large for the selected context window. "
                "Try removing large memory/file attachments or starting a new chat.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
        return;
    }

    g_last_prompt_tokens = promptCount;
    g_last_tokens_per_second = 0.0;
    std::vector<llama_token> tokens(promptCount);
    if (llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), tokens.size(), true, true) < 0) {
        llama_free(ctx);
        jstring message = env->NewStringUTF("Tokenization failed.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
        return;
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    const float safeTemp = std::max(0.05f, std::min(2.0f, temperature));
    const float safeTopP = std::max(0.05f, std::min(1.0f, topP));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(safeTopP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(safeTemp));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const auto promptStart = std::chrono::steady_clock::now();
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        jstring message = env->NewStringUTF("Model evaluation failed during prompt processing.");
        env->CallVoidMethod(callback, errorMethod, message);
        env->DeleteLocalRef(message);
        return;
    }

    const auto promptEnd = std::chrono::steady_clock::now();
    g_last_prompt_ms = std::chrono::duration<double, std::milli>(promptEnd - promptStart).count();

    const auto generationStart = std::chrono::steady_clock::now();
    g_last_generated_tokens = 0;
    std::string pendingUtf8;
    for (int generated = 0; generated < generationLimit; ++generated) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        ++g_last_generated_tokens;

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
            jstring message = env->NewStringUTF("Model evaluation failed during generation.");
            env->CallVoidMethod(callback, errorMethod, message);
            env->DeleteLocalRef(message);
            return;
        }
    }

    if (!pendingUtf8.empty()) emit(env, callback, tokenMethod, pendingUtf8);
    const auto generationEnd = std::chrono::steady_clock::now();
    g_last_generation_ms =
            std::chrono::duration<double, std::milli>(generationEnd - generationStart).count();
    g_last_tokens_per_second = g_last_generation_ms > 0.0
            ? (static_cast<double>(g_last_generated_tokens) / (g_last_generation_ms / 1000.0))
            : 0.0;
    llama_sampler_free(sampler);
    llama_free(ctx);
    env->CallVoidMethod(callback, completeMethod);
}
