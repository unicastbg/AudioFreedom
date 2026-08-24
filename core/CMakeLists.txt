add_library(audiofreedom_dsp STATIC
    src/engine.cpp
)

target_include_directories(audiofreedom_dsp
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/../protocol/include
)

target_compile_features(audiofreedom_dsp PUBLIC cxx_std_20)

if(MSVC)
    target_compile_options(audiofreedom_dsp PRIVATE /W4 /permissive-)
else()
    target_compile_options(audiofreedom_dsp PRIVATE -Wall -Wextra -Wpedantic)
endif()
