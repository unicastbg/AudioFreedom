#pragma once

#include <cstdint>

namespace aidl::android::hardware::audio::effect {

inline constexpr int32_t kReopenSupportedVersion = 2;
inline constexpr uint32_t kEventFlagNotEmpty = 0x1;
inline constexpr uint32_t kEventFlagDataMqUpdate = 0x1U << 10;
inline constexpr uint32_t kEventFlagDataMqNotEmpty = 0x1U << 11;

}  // namespace aidl::android::hardware::audio::effect

#ifndef FALLTHROUGH_INTENDED
#define FALLTHROUGH_INTENDED [[fallthrough]]
#endif
