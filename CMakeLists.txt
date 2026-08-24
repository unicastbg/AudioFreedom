cmake_minimum_required(VERSION 3.22.1)
project(audiofreedom LANGUAGES CXX)

option(AUDIOFREEDOM_BUILD_TESTS "Build native unit tests" ON)

add_subdirectory(core)

if(AUDIOFREEDOM_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
