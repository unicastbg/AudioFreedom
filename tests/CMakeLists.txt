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

add_executable(volume_leveler_tests volume_leveler_test.cpp)
target_link_libraries(volume_leveler_tests PRIVATE audiofreedom_dsp)
target_compile_features(volume_leveler_tests PRIVATE cxx_std_20)
if(MSVC)
    target_link_options(volume_leveler_tests PRIVATE /STACK:8388608)
endif()
add_test(NAME volume_leveler_tests COMMAND volume_leveler_tests)

add_executable(playback_analysis_tests playback_analysis_test.cpp)
find_package(Threads REQUIRED)
target_link_libraries(playback_analysis_tests PRIVATE audiofreedom_dsp Threads::Threads)
target_compile_features(playback_analysis_tests PRIVATE cxx_std_20)
if(MSVC)
    target_link_options(playback_analysis_tests PRIVATE /STACK:8388608)
endif()
add_test(NAME playback_analysis_tests COMMAND playback_analysis_tests)
