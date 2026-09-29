add_executable(audiofreedom_tests
    engine_test.cpp
)

target_link_libraries(audiofreedom_tests PRIVATE audiofreedom_dsp)
target_compile_features(audiofreedom_tests PRIVATE cxx_std_20)

if(MSVC)
    # Several cases intentionally keep multiple fixed-delay DSP engines on the stack.
    target_link_options(audiofreedom_tests PRIVATE /STACK:8388608)
endif()

add_test(NAME audiofreedom_tests COMMAND audiofreedom_tests)
