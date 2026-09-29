cmake_minimum_required(VERSION 3.22.1)

project(audiofreedom-assistant LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_link_options(
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

set(LLAMA_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp submodule is missing. Run git submodule update --init --recursive.")
endif()

add_subdirectory("${LLAMA_SOURCE_DIR}" llama-build EXCLUDE_FROM_ALL)

add_library(audiofreedom-assistant SHARED audiofreedom_assistant.cpp)

target_include_directories(audiofreedom-assistant PRIVATE
    "${LLAMA_SOURCE_DIR}/include"
    "${LLAMA_SOURCE_DIR}/ggml/include"
)

target_link_libraries(audiofreedom-assistant
    llama
    android
    log
)
