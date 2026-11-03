#pragma once

#include <cstdint>

namespace audiofreedom::android {

// Android positional output masks put front-left/front-right in the lowest bits.
// Indexed layouts carry no speaker identities, even when their low bits are set.
constexpr bool has_matching_front_pair(std::uint32_t input_mask,
                                       std::uint32_t output_mask,
                                       bool both_positional) noexcept {
    constexpr std::uint32_t front_pair = 0x3;
    return both_positional && input_mask == output_mask &&
           (input_mask & front_pair) == front_pair;
}

}  // namespace audiofreedom::android
