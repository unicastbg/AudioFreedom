#pragma once
#include "audiofreedom/playback_metrics.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <chrono>
#include <cstdint>
#include <numbers>

namespace audiofreedom {

class AnalysisLease final {
public:
    void renew(bool enabled) noexcept { deadline_.store(enabled ? now() + 3000 : 0); }
    [[nodiscard]] bool active() const noexcept {
        const auto deadline = deadline_.load();
        return deadline != 0 && now() < deadline;
    }
private:
    static std::int64_t now() noexcept {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
    }
    std::atomic<std::int64_t> deadline_{0};
};

// Writers never wait. Contended publications are dropped; readers retry at most three times.
class AnalysisPublication final {
public:
    void publish(PlaybackAnalysis value) noexcept {
        if (writer_.test_and_set(std::memory_order_acquire)) return;
        revision_.fetch_add(1);
        input_.store(value.input_rms);
        output_.store(value.output_rms);
        crest_.store(value.crest);
        correlation_.store(value.correlation);
        clipping_.store(value.near_full_scale_ppm);
        for (std::size_t i = 0; i < bands_.size(); ++i) bands_[i].store(value.bands[i]);
        sequence_.store(value.sequence == 0 ? 0 : ++counter_);
        revision_.fetch_add(1);
        writer_.clear(std::memory_order_release);
    }
    [[nodiscard]] PlaybackAnalysis read() const noexcept {
        for (int attempt = 0; attempt < 3; ++attempt) {
            const auto before = revision_.load();
            if (before & 1U) continue;
            PlaybackAnalysis result{input_.load(), output_.load(), crest_.load(),
                    correlation_.load(), clipping_.load(), sequence_.load()};
            for (std::size_t i = 0; i < bands_.size(); ++i) result.bands[i] = bands_[i].load();
            if (before == revision_.load()) return result;
        }
        return {};
    }
private:
    std::atomic_flag writer_ = ATOMIC_FLAG_INIT;
    std::atomic<std::uint32_t> revision_{0}, sequence_{0}, clipping_{0};
    std::atomic<std::int32_t> input_{-12000}, output_{-12000}, crest_{0}, correlation_{2000};
    std::array<std::atomic<std::int16_t>, 10> bands_{};
    std::uint32_t counter_ = 0;
};

class PlaybackAnalyzer final {
public:
    void prepare(std::uint32_t rate, std::uint32_t channels) noexcept {
        channels_ = channels;
        window_ = std::max(1U, rate / 4);
        constexpr std::array<double, 10> frequencies{31.25, 62.5, 125, 250, 500,
                                                    1000, 2000, 4000, 8000, 16000};
        for (std::size_t i = 0; i < filters_.size(); ++i) {
            auto& f = filters_[i];
            f.active = frequencies[i] < rate * 0.45;
            if (!f.active) continue;
            // RBJ constant-peak bandpass, Q = sqrt(2), approximately octave-wide.
            const double omega = 2.0 * std::numbers::pi * frequencies[i] / rate;
            const double alpha = std::sin(omega) / (2.0 * std::sqrt(2.0));
            f.b = alpha / (1.0 + alpha);
            f.a1 = -2.0 * std::cos(omega) / (1.0 + alpha);
            f.a2 = (1.0 - alpha) / (1.0 + alpha);
        }
        reset();
    }
    void reset() noexcept {
        clear_window();
        for (auto& f : filters_) f.states = {};
        publication_.publish({});
    }
    void input(const float* frame) noexcept {
        for (std::uint32_t c = 0; c < channels_; ++c) {
            const double x = safe(frame[c]);
            input_energy_ += x * x;
        }
    }
    void output(const float* frame) noexcept {
        for (std::uint32_t c = 0; c < channels_; ++c) {
            const double x = safe(frame[c]);
            output_energy_ += x * x;
            peak_ = std::max(peak_, std::abs(x));
            if (std::abs(x) >= 0.999) ++near_full_scale_;
        }
        if (channels_ == 2) {
            const double left = safe(frame[0]), right = safe(frame[1]);
            left_energy_ += left * left;
            right_energy_ += right * right;
            cross_energy_ += left * right;
        }
        const auto spectrum_channels = std::min(channels_, 2U);
        for (std::size_t i = 0; i < filters_.size(); ++i) {
            auto& f = filters_[i];
            if (!f.active) continue;
            for (std::uint32_t c = 0; c < spectrum_channels; ++c) {
                auto& s = f.states[c];
                const double x = safe(frame[c]);
                const double y = f.b * x + s[0];
                s[0] = -f.a1 * y + s[1];
                s[1] = -f.b * x - f.a2 * y;
                band_energy_[i] += y * y;
            }
        }
        if (++frames_ < window_) return;
        const double count = static_cast<double>(frames_) * channels_;
        PlaybackAnalysis result;
        result.input_rms = power_mb(input_energy_ / count);
        result.output_rms = power_mb(output_energy_ / count);
        result.crest = output_energy_ > 1.0e-12
                ? std::max(0, power_mb(peak_ * peak_) - result.output_rms) : 0;
        if (channels_ == 2 && left_energy_ > 1.0e-12 && right_energy_ > 1.0e-12) {
            result.correlation = static_cast<std::int32_t>(std::lround(1000.0 *
                    std::clamp(cross_energy_ / std::sqrt(left_energy_ * right_energy_), -1.0, 1.0)));
        }
        result.near_full_scale_ppm = static_cast<std::uint32_t>(
                std::lround(near_full_scale_ * 1000000.0 / count));
        result.sequence = 1;
        for (std::size_t i = 0; i < filters_.size(); ++i) {
            result.bands[i] = static_cast<std::int16_t>(power_mb(
                    band_energy_[i] / (static_cast<double>(frames_) * spectrum_channels)));
        }
        publication_.publish(result);
        clear_window();
    }
    [[nodiscard]] PlaybackAnalysis read() const noexcept { return publication_.read(); }
private:
    static double safe(float x) noexcept {
        return std::isfinite(x) ? std::clamp(static_cast<double>(x), -1.0e6, 1.0e6) : 0.0;
    }
    static std::int32_t power_mb(double power) noexcept {
        return power > 1.0e-12 ? static_cast<std::int32_t>(
                std::clamp(std::lround(1000.0 * std::log10(power)), -12000L, 12000L)) : -12000;
    }
    void clear_window() noexcept {
        frames_ = 0;
        input_energy_ = output_energy_ = left_energy_ = right_energy_ = cross_energy_ = peak_ = 0;
        near_full_scale_ = 0;
        band_energy_.fill(0);
    }
    struct Filter {
        double b = 0, a1 = 0, a2 = 0;
        bool active = false;
        std::array<std::array<double, 2>, 2> states{};
    };
    std::array<Filter, 10> filters_{};
    std::array<double, 10> band_energy_{};
    AnalysisPublication publication_{};
    std::uint32_t channels_ = 1, window_ = 12000, frames_ = 0;
    std::uint64_t near_full_scale_ = 0;
    double input_energy_ = 0, output_energy_ = 0, left_energy_ = 0, right_energy_ = 0,
           cross_energy_ = 0, peak_ = 0;
};
}  // namespace audiofreedom
