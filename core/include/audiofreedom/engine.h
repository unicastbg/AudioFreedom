#pragma once

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>

namespace audiofreedom {

struct StreamConfig {
    std::uint32_t sample_rate_hz = 0;
    std::uint32_t channel_count = 0;
};

struct OutputMetrics final {
    std::int32_t input_peak_millibels = -12000;
    std::int32_t output_peak_millibels = -12000;
    std::int32_t gain_reduction_millibels = 0;
};

class Engine final {
public:
    static constexpr std::size_t kEqBandCount = 10;
    static constexpr std::size_t kMaxChannelCount = 32;
    static constexpr std::int32_t kMinPreampMillibels = -2400;
    static constexpr std::int32_t kMaxPreampMillibels = 0;
    static constexpr std::int32_t kProofPreampMillibels = -1200;
    static constexpr std::int32_t kMinEqBandMillibels = -1200;
    static constexpr std::int32_t kMaxEqBandMillibels = 1200;
    static constexpr std::int32_t kMinLimiterThresholdMillibels = -600;
    static constexpr std::int32_t kMaxLimiterThresholdMillibels = 0;
    static constexpr std::int32_t kDefaultLimiterThresholdMillibels = -100;
    static constexpr std::uint32_t kMinLimiterReleaseMilliseconds = 20;
    static constexpr std::uint32_t kMaxLimiterReleaseMilliseconds = 1000;
    static constexpr std::uint32_t kDefaultLimiterReleaseMilliseconds = 120;
    static constexpr std::uint32_t kMinBassCutoffHz = 40;
    static constexpr std::uint32_t kMaxBassCutoffHz = 160;
    static constexpr std::uint32_t kDefaultBassCutoffHz = 95;
    static constexpr std::int32_t kMinBassBoostMillibels = 0;
    static constexpr std::int32_t kMaxBassBoostMillibels = 1200;
    static constexpr std::int32_t kDefaultBassBoostMillibels = 600;
    static constexpr std::uint32_t kMinBassDynamicsPercent = 0;
    static constexpr std::uint32_t kMaxBassDynamicsPercent = 100;
    static constexpr std::uint32_t kDefaultBassDynamicsPercent = 70;
    static constexpr std::uint32_t kMinDetailAmountPercent = 0;
    static constexpr std::uint32_t kMaxDetailAmountPercent = 100;
    static constexpr std::uint32_t kDefaultDetailAmountPercent = 55;
    static constexpr std::uint32_t kMinDetailFocusHz = 3000;
    static constexpr std::uint32_t kMaxDetailFocusHz = 10000;
    static constexpr std::uint32_t kDefaultDetailFocusHz = 6000;
    static constexpr std::uint32_t kMinDetailTransientsPercent = 0;
    static constexpr std::uint32_t kMaxDetailTransientsPercent = 100;
    static constexpr std::uint32_t kDefaultDetailTransientsPercent = 75;
    static constexpr std::uint32_t kMinImmersivePercent = 0;
    static constexpr std::uint32_t kMaxImmersivePercent = 100;
    static constexpr std::uint32_t kDefaultImmersiveAmountPercent = 55;
    static constexpr std::uint32_t kDefaultImmersiveWidthPercent = 60;
    static constexpr std::uint32_t kDefaultImmersiveCenterPercent = 60;
    static constexpr std::uint32_t kDefaultImmersiveRoomPercent = 25;
    static constexpr std::size_t kImmersiveDelayCapacity = 8192;
    static constexpr std::array<float, kEqBandCount> kEqBandFrequenciesHz = {
            31.25F, 62.5F, 125.0F, 250.0F, 500.0F,
            1000.0F, 2000.0F, 4000.0F, 8000.0F, 16000.0F,
    };

    Engine() noexcept;

    [[nodiscard]] bool prepare(StreamConfig config) noexcept;
    void reset() noexcept;

    void set_enabled(bool enabled) noexcept;
    [[nodiscard]] bool enabled() const noexcept;

    void set_preamp_millibels(std::int32_t millibels) noexcept;
    [[nodiscard]] std::int32_t preamp_millibels() const noexcept;

    void set_equalizer_enabled(bool enabled) noexcept;
    [[nodiscard]] bool equalizer_enabled() const noexcept;

    [[nodiscard]] bool set_eq_band_millibels(std::size_t band,
                                             std::int32_t millibels) noexcept;
    [[nodiscard]] std::int32_t eq_band_millibels(std::size_t band) const noexcept;

    void set_limiter_enabled(bool enabled) noexcept;
    [[nodiscard]] bool limiter_enabled() const noexcept;
    void set_limiter_threshold_millibels(std::int32_t millibels) noexcept;
    [[nodiscard]] std::int32_t limiter_threshold_millibels() const noexcept;
    void set_limiter_release_milliseconds(std::uint32_t milliseconds) noexcept;
    [[nodiscard]] std::uint32_t limiter_release_milliseconds() const noexcept;
    [[nodiscard]] OutputMetrics output_metrics() const noexcept;

    void set_dynamic_bass_enabled(bool enabled) noexcept;
    [[nodiscard]] bool dynamic_bass_enabled() const noexcept;
    void set_bass_cutoff_hz(std::uint32_t frequency_hz) noexcept;
    [[nodiscard]] std::uint32_t bass_cutoff_hz() const noexcept;
    void set_bass_boost_millibels(std::int32_t millibels) noexcept;
    [[nodiscard]] std::int32_t bass_boost_millibels() const noexcept;
    void set_bass_dynamics_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t bass_dynamics_percent() const noexcept;

    void set_detail_recovery_enabled(bool enabled) noexcept;
    [[nodiscard]] bool detail_recovery_enabled() const noexcept;
    void set_detail_amount_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t detail_amount_percent() const noexcept;
    void set_detail_focus_hz(std::uint32_t frequency_hz) noexcept;
    [[nodiscard]] std::uint32_t detail_focus_hz() const noexcept;
    void set_detail_transients_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t detail_transients_percent() const noexcept;

    void set_immersive_field_enabled(bool enabled) noexcept;
    [[nodiscard]] bool immersive_field_enabled() const noexcept;
    void set_immersive_amount_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t immersive_amount_percent() const noexcept;
    void set_immersive_width_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t immersive_width_percent() const noexcept;
    void set_immersive_center_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t immersive_center_percent() const noexcept;
    void set_immersive_room_percent(std::uint32_t percent) noexcept;
    [[nodiscard]] std::uint32_t immersive_room_percent() const noexcept;

    // Processes interleaved float PCM in place. No allocation or locking is performed.
    [[nodiscard]] bool process(float* samples, std::size_t frame_count) noexcept;

    [[nodiscard]] StreamConfig stream_config() const noexcept;
    [[nodiscard]] std::uint64_t processed_frames() const noexcept;

private:
    struct BiquadCoefficients final {
        float b0 = 1.0F;
        float b1 = 0.0F;
        float b2 = 0.0F;
        float a1 = 0.0F;
        float a2 = 0.0F;
    };

    struct BiquadState final {
        float z1 = 0.0F;
        float z2 = 0.0F;
    };

    void refresh_equalizer() noexcept;
    void clear_equalizer_state() noexcept;

    std::atomic<bool> enabled_{false};
    // Unity by default. kProofPreampMillibels (-12 dB) was the milestone "prove we can
    // attenuate" value; leaving it as the runtime default made every enabled session play
    // at ~25% amplitude, so the EQ/effects sounded far weaker than a unity-gain EQ. Cuts
    // for boost headroom are applied per-preset (and can be automated) rather than baked in.
    std::atomic<std::int32_t> preamp_millibels_{0};
    std::atomic<bool> equalizer_enabled_{false};
    std::array<std::atomic<std::int32_t>, kEqBandCount> eq_band_millibels_{};
    std::atomic<std::uint64_t> equalizer_revision_{1};
    std::atomic<std::uint64_t> processed_frames_{0};
    std::atomic<bool> limiter_enabled_{true};
    std::atomic<std::int32_t> limiter_threshold_millibels_{
            kDefaultLimiterThresholdMillibels};
    std::atomic<std::uint32_t> limiter_release_milliseconds_{
            kDefaultLimiterReleaseMilliseconds};
    std::atomic<std::int32_t> input_peak_millibels_{-12000};
    std::atomic<std::int32_t> output_peak_millibels_{-12000};
    std::atomic<std::int32_t> gain_reduction_millibels_{0};
    std::atomic<bool> dynamic_bass_enabled_{false};
    std::atomic<std::uint32_t> bass_cutoff_hz_{kDefaultBassCutoffHz};
    std::atomic<std::int32_t> bass_boost_millibels_{kDefaultBassBoostMillibels};
    std::atomic<std::uint32_t> bass_dynamics_percent_{kDefaultBassDynamicsPercent};
    std::atomic<bool> detail_recovery_enabled_{false};
    std::atomic<std::uint32_t> detail_amount_percent_{kDefaultDetailAmountPercent};
    std::atomic<std::uint32_t> detail_focus_hz_{kDefaultDetailFocusHz};
    std::atomic<std::uint32_t> detail_transients_percent_{
            kDefaultDetailTransientsPercent};
    std::atomic<bool> immersive_field_enabled_{false};
    std::atomic<std::uint32_t> immersive_amount_percent_{
            kDefaultImmersiveAmountPercent};
    std::atomic<std::uint32_t> immersive_width_percent_{
            kDefaultImmersiveWidthPercent};
    std::atomic<std::uint32_t> immersive_center_percent_{
            kDefaultImmersiveCenterPercent};
    std::atomic<std::uint32_t> immersive_room_percent_{kDefaultImmersiveRoomPercent};
    std::array<BiquadCoefficients, kEqBandCount> eq_coefficients_{};
    std::array<std::array<BiquadState, kMaxChannelCount>, kEqBandCount> eq_states_{};
    std::array<bool, kEqBandCount> eq_band_active_{};
    std::uint64_t applied_equalizer_revision_ = 0;
    float limiter_gain_ = 1.0F;
    std::array<std::array<float, 2>, kMaxChannelCount> bass_lowpass_states_{};
    std::array<std::array<float, 2>, kMaxChannelCount> bass_deep_lowpass_states_{};
    std::array<float, kMaxChannelCount> bass_harmonic_dc_states_{};
    std::array<float, kMaxChannelCount> bass_harmonic_lowpass_states_{};
    float bass_envelope_ = 0.0F;
    std::array<float, kMaxChannelCount> detail_lowpass_state_{};
    float detail_fast_envelope_ = 0.0F;
    float detail_slow_envelope_ = 0.0F;
    float immersive_side_lowpass_ = 0.0F;
    float immersive_crossfeed_left_ = 0.0F;
    float immersive_crossfeed_right_ = 0.0F;
    float immersive_reflection_left_ = 0.0F;
    float immersive_reflection_right_ = 0.0F;
    std::array<float, kImmersiveDelayCapacity> immersive_delay_left_{};
    std::array<float, kImmersiveDelayCapacity> immersive_delay_right_{};
    std::size_t immersive_delay_index_ = 0;
    bool immersive_state_active_ = false;
    StreamConfig config_{};
};

}  // namespace audiofreedom
