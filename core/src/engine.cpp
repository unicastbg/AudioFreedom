#include "audiofreedom/engine.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <numbers>

namespace audiofreedom {

namespace {

constexpr float kGraphicEqQ = 1.4F;
constexpr std::int32_t kSilenceMillibels = -12000;

std::int32_t linearToMillibels(const float linear) noexcept {
    if (!(linear > 0.000001F)) {
        return kSilenceMillibels;
    }
    return static_cast<std::int32_t>(std::lround(2000.0F * std::log10(linear)));
}

}  // namespace

Engine::Engine() noexcept {
    for (auto& gain : eq_band_millibels_) {
        gain.store(0, std::memory_order_relaxed);
    }
}

bool Engine::prepare(const StreamConfig config) noexcept {
    if (config.sample_rate_hz == 0 || config.channel_count == 0 ||
        config.channel_count > kMaxChannelCount) {
        config_ = {};
        return false;
    }

    config_ = config;
    applied_equalizer_revision_ = 0;
    reset();
    refresh_equalizer();
    return true;
}

void Engine::reset() noexcept {
    processed_frames_.store(0, std::memory_order_relaxed);
    clear_equalizer_state();
    limiter_gain_ = 1.0F;
    bass_lowpass_states_.fill({});
    bass_deep_lowpass_states_.fill({});
    bass_harmonic_dc_states_.fill(0.0F);
    bass_harmonic_lowpass_states_.fill(0.0F);
    bass_envelope_ = 0.0F;
    detail_lowpass_state_.fill(0.0F);
    detail_fast_envelope_ = 0.0F;
    detail_slow_envelope_ = 0.0F;
    immersive_side_lowpass_ = 0.0F;
    immersive_crossfeed_left_ = 0.0F;
    immersive_crossfeed_right_ = 0.0F;
    immersive_reflection_left_ = 0.0F;
    immersive_reflection_right_ = 0.0F;
    immersive_delay_left_.fill(0.0F);
    immersive_delay_right_.fill(0.0F);
    immersive_delay_index_ = 0;
    immersive_state_active_ = false;
    input_peak_millibels_.store(kSilenceMillibels, std::memory_order_relaxed);
    output_peak_millibels_.store(kSilenceMillibels, std::memory_order_relaxed);
    gain_reduction_millibels_.store(0, std::memory_order_relaxed);
}

void Engine::set_enabled(const bool enabled) noexcept {
    enabled_.store(enabled, std::memory_order_release);
}

bool Engine::enabled() const noexcept {
    return enabled_.load(std::memory_order_acquire);
}

void Engine::set_preamp_millibels(const std::int32_t millibels) noexcept {
    preamp_millibels_.store(
        std::clamp(millibels, kMinPreampMillibels, kMaxPreampMillibels),
        std::memory_order_release);
}

std::int32_t Engine::preamp_millibels() const noexcept {
    return preamp_millibels_.load(std::memory_order_acquire);
}

void Engine::set_equalizer_enabled(const bool enabled) noexcept {
    if (equalizer_enabled_.exchange(enabled, std::memory_order_acq_rel) != enabled) {
        equalizer_revision_.fetch_add(1, std::memory_order_release);
    }
}

bool Engine::equalizer_enabled() const noexcept {
    return equalizer_enabled_.load(std::memory_order_acquire);
}

bool Engine::set_eq_band_millibels(const std::size_t band,
                                   const std::int32_t millibels) noexcept {
    if (band >= kEqBandCount) {
        return false;
    }
    const auto clamped = std::clamp(millibels, kMinEqBandMillibels, kMaxEqBandMillibels);
    if (eq_band_millibels_[band].exchange(clamped, std::memory_order_acq_rel) != clamped) {
        equalizer_revision_.fetch_add(1, std::memory_order_release);
    }
    return true;
}

std::int32_t Engine::eq_band_millibels(const std::size_t band) const noexcept {
    if (band >= kEqBandCount) {
        return 0;
    }
    return eq_band_millibels_[band].load(std::memory_order_acquire);
}

void Engine::set_limiter_enabled(const bool enabled) noexcept {
    limiter_enabled_.store(enabled, std::memory_order_release);
}

bool Engine::limiter_enabled() const noexcept {
    return limiter_enabled_.load(std::memory_order_acquire);
}

void Engine::set_limiter_threshold_millibels(const std::int32_t millibels) noexcept {
    limiter_threshold_millibels_.store(
            std::clamp(millibels, kMinLimiterThresholdMillibels,
                       kMaxLimiterThresholdMillibels),
            std::memory_order_release);
}

std::int32_t Engine::limiter_threshold_millibels() const noexcept {
    return limiter_threshold_millibels_.load(std::memory_order_acquire);
}

void Engine::set_limiter_release_milliseconds(const std::uint32_t milliseconds) noexcept {
    limiter_release_milliseconds_.store(
            std::clamp(milliseconds, kMinLimiterReleaseMilliseconds,
                       kMaxLimiterReleaseMilliseconds),
            std::memory_order_release);
}

std::uint32_t Engine::limiter_release_milliseconds() const noexcept {
    return limiter_release_milliseconds_.load(std::memory_order_acquire);
}

OutputMetrics Engine::output_metrics() const noexcept {
    return {
            .input_peak_millibels = input_peak_millibels_.load(std::memory_order_acquire),
            .output_peak_millibels = output_peak_millibels_.load(std::memory_order_acquire),
            .gain_reduction_millibels =
                    gain_reduction_millibels_.load(std::memory_order_acquire),
    };
}

void Engine::set_dynamic_bass_enabled(const bool enabled) noexcept {
    dynamic_bass_enabled_.store(enabled, std::memory_order_release);
}

bool Engine::dynamic_bass_enabled() const noexcept {
    return dynamic_bass_enabled_.load(std::memory_order_acquire);
}

void Engine::set_bass_cutoff_hz(const std::uint32_t frequency_hz) noexcept {
    bass_cutoff_hz_.store(std::clamp(frequency_hz, kMinBassCutoffHz, kMaxBassCutoffHz),
                          std::memory_order_release);
}

std::uint32_t Engine::bass_cutoff_hz() const noexcept {
    return bass_cutoff_hz_.load(std::memory_order_acquire);
}

void Engine::set_bass_boost_millibels(const std::int32_t millibels) noexcept {
    bass_boost_millibels_.store(
            std::clamp(millibels, kMinBassBoostMillibels, kMaxBassBoostMillibels),
            std::memory_order_release);
}

std::int32_t Engine::bass_boost_millibels() const noexcept {
    return bass_boost_millibels_.load(std::memory_order_acquire);
}

void Engine::set_bass_dynamics_percent(const std::uint32_t percent) noexcept {
    bass_dynamics_percent_.store(
            std::clamp(percent, kMinBassDynamicsPercent, kMaxBassDynamicsPercent),
            std::memory_order_release);
}

std::uint32_t Engine::bass_dynamics_percent() const noexcept {
    return bass_dynamics_percent_.load(std::memory_order_acquire);
}

void Engine::set_detail_recovery_enabled(const bool enabled) noexcept {
    detail_recovery_enabled_.store(enabled, std::memory_order_release);
}

bool Engine::detail_recovery_enabled() const noexcept {
    return detail_recovery_enabled_.load(std::memory_order_acquire);
}

void Engine::set_detail_amount_percent(const std::uint32_t percent) noexcept {
    detail_amount_percent_.store(
            std::clamp(percent, kMinDetailAmountPercent, kMaxDetailAmountPercent),
            std::memory_order_release);
}

std::uint32_t Engine::detail_amount_percent() const noexcept {
    return detail_amount_percent_.load(std::memory_order_acquire);
}

void Engine::set_detail_focus_hz(const std::uint32_t frequency_hz) noexcept {
    detail_focus_hz_.store(
            std::clamp(frequency_hz, kMinDetailFocusHz, kMaxDetailFocusHz),
            std::memory_order_release);
}

std::uint32_t Engine::detail_focus_hz() const noexcept {
    return detail_focus_hz_.load(std::memory_order_acquire);
}

void Engine::set_detail_transients_percent(const std::uint32_t percent) noexcept {
    detail_transients_percent_.store(
            std::clamp(percent, kMinDetailTransientsPercent,
                       kMaxDetailTransientsPercent),
            std::memory_order_release);
}

std::uint32_t Engine::detail_transients_percent() const noexcept {
    return detail_transients_percent_.load(std::memory_order_acquire);
}

void Engine::set_immersive_field_enabled(const bool enabled) noexcept {
    immersive_field_enabled_.store(enabled, std::memory_order_release);
}

bool Engine::immersive_field_enabled() const noexcept {
    return immersive_field_enabled_.load(std::memory_order_acquire);
}

void Engine::set_immersive_amount_percent(const std::uint32_t percent) noexcept {
    immersive_amount_percent_.store(
            std::clamp(percent, kMinImmersivePercent, kMaxImmersivePercent),
            std::memory_order_release);
}

std::uint32_t Engine::immersive_amount_percent() const noexcept {
    return immersive_amount_percent_.load(std::memory_order_acquire);
}

void Engine::set_immersive_width_percent(const std::uint32_t percent) noexcept {
    immersive_width_percent_.store(
            std::clamp(percent, kMinImmersivePercent, kMaxImmersivePercent),
            std::memory_order_release);
}

std::uint32_t Engine::immersive_width_percent() const noexcept {
    return immersive_width_percent_.load(std::memory_order_acquire);
}

void Engine::set_immersive_center_percent(const std::uint32_t percent) noexcept {
    immersive_center_percent_.store(
            std::clamp(percent, kMinImmersivePercent, kMaxImmersivePercent),
            std::memory_order_release);
}

std::uint32_t Engine::immersive_center_percent() const noexcept {
    return immersive_center_percent_.load(std::memory_order_acquire);
}

void Engine::set_immersive_room_percent(const std::uint32_t percent) noexcept {
    immersive_room_percent_.store(
            std::clamp(percent, kMinImmersivePercent, kMaxImmersivePercent),
            std::memory_order_release);
}

std::uint32_t Engine::immersive_room_percent() const noexcept {
    return immersive_room_percent_.load(std::memory_order_acquire);
}

void Engine::clear_equalizer_state() noexcept {
    for (auto& band_states : eq_states_) {
        for (auto& state : band_states) {
            state = {};
        }
    }
}

void Engine::refresh_equalizer() noexcept {
    const auto revision = equalizer_revision_.load(std::memory_order_acquire);
    if (revision == applied_equalizer_revision_) {
        return;
    }

    clear_equalizer_state();
    const float sample_rate = static_cast<float>(config_.sample_rate_hz);
    for (std::size_t band = 0; band < kEqBandCount; ++band) {
        const auto millibels = eq_band_millibels_[band].load(std::memory_order_acquire);
        const float frequency = kEqBandFrequenciesHz[band];
        if (!equalizer_enabled() || millibels == 0 || frequency >= sample_rate * 0.49F) {
            eq_coefficients_[band] = {};
            eq_band_active_[band] = false;
            continue;
        }

        const float gain_db = static_cast<float>(millibels) / 100.0F;
        const float amplitude = std::pow(10.0F, gain_db / 40.0F);
        const float omega = 2.0F * std::numbers::pi_v<float> * frequency / sample_rate;
        const float cosine = std::cos(omega);
        const float alpha = std::sin(omega) / (2.0F * kGraphicEqQ);
        const float a0 = 1.0F + alpha / amplitude;

        eq_coefficients_[band] = {
                .b0 = (1.0F + alpha * amplitude) / a0,
                .b1 = (-2.0F * cosine) / a0,
                .b2 = (1.0F - alpha * amplitude) / a0,
                .a1 = (-2.0F * cosine) / a0,
                .a2 = (1.0F - alpha / amplitude) / a0,
        };
        eq_band_active_[band] = true;
    }
    applied_equalizer_revision_ = revision;
}

bool Engine::process(float* const samples, const std::size_t frame_count) noexcept {
    if (samples == nullptr || config_.channel_count == 0) {
        return false;
    }
    if (frame_count > std::numeric_limits<std::size_t>::max() / config_.channel_count) {
        return false;
    }

    float block_input_peak = 0.0F;
    float block_output_peak = 0.0F;
    float block_gain_reduction = 0.0F;

    if (enabled()) {
        refresh_equalizer();
        const auto millibels = preamp_millibels();
        const float linear_gain = std::pow(10.0F, static_cast<float>(millibels) / 2000.0F);
        const bool use_limiter = limiter_enabled();
        const bool use_dynamic_bass = dynamic_bass_enabled();
        const bool use_detail_recovery = detail_recovery_enabled();
        const bool use_immersive_field =
                immersive_field_enabled() && config_.channel_count == 2;
        const float sample_rate = static_cast<float>(config_.sample_rate_hz);
        const float bass_strength =
                static_cast<float>(bass_boost_millibels()) /
                static_cast<float>(kMaxBassBoostMillibels);
        const float bass_range_hz = static_cast<float>(bass_cutoff_hz());
        const float deep_range_hz = std::clamp(bass_range_hz * 0.52F, 28.0F, 82.0F);
        const auto onePoleCoefficient = [&](const float frequency_hz) {
            return 1.0F - std::exp(
                    -2.0F * std::numbers::pi_v<float> * frequency_hz / sample_rate);
        };
        const float bass_filter_coefficient = onePoleCoefficient(bass_range_hz);
        const float deep_filter_coefficient = onePoleCoefficient(deep_range_hz);
        const float harmonic_filter_coefficient = onePoleCoefficient(
                std::min(320.0F, bass_range_hz * 2.2F));
        const float harmonic_dc_coefficient = onePoleCoefficient(14.0F);
        const float bass_attack_coefficient =
                1.0F - std::exp(-1.0F / (0.008F * sample_rate));
        const float bass_release_coefficient =
                1.0F - std::exp(-1.0F / (0.180F * sample_rate));
        const float bass_lift =
                std::pow(10.0F, (10.0F * bass_strength) / 20.0F) - 1.0F;
        const float deep_lift =
                std::pow(10.0F, (5.0F * bass_strength) / 20.0F) - 1.0F;
        const float small_driver_support =
                static_cast<float>(bass_dynamics_percent()) / 100.0F;
        const float harmonic_mix = 0.72F * bass_strength * small_driver_support;
        const float bass_headroom_gain = 1.0F;
        const float detail_filter_coefficient = 1.0F - std::exp(
                -2.0F * std::numbers::pi_v<float> * static_cast<float>(detail_focus_hz()) /
                static_cast<float>(config_.sample_rate_hz));
        const float detail_fast_attack = 1.0F - std::exp(
                -1.0F / (0.002F * static_cast<float>(config_.sample_rate_hz)));
        const float detail_fast_release = 1.0F - std::exp(
                -1.0F / (0.030F * static_cast<float>(config_.sample_rate_hz)));
        const float detail_slow_attack = 1.0F - std::exp(
                -1.0F / (0.060F * static_cast<float>(config_.sample_rate_hz)));
        const float detail_slow_release = 1.0F - std::exp(
                -1.0F / (0.250F * static_cast<float>(config_.sample_rate_hz)));
        const float maximum_detail_boost_db =
                10.0F * static_cast<float>(detail_amount_percent()) / 100.0F;
        const float detail_transients =
                static_cast<float>(detail_transients_percent()) / 100.0F;
        const float immersive_amount =
                static_cast<float>(immersive_amount_percent()) / 100.0F;
        const float immersive_width =
                static_cast<float>(immersive_width_percent()) / 100.0F;
        const float immersive_side_gain = 1.0F + 2.5F * immersive_width;
        const float immersive_center_gain =
                0.80F + 0.5F * static_cast<float>(immersive_center_percent()) / 100.0F;
        const float immersive_room_gain =
                0.75F * static_cast<float>(immersive_room_percent()) / 100.0F;
        const float immersive_externalization_gain = 0.20F * immersive_width;
        const float immersive_side_filter = 1.0F - std::exp(
                -2.0F * std::numbers::pi_v<float> * 250.0F /
                static_cast<float>(config_.sample_rate_hz));
        const float immersive_crossfeed_filter = 1.0F - std::exp(
                -2.0F * std::numbers::pi_v<float> * 700.0F /
                static_cast<float>(config_.sample_rate_hz));
        const float immersive_reflection_filter = 1.0F - std::exp(
                -2.0F * std::numbers::pi_v<float> * 6000.0F /
                static_cast<float>(config_.sample_rate_hz));
        const auto delaySamples = [&](const float seconds) {
            return std::clamp(
                    static_cast<std::size_t>(std::lround(
                            seconds * static_cast<float>(config_.sample_rate_hz))),
                    std::size_t{1}, kImmersiveDelayCapacity - 1);
        };
        const std::size_t crossfeed_delay_left = delaySamples(0.00028F);
        const std::size_t crossfeed_delay_right = delaySamples(0.00043F);
        const std::size_t reflection_delay_left = delaySamples(0.007F);
        const std::size_t reflection_delay_right = delaySamples(0.011F);
        const std::size_t reflection_delay_left_late = delaySamples(0.017F);
        const std::size_t reflection_delay_right_late = delaySamples(0.023F);
        std::array<float, kMaxChannelCount> detail_highpass{};

        if (!use_dynamic_bass) {
            bass_lowpass_states_.fill({});
            bass_deep_lowpass_states_.fill({});
            bass_harmonic_dc_states_.fill(0.0F);
            bass_harmonic_lowpass_states_.fill(0.0F);
            bass_envelope_ = 0.0F;
        }
        if (!use_detail_recovery) {
            detail_lowpass_state_.fill(0.0F);
            detail_fast_envelope_ = 0.0F;
            detail_slow_envelope_ = 0.0F;
        }
        if (!use_immersive_field && immersive_state_active_) {
            immersive_side_lowpass_ = 0.0F;
            immersive_crossfeed_left_ = 0.0F;
            immersive_crossfeed_right_ = 0.0F;
            immersive_reflection_left_ = 0.0F;
            immersive_reflection_right_ = 0.0F;
            immersive_delay_left_.fill(0.0F);
            immersive_delay_right_.fill(0.0F);
            immersive_delay_index_ = 0;
        }
        immersive_state_active_ = use_immersive_field;
        const float limiter_threshold = std::pow(
                10.0F, static_cast<float>(limiter_threshold_millibels()) / 2000.0F);
        const float release_seconds =
                static_cast<float>(limiter_release_milliseconds()) / 1000.0F;
        const float release_coefficient = 1.0F - std::exp(
                -1.0F / (release_seconds * static_cast<float>(config_.sample_rate_hz)));

        for (std::size_t frame = 0; frame < frame_count; ++frame) {
            for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                const std::size_t index = frame * config_.channel_count + channel;
                block_input_peak = std::max(block_input_peak, std::abs(samples[index]));
                float sample = samples[index] * linear_gain * bass_headroom_gain;
                for (std::size_t band = 0; band < kEqBandCount; ++band) {
                    if (!eq_band_active_[band]) {
                        continue;
                    }
                    const auto& coefficients = eq_coefficients_[band];
                    auto& state = eq_states_[band][channel];
                    const float output = coefficients.b0 * sample + state.z1;
                    state.z1 = coefficients.b1 * sample - coefficients.a1 * output + state.z2;
                    state.z2 = coefficients.b2 * sample - coefficients.a2 * output;
                    sample = output;
                }
                samples[index] = sample;
            }

            if (use_dynamic_bass) {
                std::array<float, kMaxChannelCount> bass_band{};
                std::array<float, kMaxChannelCount> deep_band{};
                float frame_bass_peak = 0.0F;
                for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                    const std::size_t index = frame * config_.channel_count + channel;
                    auto& bass_state = bass_lowpass_states_[channel];
                    bass_state[0] += bass_filter_coefficient *
                            (samples[index] - bass_state[0]);
                    bass_state[1] += bass_filter_coefficient *
                            (bass_state[0] - bass_state[1]);
                    bass_band[channel] = bass_state[1];

                    auto& deep_state = bass_deep_lowpass_states_[channel];
                    deep_state[0] += deep_filter_coefficient *
                            (samples[index] - deep_state[0]);
                    deep_state[1] += deep_filter_coefficient *
                            (deep_state[0] - deep_state[1]);
                    deep_band[channel] = deep_state[1];
                    frame_bass_peak = std::max(frame_bass_peak,
                                               std::abs(bass_band[channel]));
                }

                const float envelope_coefficient = frame_bass_peak > bass_envelope_
                        ? bass_attack_coefficient
                        : bass_release_coefficient;
                bass_envelope_ += envelope_coefficient *
                        (frame_bass_peak - bass_envelope_);
                const float peak_excess = std::max(0.0F, bass_envelope_ - 0.16F);
                const float adaptive_restraint = 1.0F / (1.0F + 4.0F * peak_excess);
                constexpr float kEnhancementSoftLimit = 0.48F;

                std::array<float, kMaxChannelCount> bass_enhancement{};
                float enhancement_peak = 0.0F;
                for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                    const float squared = deep_band[channel] * deep_band[channel];
                    auto& harmonic_dc = bass_harmonic_dc_states_[channel];
                    harmonic_dc += harmonic_dc_coefficient * (squared - harmonic_dc);
                    const float normalized_harmonic =
                            (squared - harmonic_dc) / (bass_envelope_ + 0.02F);
                    auto& harmonic = bass_harmonic_lowpass_states_[channel];
                    harmonic += harmonic_filter_coefficient *
                            (normalized_harmonic - harmonic);

                    bass_enhancement[channel] = bass_lift * bass_band[channel] +
                            deep_lift * deep_band[channel] + harmonic_mix * harmonic;
                    bass_enhancement[channel] *= adaptive_restraint;
                    enhancement_peak = std::max(
                            enhancement_peak, std::abs(bass_enhancement[channel]));
                }
                const float limited_enhancement_peak = kEnhancementSoftLimit *
                        std::tanh(enhancement_peak / kEnhancementSoftLimit);
                const float enhancement_scale = enhancement_peak > 0.000001F
                        ? limited_enhancement_peak / enhancement_peak
                        : 1.0F;
                for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                    const std::size_t index = frame * config_.channel_count + channel;
                    samples[index] += bass_enhancement[channel] * enhancement_scale;
                }
            }

            if (use_detail_recovery) {
                float frame_detail_peak = 0.0F;
                for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                    const std::size_t index = frame * config_.channel_count + channel;
                    auto& lowpass = detail_lowpass_state_[channel];
                    lowpass += detail_filter_coefficient * (samples[index] - lowpass);
                    detail_highpass[channel] = samples[index] - lowpass;
                    frame_detail_peak =
                            std::max(frame_detail_peak, std::abs(detail_highpass[channel]));
                }
                const float fast_coefficient = frame_detail_peak > detail_fast_envelope_
                                                       ? detail_fast_attack
                                                       : detail_fast_release;
                const float slow_coefficient = frame_detail_peak > detail_slow_envelope_
                                                       ? detail_slow_attack
                                                       : detail_slow_release;
                detail_fast_envelope_ +=
                        fast_coefficient * (frame_detail_peak - detail_fast_envelope_);
                detail_slow_envelope_ +=
                        slow_coefficient * (frame_detail_peak - detail_slow_envelope_);
                const float transient = std::clamp(
                        (detail_fast_envelope_ - detail_slow_envelope_) /
                                (detail_slow_envelope_ + 0.01F),
                        0.0F, 1.0F);
                const float noise_guard = std::clamp(
                        (detail_slow_envelope_ - 0.001F) / 0.009F, 0.0F, 1.0F);
                const float effective_boost_db = maximum_detail_boost_db * noise_guard *
                        (0.32F + 0.68F * detail_transients * transient);
                const float detail_gain = std::pow(10.0F, effective_boost_db / 20.0F);
                for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                    const std::size_t index = frame * config_.channel_count + channel;
                    samples[index] += detail_highpass[channel] * (detail_gain - 1.0F);
                }
            }

            if (use_immersive_field) {
                const std::size_t left_index = frame * 2;
                const std::size_t right_index = left_index + 1;
                const float dry_left = samples[left_index];
                const float dry_right = samples[right_index];
                immersive_delay_left_[immersive_delay_index_] = dry_left;
                immersive_delay_right_[immersive_delay_index_] = dry_right;

                const auto delayedIndex = [&](const std::size_t delay) {
                    return (immersive_delay_index_ + kImmersiveDelayCapacity - delay) %
                           kImmersiveDelayCapacity;
                };
                const float delayed_cross_left =
                        immersive_delay_left_[delayedIndex(crossfeed_delay_left)];
                const float delayed_cross_right =
                        immersive_delay_right_[delayedIndex(crossfeed_delay_right)];
                immersive_crossfeed_left_ += immersive_crossfeed_filter *
                        (delayed_cross_right - immersive_crossfeed_left_);
                immersive_crossfeed_right_ += immersive_crossfeed_filter *
                        (delayed_cross_left - immersive_crossfeed_right_);

                const float mid = 0.5F * (dry_left + dry_right);
                const float side = 0.5F * (dry_left - dry_right);
                immersive_side_lowpass_ +=
                        immersive_side_filter * (side - immersive_side_lowpass_);
                const float staged_side = immersive_side_lowpass_ +
                        (side - immersive_side_lowpass_) * immersive_side_gain;
                float wet_left = mid * immersive_center_gain + staged_side;
                float wet_right = mid * immersive_center_gain - staged_side;
                constexpr float kCrossfeedAmount = 0.30F;
                wet_left = (wet_left + kCrossfeedAmount * immersive_crossfeed_left_) /
                           (1.0F + kCrossfeedAmount);
                wet_right = (wet_right + kCrossfeedAmount * immersive_crossfeed_right_) /
                            (1.0F + kCrossfeedAmount);

                const float reflection_left =
                        0.72F * immersive_delay_right_[delayedIndex(reflection_delay_left)] -
                        0.35F * immersive_delay_left_[delayedIndex(
                                reflection_delay_left_late)];
                const float reflection_right =
                        0.72F * immersive_delay_left_[delayedIndex(reflection_delay_right)] -
                        0.35F * immersive_delay_right_[delayedIndex(
                                reflection_delay_right_late)];
                immersive_reflection_left_ += immersive_reflection_filter *
                        (reflection_left - immersive_reflection_left_);
                immersive_reflection_right_ += immersive_reflection_filter *
                        (reflection_right - immersive_reflection_right_);
                const float reflection_gain =
                        immersive_externalization_gain + immersive_room_gain;
                wet_left += reflection_gain * immersive_reflection_left_;
                wet_right += reflection_gain * immersive_reflection_right_;
                const float room_normalization = 1.0F + 0.22F * reflection_gain;
                wet_left /= room_normalization;
                wet_right /= room_normalization;

                samples[left_index] =
                        dry_left + immersive_amount * (wet_left - dry_left);
                samples[right_index] =
                        dry_right + immersive_amount * (wet_right - dry_right);
                immersive_delay_index_ =
                        (immersive_delay_index_ + 1) % kImmersiveDelayCapacity;
            }

            float frame_peak = 0.0F;
            for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                const std::size_t index = frame * config_.channel_count + channel;
                frame_peak = std::max(frame_peak, std::abs(samples[index]));
            }

            if (use_limiter) {
                const float target_gain = frame_peak > limiter_threshold
                                                  ? limiter_threshold / frame_peak
                                                  : 1.0F;
                if (target_gain < limiter_gain_) {
                    limiter_gain_ = target_gain;
                } else {
                    limiter_gain_ += (1.0F - limiter_gain_) * release_coefficient;
                    limiter_gain_ = std::min(limiter_gain_, target_gain);
                }
            } else {
                limiter_gain_ = 1.0F;
            }

            block_gain_reduction = -linearToMillibels(limiter_gain_) / 100.0F;
            for (std::size_t channel = 0; channel < config_.channel_count; ++channel) {
                const std::size_t index = frame * config_.channel_count + channel;
                samples[index] *= limiter_gain_;
                block_output_peak = std::max(block_output_peak, std::abs(samples[index]));
            }
        }
    } else {
        limiter_gain_ = 1.0F;
        const std::size_t sample_count = frame_count * config_.channel_count;
        for (std::size_t index = 0; index < sample_count; ++index) {
            const float magnitude = std::abs(samples[index]);
            block_input_peak = std::max(block_input_peak, magnitude);
            block_output_peak = std::max(block_output_peak, magnitude);
        }
    }

    input_peak_millibels_.store(linearToMillibels(block_input_peak),
                                 std::memory_order_release);
    output_peak_millibels_.store(linearToMillibels(block_output_peak),
                                  std::memory_order_release);
    gain_reduction_millibels_.store(
            static_cast<std::int32_t>(std::lround(block_gain_reduction * 100.0F)),
            std::memory_order_release);

    processed_frames_.fetch_add(frame_count, std::memory_order_relaxed);
    return true;
}

StreamConfig Engine::stream_config() const noexcept {
    return config_;
}

std::uint64_t Engine::processed_frames() const noexcept {
    return processed_frames_.load(std::memory_order_relaxed);
}

}  // namespace audiofreedom
