#pragma once

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace audiofreedom {

struct VolumeLevelerConfig final {
    bool enabled = false;
    std::int32_t target_millibels = -1800;
    std::uint32_t strength_percent = 50;
    std::uint32_t maximum_boost_millibels = 600;
    std::uint32_t response_milliseconds = 1500;

    [[nodiscard]] bool valid() const noexcept {
        return target_millibels >= -3000 && target_millibels <= -1200 &&
               strength_percent <= 100 && maximum_boost_millibels <= 1200 &&
               response_milliseconds >= 200 && response_milliseconds <= 5000;
    }
};

// Causal RMS leveling: fixed storage, linked channels, no lookahead or PCM buffering.
class VolumeLeveler final {
public:
    void prepare(std::uint32_t sample_rate) noexcept {
        sample_rate_ = static_cast<float>(sample_rate);
        interval_ = std::max(1U, sample_rate / 100);
        energy_coefficient_ = 1.0F - std::exp(-1.0F / (0.1F * sample_rate_));
        reset();
    }
    void reset() noexcept {
        energy_ = 0.0F;
        gain_ = target_gain_ = 1.0F;
        quiet_frames_ = warmup_frames_ = tick_ = 0;
    }
    void configure(VolumeLevelerConfig config) noexcept {
        if (config.enabled != config_.enabled) reset();
        config_ = config;
        const float seconds = config.response_milliseconds / 1000.0F;
        rise_ = 1.0F - std::exp(-1.0F / (seconds * sample_rate_));
        fall_ = 1.0F - std::exp(-1.0F / (0.25F * seconds * sample_rate_));
    }
    void process_frame(float* frame, std::size_t channels) noexcept {
        if (!config_.enabled) return;
        double power = 0.0;
        float peak = 0.0F;
        for (std::size_t channel = 0; channel < channels; ++channel) {
            if (!std::isfinite(frame[channel])) frame[channel] = 0.0F;
            const double sample = frame[channel];
            power += sample * sample;
            peak = std::max(peak, std::abs(frame[channel]));
        }
        energy_ += energy_coefficient_ *
                (static_cast<float>(std::min(power / channels, 1.0e12)) - energy_);
        const auto warmup = static_cast<std::uint32_t>(sample_rate_ * 0.25F);
        warmup_frames_ = std::min(warmup_frames_ + 1, warmup);
        quiet_frames_ = peak < 0.0001F ? std::min(quiet_frames_ + 1, warmup) : 0;
        if (quiet_frames_ >= warmup) {
            energy_ = 0.0F;
            warmup_frames_ = 0;
            target_gain_ = 1.0F;
            gain_ = std::min(gain_, 1.0F);
        }
        if (++tick_ >= interval_) {
            tick_ = 0;
            if (energy_ > 1.0e-6F && warmup_frames_ >= warmup) {
                const float measured = 1000.0F * std::log10(energy_);
                const float correction = std::clamp(
                        (config_.target_millibels - measured) *
                                (config_.strength_percent / 100.0F),
                        -3600.0F, static_cast<float>(config_.maximum_boost_millibels));
                target_gain_ = std::pow(10.0F, correction / 2000.0F);
            } else target_gain_ = 1.0F;
        }
        gain_ += (target_gain_ < gain_ ? fall_ : rise_) * (target_gain_ - gain_);
        for (std::size_t channel = 0; channel < channels; ++channel) frame[channel] *= gain_;
    }
private:
    VolumeLevelerConfig config_{};
    float sample_rate_ = 48000.0F;
    float energy_coefficient_ = 0.0F;
    float rise_ = 0.0F;
    float fall_ = 0.0F;
    float energy_ = 0.0F;
    float gain_ = 1.0F;
    float target_gain_ = 1.0F;
    std::uint32_t interval_ = 480;
    std::uint32_t quiet_frames_ = 0;
    std::uint32_t warmup_frames_ = 0;
    std::uint32_t tick_ = 0;
};
}  // namespace audiofreedom
