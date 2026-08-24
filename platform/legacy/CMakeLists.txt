cmake_minimum_required(VERSION 3.22.1)
project(audiofreedom_legacy_effect LANGUAGES CXX)

set(AUDIOFREEDOM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../..")
set(AUDIOFREEDOM_SUPPORT_ROOT "" CACHE PATH "Path containing staged AOSP system headers")
if(NOT IS_DIRECTORY "${AUDIOFREEDOM_SUPPORT_ROOT}")
    message(FATAL_ERROR "AUDIOFREEDOM_SUPPORT_ROOT is missing")
endif()

add_library(audiofreedomfx_legacy SHARED
    AudioFreedomLegacyEffect.cpp
    "${AUDIOFREEDOM_ROOT}/core/src/engine.cpp"
)
target_compile_features(audiofreedomfx_legacy PRIVATE cxx_std_20)
target_compile_options(audiofreedomfx_legacy PRIVATE -Wall -Wextra -Wpedantic -fno-rtti)
target_link_options(audiofreedomfx_legacy PRIVATE
    "LINKER:-z,max-page-size=16384"
    "LINKER:-soname,libaudiofreedomfx_legacy.so"
)
target_include_directories(audiofreedomfx_legacy PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${AUDIOFREEDOM_ROOT}/core/include"
    "${AUDIOFREEDOM_ROOT}/protocol/include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/audio-system-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/cutils-include"
)
target_link_libraries(audiofreedomfx_legacy PRIVATE log m)

add_executable(audiofreedom_legacy_probe LegacyEffectProbe.cpp)
target_compile_features(audiofreedom_legacy_probe PRIVATE cxx_std_20)
target_compile_options(audiofreedom_legacy_probe PRIVATE -Wall -Wextra -Wpedantic)
target_link_options(audiofreedom_legacy_probe PRIVATE "LINKER:-z,max-page-size=16384")
target_include_directories(audiofreedom_legacy_probe PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${AUDIOFREEDOM_ROOT}/protocol/include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/audio-system-include"
    "${AUDIOFREEDOM_SUPPORT_ROOT}/cutils-include"
)
target_link_libraries(audiofreedom_legacy_probe PRIVATE dl m)
