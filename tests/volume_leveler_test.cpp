#include "audiofreedom/engine.h"
#include "audiofreedom/wire.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <vector>

static void check(bool condition, const char* message) {
    if (!condition) { std::cerr << message << '\n'; std::exit(1); }
}

static std::vector<float> tone(unsigned rate, unsigned channels, float amplitude, unsigned seconds) {
    std::vector<float> result(rate * channels * seconds);
    for (std::size_t i = 0; i < result.size() / channels; ++i)
        for (unsigned c = 0; c < channels; ++c)
            result[i * channels + c] = amplitude * std::sin(6.2831853F * 440.0F * i / rate) *
                    (c == 1 ? 0.5F : 1.0F);
    return result;
}

static double rms(const std::vector<float>& data, unsigned channels, unsigned frames) {
    double power = 0;
    for (std::size_t i = data.size() / channels - frames; i < data.size() / channels; ++i)
        power += data[i * channels] * data[i * channels];
    return std::sqrt(power / frames);
}

int main() {
    for (unsigned rate : {44100U, 48000U, 96000U, 192000U}) {
        for (unsigned channels : {1U, 2U, 6U}) {
            audiofreedom::Engine engine;
            check(engine.prepare({rate, channels}), "prepare failed");
            engine.set_enabled(true);
            engine.set_limiter_enabled(false);
            engine.set_volume_leveler_configuration({true, -1800, 100, 1200, 200});
            auto quiet = tone(rate, channels, 0.06F, 4);
            const double before = rms(quiet, channels, rate);
            check(engine.process(quiet.data(), quiet.size() / channels), "quiet processing failed");
            check(rms(quiet, channels, rate) > before * 2.0, "quiet tone was not raised");
            const double leveled_quiet = rms(quiet, channels, rate);
            if (channels == 2) for (std::size_t i = 0; i < quiet.size(); i += 2)
                check(std::abs(quiet[i + 1] - 0.5F * quiet[i]) < 1e-6F, "stereo balance changed");
            auto loud = tone(rate, channels, 0.8F, 4);
            check(engine.process(loud.data(), loud.size() / channels), "loud processing failed");
            check(rms(loud, channels, rate) < 0.2, "loud tone was not reduced");
            check(std::abs(rms(loud, channels, rate) - leveled_quiet) < leveled_quiet * 0.1,
                    "full-strength quiet and loud passages did not converge");
            for (float value : loud) check(std::isfinite(value) && std::abs(value) <= 0.891252F,
                    "leveler peak protection failed with limiter disabled");
            auto noise = tone(rate, channels, 0.00001F, 2);
            check(engine.process(noise.data(), noise.size() / channels), "noise processing failed");
            check(rms(noise, channels, rate) <= 0.00001, "sustained near-silence was boosted");
            engine.reset();
            engine.set_volume_leveler_configuration({false, -1800, 100, 1200, 200});
            auto bypass = tone(rate, channels, 0.2F, 1);
            auto original = bypass;
            check(engine.process(bypass.data(), rate), "bypass failed");
            check(bypass == original, "disabled leveler changed PCM");
        }
    }
    audiofreedom::Engine whole, chunked;
    check(whole.prepare({48000, 2}) && chunked.prepare({48000, 2}), "chunk prepare failed");
    for (auto* engine : {&whole, &chunked}) {
        engine->set_enabled(true);
        engine->set_volume_leveler_configuration({true, -1800, 80, 600, 500});
    }
    auto a = tone(48000, 2, 0.08F, 3), b = a;
    check(whole.process(a.data(), a.size() / 2), "whole block failed");
    for (std::size_t frame = 0; frame < b.size() / 2; frame += 137) {
        auto count = std::min<std::size_t>(137, b.size() / 2 - frame);
        check(chunked.process(b.data() + frame * 2, count), "chunk failed");
    }
    check(a == b, "block boundaries changed leveling");
    whole.reset();
    whole.set_limiter_enabled(false);
    whole.set_volume_leveler_configuration({true, -1800, 0, 1200, 200});
    auto dry = tone(48000, 2, 0.3F, 1), original_dry = dry;
    check(whole.process(dry.data(), 48000) && dry == original_dry,
            "zero strength changed a signal below the protection ceiling");
    whole.set_enabled(false);
    whole.set_volume_leveler_configuration({true, -1800, 100, 1200, 200});
    check(whole.process(dry.data(), 48000) && dry == original_dry,
            "master bypass did not preserve PCM with leveler configured");
    whole.set_enabled(true);
    whole.set_volume_leveler_configuration({true, -1800, 100, 300, 200});
    auto limited = tone(48000, 2, 0.01F, 4);
    const auto input_rms = rms(limited, 2, 48000);
    check(whole.process(limited.data(), limited.size() / 2), "boost limit test failed");
    check(rms(limited, 2, 48000) <= input_rms * 1.413, "maximum boost exceeded +3 dB");
    using namespace audiofreedom::protocol;
    auto message = make_volume_leveler_configuration({true, -2200, 75, 900, 3000});
    auto wire = encode_message(message);
    check(wire.has_value(), "leveler encode failed");
    auto decoded = decode_message(*wire);
    auto config = read_volume_leveler_configuration(*decoded);
    check(config && config->target_millibels == -2200 && config->strength_percent == 75 &&
          config->maximum_boost_millibels == 900 && config->response_milliseconds == 3000,
          "leveler wire round trip failed");
    message.payload[0] = 2;
    check(!read_volume_leveler_configuration(message), "invalid boolean accepted");
    message = make_volume_leveler_configuration({});
    message.payload_size = 19;
    check(!read_volume_leveler_configuration(message), "short payload accepted");
    whole.set_volume_leveler_configuration({true, 1, 999, 9999, 0});
    check(whole.volume_leveler_configuration().valid(), "engine did not clamp configuration");
    std::cout << "Volume Leveler tests passed\n";
}
