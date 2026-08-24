cmake_minimum_required(VERSION 3.22.1)
project(audiofreedom_controller_abi LANGUAGES CXX)

set(AUDIOFREEDOM_DEVICE_LIBS "" CACHE PATH "Path containing target Android shared libraries")
if(NOT IS_DIRECTORY "${AUDIOFREEDOM_DEVICE_LIBS}")
    message(FATAL_ERROR "AUDIOFREEDOM_DEVICE_LIBS is missing")
endif()

file(GLOB NDK_HOST_PREBUILTS "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/*")
list(GET NDK_HOST_PREBUILTS 0 NDK_HOST_PREBUILT)
set(AUDIOFREEDOM_LIBCXX_HEADERS "${CMAKE_CURRENT_LIST_DIR}/../standalone/include/c++/v1")

function(import_device_library target filename)
    add_library(${target} SHARED IMPORTED)
    set_target_properties(${target} PROPERTIES
        IMPORTED_LOCATION "${AUDIOFREEDOM_DEVICE_LIBS}/${filename}"
    )
endfunction()

import_device_library(audioclient libaudioclient.so)
import_device_library(android_utils libutils.so)
import_device_library(device_cxx libc++.so)

add_executable(audiofreedom-controller AudioFreedomControllerAbi.cpp)
target_compile_features(audiofreedom-controller PRIVATE cxx_std_20)
target_compile_options(audiofreedom-controller PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -nostdlib++
)
target_link_options(audiofreedom-controller PRIVATE
    -nostdlib++
    "LINKER:-z,max-page-size=16384"
)
target_include_directories(audiofreedom-controller SYSTEM PRIVATE
    "${AUDIOFREEDOM_LIBCXX_HEADERS}"
    "${NDK_HOST_PREBUILT}/sysroot/usr/include/c++/v1"
)
target_link_libraries(audiofreedom-controller PRIVATE
    audioclient
    android_utils
    device_cxx
    dl
    m
)
