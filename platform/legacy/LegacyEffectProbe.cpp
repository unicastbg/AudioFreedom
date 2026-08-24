#include "audiofreedom/wire.h"

#include <dlfcn.h>
#include <hardware/audio_effect.h>

#include <array>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>

namespace {

constexpr effect_uuid_t kImplementationUuid = {
        0x2f6e8c10, 0x8d44, 0x4b42, 0xb110, {0x16, 0xf3, 0xa7, 0x29, 0xef, 0x01}};

bool commandStatus(effect_handle_t handle, std::uint32_t command,
                   std::uint32_t size, void* data) {
    std::int32_t status = -1;
    std::uint32_t reply_size = sizeof(status);
    return (*handle)->command(handle, command, size, data, &reply_size, &status) == 0 &&
           reply_size == sizeof(status) && status == 0;
}
}  // namespace

int main(int argc, char** argv) {
    if (argc != 2) {
        std::fprintf(stderr, "usage: %s /path/to/libaudiofreedomfx_legacy.so\n", argv[0]);
        return 2;
    }
    void* shared_object = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (shared_object == nullptr) {
        std::fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 3;
    }
    auto* library = static_cast<audio_effect_library_t*>(dlsym(shared_object, "AELI"));
    if (library == nullptr || library->tag != AUDIO_EFFECT_LIBRARY_TAG ||
        library->version != EFFECT_LIBRARY_API_VERSION) return 4;

    effect_descriptor_t descriptor{};
    if (library->get_descriptor(&kImplementationUuid, &descriptor) != 0 ||
        std::strcmp(descriptor.name, "AudioFreedom") != 0) return 5;

    effect_handle_t handle = nullptr;
    if (library->create_effect(&kImplementationUuid, 0, 1, &handle) != 0 || handle == nullptr) {
        return 6;
    }

    effect_config_t config{};
    config.inputCfg.samplingRate = 48000;
    config.outputCfg.samplingRate = 48000;
    config.inputCfg.channels = AUDIO_CHANNEL_OUT_STEREO;
    config.outputCfg.channels = AUDIO_CHANNEL_OUT_STEREO;
    config.inputCfg.format = AUDIO_FORMAT_PCM_FLOAT;
    config.outputCfg.format = AUDIO_FORMAT_PCM_FLOAT;
    config.inputCfg.accessMode = EFFECT_BUFFER_ACCESS_READ;
    config.outputCfg.accessMode = EFFECT_BUFFER_ACCESS_WRITE;
    config.inputCfg.mask = EFFECT_CONFIG_ALL;
    config.outputCfg.mask = EFFECT_CONFIG_ALL;
    if (!commandStatus(handle, EFFECT_CMD_SET_CONFIG, sizeof(config), &config) ||
        !commandStatus(handle, EFFECT_CMD_ENABLE, 0, nullptr)) return 7;

    const auto settings = audiofreedom::protocol::encode_message(
            audiofreedom::protocol::make_equalizer_configuration({
                    .preamp_millibels = -1200,
                    .enabled = true,
                    .band_millibels = {},
            }));
    struct ParameterBlock {
        std::int32_t status = 0;
        std::uint32_t psize = 4;
        std::uint32_t vsize = audiofreedom::protocol::kWireMessageSize;
        std::array<std::uint8_t, 4 + audiofreedom::protocol::kWireMessageSize> data{};
    } parameter;
    std::memcpy(parameter.data.data(), "AFX1", 4);
    std::copy(settings->begin(), settings->end(), parameter.data.begin() + 4);
    if (!commandStatus(handle, EFFECT_CMD_SET_PARAM, sizeof(parameter), &parameter)) return 8;

    std::array<float, 256> input{};
    std::array<float, 256> output{};
    input.fill(0.25F);
    audio_buffer_t input_buffer{.frameCount = input.size() / 2, .f32 = input.data()};
    audio_buffer_t output_buffer{.frameCount = output.size() / 2, .f32 = output.data()};
    if ((*handle)->process(handle, &input_buffer, &output_buffer) != 0) return 9;
    const auto expected = 0.25F * std::pow(10.0F, -12.0F / 20.0F);
    if (std::fabs(output[64] - expected) > 0.005F) {
        std::fprintf(stderr, "unexpected proof gain: output=%f expected=%f\n", output[64], expected);
        return 10;
    }

    if (library->release_effect(handle) != 0) return 11;
    dlclose(shared_object);
    std::printf("AudioFreedom legacy ABI and -12 dB processing probe passed.\n");
    return 0;
}
