#pragma once

#include <array>
#include <cstdint>
#include <type_traits>

namespace audiofreedom::protocol {

inline constexpr std::uint32_t kMagic = 0x31584641;  // "AFX1" in little-endian wire order.
inline constexpr std::uint16_t kProtocolMajor = 1;
inline constexpr std::uint16_t kProtocolMinor = 5;

enum class ParameterId : std::uint32_t {
    kProtocolVersion = 0x0000,
    kEnabled = 0x1000,
    kPreampMillibels = 0x1001,
    kEqualizerEnabled = 0x1100,
    kEqualizerBandMillibels = 0x1101,
    kEqualizerConfiguration = 0x1102,
    kLimiterConfiguration = 0x1200,
    kDynamicBassConfiguration = 0x1300,
    kDetailRecoveryConfiguration = 0x1400,
    kImmersiveFieldConfiguration = 0x1500,
    kDriverStatus = 0x2000,
    kOutputMetrics = 0x2001,
};

struct EqualizerBandGain final {
    std::uint32_t band = 0;
    std::int32_t millibels = 0;
};

struct EqualizerConfiguration final {
    std::int32_t preamp_millibels = -1200;
    bool enabled = false;
    std::array<std::int32_t, 10> band_millibels{};
};

struct LimiterConfiguration final {
    bool enabled = true;
    std::int32_t threshold_millibels = -100;
    std::uint32_t release_milliseconds = 120;
};

struct DynamicBassConfiguration final {
    bool enabled = false;
    std::int32_t boost_millibels = 600;
    std::uint32_t cutoff_hz = 80;
    std::uint32_t dynamics_percent = 70;
};

struct DetailRecoveryConfiguration final {
    bool enabled = false;
    std::uint32_t amount_percent = 55;
    std::uint32_t focus_hz = 6000;
    std::uint32_t transients_percent = 75;
};

struct ImmersiveFieldConfiguration final {
    bool enabled = false;
    std::uint32_t amount_percent = 55;
    std::uint32_t width_percent = 60;
    std::uint32_t center_percent = 60;
    std::uint32_t room_percent = 25;
};

struct OutputMetrics final {
    std::int32_t input_peak_millibels = -12000;
    std::int32_t output_peak_millibels = -12000;
    std::int32_t gain_reduction_millibels = 0;
};

enum class ProcessingState : std::uint32_t {
    kUnavailable = 0,
    kIdle = 1,
    kProcessing = 2,
    kError = 3,
};

struct Version final {
    std::uint16_t major = kProtocolMajor;
    std::uint16_t minor = kProtocolMinor;
};

struct DriverStatus final {
    std::uint32_t magic = kMagic;
    Version protocol{};
    ProcessingState state = ProcessingState::kUnavailable;
    std::uint32_t sample_rate_hz = 0;
    std::uint32_t channel_count = 0;
    std::uint64_t processed_frames = 0;
};

static_assert(std::is_trivially_copyable_v<Version>);
static_assert(std::is_trivially_copyable_v<DriverStatus>);
static_assert(std::is_trivially_copyable_v<EqualizerBandGain>);
static_assert(std::is_trivially_copyable_v<EqualizerConfiguration>);
static_assert(std::is_trivially_copyable_v<LimiterConfiguration>);
static_assert(std::is_trivially_copyable_v<DynamicBassConfiguration>);
static_assert(std::is_trivially_copyable_v<DetailRecoveryConfiguration>);
static_assert(std::is_trivially_copyable_v<ImmersiveFieldConfiguration>);
static_assert(std::is_trivially_copyable_v<OutputMetrics>);

}  // namespace audiofreedom::protocol
