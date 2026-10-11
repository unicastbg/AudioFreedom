#pragma once
#include <array>
#include <cstdint>

namespace audiofreedom {
struct PlaybackAnalysis final {
    std::int32_t input_rms = -12000;
    std::int32_t output_rms = -12000;
    std::int32_t crest = 0;
    std::int32_t correlation = 2000;
    std::uint32_t near_full_scale_ppm = 0;
    std::uint32_t sequence = 0;
    std::array<std::int16_t, 10> bands{-12000, -12000, -12000, -12000, -12000,
                                    -12000, -12000, -12000, -12000, -12000};
};
}  // namespace audiofreedom
