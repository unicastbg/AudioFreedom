cmake_minimum_required(VERSION 3.22.1)
project(audiofreedom_android_effect LANGUAGES C CXX)

set(AUDIOFREEDOM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../..")
set(AUDIOFREEDOM_SUPPORT_ROOT ""
    CACHE PATH "Path containing generated AIDL headers and AOSP support headers")
set(AUDIOFREEDOM_DEVICE_LIBS ""
    CACHE PATH "Path containing ABI-matched Android shared libraries")
option(AUDIOFREEDOM_STATIC_AIDL
    "Embed the generated Effects HAL binder implementation in the effect library" OFF)

if(NOT IS_DIRECTORY "${AUDIOFREEDOM_SUPPORT_ROOT}")
    message(FATAL_ERROR "AUDIOFREEDOM_SUPPORT_ROOT is missing")
endif()
if(NOT IS_DIRECTORY "${AUDIOFREEDOM_DEVICE_LIBS}")
    message(FATAL_ERROR "AUDIOFREEDOM_DEVICE_LIBS is missing")
endif()

set(EFFECT_COMMON "${AUDIOFREEDOM_SUPPORT_ROOT}/effect-default-v2")
file(GLOB NDK_HOST_PREBUILTS "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/*")
list(GET NDK_HOST_PREBUILTS 0 NDK_HOST_PREBUILT)

function(import_device_library target filename)
    add_library(${target} SHARED IMPORTED)
    set_target_properties(${target} PROPERTIES
        IMPORTED_LOCATION "${AUDIOFREEDOM_DEVICE_LIBS}/${filename}"
    )
endfunction()

import_device_library(aidl_audio_effect android.hardware.audio.effect-V3-ndk.so)
import_device_library(aidl_audio_common android.hardware.audio.common-V3-ndk.so)
import_device_library(aidl_media_audio android.media.audio.common.types-V6-ndk.so)
import_device_library(aidl_common android.hardware.common-V2-ndk.so)
import_device_library(aidl_fmq android.hardware.common.fmq-V1-ndk.so)
import_device_library(audioaidlcommon libaudioaidlcommon.so)
import_device_library(audioutils libaudioutils.so)
import_device_library(android_base libbase.so)
import_device_library(binder_ndk libbinder_ndk.so)
import_device_library(cutils libcutils.so)
import_device_library(fmq libfmq.so)
import_device_library(android_log liblog.so)
import_device_library(android_utils libutils.so)
import_device_library(device_cxx libc++.so)

set(AUDIOFREEDOM_EFFECT_BINDINGS)
set(AUDIOFREEDOM_EFFECT_BINDINGS_INCLUDE)
if(AUDIOFREEDOM_STATIC_AIDL)
    set(AUDIOFREEDOM_EFFECT_BINDINGS_ROOT
        "${AUDIOFREEDOM_SUPPORT_ROOT}/generated-effect-v3")
    file(GLOB_RECURSE AUDIOFREEDOM_EFFECT_BINDINGS CONFIGURE_DEPENDS
        "${AUDIOFREEDOM_EFFECT_BINDINGS_ROOT}/src/android/hardware/audio/effect/*.cpp"
        "${AUDIOFREEDOM_EFFECT_BINDINGS_ROOT}/src/android/media/audio/eraser/*.cpp"
    )
    list(FILTER AUDIOFREEDOM_EFFECT_BINDINGS EXCLUDE REGEX "/IFactory\\.cpp$")
    set(AUDIOFREEDOM_EFFECT_BINDINGS_INCLUDE
        "${AUDIOFREEDOM_EFFECT_BINDINGS_ROOT}/include")
endif()

add_library(audiofreedomfx SHARED
    "${AUDIOFREEDOM_ROOT}/platform/aidl/AudioFreedomEffect.cpp"
    "${AUDIOFREEDOM_ROOT}/core/src/engine.cpp"
    "${EFFECT_COMMON}/EffectContext.cpp"
    "${EFFECT_COMMON}/EffectImpl.cpp"
    "${EFFECT_COMMON}/EffectThread.cpp"
    ${AUDIOFREEDOM_EFFECT_BINDINGS}
)

target_compile_features(audiofreedomfx PRIVATE cxx_std_20)
target_compile_definitions(audiofreedomfx PRIVATE BACKEND_NDK)
target_compile_options(audiofreedomfx PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fno-rtti
    -nostdlib++
)
target_link_options(audiofreedomfx PRIVATE
    -nostdlib++
    "LINKER:-z,max-page-size=16384"
    "LINKER:-soname,libaudiofreedomfx.so"
)

target_include_directories(audiofreedomfx PRIVATE
    ${AUDIOFREEDOM_EFFECT_BINDINGS_INCLUDE}
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${AUDIOFREEDOM_ROOT}/platform/aidl"
    "${AUDIOFREEDOM_ROOT}/core/include"
    "${AUDIOFREEDOM_ROOT}/protocol/include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/generated/include"
    "${EFFECT_COMMON}"
    "${EFFECT_COMMON}/include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/libfmq-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/libfmq-base"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/base-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/utils-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/liblog-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/cutils-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/system-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/audio-system-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/hardware-audio-aidl/common/include"
)
target_include_directories(audiofreedomfx SYSTEM PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/include/c++/v1"
    "${NDK_HOST_PREBUILT}/sysroot/usr/include/c++/v1"
)

target_link_libraries(audiofreedomfx PRIVATE
    aidl_media_audio
    aidl_common
    aidl_fmq
    audioaidlcommon
    audioutils
    android_base
    binder_ndk
    cutils
    fmq
    android_log
    android_utils
    device_cxx
    atomic
    dl
    m
)
if(NOT AUDIOFREEDOM_STATIC_AIDL)
    target_link_libraries(audiofreedomfx PRIVATE aidl_audio_effect)
else()
    target_link_libraries(audiofreedomfx PRIVATE aidl_audio_common)
endif()

add_executable(audiofreedom_dlopen_probe EffectLibraryProbe.c)
target_compile_options(audiofreedom_dlopen_probe PRIVATE -Wall -Wextra -Wpedantic)
target_link_options(audiofreedom_dlopen_probe PRIVATE "LINKER:-z,max-page-size=16384")
target_link_libraries(audiofreedom_dlopen_probe PRIVATE dl)

add_executable(audiofreedom_factory_probe FactoryProbe.cpp)
target_compile_features(audiofreedom_factory_probe PRIVATE cxx_std_20)
target_compile_options(audiofreedom_factory_probe PRIVATE -Wall -Wextra -Wpedantic -nostdlib++)
target_link_options(audiofreedom_factory_probe PRIVATE
    -nostdlib++
    "LINKER:-z,max-page-size=16384"
)
target_include_directories(audiofreedom_factory_probe PRIVATE
    "${AUDIOFREEDOM_SUPPORT_ROOT}/generated/include"
)
target_include_directories(audiofreedom_factory_probe SYSTEM PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/include/c++/v1"
    "${NDK_HOST_PREBUILT}/sysroot/usr/include/c++/v1"
)
target_link_libraries(audiofreedom_factory_probe PRIVATE
    aidl_audio_effect
    aidl_media_audio
    binder_ndk
    device_cxx
    dl
    m
)
