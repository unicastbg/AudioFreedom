add_executable(audiofreedom_tests
    engine_test.cpp
)

target_link_libraries(audiofreedom_tests PRIVATE audiofreedom_dsp)
target_compile_features(audiofreedom_tests PRIVATE cxx_std_20)

add_test(NAME audiofreedom_tests COMMAND audiofreedom_tests)
