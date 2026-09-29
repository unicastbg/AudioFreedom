# Third-party notices

## Android Open Source Project

The staged AIDL effect backend compiles Android Open Source Project effect-framework
sources referenced by `effectCommonFile`. The legacy compatibility header at
`platform/legacy/include/hardware/audio_effect.h` also originates from AOSP.

Copyright The Android Open Source Project. Licensed under the Apache License 2.0.

Source: <https://android.googlesource.com/platform/hardware/interfaces/+/android16-release/audio/aidl/default/>

License: [Apache License 2.0](LICENSES/Apache-2.0.txt)

## AndroidX and Jetpack Compose

The companion app uses AndroidX Activity, Core, Material, and Jetpack Compose libraries.
These libraries are Copyright The Android Open Source Project and licensed under the
Apache License 2.0.

Source: <https://android.googlesource.com/platform/frameworks/support/>

License: [Apache License 2.0](LICENSES/Apache-2.0.txt)

## Android Hidden Api Bypass

The AudioFreedom companion app uses Android Hidden Api Bypass to provide a device-local
fallback on Android frameworks that hide the `AudioEffect` parameter methods needed to
control the installed effect.

Copyright 2021-2025 LSPosed. Licensed under the Apache License 2.0.

Source: <https://github.com/LSPosed/AndroidHiddenApiBypass>

License: [Apache License 2.0](LICENSES/Apache-2.0.txt)

## llama.cpp

The optional local language-model runtime embeds selected llama.cpp sources at the pinned
commit recorded by the `third_party/llama.cpp` Git submodule.

Copyright 2023-2026 The ggml authors. Licensed under the MIT License.

Source: <https://github.com/ggml-org/llama.cpp>

License: [MIT License](LICENSES/MIT.txt)

## whisper.cpp

The optional offline speech-recognition runtime embeds selected whisper.cpp sources at the
pinned commit recorded by the `third_party/whisper.cpp` Git submodule.

Copyright 2023-2026 The ggml authors. Licensed under the MIT License.

Source: <https://github.com/ggml-org/whisper.cpp>

License: [MIT License](LICENSES/MIT.txt)

## Optional model downloads

AudioFreedom does not bundle model weights in the APK. When requested by the user, it can
download a checksum-pinned Qwen3 0.6B GGUF model from ggml-org's conversion repository and
the multilingual Whisper Tiny model from the whisper.cpp model repository.

Qwen3 0.6B source: <https://huggingface.co/Qwen/Qwen3-0.6B>

Qwen3 GGUF conversion: <https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF>

Whisper model source: <https://github.com/openai/whisper>

Whisper GGML conversion: <https://huggingface.co/ggerganov/whisper.cpp>

## AudioFreedom original source

The notices above apply only to the identified third-party portions. AudioFreedom's
original source remains subject to the repository's all-rights-reserved `LICENSE`.
