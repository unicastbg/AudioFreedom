cmake_minimum_required(VERSION 3.22.1)

project(audiofreedom-voice LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_link_options(
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

set(WHISPER_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp")
if(NOT EXISTS "${WHISPER_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp submodule is missing. Run git submodule update --init --recursive.")
endif()

add_subdirectory("${WHISPER_SOURCE_DIR}" whisper-build EXCLUDE_FROM_ALL)

add_library(audiofreedom-voice SHARED audiofreedom_voice.cpp)
set_target_properties(audiofreedom-voice PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    C_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)
target_include_directories(audiofreedom-voice PRIVATE "${WHISPER_SOURCE_DIR}/include")
target_link_libraries(audiofreedom-voice whisper android log)
