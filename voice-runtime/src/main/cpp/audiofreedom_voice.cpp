#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {

std::mutex g_mutex;
whisper_context *g_context = nullptr;

void runtime_log(ggml_log_level level, const char *message, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, "AudioFreedomVoice", message);
    }
}

void throw_java(JNIEnv *env, const char *type, const std::string &message) {
    jclass exception = env->FindClass(type);
    if (exception != nullptr) {
        env->ThrowNew(exception, message.c_str());
    }
}

std::string trim(std::string value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return {};
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_svetlio_audiofreedom_voice_runtime_LocalWhisperRuntime_nativeLoadModel(
        JNIEnv *env, jobject, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *characters = env->GetStringUTFChars(path, nullptr);
    if (characters == nullptr) return;
    auto *loaded = whisper_init_from_file_with_params(
        characters,
        whisper_context_default_params()
    );
    env->ReleaseStringUTFChars(path, characters);
    if (loaded == nullptr) {
        throw_java(env, "java/io/IOException", "Unable to load the Whisper model");
        return;
    }
    if (g_context != nullptr) whisper_free(g_context);
    g_context = loaded;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_svetlio_audiofreedom_voice_runtime_LocalWhisperRuntime_nativeTranscribe(
        JNIEnv *env, jobject, jshortArray pcm, jstring language, jboolean command_hints) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "No Whisper model is loaded");
        return nullptr;
    }

    const jsize count = env->GetArrayLength(pcm);
    std::vector<jshort> input(static_cast<size_t>(count));
    env->GetShortArrayRegion(pcm, 0, count, input.data());
    if (env->ExceptionCheck()) return nullptr;

    std::vector<float> samples(static_cast<size_t>(count));
    std::transform(input.begin(), input.end(), samples.begin(), [](jshort sample) {
        return static_cast<float>(sample) / 32768.0F;
    });

    const char *language_chars = env->GetStringUTFChars(language, nullptr);
    if (language_chars == nullptr) return nullptr;
    std::string language_value(language_chars);
    env->ReleaseStringUTFChars(language, language_chars);

    auto parameters = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    parameters.n_threads = std::max(1U, std::min(4U, std::thread::hardware_concurrency()));
    parameters.translate = false;
    parameters.no_context = true;
    parameters.no_timestamps = true;
    parameters.single_segment = false;
    parameters.print_progress = false;
    parameters.print_realtime = false;
    parameters.print_special = false;
    parameters.suppress_blank = true;
    parameters.language = language_value.empty() ? "auto" : language_value.c_str();
    parameters.beam_search.beam_size = 5;
    const char *english_prompt =
        "AudioFreedom sound commands: more bass, less bass, add treble, increase mids, "
        "clear vocals, less harshness, more detail, wider stage, add echo, reverb, volume leveler, normalizer, "
        "equalizer, immersive field, output protection, "
        "load profile, undo, cancel, help, what can you do, bass help, equalizer help, profiles help.";
    const char *bulgarian_prompt =
        "AudioFreedom команди за звук: повече бас, по-малко бас, добави високи, "
        "увеличи средите, ясни вокали, повече детайл, обгръщащ звук, добави ехо, "
        "реверберация, еквалайзер, "
        "зареди профил, отмени, отказ, спри, помощ, какво можеш да правиш, помощ за баса, помощ за профилите.";
    parameters.initial_prompt = !command_hints ? nullptr : language_value == "bg"
        ? bulgarian_prompt
        : (language_value == "en" ? english_prompt : nullptr);

    if (whisper_full(g_context, parameters, samples.data(), static_cast<int>(samples.size())) != 0) {
        throw_java(env, "java/lang/IllegalStateException", "Whisper could not transcribe the recording");
        return nullptr;
    }

    std::string result;
    const int segments = whisper_full_n_segments(g_context);
    for (int index = 0; index < segments; ++index) {
        if (whisper_full_get_segment_no_speech_prob(g_context, index) >= 0.6F) continue;
        const char *text = whisper_full_get_segment_text(g_context, index);
        if (text != nullptr) result.append(text);
    }
    const std::string cleaned = trim(result);
    return env->NewStringUTF(cleaned.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_svetlio_audiofreedom_voice_runtime_LocalWhisperRuntime_nativeUnload(
        JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_svetlio_audiofreedom_voice_runtime_LocalVoiceActivityDetector_nativeCreate(
        JNIEnv *env, jobject, jstring path) {
    whisper_log_set(runtime_log, nullptr);
    const char *characters = env->GetStringUTFChars(path, nullptr);
    if (characters == nullptr) return 0;
    auto parameters = whisper_vad_default_context_params();
    parameters.n_threads = 1;
    parameters.use_gpu = false;
    auto *context = whisper_vad_init_from_file_with_params(characters, parameters);
    env->ReleaseStringUTFChars(path, characters);
    if (context == nullptr) {
        throw_java(env, "java/io/IOException", "Unable to load the speech detector");
        return 0;
    }
    // The streaming API preserves recurrent state; start each recording from zero.
    whisper_vad_reset_state(context);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(context));
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_svetlio_audiofreedom_voice_runtime_LocalVoiceActivityDetector_nativeProbability(
        JNIEnv *env, jobject, jlong handle, jshortArray pcm, jint count) {
    auto *context = reinterpret_cast<whisper_vad_context *>(static_cast<intptr_t>(handle));
    if (context == nullptr || count < 1 || count > 512 || count > env->GetArrayLength(pcm)) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid speech detector frame");
        return 0.0F;
    }
    jshort input[512] = {};
    float samples[512] = {};
    env->GetShortArrayRegion(pcm, 0, count, input);
    if (env->ExceptionCheck()) return 0.0F;
    for (int index = 0; index < count; ++index) samples[index] = input[index] / 32768.0F;
    if (!whisper_vad_detect_speech_no_reset(context, samples, 512)) {
        throw_java(env, "java/lang/IllegalStateException", "Speech detection failed");
        return 0.0F;
    }
    const int probabilities = whisper_vad_n_probs(context);
    return probabilities > 0 ? whisper_vad_probs(context)[probabilities - 1] : 0.0F;
}

extern "C" JNIEXPORT void JNICALL
Java_com_svetlio_audiofreedom_voice_runtime_LocalVoiceActivityDetector_nativeFree(
        JNIEnv *, jobject, jlong handle) {
    if (handle != 0) whisper_vad_free(
        reinterpret_cast<whisper_vad_context *>(static_cast<intptr_t>(handle)));
}
