#pragma once

#include "audiofreedom/protocol.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <span>

namespace audiofreedom::protocol {

inline constexpr std::size_t kWireHeaderSize = 16;
inline constexpr std::size_t kWirePayloadCapacity = 24;
inline constexpr std::size_t kWireMessageSize = kWireHeaderSize + kWirePayloadCapacity;

struct WireMessage final {
    ParameterId parameter_id = ParameterId::kProtocolVersion;
    std::array<std::uint8_t, kWirePayloadCapacity> payload{};
    std::uint32_t payload_size = 0;
};

using WireBytes = std::array<std::uint8_t, kWireMessageSize>;

namespace detail {

constexpr void put_u16(std::span<std::uint8_t> output, const std::size_t offset,
                       const std::uint16_t value) noexcept {
    output[offset] = static_cast<std::uint8_t>(value);
    output[offset + 1] = static_cast<std::uint8_t>(value >> 8U);
}

constexpr void put_u32(std::span<std::uint8_t> output, const std::size_t offset,
                       const std::uint32_t value) noexcept {
    for (std::size_t index = 0; index < 4; ++index) {
        output[offset + index] = static_cast<std::uint8_t>(value >> (index * 8U));
    }
}

constexpr void put_u64(std::span<std::uint8_t> output, const std::size_t offset,
                       const std::uint64_t value) noexcept {
    for (std::size_t index = 0; index < 8; ++index) {
        output[offset + index] = static_cast<std::uint8_t>(value >> (index * 8U));
    }
}

constexpr std::uint16_t get_u16(const std::span<const std::uint8_t> input,
                                const std::size_t offset) noexcept {
    return static_cast<std::uint16_t>(input[offset]) |
           static_cast<std::uint16_t>(input[offset + 1]) << 8U;
}

constexpr std::uint32_t get_u32(const std::span<const std::uint8_t> input,
                                const std::size_t offset) noexcept {
    std::uint32_t value = 0;
    for (std::size_t index = 0; index < 4; ++index) {
        value |= static_cast<std::uint32_t>(input[offset + index]) << (index * 8U);
    }
    return value;
}

constexpr std::uint64_t get_u64(const std::span<const std::uint8_t> input,
                                const std::size_t offset) noexcept {
    std::uint64_t value = 0;
    for (std::size_t index = 0; index < 8; ++index) {
        value |= static_cast<std::uint64_t>(input[offset + index]) << (index * 8U);
    }
    return value;
}

}  // namespace detail

constexpr std::optional<WireBytes> encode_message(const WireMessage& message) noexcept {
    if (message.payload_size > kWirePayloadCapacity) {
        return std::nullopt;
    }

    WireBytes bytes{};
    detail::put_u32(bytes, 0, kMagic);
    detail::put_u16(bytes, 4, kProtocolMajor);
    detail::put_u16(bytes, 6, kProtocolMinor);
    detail::put_u32(bytes, 8, static_cast<std::uint32_t>(message.parameter_id));
    detail::put_u32(bytes, 12, message.payload_size);
    for (std::size_t index = 0; index < message.payload_size; ++index) {
        bytes[kWireHeaderSize + index] = message.payload[index];
    }
    return bytes;
}

constexpr std::optional<WireMessage> decode_message(
        const std::span<const std::uint8_t> bytes) noexcept {
    if (bytes.size() != kWireMessageSize || detail::get_u32(bytes, 0) != kMagic ||
        detail::get_u16(bytes, 4) != kProtocolMajor) {
        return std::nullopt;
    }

    const auto payload_size = detail::get_u32(bytes, 12);
    if (payload_size > kWirePayloadCapacity) {
        return std::nullopt;
    }

    WireMessage message;
    message.parameter_id = static_cast<ParameterId>(detail::get_u32(bytes, 8));
    message.payload_size = payload_size;
    for (std::size_t index = 0; index < payload_size; ++index) {
        message.payload[index] = bytes[kWireHeaderSize + index];
    }
    return message;
}

constexpr WireMessage make_query(const ParameterId parameter_id) noexcept {
    return {.parameter_id = parameter_id};
}

constexpr WireMessage make_protocol_version() noexcept {
    WireMessage message{.parameter_id = ParameterId::kProtocolVersion, .payload_size = 4};
    detail::put_u16(message.payload, 0, kProtocolMajor);
    detail::put_u16(message.payload, 2, kProtocolMinor);
    return message;
}

constexpr WireMessage make_enabled(const bool enabled) noexcept {
    WireMessage message{.parameter_id = ParameterId::kEnabled, .payload_size = 1};
    message.payload[0] = enabled ? 1 : 0;
    return message;
}

constexpr WireMessage make_preamp(const std::int32_t millibels) noexcept {
    WireMessage message{.parameter_id = ParameterId::kPreampMillibels, .payload_size = 4};
    detail::put_u32(message.payload, 0, static_cast<std::uint32_t>(millibels));
    return message;
}

constexpr WireMessage make_equalizer_enabled(const bool enabled) noexcept {
    WireMessage message{.parameter_id = ParameterId::kEqualizerEnabled, .payload_size = 1};
    message.payload[0] = enabled ? 1 : 0;
    return message;
}

constexpr WireMessage make_equalizer_band(const std::uint32_t band,
                                           const std::int32_t millibels) noexcept {
    WireMessage message{
            .parameter_id = ParameterId::kEqualizerBandMillibels,
            .payload_size = 8,
    };
    detail::put_u32(message.payload, 0, band);
    detail::put_u32(message.payload, 4, static_cast<std::uint32_t>(millibels));
    return message;
}

constexpr WireMessage make_equalizer_configuration(
        const EqualizerConfiguration& configuration) noexcept {
    WireMessage message{
            .parameter_id = ParameterId::kEqualizerConfiguration,
            .payload_size = 24,
    };
    detail::put_u16(message.payload, 0,
                    static_cast<std::uint16_t>(configuration.preamp_millibels));
    message.payload[2] = configuration.enabled ? 1 : 0;
    message.payload[3] = 0;
    for (std::size_t band = 0; band < configuration.band_millibels.size(); ++band) {
        detail::put_u16(message.payload, 4 + band * 2,
                        static_cast<std::uint16_t>(configuration.band_millibels[band]));
    }
    return message;
}

constexpr WireMessage make_limiter_configuration(
        const LimiterConfiguration& configuration) noexcept {
    WireMessage message{
            .parameter_id = ParameterId::kLimiterConfiguration,
            .payload_size = 12,
    };
    message.payload[0] = configuration.enabled ? 1 : 0;
    detail::put_u32(message.payload, 4,
                    static_cast<std::uint32_t>(configuration.threshold_millibels));
    detail::put_u32(message.payload, 8, configuration.release_milliseconds);
    return message;
}

constexpr WireMessage make_dynamic_bass_configuration(
        const DynamicBassConfiguration& configuration) noexcept {
    WireMessage message{
            .parameter_id = ParameterId::kDynamicBassConfiguration,
            .payload_size = 16,
    };
    message.payload[0] = configuration.enabled ? 1 : 0;
    detail::put_u32(message.payload, 4,
                    static_cast<std::uint32_t>(configuration.boost_millibels));
    detail::put_u32(message.payload, 8, configuration.cutoff_hz);
    detail::put_u32(message.payload, 12, configuration.dynamics_percent);
    return message;
}

constexpr WireMessage make_detail_recovery_configuration(
        const DetailRecoveryConfiguration& configuration) noexcept {
    WireMessage message{
            .parameter_id = ParameterId::kDetailRecoveryConfiguration,
            .payload_size = 16,
    };
    message.payload[0] = configuration.enabled ? 1 : 0;
    detail::put_u32(message.payload, 4, configuration.amount_percent);
    detail::put_u32(message.payload, 8, configuration.focus_hz);
    detail::put_u32(message.payload, 12, configuration.transients_percent);
    return message;
}

constexpr WireMessage make_immersive_field_configuration(
        const ImmersiveFieldConfiguration& configuration) noexcept {
    WireMessage message{
            .parameter_id = ParameterId::kImmersiveFieldConfiguration,
            .payload_size = 20,
    };
    message.payload[0] = configuration.enabled ? 1 : 0;
    detail::put_u32(message.payload, 4, configuration.amount_percent);
    detail::put_u32(message.payload, 8, configuration.width_percent);
    detail::put_u32(message.payload, 12, configuration.center_percent);
    detail::put_u32(message.payload, 16, configuration.room_percent);
    return message;
}

constexpr WireMessage make_output_metrics(const OutputMetrics& metrics) noexcept {
    WireMessage message{.parameter_id = ParameterId::kOutputMetrics, .payload_size = 12};
    detail::put_u32(message.payload, 0,
                    static_cast<std::uint32_t>(metrics.input_peak_millibels));
    detail::put_u32(message.payload, 4,
                    static_cast<std::uint32_t>(metrics.output_peak_millibels));
    detail::put_u32(message.payload, 8,
                    static_cast<std::uint32_t>(metrics.gain_reduction_millibels));
    return message;
}

constexpr WireMessage make_driver_status(const DriverStatus& status) noexcept {
    WireMessage message{.parameter_id = ParameterId::kDriverStatus, .payload_size = 20};
    detail::put_u32(message.payload, 0, static_cast<std::uint32_t>(status.state));
    detail::put_u32(message.payload, 4, status.sample_rate_hz);
    detail::put_u32(message.payload, 8, status.channel_count);
    detail::put_u64(message.payload, 12, status.processed_frames);
    return message;
}

constexpr std::optional<Version> read_protocol_version(const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kProtocolVersion || message.payload_size != 4) {
        return std::nullopt;
    }
    return Version{
            .major = detail::get_u16(message.payload, 0),
            .minor = detail::get_u16(message.payload, 2),
    };
}

constexpr std::optional<bool> read_enabled(const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kEnabled || message.payload_size != 1 ||
        message.payload[0] > 1) {
        return std::nullopt;
    }
    return message.payload[0] == 1;
}

constexpr std::optional<std::int32_t> read_preamp(const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kPreampMillibels || message.payload_size != 4) {
        return std::nullopt;
    }
    return static_cast<std::int32_t>(detail::get_u32(message.payload, 0));
}

constexpr std::optional<bool> read_equalizer_enabled(const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kEqualizerEnabled || message.payload_size != 1 ||
        message.payload[0] > 1) {
        return std::nullopt;
    }
    return message.payload[0] == 1;
}

constexpr std::optional<EqualizerBandGain> read_equalizer_band(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kEqualizerBandMillibels ||
        message.payload_size != 8) {
        return std::nullopt;
    }
    return EqualizerBandGain{
            .band = detail::get_u32(message.payload, 0),
            .millibels = static_cast<std::int32_t>(detail::get_u32(message.payload, 4)),
    };
}

constexpr std::optional<EqualizerConfiguration> read_equalizer_configuration(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kEqualizerConfiguration ||
        message.payload_size != 24 || message.payload[2] > 1) {
        return std::nullopt;
    }

    EqualizerConfiguration configuration;
    configuration.preamp_millibels =
            static_cast<std::int16_t>(detail::get_u16(message.payload, 0));
    configuration.enabled = message.payload[2] == 1;
    for (std::size_t band = 0; band < configuration.band_millibels.size(); ++band) {
        configuration.band_millibels[band] =
                static_cast<std::int16_t>(detail::get_u16(message.payload, 4 + band * 2));
    }
    return configuration;
}

constexpr std::optional<LimiterConfiguration> read_limiter_configuration(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kLimiterConfiguration ||
        message.payload_size != 12 || message.payload[0] > 1) {
        return std::nullopt;
    }
    return LimiterConfiguration{
            .enabled = message.payload[0] == 1,
            .threshold_millibels =
                    static_cast<std::int32_t>(detail::get_u32(message.payload, 4)),
            .release_milliseconds = detail::get_u32(message.payload, 8),
    };
}

constexpr std::optional<DynamicBassConfiguration> read_dynamic_bass_configuration(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kDynamicBassConfiguration ||
        message.payload_size != 16 || message.payload[0] > 1) {
        return std::nullopt;
    }
    return DynamicBassConfiguration{
            .enabled = message.payload[0] == 1,
            .boost_millibels =
                    static_cast<std::int32_t>(detail::get_u32(message.payload, 4)),
            .cutoff_hz = detail::get_u32(message.payload, 8),
            .dynamics_percent = detail::get_u32(message.payload, 12),
    };
}

constexpr std::optional<DetailRecoveryConfiguration> read_detail_recovery_configuration(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kDetailRecoveryConfiguration ||
        message.payload_size != 16 || message.payload[0] > 1) {
        return std::nullopt;
    }
    return DetailRecoveryConfiguration{
            .enabled = message.payload[0] == 1,
            .amount_percent = detail::get_u32(message.payload, 4),
            .focus_hz = detail::get_u32(message.payload, 8),
            .transients_percent = detail::get_u32(message.payload, 12),
    };
}

constexpr std::optional<ImmersiveFieldConfiguration> read_immersive_field_configuration(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kImmersiveFieldConfiguration ||
        message.payload_size != 20 || message.payload[0] > 1) {
        return std::nullopt;
    }
    return ImmersiveFieldConfiguration{
            .enabled = message.payload[0] == 1,
            .amount_percent = detail::get_u32(message.payload, 4),
            .width_percent = detail::get_u32(message.payload, 8),
            .center_percent = detail::get_u32(message.payload, 12),
            .room_percent = detail::get_u32(message.payload, 16),
    };
}

constexpr std::optional<OutputMetrics> read_output_metrics(
        const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kOutputMetrics || message.payload_size != 12) {
        return std::nullopt;
    }
    return OutputMetrics{
            .input_peak_millibels =
                    static_cast<std::int32_t>(detail::get_u32(message.payload, 0)),
            .output_peak_millibels =
                    static_cast<std::int32_t>(detail::get_u32(message.payload, 4)),
            .gain_reduction_millibels =
                    static_cast<std::int32_t>(detail::get_u32(message.payload, 8)),
    };
}

constexpr std::optional<DriverStatus> read_driver_status(const WireMessage& message) noexcept {
    if (message.parameter_id != ParameterId::kDriverStatus || message.payload_size != 20) {
        return std::nullopt;
    }

    const auto state = detail::get_u32(message.payload, 0);
    if (state > static_cast<std::uint32_t>(ProcessingState::kError)) {
        return std::nullopt;
    }

    DriverStatus status;
    status.state = static_cast<ProcessingState>(state);
    status.sample_rate_hz = detail::get_u32(message.payload, 4);
    status.channel_count = detail::get_u32(message.payload, 8);
    status.processed_frames = detail::get_u64(message.payload, 12);
    return status;
}

}  // namespace audiofreedom::protocol
