#include "audiofreedom/engine.h"
#include "audiofreedom/protocol.h"
#include "audiofreedom/wire.h"

#include <cmath>
#include <array>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <limits>

namespace {

void expect(const bool condition, const char* const message) {
    if (!condition) {
        std::cerr << "FAILED: " << message << '\n';
        std::exit(EXIT_FAILURE);
    }
}

void expect_near(const float actual, const float expected, const float tolerance,
                 const char* const message) {
    expect(std::abs(actual - expected) <= tolerance, message);
}

void test_rejects_invalid_streams() {
    audiofreedom::Engine engine;
    expect(!engine.prepare({0, 2}), "zero sample rate must be rejected");
    expect(!engine.prepare({48000, 0}), "zero channels must be rejected");
    expect(!engine.prepare({48000, 33}), "unreasonable channel count must be rejected");
}

void test_bypass_preserves_pcm() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "valid stream must prepare");
    float samples[] = {1.0F, -1.0F, 0.25F, -0.25F};

    expect(engine.process(samples, 2), "bypass processing must succeed");
    expect_near(samples[0], 1.0F, 0.0F, "bypass changed left sample");
    expect_near(samples[1], -1.0F, 0.0F, "bypass changed right sample");
    expect(engine.processed_frames() == 2, "frame counter must advance in bypass");
}

void test_proof_gain_is_minus_twelve_db() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "valid stream must prepare");
    engine.set_preamp_millibels(audiofreedom::Engine::kProofPreampMillibels);
    engine.set_enabled(true);
    float samples[] = {1.0F, -1.0F};

    expect(engine.process(samples, 1), "enabled processing must succeed");
    constexpr float expected = 0.25118864F;
    expect_near(samples[0], expected, 0.000001F, "left gain is not -12 dB");
    expect_near(samples[1], -expected, 0.000001F, "right gain is not -12 dB");
}

void test_gain_handles_all_supported_channel_counts() {
    for (const std::uint32_t channels : {1U, 2U, 6U, 8U, 32U}) {
        audiofreedom::Engine engine;
        expect(engine.prepare({192000, channels}), "multichannel stream must prepare");
        engine.set_preamp_millibels(audiofreedom::Engine::kProofPreampMillibels);
        engine.set_enabled(true);
        std::array<float, 32> samples{};
        samples.fill(1.0F);

        expect(engine.process(samples.data(), 1), "multichannel processing must succeed");
        for (std::uint32_t channel = 0; channel < channels; ++channel) {
            expect_near(samples[channel], 0.25118864F, 0.000001F,
                        "channel gain is not -12 dB");
        }
    }
}

void test_frame_counter_and_reset() {
    audiofreedom::Engine engine;
    expect(engine.prepare({44100, 1}), "mono stream must prepare");
    float samples[8]{};
    expect(engine.process(samples, 3), "first frame block must process");
    expect(engine.process(samples, 5), "second frame block must process");
    expect(engine.processed_frames() == 8, "frame counter must accumulate blocks");
    engine.reset();
    expect(engine.processed_frames() == 0, "reset must clear frame counter");
    expect(engine.stream_config().sample_rate_hz == 44100,
           "reset must preserve stream configuration");
}

void test_process_rejects_invalid_buffers_and_size_overflow() {
    audiofreedom::Engine engine;
    float sample = 1.0F;
    expect(!engine.process(&sample, 1), "unprepared engine must reject processing");
    expect(engine.prepare({48000, 2}), "stereo stream must prepare");
    expect(!engine.process(nullptr, 1), "null PCM must be rejected");
    expect(!engine.process(&sample, std::numeric_limits<std::size_t>::max()),
           "sample-count overflow must be rejected");
    expect(engine.processed_frames() == 0, "rejected processing must not advance frames");
}

void test_preamp_is_clamped() {
    audiofreedom::Engine engine;
    engine.set_preamp_millibels(1200);
    expect(engine.preamp_millibels() == 0, "positive preamp must clamp to 0 dB");
    engine.set_preamp_millibels(-5000);
    expect(engine.preamp_millibels() == -2400, "preamp must clamp to -24 dB");
}

void test_flat_equalizer_preserves_pcm() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "valid stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_equalizer_enabled(true);
    engine.set_enabled(true);
    float samples[] = {0.75F, -0.5F, 0.125F, -0.25F};

    expect(engine.process(samples, 2), "flat equalizer processing must succeed");
    expect_near(samples[0], 0.75F, 0.0F, "flat EQ changed left sample");
    expect_near(samples[1], -0.5F, 0.0F, "flat EQ changed right sample");
    expect_near(samples[2], 0.125F, 0.0F, "flat EQ changed second left sample");
    expect_near(samples[3], -0.25F, 0.0F, "flat EQ changed second right sample");
}

void test_equalizer_band_gain_and_limits() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 1}), "valid stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_equalizer_enabled(true);
    expect(engine.set_eq_band_millibels(5, 600), "1 kHz band must be accepted");
    expect(!engine.set_eq_band_millibels(audiofreedom::Engine::kEqBandCount, 100),
           "invalid band index must be rejected");
    expect(engine.set_eq_band_millibels(0, 5000), "valid band must clamp high gain");
    expect(engine.eq_band_millibels(0) == 1200, "EQ high gain was not clamped");
    expect(engine.set_eq_band_millibels(0, -5000), "valid band must clamp low gain");
    expect(engine.eq_band_millibels(0) == -1200, "EQ low gain was not clamped");
    expect(engine.set_eq_band_millibels(0, 0), "valid band must return to flat");
    engine.set_enabled(true);

    constexpr std::size_t sample_count = 48000;
    std::array<float, sample_count> samples{};
    std::array<float, sample_count> input{};
    for (std::size_t index = 0; index < sample_count; ++index) {
        const float phase = 2.0F * 3.14159265358979323846F * 1000.0F *
                            static_cast<float>(index) / 48000.0F;
        samples[index] = 0.1F * std::sin(phase);
        input[index] = samples[index];
    }

    expect(engine.process(samples.data(), sample_count), "1 kHz sine must process");
    double input_energy = 0.0;
    double output_energy = 0.0;
    for (std::size_t index = 4096; index < sample_count; ++index) {
        input_energy += static_cast<double>(input[index]) * input[index];
        output_energy += static_cast<double>(samples[index]) * samples[index];
    }
    const float measured_gain_db = 10.0F * std::log10(
            static_cast<float>(output_energy / input_energy));
    expect_near(measured_gain_db, 6.0F, 0.05F, "1 kHz EQ band is not +6 dB");
}

void test_equalizer_handles_multichannel_and_reset() {
    audiofreedom::Engine engine;
    expect(engine.prepare({44100, 8}), "multichannel stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_equalizer_enabled(true);
    expect(engine.set_eq_band_millibels(2, 300), "125 Hz band must be accepted");
    engine.set_enabled(true);
    std::array<float, 8> samples{};
    samples.fill(0.25F);
    expect(engine.process(samples.data(), 1), "multichannel EQ must process");
    for (const float sample : samples) {
        expect(std::isfinite(sample), "multichannel EQ produced a non-finite sample");
    }
    engine.reset();
    expect(engine.processed_frames() == 0, "EQ reset must clear frame count");
}

void test_limiter_caps_peaks_and_links_channels() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "limiter stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(true);
    engine.set_limiter_threshold_millibels(-100);
    engine.set_enabled(true);
    float samples[] = {2.0F, 0.5F};

    expect(engine.process(samples, 1), "limiter frame must process");
    constexpr float threshold = 0.89125094F;
    expect_near(samples[0], threshold, 0.000001F, "limiter did not cap peak");
    expect_near(samples[1], threshold * 0.25F, 0.000001F,
                "limiter did not preserve stereo balance");
    const auto metrics = engine.output_metrics();
    expect(metrics.input_peak_millibels == 602, "input peak meter is incorrect");
    expect(metrics.output_peak_millibels == -100, "output peak meter is incorrect");
    expect(metrics.gain_reduction_millibels > 600,
           "gain reduction meter did not report limiting");
}

void test_limiter_release_and_clamping() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 1}), "limiter stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_threshold_millibels(-5000);
    expect(engine.limiter_threshold_millibels() == -600,
           "limiter threshold must clamp low");
    engine.set_limiter_threshold_millibels(100);
    expect(engine.limiter_threshold_millibels() == 0,
           "limiter threshold must clamp high");
    engine.set_limiter_threshold_millibels(-100);
    engine.set_limiter_release_milliseconds(1);
    expect(engine.limiter_release_milliseconds() == 20,
           "limiter release must clamp short values");
    engine.set_limiter_release_milliseconds(5000);
    expect(engine.limiter_release_milliseconds() == 1000,
           "limiter release must clamp long values");
    engine.set_limiter_release_milliseconds(20);
    engine.set_enabled(true);

    float peak = 2.0F;
    expect(engine.process(&peak, 1), "limiter peak must process");
    const auto reduction_after_peak = engine.output_metrics().gain_reduction_millibels;
    std::array<float, 4800> quiet{};
    quiet.fill(0.1F);
    expect(engine.process(quiet.data(), quiet.size()), "limiter release signal must process");
    expect(engine.output_metrics().gain_reduction_millibels < reduction_after_peak,
           "limiter gain did not recover during release");
}

float measure_bass_foundation_gain(const float frequency_hz, const float amplitude,
                                   const std::uint32_t range_hz,
                                   const std::int32_t strength_wire_value,
                                   const std::uint32_t small_driver_support = 0) {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 1}), "bass foundation stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_dynamic_bass_enabled(true);
    engine.set_bass_cutoff_hz(range_hz);
    engine.set_bass_boost_millibels(strength_wire_value);
    engine.set_bass_dynamics_percent(small_driver_support);
    engine.set_enabled(true);

    constexpr std::size_t sample_count = 48000;
    std::array<float, sample_count> samples{};
    std::array<float, sample_count> input{};
    for (std::size_t index = 0; index < sample_count; ++index) {
        const float phase = 2.0F * 3.14159265358979323846F * frequency_hz *
                            static_cast<float>(index) / 48000.0F;
        samples[index] = amplitude * std::sin(phase);
        input[index] = samples[index];
    }
    expect(engine.process(samples.data(), samples.size()),
           "bass foundation signal must process");

    double input_energy = 0.0;
    double output_energy = 0.0;
    for (std::size_t index = 8192; index < sample_count; ++index) {
        input_energy += static_cast<double>(input[index]) * input[index];
        output_energy += static_cast<double>(samples[index]) * samples[index];
    }
    return 10.0F * std::log10(static_cast<float>(output_energy / input_energy));
}

void test_dynamic_bass_targets_low_frequencies_and_tracks_level() {
    constexpr std::uint32_t balanced_headphones = 95;
    constexpr std::int32_t half_strength = 600;
    const float sub_gain = measure_bass_foundation_gain(
            40.0F, 0.05F, balanced_headphones, half_strength);
    const float deep_bass_gain = measure_bass_foundation_gain(
            70.0F, 0.05F, balanced_headphones, half_strength);
    const float upper_bass_gain = measure_bass_foundation_gain(
            160.0F, 0.05F, balanced_headphones, half_strength);
    const float mid_gain = measure_bass_foundation_gain(
            1000.0F, 0.05F, balanced_headphones, half_strength);
    std::cout << "Bass Foundation response (dB): 40=" << sub_gain
              << " 70=" << deep_bass_gain << " 160=" << upper_bass_gain
              << " 1000=" << mid_gain << '\n';
    const float low_strength_sub = measure_bass_foundation_gain(
            40.0F, 0.05F, balanced_headphones, 120);
    const float loud_gain = measure_bass_foundation_gain(
            40.0F, 0.5F, balanced_headphones, half_strength);
    std::cout << "Bass Foundation strength (dB at 40 Hz): 10%="
              << low_strength_sub << " 50%=" << sub_gain
              << " loud50%=" << loud_gain << '\n';
    expect(sub_gain > 4.0F, "bass foundation did not create deep low-frequency weight");
    expect(sub_gain > upper_bass_gain + 2.0F,
           "bass foundation did not taper through the upper-bass band");
    expect(std::abs(mid_gain) < 0.4F,
           "bass foundation changed the midrange response");
    expect(sub_gain > low_strength_sub + 2.5F,
           "bass foundation strength did not scale the low band");
    expect(sub_gain > loud_gain + 0.4F,
           "bass foundation did not restrain already-loud bass");
}

float measure_bass_harmonic_amplitude(const std::uint32_t support_percent) {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 1}), "small-driver support stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_dynamic_bass_enabled(true);
    engine.set_bass_cutoff_hz(150);
    engine.set_bass_boost_millibels(900);
    engine.set_bass_dynamics_percent(support_percent);
    engine.set_enabled(true);

    constexpr std::size_t sample_count = 48000;
    std::array<float, sample_count> samples{};
    for (std::size_t index = 0; index < sample_count; ++index) {
        const float phase = 2.0F * 3.14159265358979323846F * 40.0F *
                            static_cast<float>(index) / 48000.0F;
        samples[index] = 0.05F * std::sin(phase);
    }
    expect(engine.process(samples.data(), samples.size()),
           "small-driver support signal must process");

    double sine_sum = 0.0;
    double cosine_sum = 0.0;
    constexpr std::size_t start = 12000;
    for (std::size_t index = start; index < sample_count; ++index) {
        const double phase = 2.0 * 3.14159265358979323846 * 80.0 *
                             static_cast<double>(index) / 48000.0;
        sine_sum += static_cast<double>(samples[index]) * std::sin(phase);
        cosine_sum += static_cast<double>(samples[index]) * std::cos(phase);
    }
    const double scale = 2.0 / static_cast<double>(sample_count - start);
    return static_cast<float>(scale * std::sqrt(sine_sum * sine_sum +
                                                 cosine_sum * cosine_sum));
}

void test_bass_foundation_small_driver_support_adds_audible_harmonics() {
    const float disabled = measure_bass_harmonic_amplitude(0);
    const float enabled = measure_bass_harmonic_amplitude(100);
    std::cout << "Bass Foundation 80 Hz harmonic: off=" << disabled
              << " support100=" << enabled << '\n';
    expect(enabled > disabled + 0.004F,
           "small-driver support did not synthesize a useful upper harmonic");
}

void test_dynamic_bass_links_stereo_and_clamps_settings() {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "dynamic bass stereo stream must prepare");
    engine.set_bass_cutoff_hz(1);
    expect(engine.bass_cutoff_hz() == 40, "bass cutoff must clamp low");
    engine.set_bass_cutoff_hz(1000);
    expect(engine.bass_cutoff_hz() == 160, "bass cutoff must clamp high");
    engine.set_bass_boost_millibels(-100);
    expect(engine.bass_boost_millibels() == 0, "bass boost must clamp low");
    engine.set_bass_boost_millibels(5000);
    expect(engine.bass_boost_millibels() == 1200, "bass boost must clamp high");
    engine.set_bass_dynamics_percent(500);
    expect(engine.bass_dynamics_percent() == 100, "bass dynamics must clamp high");

    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_dynamic_bass_enabled(true);
    engine.set_bass_cutoff_hz(80);
    engine.set_bass_boost_millibels(600);
    engine.set_bass_dynamics_percent(0);
    engine.set_enabled(true);
    std::array<float, 9600> samples{};
    for (std::size_t frame = 0; frame < samples.size() / 2; ++frame) {
        const float sample = 0.1F * std::sin(
                2.0F * 3.14159265358979323846F * 40.0F *
                static_cast<float>(frame) / 48000.0F);
        samples[frame * 2] = sample;
        samples[frame * 2 + 1] = sample * 0.5F;
    }
    expect(engine.process(samples.data(), samples.size() / 2),
           "dynamic bass stereo signal must process");
    expect_near(samples[samples.size() - 2] * 0.5F, samples[samples.size() - 1], 0.00001F,
                "dynamic bass changed stereo balance");
}

void test_bass_foundation_profiles_remain_finite_and_limited() {
    struct Profile final {
        std::uint32_t range_hz;
        std::uint32_t support_percent;
    };
    constexpr std::array<Profile, 5> profiles{
            Profile{70, 10}, Profile{95, 20}, Profile{110, 40},
            Profile{130, 65}, Profile{150, 90},
    };
    const float ceiling = std::pow(10.0F, -100.0F / 2000.0F);
    for (const auto profile : profiles) {
        audiofreedom::Engine engine;
        expect(engine.prepare({48000, 2}), "bass foundation profile must prepare");
        engine.set_preamp_millibels(0);
        engine.set_dynamic_bass_enabled(true);
        engine.set_bass_cutoff_hz(profile.range_hz);
        engine.set_bass_dynamics_percent(profile.support_percent);
        engine.set_bass_boost_millibels(1200);
        engine.set_limiter_enabled(true);
        engine.set_limiter_threshold_millibels(-100);
        engine.set_enabled(true);

        std::array<float, 9600> samples{};
        for (std::size_t frame = 0; frame < samples.size() / 2; ++frame) {
            const float time = static_cast<float>(frame) / 48000.0F;
            const float sample = 0.42F * std::sin(2.0F * 3.14159265358979323846F *
                                                  40.0F * time) +
                    0.30F * std::sin(2.0F * 3.14159265358979323846F * 75.0F * time) +
                    0.20F * std::sin(2.0F * 3.14159265358979323846F * 1000.0F * time);
            samples[frame * 2] = sample;
            samples[frame * 2 + 1] = sample * 0.8F;
        }
        expect(engine.process(samples.data(), samples.size() / 2),
               "bass foundation profile signal must process");
        for (const float sample : samples) {
            expect(std::isfinite(sample), "bass foundation produced a non-finite sample");
            expect(std::abs(sample) <= ceiling + 0.00001F,
                   "linked limiter did not contain a bass foundation peak");
        }
    }
}

float measure_detail_gain(const float frequency_hz) {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 1}), "detail recovery stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_detail_recovery_enabled(true);
    engine.set_detail_amount_percent(100);
    engine.set_detail_focus_hz(6000);
    engine.set_detail_transients_percent(0);
    engine.set_enabled(true);

    constexpr std::size_t sample_count = 48000;
    std::array<float, sample_count> samples{};
    std::array<float, sample_count> input{};
    for (std::size_t index = 0; index < sample_count; ++index) {
        const float phase = 2.0F * 3.14159265358979323846F * frequency_hz *
                            static_cast<float>(index) / 48000.0F;
        samples[index] = 0.1F * std::sin(phase);
        input[index] = samples[index];
    }
    expect(engine.process(samples.data(), samples.size()), "detail recovery signal must process");
    double input_energy = 0.0;
    double output_energy = 0.0;
    for (std::size_t index = 8192; index < sample_count; ++index) {
        input_energy += static_cast<double>(input[index]) * input[index];
        output_energy += static_cast<double>(samples[index]) * samples[index];
    }
    return 10.0F * std::log10(static_cast<float>(output_energy / input_energy));
}

void test_detail_recovery_targets_high_frequencies_and_transients() {
    expect(measure_detail_gain(8000.0F) > 0.5F,
           "detail recovery did not lift high frequencies");
    expect(measure_detail_gain(500.0F) < 0.2F,
           "detail recovery changed low frequencies too much");

    audiofreedom::Engine dynamic;
    audiofreedom::Engine steady;
    for (auto* engine : {&dynamic, &steady}) {
        expect(engine->prepare({48000, 1}), "detail transient stream must prepare");
        engine->set_preamp_millibels(0);
        engine->set_limiter_enabled(false);
        engine->set_detail_recovery_enabled(true);
        engine->set_detail_amount_percent(100);
        engine->set_detail_focus_hz(6000);
        engine->set_enabled(true);
    }
    dynamic.set_detail_transients_percent(100);
    steady.set_detail_transients_percent(0);
    std::array<float, 4801> dynamic_samples{};
    std::array<float, 4801> steady_samples{};
    for (std::size_t index = 0; index < 4800; ++index) {
        const float sample = 0.05F * std::sin(
                2.0F * 3.14159265358979323846F * 8000.0F *
                static_cast<float>(index) / 48000.0F);
        dynamic_samples[index] = sample;
        steady_samples[index] = sample;
    }
    dynamic_samples.back() = 0.8F;
    steady_samples.back() = 0.8F;
    expect(dynamic.process(dynamic_samples.data(), dynamic_samples.size()),
           "dynamic detail transient must process");
    expect(steady.process(steady_samples.data(), steady_samples.size()),
           "steady detail transient must process");
    expect(std::abs(dynamic_samples.back()) > std::abs(steady_samples.back()) + 0.01F,
           "detail transient control did not increase transient emphasis");
}

void test_detail_recovery_bypass_stereo_and_clamping() {
    audiofreedom::Engine engine;
    engine.set_detail_amount_percent(500);
    expect(engine.detail_amount_percent() == 100, "detail amount must clamp high");
    engine.set_detail_focus_hz(1);
    expect(engine.detail_focus_hz() == 3000, "detail focus must clamp low");
    engine.set_detail_focus_hz(50000);
    expect(engine.detail_focus_hz() == 10000, "detail focus must clamp high");
    engine.set_detail_transients_percent(500);
    expect(engine.detail_transients_percent() == 100,
           "detail transients must clamp high");

    expect(engine.prepare({48000, 2}), "detail stereo stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_detail_recovery_enabled(true);
    engine.set_detail_amount_percent(0);
    engine.set_enabled(true);
    float samples[] = {0.4F, 0.2F, -0.3F, -0.15F};
    expect(engine.process(samples, 2), "zero-amount detail signal must process");
    expect_near(samples[0], 0.4F, 0.0F, "zero detail amount changed left sample");
    expect_near(samples[1], 0.2F, 0.0F, "zero detail amount changed right sample");
    expect_near(samples[2], -0.3F, 0.0F, "zero detail amount changed second left sample");
    expect_near(samples[3], -0.15F, 0.0F, "zero detail amount changed second right sample");
}

float measure_immersive_side_gain(const float frequency_hz) {
    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "immersive stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_immersive_field_enabled(true);
    engine.set_immersive_amount_percent(100);
    engine.set_immersive_width_percent(100);
    engine.set_immersive_center_percent(50);
    engine.set_immersive_room_percent(0);
    engine.set_enabled(true);

    constexpr std::size_t frame_count = 48000;
    std::array<float, frame_count * 2> samples{};
    std::array<float, frame_count * 2> input{};
    for (std::size_t frame = 0; frame < frame_count; ++frame) {
        const float phase = 2.0F * 3.14159265358979323846F * frequency_hz *
                            static_cast<float>(frame) / 48000.0F;
        const float sample = 0.05F * std::sin(phase);
        samples[frame * 2] = sample;
        samples[frame * 2 + 1] = -sample;
        input[frame * 2] = sample;
        input[frame * 2 + 1] = -sample;
    }
    expect(engine.process(samples.data(), frame_count), "immersive side signal must process");
    double input_energy = 0.0;
    double output_energy = 0.0;
    for (std::size_t index = 16384; index < samples.size(); ++index) {
        input_energy += static_cast<double>(input[index]) * input[index];
        output_energy += static_cast<double>(samples[index]) * samples[index];
    }
    return 10.0F * std::log10(static_cast<float>(output_energy / input_energy));
}

void test_immersive_field_shapes_stage_without_latency() {
    const float low_side_gain = measure_immersive_side_gain(100.0F);
    const float high_side_gain = measure_immersive_side_gain(5000.0F);
    std::cout << "Immersive Field side gain (dB): 100=" << low_side_gain
              << " 5000=" << high_side_gain << '\n';
    expect(high_side_gain > 7.0F,
           "immersive width is still too subtle at maximum strength");
    expect(high_side_gain > low_side_gain + 3.0F,
           "immersive width is not frequency dependent");

    audiofreedom::Engine engine;
    expect(engine.prepare({48000, 2}), "immersive bypass stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_immersive_field_enabled(true);
    engine.set_immersive_amount_percent(0);
    engine.set_enabled(true);
    float samples[] = {0.4F, -0.2F, -0.3F, 0.1F};
    expect(engine.process(samples, 2), "zero-amount immersive signal must process");
    expect_near(samples[0], 0.4F, 0.0F, "zero immersive amount changed left sample");
    expect_near(samples[1], -0.2F, 0.0F, "zero immersive amount changed right sample");
    expect_near(samples[2], -0.3F, 0.0F,
                "zero immersive amount changed second left sample");
    expect_near(samples[3], 0.1F, 0.0F,
                "zero immersive amount changed second right sample");
}

void test_immersive_field_reflections_stereo_and_clamping() {
    audiofreedom::Engine engine;
    engine.set_immersive_amount_percent(500);
    engine.set_immersive_width_percent(500);
    engine.set_immersive_center_percent(500);
    engine.set_immersive_room_percent(500);
    expect(engine.immersive_amount_percent() == 100, "immersive amount must clamp high");
    expect(engine.immersive_width_percent() == 100, "immersive width must clamp high");
    expect(engine.immersive_center_percent() == 100, "immersive center must clamp high");
    expect(engine.immersive_room_percent() == 100, "immersive room must clamp high");

    expect(engine.prepare({48000, 2}), "immersive reflection stream must prepare");
    engine.set_preamp_millibels(0);
    engine.set_limiter_enabled(false);
    engine.set_immersive_field_enabled(true);
    engine.set_immersive_amount_percent(100);
    engine.set_immersive_width_percent(0);
    engine.set_immersive_center_percent(50);
    engine.set_immersive_room_percent(100);
    engine.set_enabled(true);
    std::array<float, 1600> samples{};
    samples[0] = 1.0F;
    expect(engine.process(samples.data(), samples.size() / 2),
           "immersive reflection impulse must process");
    bool found_tail = false;
    double tail_energy = 0.0;
    for (std::size_t index = 800; index < samples.size(); ++index) {
        found_tail = found_tail || std::abs(samples[index]) > 0.00001F;
        tail_energy += static_cast<double>(samples[index]) * samples[index];
    }
    expect(found_tail, "immersive room produced no early-reflection tail");
    expect(tail_energy > 0.005,
           "immersive room reflection remains too weak to be perceptible");

    audiofreedom::Engine multichannel;
    expect(multichannel.prepare({48000, 6}), "immersive multichannel stream must prepare");
    multichannel.set_preamp_millibels(0);
    multichannel.set_limiter_enabled(false);
    multichannel.set_immersive_field_enabled(true);
    multichannel.set_immersive_amount_percent(100);
    multichannel.set_enabled(true);
    float channels[] = {0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F};
    expect(multichannel.process(channels, 1), "immersive multichannel bypass must process");
    for (std::size_t index = 0; index < 6; ++index) {
        expect_near(channels[index], 0.1F * static_cast<float>(index + 1), 0.000001F,
                    "immersive field changed unsupported multichannel audio");
    }
}

void test_protocol_identity() {
    audiofreedom::protocol::DriverStatus status;
    expect(status.magic == audiofreedom::protocol::kMagic, "status magic mismatch");
    expect(status.protocol.major == 1, "protocol major mismatch");
}

void test_wire_parameter_round_trip() {
    using namespace audiofreedom::protocol;

    const auto encoded = encode_message(make_preamp(-1200));
    expect(encoded.has_value(), "preamp message must encode");
    const auto decoded = decode_message(*encoded);
    expect(decoded.has_value(), "preamp message must decode");
    const auto preamp = read_preamp(*decoded);
    expect(preamp.has_value() && *preamp == -1200, "preamp value changed on the wire");
}

void test_wire_equalizer_round_trip() {
    using namespace audiofreedom::protocol;

    const auto enabledBytes = encode_message(make_equalizer_enabled(true));
    expect(enabledBytes.has_value(), "EQ enabled message must encode");
    const auto enabledMessage = decode_message(*enabledBytes);
    expect(enabledMessage.has_value(), "EQ enabled message must decode");
    const auto enabled = read_equalizer_enabled(*enabledMessage);
    expect(enabled.has_value() && *enabled, "EQ enabled value changed on the wire");

    const auto bandBytes = encode_message(make_equalizer_band(7, -350));
    expect(bandBytes.has_value(), "EQ band message must encode");
    const auto bandMessage = decode_message(*bandBytes);
    expect(bandMessage.has_value(), "EQ band message must decode");
    const auto band = read_equalizer_band(*bandMessage);
    expect(band.has_value() && band->band == 7 && band->millibels == -350,
           "EQ band value changed on the wire");

    EqualizerConfiguration input;
    input.preamp_millibels = -650;
    input.enabled = true;
    input.band_millibels = {350, 500, 300, 0, -100, -100, 100, 200, 200, 100};
    const auto configurationBytes = encode_message(make_equalizer_configuration(input));
    expect(configurationBytes.has_value(), "EQ configuration must encode");
    const auto configurationMessage = decode_message(*configurationBytes);
    expect(configurationMessage.has_value(), "EQ configuration must decode");
    const auto output = read_equalizer_configuration(*configurationMessage);
    expect(output.has_value(), "EQ configuration payload must parse");
    expect(output->preamp_millibels == -650 && output->enabled,
           "EQ configuration header changed on the wire");
    expect(output->band_millibels == input.band_millibels,
           "EQ configuration bands changed on the wire");
}

void test_wire_limiter_and_metrics_round_trip() {
    using namespace audiofreedom::protocol;

    LimiterConfiguration limiter{
            .enabled = true,
            .threshold_millibels = -150,
            .release_milliseconds = 180,
    };
    const auto limiterBytes = encode_message(make_limiter_configuration(limiter));
    expect(limiterBytes.has_value(), "limiter configuration must encode");
    const auto limiterMessage = decode_message(*limiterBytes);
    expect(limiterMessage.has_value(), "limiter configuration must decode");
    const auto limiterOutput = read_limiter_configuration(*limiterMessage);
    expect(limiterOutput.has_value() && limiterOutput->enabled &&
                   limiterOutput->threshold_millibels == -150 &&
                   limiterOutput->release_milliseconds == 180,
           "limiter configuration changed on the wire");

    OutputMetrics metrics{
            .input_peak_millibels = -50,
            .output_peak_millibels = -100,
            .gain_reduction_millibels = 250,
    };
    const auto metricsBytes = encode_message(make_output_metrics(metrics));
    expect(metricsBytes.has_value(), "output metrics must encode");
    const auto metricsMessage = decode_message(*metricsBytes);
    expect(metricsMessage.has_value(), "output metrics must decode");
    const auto metricsOutput = read_output_metrics(*metricsMessage);
    expect(metricsOutput.has_value() && metricsOutput->input_peak_millibels == -50 &&
                   metricsOutput->output_peak_millibels == -100 &&
                   metricsOutput->gain_reduction_millibels == 250,
           "output metrics changed on the wire");
}

void test_wire_dynamic_bass_round_trip() {
    using namespace audiofreedom::protocol;
    DynamicBassConfiguration input{
            .enabled = true,
            .boost_millibels = 750,
            .cutoff_hz = 65,
            .dynamics_percent = 80,
    };
    const auto bytes = encode_message(make_dynamic_bass_configuration(input));
    expect(bytes.has_value(), "dynamic bass configuration must encode");
    const auto message = decode_message(*bytes);
    expect(message.has_value(), "dynamic bass configuration must decode");
    const auto output = read_dynamic_bass_configuration(*message);
    expect(output.has_value() && output->enabled && output->boost_millibels == 750 &&
                   output->cutoff_hz == 65 && output->dynamics_percent == 80,
           "dynamic bass configuration changed on the wire");
}

void test_wire_detail_recovery_round_trip() {
    using namespace audiofreedom::protocol;
    DetailRecoveryConfiguration input{
            .enabled = true,
            .amount_percent = 65,
            .focus_hz = 7000,
            .transients_percent = 85,
    };
    const auto bytes = encode_message(make_detail_recovery_configuration(input));
    expect(bytes.has_value(), "detail recovery configuration must encode");
    const auto message = decode_message(*bytes);
    expect(message.has_value(), "detail recovery configuration must decode");
    const auto output = read_detail_recovery_configuration(*message);
    expect(output.has_value() && output->enabled && output->amount_percent == 65 &&
                   output->focus_hz == 7000 && output->transients_percent == 85,
           "detail recovery configuration changed on the wire");
}

void test_wire_immersive_field_round_trip() {
    using namespace audiofreedom::protocol;
    ImmersiveFieldConfiguration input{
            .enabled = true,
            .amount_percent = 70,
            .width_percent = 80,
            .center_percent = 65,
            .room_percent = 35,
    };
    const auto bytes = encode_message(make_immersive_field_configuration(input));
    expect(bytes.has_value(), "immersive field configuration must encode");
    const auto message = decode_message(*bytes);
    expect(message.has_value(), "immersive field configuration must decode");
    const auto output = read_immersive_field_configuration(*message);
    expect(output.has_value() && output->enabled && output->amount_percent == 70 &&
                   output->width_percent == 80 && output->center_percent == 65 &&
                   output->room_percent == 35,
           "immersive field configuration changed on the wire");
}

void test_wire_protocol_version_round_trip() {
    using namespace audiofreedom::protocol;

    const auto encoded = encode_message(make_protocol_version());
    expect(encoded.has_value(), "protocol version must encode");
    const auto decoded = decode_message(*encoded);
    expect(decoded.has_value(), "protocol version must decode");
    const auto version = read_protocol_version(*decoded);
    expect(version.has_value(), "protocol version payload must parse");
    expect(version->major == kProtocolMajor, "protocol major changed on the wire");
    expect(version->minor == kProtocolMinor, "protocol minor changed on the wire");
}

void test_wire_is_explicit_little_endian() {
    using namespace audiofreedom::protocol;

    const auto encoded = encode_message(make_preamp(-1200));
    expect(encoded.has_value(), "preamp message must encode");
    expect((*encoded)[0] == 0x41 && (*encoded)[1] == 0x46 && (*encoded)[2] == 0x58 &&
                   (*encoded)[3] == 0x31,
           "wire magic is not little-endian AFX1");
    expect((*encoded)[8] == 0x01 && (*encoded)[9] == 0x10,
           "parameter id is not little-endian");
    expect((*encoded)[12] == 4 && (*encoded)[13] == 0,
           "payload length is not little-endian");
    expect((*encoded)[16] == 0x50 && (*encoded)[17] == 0xFB &&
                   (*encoded)[18] == 0xFF && (*encoded)[19] == 0xFF,
           "signed preamp payload is not little-endian two's complement");
}

void test_wire_status_round_trip() {
    using namespace audiofreedom::protocol;

    DriverStatus input;
    input.state = ProcessingState::kProcessing;
    input.sample_rate_hz = 48000;
    input.channel_count = 2;
    input.processed_frames = 123456789;

    const auto encoded = encode_message(make_driver_status(input));
    expect(encoded.has_value(), "driver status must encode");
    const auto decoded = decode_message(*encoded);
    expect(decoded.has_value(), "driver status must decode");
    const auto output = read_driver_status(*decoded);
    expect(output.has_value(), "driver status payload must parse");
    expect(output->state == ProcessingState::kProcessing, "driver state changed on the wire");
    expect(output->sample_rate_hz == 48000, "sample rate changed on the wire");
    expect(output->channel_count == 2, "channel count changed on the wire");
    expect(output->processed_frames == 123456789, "frame count changed on the wire");
}

void test_wire_rejects_invalid_messages() {
    using namespace audiofreedom::protocol;

    WireBytes bytes{};
    expect(!decode_message(bytes).has_value(), "message without magic must be rejected");

    const auto encoded = encode_message(make_enabled(true));
    expect(encoded.has_value(), "enabled message must encode");
    auto invalid = *encoded;
    invalid[4] = 2;
    expect(!decode_message(invalid).has_value(), "unknown protocol major must be rejected");

    invalid = *encoded;
    invalid[12] = static_cast<std::uint8_t>(kWirePayloadCapacity + 1);
    expect(!decode_message(invalid).has_value(), "oversized payload must be rejected");

    std::array<std::uint8_t, kWireMessageSize - 1> shortMessage{};
    expect(!decode_message(shortMessage).has_value(), "short message must be rejected");

    WireMessage oversized{.payload_size = static_cast<std::uint32_t>(kWirePayloadCapacity + 1)};
    expect(!encode_message(oversized).has_value(), "oversized message must not encode");

    auto invalidEnabled = make_enabled(true);
    invalidEnabled.payload[0] = 2;
    expect(!read_enabled(invalidEnabled).has_value(), "non-boolean enabled value must fail");

    DriverStatus status;
    auto invalidStatus = make_driver_status(status);
    invalidStatus.payload[0] = 4;
    expect(!read_driver_status(invalidStatus).has_value(),
           "unknown processing state must fail");
}

}  // namespace

int main() {
    test_rejects_invalid_streams();
    test_bypass_preserves_pcm();
    test_proof_gain_is_minus_twelve_db();
    test_gain_handles_all_supported_channel_counts();
    test_frame_counter_and_reset();
    test_process_rejects_invalid_buffers_and_size_overflow();
    test_preamp_is_clamped();
    test_flat_equalizer_preserves_pcm();
    test_equalizer_band_gain_and_limits();
    test_equalizer_handles_multichannel_and_reset();
    test_limiter_caps_peaks_and_links_channels();
    test_limiter_release_and_clamping();
    test_dynamic_bass_targets_low_frequencies_and_tracks_level();
    test_bass_foundation_small_driver_support_adds_audible_harmonics();
    test_dynamic_bass_links_stereo_and_clamps_settings();
    test_bass_foundation_profiles_remain_finite_and_limited();
    test_detail_recovery_targets_high_frequencies_and_transients();
    test_detail_recovery_bypass_stereo_and_clamping();
    test_immersive_field_shapes_stage_without_latency();
    test_immersive_field_reflections_stereo_and_clamping();
    test_protocol_identity();
    test_wire_parameter_round_trip();
    test_wire_equalizer_round_trip();
    test_wire_limiter_and_metrics_round_trip();
    test_wire_dynamic_bass_round_trip();
    test_wire_detail_recovery_round_trip();
    test_wire_immersive_field_round_trip();
    test_wire_protocol_version_round_trip();
    test_wire_is_explicit_little_endian();
    test_wire_status_round_trip();
    test_wire_rejects_invalid_messages();
    std::cout << "All AudioFreedom native tests passed.\n";
    return EXIT_SUCCESS;
}
