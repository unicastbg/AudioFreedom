#include "audiofreedom/engine.h"
#include "audiofreedom/wire.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <numbers>
#include <thread>
#include <vector>

static void check(bool condition, const char* text) {
    if (!condition) { std::cerr << text << '\n'; std::exit(1); }
}

static std::vector<float> tone(unsigned rate, unsigned channels, float right = 1.0F) {
    std::vector<float> data(rate * channels);
    for (unsigned f = 0; f < rate; ++f) {
        const float sample = 0.5F * std::sin(2.0 * std::numbers::pi * 1000.0 * f / rate);
        for (unsigned c = 0; c < channels; ++c)
            data[f * channels + c] = sample * (c == 1 ? right : 1.0F);
    }
    return data;
}

static void process(audiofreedom::Engine& engine, std::vector<float>& data, unsigned block) {
    const auto channels = engine.stream_config().channel_count;
    for (std::size_t f = 0; f < data.size() / channels; f += block) {
        engine.set_analysis_enabled(true);
        check(engine.process(data.data() + f * channels,
                std::min<std::size_t>(block, data.size() / channels - f)), "processing failed");
    }
}

int main() {
    for (unsigned rate : {44100U, 48000U, 96000U, 192000U}) {
        for (unsigned channels : {1U, 2U, 6U, 32U}) {
            audiofreedom::Engine engine;
            check(engine.prepare({rate, channels}), "prepare failed");
            engine.set_enabled(true);
            engine.set_limiter_enabled(false);
            engine.set_preamp_millibels(-600);
            auto data = tone(rate, channels);
            process(engine, data, 137);
            const auto result = engine.playback_analysis();
            check(result.sequence != 0, "analysis did not publish");
            check(std::abs(result.input_rms + 903) <= 2, "input RMS inaccurate");
            check(std::abs(result.output_rms + 1503) <= 2, "output RMS inaccurate");
            check(std::abs(result.crest - 301) <= 2, "crest factor inaccurate");
            check(result.correlation == (channels == 2 ? 1000 : 2000), "correlation inaccurate");
            check(result.near_full_scale_ppm == 0, "false full-scale detection");
            check(std::max_element(result.bands.begin(), result.bands.end()) - result.bands.begin() == 5,
                    "spectrum did not identify 1 kHz");
            engine.set_analysis_enabled(false);
            check(engine.process(data.data(), 1), "disable failed");
            check(engine.playback_analysis().sequence == 0, "disabled analysis remained live");
        }
    }
    {
        audiofreedom::Engine first, second;
        check(first.prepare({48000, 2}) && second.prepare({48000, 2}), "prepare failed");
        auto a = tone(48000, 2, -1), b = a;
        process(first, a, 1);
        process(second, b, 480);
        check(a == b, "analysis altered bypass audio");
        const auto x = first.playback_analysis(), y = second.playback_analysis();
        check(x.correlation == -1000, "anti-phase correlation incorrect");
        check(x.input_rms == y.input_rms && x.bands == y.bands, "analysis depends on block size");
        std::fill(a.begin(), a.end(), 0.0F);
        process(first, a, 512);
        check(first.playback_analysis().output_rms == -12000 &&
                first.playback_analysis().correlation == 2000, "silence metrics invalid");
        std::fill(a.begin(), a.end(), 1.0F);
        process(first, a, 512);
        check(first.playback_analysis().near_full_scale_ppm == 1000000, "full-scale count incorrect");
        a[0] = std::numeric_limits<float>::quiet_NaN();
        process(first, a, 512);
        check(first.playback_analysis().output_rms == 0, "nonfinite input poisoned analysis");
        first.reset();
        check(first.playback_analysis().sequence == 0, "reset retained telemetry");
    }
    {
        audiofreedom::Engine analyzed, reference;
        check(analyzed.prepare({48000, 2}) && reference.prepare({48000, 2}), "prepare failed");
        for (auto* engine : {&analyzed, &reference}) {
            engine->set_enabled(true);
            engine->set_equalizer_enabled(true);
            check(engine->set_eq_band_millibels(5, 300), "EQ setup failed");
            engine->set_dynamic_bass_enabled(true);
            engine->set_reverb_enabled(true);
            engine->set_volume_leveler_configuration({true, -1800, 65, 600, 1500});
        }
        auto a = tone(48000, 2), b = a;
        process(analyzed, a, 512);
        for (std::size_t f = 0; f < b.size() / 2; f += 512)
            check(reference.process(b.data() + f * 2,
                    std::min<std::size_t>(512, b.size() / 2 - f)), "reference processing failed");
        check(a == b, "analysis changed active DSP output");
    }
    {
        audiofreedom::AnalysisPublication publication;
        auto writer = [&publication](int id) {
            for (int i = 0; i < 10000; ++i) {
                audiofreedom::PlaybackAnalysis value;
                value.sequence = 1;
                value.input_rms = -100 * id;
                value.output_rms = value.input_rms;
                value.bands.fill(static_cast<std::int16_t>(value.input_rms));
                publication.publish(value);
            }
        };
        std::thread a(writer, 1), b(writer, 2);
        for (int i = 0; i < 20000; ++i) {
            const auto value = publication.read();
            if (value.sequence == 0) continue;
            check(value.input_rms == value.output_rms && value.bands[0] == value.input_rms &&
                    value.bands[9] == value.input_rms, "torn concurrent publication");
        }
        a.join(); b.join();
    }
    {
        using namespace audiofreedom::protocol;
        auto message = make_analysis_enabled(true);
        check(read_analysis_enabled(message) == true, "enable wire decode failed");
        message.payload[0] = 2;
        check(!read_analysis_enabled(message), "invalid boolean accepted");
        audiofreedom::PlaybackAnalysis value;
        value.sequence = 42; value.input_rms = -900; value.bands[5] = -1200;
        const auto metrics = make_playback_analysis(value);
        const auto bands = make_playback_analysis(value, true);
        check(metrics.payload_size == 24 && detail::get_u32(metrics.payload, 0) == 42 &&
                static_cast<std::int32_t>(detail::get_u32(metrics.payload, 4)) == -900,
                "metrics wire layout incorrect");
        check(bands.parameter_id == ParameterId::kPlaybackSpectrum &&
                static_cast<std::int16_t>(detail::get_u16(bands.payload, 14)) == -1200,
                "spectrum wire layout incorrect");
        const auto encoded = encode_message(metrics);
        check(encoded.has_value() && decode_message(*encoded).has_value(), "metrics wire roundtrip failed");
        audiofreedom::AnalysisLease lease;
        check(!lease.active(), "lease starts active");
        lease.renew(true); check(lease.active(), "lease renewal failed");
        lease.renew(false); check(!lease.active(), "lease disable failed");
        lease.renew(true);
        std::this_thread::sleep_for(std::chrono::milliseconds(3100));
        check(!lease.active(), "unrenewed lease did not expire");
    }
    std::cout << "Playback analysis tests passed\n";
}
