#include <algorithm>
#include <atomic>
#include <jni.h>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

namespace {

std::mutex runtime_mutex;
llama_model * model = nullptr;
llama_context * context = nullptr;
bool backend_initialized = false;
std::vector<llama_token> cached_prompt_tokens;

void throw_state(JNIEnv * env, const std::string & message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception != nullptr) {
        env->ThrowNew(exception, message.c_str());
    }
}

std::string from_java(JNIEnv * env, jstring value) {
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jbyteArray to_byte_array(JNIEnv * env, const std::string & value) {
    auto result = env->NewByteArray(static_cast<jsize>(value.size()));
    if (result != nullptr && !value.empty()) {
        env->SetByteArrayRegion(
                result,
                0,
                static_cast<jsize>(value.size()),
                reinterpret_cast<const jbyte *>(value.data()));
    }
    return result;
}

void unload_locked() {
    cached_prompt_tokens.clear();
    if (context != nullptr) {
        llama_free(context);
        context = nullptr;
    }
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }
}

bool decode_prompt(const std::vector<llama_token> & tokens, size_t offset) {
    constexpr int batch_size = 512;
    for (size_t cursor = offset; cursor < tokens.size(); cursor += batch_size) {
        const int count = static_cast<int>(std::min<size_t>(batch_size, tokens.size() - cursor));
        llama_batch batch = llama_batch_get_one(
                const_cast<llama_token *>(tokens.data() + cursor),
                count);
        if (llama_decode(context, batch) != 0) {
            return false;
        }
    }
    return true;
}

size_t prepare_prompt_cache(const std::vector<llama_token> & prompt_tokens) {
    size_t reusable = 0;
    const size_t shared = std::min(cached_prompt_tokens.size(), prompt_tokens.size());
    while (reusable < shared && cached_prompt_tokens[reusable] == prompt_tokens[reusable]) {
        ++reusable;
    }

    // Re-evaluate at least the final prompt token so the current logits are valid for sampling.
    if (reusable == prompt_tokens.size() && reusable > 0) {
        --reusable;
    }

    llama_memory_t memory = llama_get_memory(context);
    if (reusable == 0) {
        llama_memory_clear(memory, false);
    } else if (!llama_memory_seq_rm(
                       memory,
                       0,
                       static_cast<llama_pos>(reusable),
                       -1)) {
        llama_memory_clear(memory, false);
        reusable = 0;
    }
    return reusable;
}

std::string apply_chat_template(
        const std::string & system_prompt,
        const std::string & user_prompt) {
    const llama_chat_message messages[] = {
        {"system", system_prompt.c_str()},
        {"user", user_prompt.c_str()},
    };
    const char * chat_template = llama_model_chat_template(model, nullptr);
    int32_t required = llama_chat_apply_template(
            chat_template,
            messages,
            2,
            true,
            nullptr,
            0);
    if (required < 0) {
        return {};
    }
    std::vector<char> buffer(static_cast<size_t>(required) + 1);
    int32_t written = llama_chat_apply_template(
            chat_template,
            messages,
            2,
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size()));
    if (written < 0) {
        return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(written));
}

std::vector<llama_token> tokenize(const std::string & prompt) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    int count = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            nullptr,
            0,
            true,
            true);
    if (count >= 0) {
        return {};
    }
    std::vector<llama_token> tokens(static_cast<size_t>(-count));
    count = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true);
    if (count < 0) {
        return {};
    }
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

std::string token_piece(llama_token token) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    int size = llama_token_to_piece(vocab, token, nullptr, 0, 0, true);
    if (size >= 0) {
        return {};
    }
    std::vector<char> buffer(static_cast<size_t>(-size));
    size = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            true);
    if (size < 0) {
        return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(size));
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_svetlio_audiofreedom_assistant_runtime_LocalLlmRuntime_nativeInitialize(
        JNIEnv *,
        jobject) {
    std::lock_guard<std::mutex> lock(runtime_mutex);
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_svetlio_audiofreedom_assistant_runtime_LocalLlmRuntime_nativeLoadModel(
        JNIEnv * env,
        jobject,
        jstring path,
        jint context_size) {
    std::lock_guard<std::mutex> lock(runtime_mutex);
    unload_locked();

    llama_model_params model_params = llama_model_default_params();
    const std::string model_path = from_java(env, path);
    model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (model == nullptr) {
        throw_state(env, "Unable to load the local language model");
        return;
    }

    const unsigned int hardware_threads = std::thread::hardware_concurrency();
    const int threads = std::clamp(static_cast<int>(hardware_threads) - 2, 2, 4);
    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_size);
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.no_perf = true;
    context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        unload_locked();
        throw_state(env, "Unable to allocate the local language model context");
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_svetlio_audiofreedom_assistant_runtime_LocalLlmRuntime_nativeGenerate(
        JNIEnv * env,
        jobject,
        jstring system_prompt,
        jstring user_prompt,
        jstring grammar,
        jint maximum_tokens) {
    std::lock_guard<std::mutex> lock(runtime_mutex);
    if (model == nullptr || context == nullptr) {
        throw_state(env, "No local language model is loaded");
        return nullptr;
    }

    const std::string prompt = apply_chat_template(
            from_java(env, system_prompt),
            from_java(env, user_prompt));
    if (prompt.empty()) {
        throw_state(env, "The model chat template could not format the request");
        return nullptr;
    }

    const std::vector<llama_token> prompt_tokens = tokenize(prompt);
    if (prompt_tokens.empty()) {
        throw_state(env, "The assistant request could not be tokenized");
        return nullptr;
    }
    if (prompt_tokens.size() + static_cast<size_t>(maximum_tokens) >= llama_n_ctx(context)) {
        throw_state(env, "The assistant request exceeds the local context size");
        return nullptr;
    }
    const size_t cached_tokens = prepare_prompt_cache(prompt_tokens);
    if (!decode_prompt(prompt_tokens, cached_tokens)) {
        cached_prompt_tokens.clear();
        throw_state(env, "The local model could not process the assistant request");
        return nullptr;
    }
    cached_prompt_tokens = prompt_tokens;

    const std::string grammar_text = from_java(env, grammar);
    llama_sampler * grammar_sampler = llama_sampler_init_grammar(
            llama_model_get_vocab(model),
            grammar_text.c_str(),
            "root");
    if (grammar_sampler == nullptr) {
        throw_state(env, "The assistant output grammar is invalid");
        return nullptr;
    }
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, grammar_sampler);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    for (int generated = 0; generated < maximum_tokens; ++generated) {
        const llama_token token = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(llama_model_get_vocab(model), token)) {
            break;
        }
        output += token_piece(token);
        llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(context, batch) != 0) {
            llama_sampler_free(sampler);
            throw_state(env, "The local model stopped while generating a response");
            return nullptr;
        }
    }
    llama_sampler_free(sampler);
    return to_byte_array(env, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_svetlio_audiofreedom_assistant_runtime_LocalLlmRuntime_nativeUnload(
        JNIEnv *,
        jobject) {
    std::lock_guard<std::mutex> lock(runtime_mutex);
    unload_locked();
}
