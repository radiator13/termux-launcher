#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

namespace {
constexpr const char * TAG = "TAI-llama.cpp";
thread_local std::string last_error;

struct Runtime {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::atomic<bool> cancelled{false};
    std::mutex generation_mutex;
};

std::string from_jstring(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

void set_error(const std::string & value) {
    last_error = value;
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", value.c_str());
}

jstring to_jstring(JNIEnv * env, const std::string & value) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(value.size()));
    if (!value.empty()) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()), reinterpret_cast<const jbyte *>(value.data()));
    jclass string_class = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, charset));
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(string_class);
    return result;
}

bool abort_generation(void * data) {
    return static_cast<Runtime *>(data)->cancelled.load();
}

std::string apply_chat_template(Runtime * runtime, const std::string & prompt) {
    const char * model_template = llama_model_chat_template(runtime->model, nullptr);
    if (!model_template) return prompt;
    llama_chat_message message{"user", prompt.c_str()};
    std::vector<char> output(std::max<size_t>(4096, prompt.size() * 2 + 1024));
    int length = llama_chat_apply_template(model_template, &message, 1, true,
        output.data(), static_cast<int32_t>(output.size()));
    if (length > static_cast<int>(output.size())) {
        output.resize(length + 1);
        length = llama_chat_apply_template(model_template, &message, 1, true,
            output.data(), static_cast<int32_t>(output.size()));
    }
    return length > 0 ? std::string(output.data(), length) : prompt;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeAvailable(JNIEnv *, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeLastError(JNIEnv * env, jclass) {
    return env->NewStringUTF(last_error.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeLoad(
    JNIEnv * env, jclass, jstring path_value, jint context_size, jint gpu_layers, jint threads) {
    last_error.clear();
    const std::string path = from_jstring(env, path_value);
    if (path.empty()) {
        set_error("Missing GGUF model path");
        return 0;
    }

    llama_backend_init();
    auto * runtime = new Runtime();
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpu_layers;
    runtime->model = llama_model_load_from_file(path.c_str(), model_params);
    if (!runtime->model) {
        set_error("llama.cpp could not load the GGUF model");
        delete runtime;
        return 0;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::max(512, context_size));
    context_params.n_batch = std::min<uint32_t>(context_params.n_ctx, 512);
    context_params.n_threads = std::max(1, threads);
    context_params.n_threads_batch = std::max(1, threads);
    context_params.abort_callback = abort_generation;
    context_params.abort_callback_data = runtime;
    runtime->context = llama_init_from_model(runtime->model, context_params);
    if (!runtime->context) {
        set_error("llama.cpp could not allocate the model context");
        llama_model_free(runtime->model);
        delete runtime;
        return 0;
    }
    return reinterpret_cast<jlong>(runtime);
}

extern "C" JNIEXPORT void JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeUnload(JNIEnv *, jclass, jlong handle) {
    auto * runtime = reinterpret_cast<Runtime *>(handle);
    if (!runtime) return;
    runtime->cancelled.store(true);
    std::lock_guard<std::mutex> lock(runtime->generation_mutex);
    if (runtime->context) llama_free(runtime->context);
    if (runtime->model) llama_model_free(runtime->model);
    delete runtime;
}

extern "C" JNIEXPORT void JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeCancel(JNIEnv *, jclass, jlong handle) {
    auto * runtime = reinterpret_cast<Runtime *>(handle);
    if (runtime) runtime->cancelled.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_termux_ai_LlamaCppBridge_nativeGenerate(
    JNIEnv * env, jclass, jlong handle, jstring prompt_value, jboolean chat,
    jint max_tokens, jint top_k, jdouble top_p, jdouble temperature, jobject callback) {
    last_error.clear();
    auto * runtime = reinterpret_cast<Runtime *>(handle);
    if (!runtime || !runtime->model || !runtime->context) {
        set_error("llama.cpp runtime is not loaded");
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(runtime->generation_mutex);
    runtime->cancelled.store(false);
    llama_memory_clear(llama_get_memory(runtime->context), true);

    std::string prompt = from_jstring(env, prompt_value);
    if (chat == JNI_TRUE) prompt = apply_chat_template(runtime, prompt);
    const llama_vocab * vocab = llama_model_get_vocab(runtime->model);
    int token_count = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (token_count <= 0 || token_count >= static_cast<int>(llama_n_ctx(runtime->context))) {
        set_error("Prompt is empty or exceeds the configured context window");
        return nullptr;
    }
    std::vector<llama_token> tokens(token_count);
    if (llama_tokenize(vocab, prompt.c_str(), prompt.size(), tokens.data(), tokens.size(), true, true) < 0) {
        set_error("Failed to tokenize prompt");
        return nullptr;
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (top_k > 0) llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    if (top_p > 0.0 && top_p < 1.0) llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    if (temperature <= 0.0) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(static_cast<float>(temperature)));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    jclass callback_class = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID on_token = callback_class
        ? env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V") : nullptr;
    std::string response;
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    const int limit = std::max(1, static_cast<int>(max_tokens));
    for (int generated = 0; generated < limit && !runtime->cancelled.load(); generated++) {
        if (llama_decode(runtime->context, batch) != 0) {
            if (!runtime->cancelled.load()) set_error("llama.cpp decode failed");
            break;
        }
        llama_token token = llama_sampler_sample(sampler, runtime->context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        std::vector<char> piece(256);
        int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        if (length < 0) {
            piece.resize(-length);
            length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        }
        if (length > 0) {
            std::string text_piece(piece.data(), length);
            response += text_piece;
            if (on_token) {
                jstring java_piece = to_jstring(env, text_piece);
                env->CallVoidMethod(callback, on_token, java_piece);
                env->DeleteLocalRef(java_piece);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    runtime->cancelled.store(true);
                }
            }
        }
        batch = llama_batch_get_one(&token, 1);
    }
    llama_sampler_free(sampler);
    if (runtime->cancelled.load()) {
        set_error("Generation cancelled");
        return nullptr;
    }
    return to_jstring(env, response);
}
