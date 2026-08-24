#define LOG_TAG "AudioFreedomLegacy"

#include "audiofreedom/engine.h"
#include "audiofreedom/wire.h"

#include <android/log.h>
#include <hardware/audio_effect.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <new>
#include <span>

namespace {

using audiofreedom::protocol::ParameterId;
using audiofreedom::protocol::WireMessage;

constexpr effect_uuid_t kTypeUuid = {
        0xa7e03c90, 0x7c3d, 0x4f48, 0x9c8d, {0x49, 0x7c, 0x8f, 0x1b, 0x12, 0x01}};
constexpr effect_uuid_t kImplementationUuid = {
        0x2f6e8c10, 0x8d44, 0x4b42, 0xb110, {0x16, 0xf3, 0xa7, 0x29, 0xef, 0x01}};
constexpr std::array<std::uint8_t, 4> kParameterKey = {'A', 'F', 'X', '1'};
constexpr std::size_t kScratchSamples = 1024;

constexpr effect_descriptor_t kDescriptor = {
        .type = kTypeUuid,
        .uuid = kImplementationUuid,
        .apiVersion = EFFECT_CONTROL_API_VERSION,
        .flags = EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_FIRST,
        .cpuLoad = 0,
        .memoryUsage = 0,
        .name = "AudioFreedom",
        .implementor = "AudioFreedom",
};

bool uuidEquals(const effect_uuid_t& left, const effect_uuid_t& right) {
    return std::memcmp(&left, &right, sizeof(effect_uuid_t)) == 0;
}

struct EffectModule {
    effect_interface_s* interface = nullptr;
    audiofreedom::Engine engine;
    effect_config_t config{};
    std::int32_t session_id = 0;
    std::int32_t io_id = 0;
    bool configured = false;
    bool enabled = false;
};

EffectModule* moduleFromHandle(effect_handle_t handle) {
    return reinterpret_cast<EffectModule*>(handle);
}

void writeStatus(std::uint32_t* reply_size, void* reply_data, std::int32_t status) {
    if (reply_size == nullptr || reply_data == nullptr || *reply_size < sizeof(status)) {
        return;
    }
    std::memcpy(reply_data, &status, sizeof(status));
    *reply_size = sizeof(status);
}

bool validateAndPrepare(EffectModule& module, const effect_config_t& config) {
    const auto input_channels = audio_channel_count_from_out_mask(
            static_cast<audio_channel_mask_t>(config.inputCfg.channels));
    const auto output_channels = audio_channel_count_from_out_mask(
            static_cast<audio_channel_mask_t>(config.outputCfg.channels));
    const auto input_format = static_cast<audio_format_t>(config.inputCfg.format);
    const auto output_format = static_cast<audio_format_t>(config.outputCfg.format);
    const bool supported_format = input_format == AUDIO_FORMAT_PCM_FLOAT ||
                                  input_format == AUDIO_FORMAT_PCM_16_BIT;
    if (config.inputCfg.samplingRate == 0 ||
        config.inputCfg.samplingRate != config.outputCfg.samplingRate ||
        input_channels == 0 || input_channels != output_channels ||
        input_channels > audiofreedom::Engine::kMaxChannelCount ||
        input_format != output_format || !supported_format) {
        return false;
    }

    if (!module.engine.prepare({
                .sample_rate_hz = config.inputCfg.samplingRate,
                .channel_count = static_cast<std::uint32_t>(input_channels),
        })) {
        return false;
    }
    module.config = config;
    module.configured = true;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "configured session=%d io=%d rate=%u channels=%u format=%#x",
                        module.session_id, module.io_id, config.inputCfg.samplingRate,
                        input_channels, static_cast<unsigned>(input_format));
    return true;
}

bool applyMessage(audiofreedom::Engine& engine, const WireMessage& message) {
    switch (message.parameter_id) {
        case ParameterId::kEnabled: {
            const auto value = audiofreedom::protocol::read_enabled(message);
            if (!value) return false;
            engine.set_enabled(*value);
            return true;
        }
        case ParameterId::kPreampMillibels: {
            const auto value = audiofreedom::protocol::read_preamp(message);
            if (!value || *value < audiofreedom::Engine::kMinPreampMillibels ||
                *value > audiofreedom::Engine::kMaxPreampMillibels) return false;
            engine.set_preamp_millibels(*value);
            return true;
        }
        case ParameterId::kEqualizerEnabled: {
            const auto value = audiofreedom::protocol::read_equalizer_enabled(message);
            if (!value) return false;
            engine.set_equalizer_enabled(*value);
            return true;
        }
        case ParameterId::kEqualizerBandMillibels: {
            const auto value = audiofreedom::protocol::read_equalizer_band(message);
            return value && value->band < audiofreedom::Engine::kEqBandCount &&
                   value->millibels >= audiofreedom::Engine::kMinEqBandMillibels &&
                   value->millibels <= audiofreedom::Engine::kMaxEqBandMillibels &&
                   engine.set_eq_band_millibels(value->band, value->millibels);
        }
        case ParameterId::kEqualizerConfiguration: {
            const auto value = audiofreedom::protocol::read_equalizer_configuration(message);
            if (!value ||
                value->preamp_millibels < audiofreedom::Engine::kMinPreampMillibels ||
                value->preamp_millibels > audiofreedom::Engine::kMaxPreampMillibels) return false;
            for (const auto gain : value->band_millibels) {
                if (gain < audiofreedom::Engine::kMinEqBandMillibels ||
                    gain > audiofreedom::Engine::kMaxEqBandMillibels) return false;
            }
            engine.set_preamp_millibels(value->preamp_millibels);
            engine.set_equalizer_enabled(value->enabled);
            for (std::size_t band = 0; band < value->band_millibels.size(); ++band) {
                (void)engine.set_eq_band_millibels(band, value->band_millibels[band]);
            }
            return true;
        }
        case ParameterId::kLimiterConfiguration: {
            const auto value = audiofreedom::protocol::read_limiter_configuration(message);
            if (!value ||
                value->threshold_millibels < audiofreedom::Engine::kMinLimiterThresholdMillibels ||
                value->threshold_millibels > audiofreedom::Engine::kMaxLimiterThresholdMillibels ||
                value->release_milliseconds < audiofreedom::Engine::kMinLimiterReleaseMilliseconds ||
                value->release_milliseconds > audiofreedom::Engine::kMaxLimiterReleaseMilliseconds) return false;
            engine.set_limiter_enabled(value->enabled);
            engine.set_limiter_threshold_millibels(value->threshold_millibels);
            engine.set_limiter_release_milliseconds(value->release_milliseconds);
            return true;
        }
        case ParameterId::kDynamicBassConfiguration: {
            const auto value = audiofreedom::protocol::read_dynamic_bass_configuration(message);
            if (!value || value->boost_millibels < audiofreedom::Engine::kMinBassBoostMillibels ||
                value->boost_millibels > audiofreedom::Engine::kMaxBassBoostMillibels ||
                value->cutoff_hz < audiofreedom::Engine::kMinBassCutoffHz ||
                value->cutoff_hz > audiofreedom::Engine::kMaxBassCutoffHz ||
                value->dynamics_percent > audiofreedom::Engine::kMaxBassDynamicsPercent) return false;
            engine.set_dynamic_bass_enabled(value->enabled);
            engine.set_bass_boost_millibels(value->boost_millibels);
            engine.set_bass_cutoff_hz(value->cutoff_hz);
            engine.set_bass_dynamics_percent(value->dynamics_percent);
            return true;
        }
        case ParameterId::kDetailRecoveryConfiguration: {
            const auto value = audiofreedom::protocol::read_detail_recovery_configuration(message);
            if (!value || value->amount_percent > audiofreedom::Engine::kMaxDetailAmountPercent ||
                value->focus_hz < audiofreedom::Engine::kMinDetailFocusHz ||
                value->focus_hz > audiofreedom::Engine::kMaxDetailFocusHz ||
                value->transients_percent > audiofreedom::Engine::kMaxDetailTransientsPercent) return false;
            engine.set_detail_recovery_enabled(value->enabled);
            engine.set_detail_amount_percent(value->amount_percent);
            engine.set_detail_focus_hz(value->focus_hz);
            engine.set_detail_transients_percent(value->transients_percent);
            return true;
        }
        case ParameterId::kImmersiveFieldConfiguration: {
            const auto value = audiofreedom::protocol::read_immersive_field_configuration(message);
            if (!value || value->amount_percent > audiofreedom::Engine::kMaxImmersivePercent ||
                value->width_percent > audiofreedom::Engine::kMaxImmersivePercent ||
                value->center_percent > audiofreedom::Engine::kMaxImmersivePercent ||
                value->room_percent > audiofreedom::Engine::kMaxImmersivePercent) return false;
            engine.set_immersive_field_enabled(value->enabled);
            engine.set_immersive_amount_percent(value->amount_percent);
            engine.set_immersive_width_percent(value->width_percent);
            engine.set_immersive_center_percent(value->center_percent);
            engine.set_immersive_room_percent(value->room_percent);
            return true;
        }
        default:
            return false;
    }
}

std::optional<WireMessage> queryMessage(const EffectModule& module,
                                        const WireMessage& query) {
    if (query.payload_size != 0) return std::nullopt;
    switch (query.parameter_id) {
        case ParameterId::kProtocolVersion:
            return audiofreedom::protocol::make_protocol_version();
        case ParameterId::kEnabled:
            return audiofreedom::protocol::make_enabled(module.engine.enabled());
        case ParameterId::kPreampMillibels:
            return audiofreedom::protocol::make_preamp(module.engine.preamp_millibels());
        case ParameterId::kEqualizerEnabled:
            return audiofreedom::protocol::make_equalizer_enabled(
                    module.engine.equalizer_enabled());
        case ParameterId::kDriverStatus: {
            const auto config = module.engine.stream_config();
            audiofreedom::protocol::DriverStatus status;
            status.state = !module.configured
                    ? audiofreedom::protocol::ProcessingState::kUnavailable
                    : (module.enabled && module.engine.enabled()
                               ? audiofreedom::protocol::ProcessingState::kProcessing
                               : audiofreedom::protocol::ProcessingState::kIdle);
            status.sample_rate_hz = config.sample_rate_hz;
            status.channel_count = config.channel_count;
            status.processed_frames = module.engine.processed_frames();
            return audiofreedom::protocol::make_driver_status(status);
        }
        case ParameterId::kOutputMetrics: {
            const auto metrics = module.engine.output_metrics();
            return audiofreedom::protocol::make_output_metrics({
                    .input_peak_millibels = metrics.input_peak_millibels,
                    .output_peak_millibels = metrics.output_peak_millibels,
                    .gain_reduction_millibels = metrics.gain_reduction_millibels,
            });
        }
        default:
            return std::nullopt;
    }
}

int32_t processFloat(EffectModule& module, audio_buffer_t& input, audio_buffer_t& output) {
    const auto channels = module.engine.stream_config().channel_count;
    const auto samples = input.frameCount * channels;
    const bool accumulate = module.config.outputCfg.accessMode == EFFECT_BUFFER_ACCESS_ACCUMULATE;
    if (!accumulate) {
        if (input.f32 != output.f32) std::copy_n(input.f32, samples, output.f32);
        return module.engine.process(output.f32, input.frameCount) ? 0 : -EINVAL;
    }

    std::array<float, kScratchSamples> scratch{};
    std::size_t sample_offset = 0;
    while (sample_offset < samples) {
        auto count = std::min(kScratchSamples, samples - sample_offset);
        count -= count % channels;
        std::copy_n(input.f32 + sample_offset, count, scratch.data());
        if (!module.engine.process(scratch.data(), count / channels)) return -EINVAL;
        for (std::size_t index = 0; index < count; ++index) {
            output.f32[sample_offset + index] += scratch[index];
        }
        sample_offset += count;
    }
    return 0;
}

int32_t processInt16(EffectModule& module, audio_buffer_t& input, audio_buffer_t& output) {
    const auto channels = module.engine.stream_config().channel_count;
    const auto samples = input.frameCount * channels;
    const bool accumulate = module.config.outputCfg.accessMode == EFFECT_BUFFER_ACCESS_ACCUMULATE;
    std::array<float, kScratchSamples> scratch{};
    std::size_t sample_offset = 0;
    while (sample_offset < samples) {
        auto count = std::min(kScratchSamples, samples - sample_offset);
        count -= count % channels;
        for (std::size_t index = 0; index < count; ++index) {
            scratch[index] = static_cast<float>(input.s16[sample_offset + index]) / 32768.0F;
        }
        if (!module.engine.process(scratch.data(), count / channels)) return -EINVAL;
        for (std::size_t index = 0; index < count; ++index) {
            const auto scaled = static_cast<std::int32_t>(std::lrintf(
                    std::clamp(scratch[index], -1.0F, 0.9999695F) * 32768.0F));
            const auto mixed = accumulate
                    ? scaled + static_cast<std::int32_t>(output.s16[sample_offset + index])
                    : scaled;
            output.s16[sample_offset + index] = static_cast<std::int16_t>(
                    std::clamp(mixed, -32768, 32767));
        }
        sample_offset += count;
    }
    return 0;
}

int32_t effectProcess(effect_handle_t handle, audio_buffer_t* input,
                      audio_buffer_t* output) {
    auto* module = moduleFromHandle(handle);
    if (module == nullptr || !module->configured || input == nullptr || output == nullptr ||
        input->raw == nullptr || output->raw == nullptr ||
        input->frameCount != output->frameCount) return -EINVAL;
    if (!module->enabled || !module->engine.enabled()) {
        if (input->raw != output->raw) {
            const auto channels = module->engine.stream_config().channel_count;
            const auto bytes_per_sample =
                    static_cast<audio_format_t>(module->config.inputCfg.format) ==
                                    AUDIO_FORMAT_PCM_FLOAT
                            ? sizeof(float)
                            : sizeof(std::int16_t);
            std::memcpy(output->raw, input->raw,
                        input->frameCount * channels * bytes_per_sample);
        }
        return 0;
    }
    return static_cast<audio_format_t>(module->config.inputCfg.format) == AUDIO_FORMAT_PCM_FLOAT
            ? processFloat(*module, *input, *output)
            : processInt16(*module, *input, *output);
}

int32_t setParameter(EffectModule& module, std::uint32_t command_size,
                     const void* command_data) {
    if (command_data == nullptr || command_size < sizeof(effect_param_t)) return -EINVAL;
    const auto* parameter = static_cast<const effect_param_t*>(command_data);
    const auto aligned_parameter_size = (parameter->psize + 3U) & ~3U;
    const auto required = sizeof(effect_param_t) + aligned_parameter_size + parameter->vsize;
    if (required > command_size || parameter->psize != kParameterKey.size() ||
        std::memcmp(parameter->data, kParameterKey.data(), kParameterKey.size()) != 0) return -EINVAL;
    const auto* value = reinterpret_cast<const std::uint8_t*>(parameter->data) +
                        aligned_parameter_size;
    const auto message = audiofreedom::protocol::decode_message(
            std::span<const std::uint8_t>(value, parameter->vsize));
    return message && applyMessage(module.engine, *message) ? 0 : -EINVAL;
}

int32_t getParameter(const EffectModule& module, std::uint32_t command_size,
                     const void* command_data, std::uint32_t* reply_size,
                     void* reply_data) {
    if (command_data == nullptr || reply_size == nullptr || reply_data == nullptr ||
        command_size < sizeof(effect_param_t)) return -EINVAL;
    const auto* request = static_cast<const effect_param_t*>(command_data);
    const auto aligned_parameter_size = (request->psize + 3U) & ~3U;
    if (sizeof(effect_param_t) + aligned_parameter_size > command_size) return -EINVAL;
    const auto query = audiofreedom::protocol::decode_message(
            std::span<const std::uint8_t>(
                    reinterpret_cast<const std::uint8_t*>(request->data), request->psize));
    if (!query) return -EINVAL;
    const auto response = queryMessage(module, *query);
    const auto encoded = response ? audiofreedom::protocol::encode_message(*response) : std::nullopt;
    if (!encoded) return -EINVAL;
    const auto required = sizeof(effect_param_t) + aligned_parameter_size + encoded->size();
    if (*reply_size < required) return -ENOMEM;
    auto* reply = static_cast<effect_param_t*>(reply_data);
    reply->status = 0;
    reply->psize = request->psize;
    reply->vsize = encoded->size();
    std::memcpy(reply->data, request->data, request->psize);
    auto* value = reinterpret_cast<std::uint8_t*>(reply->data) + aligned_parameter_size;
    std::copy(encoded->begin(), encoded->end(), value);
    *reply_size = required;
    return 0;
}

int32_t effectCommand(effect_handle_t handle, uint32_t command_code,
                      uint32_t command_size, void* command_data,
                      uint32_t* reply_size, void* reply_data) {
    auto* module = moduleFromHandle(handle);
    if (module == nullptr) return -EINVAL;
    switch (command_code) {
        case EFFECT_CMD_INIT:
            module->engine.reset();
            writeStatus(reply_size, reply_data, 0);
            return 0;
        case EFFECT_CMD_SET_CONFIG: {
            const auto status = command_size == sizeof(effect_config_t) && command_data != nullptr &&
                                        validateAndPrepare(
                                                *module,
                                                *static_cast<effect_config_t*>(command_data))
                                ? 0
                                : -EINVAL;
            writeStatus(reply_size, reply_data, status);
            return 0;
        }
        case EFFECT_CMD_RESET:
            module->engine.reset();
            if (reply_size != nullptr) *reply_size = 0;
            return 0;
        case EFFECT_CMD_ENABLE:
            module->enabled = true;
            module->engine.set_enabled(true);
            writeStatus(reply_size, reply_data, 0);
            return 0;
        case EFFECT_CMD_DISABLE:
            module->enabled = false;
            module->engine.set_enabled(false);
            writeStatus(reply_size, reply_data, 0);
            return 0;
        case EFFECT_CMD_SET_PARAM: {
            const auto status = setParameter(*module, command_size, command_data);
            writeStatus(reply_size, reply_data, status);
            return 0;
        }
        case EFFECT_CMD_GET_PARAM:
            return getParameter(*module, command_size, command_data, reply_size, reply_data);
        case EFFECT_CMD_GET_CONFIG:
            if (reply_size == nullptr || reply_data == nullptr ||
                *reply_size < sizeof(effect_config_t)) return -EINVAL;
            std::memcpy(reply_data, &module->config, sizeof(effect_config_t));
            *reply_size = sizeof(effect_config_t);
            return 0;
        case EFFECT_CMD_OFFLOAD:
            writeStatus(reply_size, reply_data, -ENOSYS);
            return 0;
        default:
            return 0;
    }
}

int32_t effectGetDescriptor(effect_handle_t handle, effect_descriptor_t* descriptor) {
    if (moduleFromHandle(handle) == nullptr || descriptor == nullptr) return -EINVAL;
    *descriptor = kDescriptor;
    return 0;
}

effect_interface_s kInterface = {
        .process = effectProcess,
        .command = effectCommand,
        .get_descriptor = effectGetDescriptor,
        .process_reverse = nullptr,
};

int32_t createEffect(const effect_uuid_t* uuid, int32_t session_id, int32_t io_id,
                     effect_handle_t* handle) {
    if (uuid == nullptr || handle == nullptr) return -EINVAL;
    if (!uuidEquals(*uuid, kImplementationUuid)) return -ENOENT;
    auto* module = new (std::nothrow) EffectModule;
    if (module == nullptr) return -ENOMEM;
    module->interface = &kInterface;
    module->session_id = session_id;
    module->io_id = io_id;
    *handle = reinterpret_cast<effect_handle_t>(&module->interface);
    return 0;
}

int32_t releaseEffect(effect_handle_t handle) {
    auto* module = moduleFromHandle(handle);
    if (module == nullptr) return -EINVAL;
    delete module;
    return 0;
}

int32_t getDescriptor(const effect_uuid_t* uuid, effect_descriptor_t* descriptor) {
    if (uuid == nullptr || descriptor == nullptr) return -EINVAL;
    if (!uuidEquals(*uuid, kImplementationUuid)) return -ENOENT;
    *descriptor = kDescriptor;
    return 0;
}

}  // namespace

extern "C" __attribute__((visibility("default"))) audio_effect_library_t
        AUDIO_EFFECT_LIBRARY_INFO_SYM = {
                .tag = AUDIO_EFFECT_LIBRARY_TAG,
                .version = EFFECT_LIBRARY_API_VERSION,
                .name = "AudioFreedom legacy effect library",
                .implementor = "AudioFreedom",
                .create_effect = createEffect,
                .release_effect = releaseEffect,
                .get_descriptor = getDescriptor,
                .create_effect_3_1 = nullptr,
        };
