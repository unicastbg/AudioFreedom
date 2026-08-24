#define LOG_TAG "AHAL_AudioFreedomEffect"

#include "AudioFreedomEffect.h"

#include <aidl/android/hardware/audio/effect/DefaultExtension.h>
#include <android-base/logging.h>
#include <system/audio_effects/effect_uuid.h>

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <span>

using aidl::android::hardware::audio::effect::AudioFreedomEffect;
using aidl::android::hardware::audio::effect::DefaultExtension;
using aidl::android::hardware::audio::effect::Descriptor;
using aidl::android::hardware::audio::effect::IEffect;
using aidl::android::hardware::audio::effect::VendorExtension;
using aidl::android::media::audio::common::AudioUuid;

namespace aidl::android::hardware::audio::effect {

const AudioUuid& getAudioFreedomTypeUuid() {
    static const AudioUuid uuid = stringToUuid("a7e03c90-7c3d-4f48-9c8d-497c8f1b1201");
    return uuid;
}

const AudioUuid& getAudioFreedomImplUuid() {
    static const AudioUuid uuid = stringToUuid("2f6e8c10-8d44-4b42-b110-16f3a729ef01");
    return uuid;
}

}  // namespace aidl::android::hardware::audio::effect

using aidl::android::hardware::audio::effect::getAudioFreedomImplUuid;
using aidl::android::hardware::audio::effect::getAudioFreedomTypeUuid;

extern "C" binder_exception_t createEffect(const AudioUuid* implUuid,
                                            std::shared_ptr<IEffect>* instance) {
    if (implUuid == nullptr || *implUuid != getAudioFreedomImplUuid() || instance == nullptr) {
        LOG(ERROR) << "createEffect rejected invalid arguments";
        return EX_ILLEGAL_ARGUMENT;
    }
    *instance = ndk::SharedRefBase::make<AudioFreedomEffect>();
    LOG(INFO) << "createEffect created AudioFreedom instance " << instance->get();
    return EX_NONE;
}

extern "C" binder_exception_t queryEffect(const AudioUuid* implUuid, Descriptor* descriptor) {
    if (implUuid == nullptr || *implUuid != getAudioFreedomImplUuid() || descriptor == nullptr) {
        return EX_ILLEGAL_ARGUMENT;
    }
    *descriptor = AudioFreedomEffect::kDescriptor;
    return EX_NONE;
}

namespace aidl::android::hardware::audio::effect {

namespace {

std::atomic<bool> gProcessingEnabled{true};
std::atomic<int32_t> gPreampMillibels{audiofreedom::Engine::kProofPreampMillibels};
std::atomic<bool> gEqualizerEnabled{false};
std::array<std::atomic<int32_t>, audiofreedom::Engine::kEqBandCount> gEqBandMillibels{};
std::atomic<bool> gDynamicBassEnabled{false};
std::atomic<int32_t> gBassBoostMillibels{
        audiofreedom::Engine::kDefaultBassBoostMillibels};
std::atomic<uint32_t> gBassCutoffHz{audiofreedom::Engine::kDefaultBassCutoffHz};
std::atomic<uint32_t> gBassDynamicsPercent{
        audiofreedom::Engine::kDefaultBassDynamicsPercent};
std::atomic<bool> gDetailRecoveryEnabled{false};
std::atomic<uint32_t> gDetailAmountPercent{
        audiofreedom::Engine::kDefaultDetailAmountPercent};
std::atomic<uint32_t> gDetailFocusHz{audiofreedom::Engine::kDefaultDetailFocusHz};
std::atomic<uint32_t> gDetailTransientsPercent{
        audiofreedom::Engine::kDefaultDetailTransientsPercent};
std::atomic<bool> gImmersiveFieldEnabled{false};
std::atomic<uint32_t> gImmersiveAmountPercent{
        audiofreedom::Engine::kDefaultImmersiveAmountPercent};
std::atomic<uint32_t> gImmersiveWidthPercent{
        audiofreedom::Engine::kDefaultImmersiveWidthPercent};
std::atomic<uint32_t> gImmersiveCenterPercent{
        audiofreedom::Engine::kDefaultImmersiveCenterPercent};
std::atomic<uint32_t> gImmersiveRoomPercent{
        audiofreedom::Engine::kDefaultImmersiveRoomPercent};
std::atomic<bool> gLimiterEnabled{true};
std::atomic<int32_t> gLimiterThresholdMillibels{
        audiofreedom::Engine::kDefaultLimiterThresholdMillibels};
std::atomic<uint32_t> gLimiterReleaseMilliseconds{
        audiofreedom::Engine::kDefaultLimiterReleaseMilliseconds};
std::atomic<int32_t> gInputPeakMillibels{-12000};
std::atomic<int32_t> gOutputPeakMillibels{-12000};
std::atomic<int32_t> gGainReductionMillibels{0};
std::atomic<uint64_t> gSettingsRevision{1};

std::optional<audiofreedom::protocol::WireMessage> decodeDirectWire(
        const std::vector<uint8_t>& bytes) {
    return audiofreedom::protocol::decode_message(
            std::span<const uint8_t>(bytes.data(), bytes.size()));
}

uint32_t readLittleEndianU32(const std::vector<uint8_t>& bytes, const size_t offset) {
    if (offset > bytes.size() || bytes.size() - offset < sizeof(uint32_t)) {
        return 0;
    }
    uint32_t value = 0;
    for (size_t index = 0; index < sizeof(uint32_t); ++index) {
        value |= static_cast<uint32_t>(bytes[offset + index]) << (index * 8U);
    }
    return value;
}

void writeLittleEndianU32(std::vector<uint8_t>& bytes, const size_t offset,
                          const uint32_t value) {
    for (size_t index = 0; index < sizeof(uint32_t); ++index) {
        bytes[offset + index] = static_cast<uint8_t>(value >> (index * 8U));
    }
}

std::optional<audiofreedom::protocol::WireMessage> decodeWrappedWire(
        const std::vector<uint8_t>& bytes, const bool fromValueArea) {
    constexpr size_t kEffectParamHeaderSize = 12;
    if (bytes.size() < kEffectParamHeaderSize) {
        return std::nullopt;
    }

    const size_t parameterSize = readLittleEndianU32(bytes, 4);
    const size_t valueSize = readLittleEndianU32(bytes, 8);
    if (parameterSize > bytes.size() - kEffectParamHeaderSize) {
        return std::nullopt;
    }
    const size_t alignedParameterSize = (parameterSize + 3U) & ~size_t{3U};
    if (alignedParameterSize > bytes.size() - kEffectParamHeaderSize) {
        return std::nullopt;
    }
    const size_t valueOffset = kEffectParamHeaderSize + alignedParameterSize;
    if (valueSize > bytes.size() - valueOffset) {
        return std::nullopt;
    }

    const size_t offset = fromValueArea ? valueOffset : kEffectParamHeaderSize;
    const size_t size = fromValueArea ? valueSize : parameterSize;
    return audiofreedom::protocol::decode_message(
            std::span<const uint8_t>(bytes.data() + offset, size));
}

std::optional<audiofreedom::protocol::WireMessage> decodeSetWire(
        const std::vector<uint8_t>& bytes) {
    if (const auto direct = decodeDirectWire(bytes); direct.has_value()) {
        return direct;
    }
    return decodeWrappedWire(bytes, true);
}

std::optional<audiofreedom::protocol::WireMessage> decodeQueryWire(
        const std::vector<uint8_t>& bytes) {
    if (const auto direct = decodeDirectWire(bytes); direct.has_value()) {
        return direct;
    }
    return decodeWrappedWire(bytes, false);
}

std::vector<uint8_t> encodeWire(const audiofreedom::protocol::WireMessage& message) {
    const auto encoded = audiofreedom::protocol::encode_message(message);
    if (!encoded.has_value()) {
        return {};
    }
    return {encoded->begin(), encoded->end()};
}

std::vector<uint8_t> encodeWireResponse(
        const std::vector<uint8_t>& request,
        const audiofreedom::protocol::WireMessage& message) {
    const auto direct = encodeWire(message);
    if (request.size() == audiofreedom::protocol::kWireMessageSize || direct.empty()) {
        return direct;
    }

    constexpr size_t kEffectParamHeaderSize = 12;
    if (request.size() < kEffectParamHeaderSize) {
        return {};
    }
    const size_t parameterSize = readLittleEndianU32(request, 4);
    const size_t alignedParameterSize = (parameterSize + 3U) & ~size_t{3U};
    if (parameterSize > request.size() - kEffectParamHeaderSize ||
        alignedParameterSize > request.size() - kEffectParamHeaderSize) {
        return {};
    }

    const size_t valueOffset = kEffectParamHeaderSize + alignedParameterSize;
    std::vector<uint8_t> wrapped(valueOffset + direct.size());
    writeLittleEndianU32(wrapped, 0, 0);
    writeLittleEndianU32(wrapped, 4, static_cast<uint32_t>(parameterSize));
    writeLittleEndianU32(wrapped, 8, static_cast<uint32_t>(direct.size()));
    std::copy_n(request.begin() + kEffectParamHeaderSize, parameterSize,
                wrapped.begin() + kEffectParamHeaderSize);
    std::copy(direct.begin(), direct.end(), wrapped.begin() + valueOffset);
    return wrapped;
}

}  // namespace

AudioFreedomEffectContext::AudioFreedomEffectContext(const int statusDepth,
                                                     const Parameter::Common& common)
    : EffectContext(statusDepth, common) {
    mEngine.set_enabled(true);
    mPrepared = prepareEngine(common) == RetCode::SUCCESS;
    syncGlobalSettings();
    LOG(INFO) << "context session=" << common.session << " io=" << common.ioHandle
              << " sampleRate=" << common.input.base.sampleRate
              << " channels=" << mEngine.stream_config().channel_count
              << " frameCount=" << common.input.frameCount
              << " prepared=" << mPrepared;
}

RetCode AudioFreedomEffectContext::prepareEngine(const Parameter::Common& common) {
    const auto inputChannels =
            ::aidl::android::hardware::audio::common::getChannelCount(common.input.base.channelMask);
    const auto outputChannels = ::aidl::android::hardware::audio::common::getChannelCount(
            common.output.base.channelMask);
    if (inputChannels == 0 || inputChannels != outputChannels || common.input.base.sampleRate <= 0 ||
        common.input.base.sampleRate != common.output.base.sampleRate) {
        mPrepared = false;
        return RetCode::ERROR_ILLEGAL_PARAMETER;
    }

    mPrepared = mEngine.prepare({
            .sample_rate_hz = static_cast<uint32_t>(common.input.base.sampleRate),
            .channel_count = static_cast<uint32_t>(inputChannels),
    });
    return mPrepared ? RetCode::SUCCESS : RetCode::ERROR_ILLEGAL_PARAMETER;
}

RetCode AudioFreedomEffectContext::setCommon(const Parameter::Common& common) {
    if (prepareEngine(common) != RetCode::SUCCESS) {
        return RetCode::ERROR_ILLEGAL_PARAMETER;
    }
    const auto result = EffectContext::setCommon(common);
    if (result != RetCode::SUCCESS) {
        mPrepared = false;
    }
    return result;
}

RetCode AudioFreedomEffectContext::enable() {
    mRunning = true;
    if (getSessionId() == 0) {
        gProcessingEnabled.store(true, std::memory_order_release);
        LOG(ERROR) << "global processing enabled by control session";
    }
    LOG(INFO) << "processing enabled session=" << getSessionId();
    return RetCode::SUCCESS;
}

RetCode AudioFreedomEffectContext::disable() {
    mRunning = false;
    if (getSessionId() == 0) {
        gProcessingEnabled.store(false, std::memory_order_release);
        LOG(ERROR) << "global processing disabled by control session";
    }
    return RetCode::SUCCESS;
}

RetCode AudioFreedomEffectContext::reset() {
    mEngine.reset();
    return RetCode::SUCCESS;
}

void AudioFreedomEffectContext::syncGlobalSettings() {
    const auto revision = gSettingsRevision.load(std::memory_order_acquire);
    if (revision == mAppliedSettingsRevision) {
        return;
    }

    mEngine.set_preamp_millibels(gPreampMillibels.load(std::memory_order_acquire));
    mEngine.set_equalizer_enabled(gEqualizerEnabled.load(std::memory_order_acquire));
    for (size_t band = 0; band < audiofreedom::Engine::kEqBandCount; ++band) {
        (void)mEngine.set_eq_band_millibels(
                band, gEqBandMillibels[band].load(std::memory_order_acquire));
    }
    mEngine.set_dynamic_bass_enabled(gDynamicBassEnabled.load(std::memory_order_acquire));
    mEngine.set_bass_boost_millibels(gBassBoostMillibels.load(std::memory_order_acquire));
    mEngine.set_bass_cutoff_hz(gBassCutoffHz.load(std::memory_order_acquire));
    mEngine.set_bass_dynamics_percent(
            gBassDynamicsPercent.load(std::memory_order_acquire));
    mEngine.set_detail_recovery_enabled(
            gDetailRecoveryEnabled.load(std::memory_order_acquire));
    mEngine.set_detail_amount_percent(gDetailAmountPercent.load(std::memory_order_acquire));
    mEngine.set_detail_focus_hz(gDetailFocusHz.load(std::memory_order_acquire));
    mEngine.set_detail_transients_percent(
            gDetailTransientsPercent.load(std::memory_order_acquire));
    mEngine.set_immersive_field_enabled(
            gImmersiveFieldEnabled.load(std::memory_order_acquire));
    mEngine.set_immersive_amount_percent(
            gImmersiveAmountPercent.load(std::memory_order_acquire));
    mEngine.set_immersive_width_percent(
            gImmersiveWidthPercent.load(std::memory_order_acquire));
    mEngine.set_immersive_center_percent(
            gImmersiveCenterPercent.load(std::memory_order_acquire));
    mEngine.set_immersive_room_percent(
            gImmersiveRoomPercent.load(std::memory_order_acquire));
    mEngine.set_limiter_enabled(gLimiterEnabled.load(std::memory_order_acquire));
    mEngine.set_limiter_threshold_millibels(
            gLimiterThresholdMillibels.load(std::memory_order_acquire));
    mEngine.set_limiter_release_milliseconds(
            gLimiterReleaseMilliseconds.load(std::memory_order_acquire));
    mAppliedSettingsRevision = revision;
}

RetCode AudioFreedomEffectContext::setParams(const std::vector<uint8_t>& params) {
    const auto message = decodeSetWire(params);
    if (!message.has_value()) {
        return RetCode::ERROR_ILLEGAL_PARAMETER;
    }

    using audiofreedom::protocol::ParameterId;
    switch (message->parameter_id) {
        case ParameterId::kEnabled: {
            const auto enabled = audiofreedom::protocol::read_enabled(*message);
            if (!enabled.has_value()) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_enabled(*enabled);
            if (getSessionId() == 0) {
                gProcessingEnabled.store(*enabled, std::memory_order_release);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kPreampMillibels: {
            const auto millibels = audiofreedom::protocol::read_preamp(*message);
            if (!millibels.has_value() || *millibels < audiofreedom::Engine::kMinPreampMillibels ||
                *millibels > audiofreedom::Engine::kMaxPreampMillibels) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_preamp_millibels(*millibels);
            if (getSessionId() == 0) {
                gPreampMillibels.store(*millibels, std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kEqualizerEnabled: {
            const auto enabled = audiofreedom::protocol::read_equalizer_enabled(*message);
            if (!enabled.has_value()) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_equalizer_enabled(*enabled);
            if (getSessionId() == 0) {
                gEqualizerEnabled.store(*enabled, std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kEqualizerBandMillibels: {
            const auto gain = audiofreedom::protocol::read_equalizer_band(*message);
            if (!gain.has_value() || gain->band >= audiofreedom::Engine::kEqBandCount ||
                gain->millibels < audiofreedom::Engine::kMinEqBandMillibels ||
                gain->millibels > audiofreedom::Engine::kMaxEqBandMillibels) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            (void)mEngine.set_eq_band_millibels(gain->band, gain->millibels);
            if (getSessionId() == 0) {
                gEqBandMillibels[gain->band].store(gain->millibels,
                                                   std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kEqualizerConfiguration: {
            const auto configuration =
                    audiofreedom::protocol::read_equalizer_configuration(*message);
            if (!configuration.has_value() ||
                configuration->preamp_millibels < audiofreedom::Engine::kMinPreampMillibels ||
                configuration->preamp_millibels > audiofreedom::Engine::kMaxPreampMillibels) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            for (const auto gain : configuration->band_millibels) {
                if (gain < audiofreedom::Engine::kMinEqBandMillibels ||
                    gain > audiofreedom::Engine::kMaxEqBandMillibels) {
                    return RetCode::ERROR_ILLEGAL_PARAMETER;
                }
            }

            mEngine.set_preamp_millibels(configuration->preamp_millibels);
            for (size_t band = 0; band < configuration->band_millibels.size(); ++band) {
                (void)mEngine.set_eq_band_millibels(band,
                                                    configuration->band_millibels[band]);
            }
            mEngine.set_equalizer_enabled(configuration->enabled);
            if (getSessionId() == 0) {
                gPreampMillibels.store(configuration->preamp_millibels,
                                       std::memory_order_release);
                for (size_t band = 0; band < configuration->band_millibels.size(); ++band) {
                    gEqBandMillibels[band].store(configuration->band_millibels[band],
                                                 std::memory_order_release);
                }
                gEqualizerEnabled.store(configuration->enabled, std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kLimiterConfiguration: {
            const auto configuration =
                    audiofreedom::protocol::read_limiter_configuration(*message);
            if (!configuration.has_value() ||
                configuration->threshold_millibels <
                        audiofreedom::Engine::kMinLimiterThresholdMillibels ||
                configuration->threshold_millibels >
                        audiofreedom::Engine::kMaxLimiterThresholdMillibels ||
                configuration->release_milliseconds <
                        audiofreedom::Engine::kMinLimiterReleaseMilliseconds ||
                configuration->release_milliseconds >
                        audiofreedom::Engine::kMaxLimiterReleaseMilliseconds) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_limiter_enabled(configuration->enabled);
            mEngine.set_limiter_threshold_millibels(configuration->threshold_millibels);
            mEngine.set_limiter_release_milliseconds(configuration->release_milliseconds);
            if (getSessionId() == 0) {
                gLimiterEnabled.store(configuration->enabled, std::memory_order_release);
                gLimiterThresholdMillibels.store(configuration->threshold_millibels,
                                                  std::memory_order_release);
                gLimiterReleaseMilliseconds.store(configuration->release_milliseconds,
                                                    std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kDynamicBassConfiguration: {
            const auto configuration =
                    audiofreedom::protocol::read_dynamic_bass_configuration(*message);
            if (!configuration.has_value() ||
                configuration->boost_millibels <
                        audiofreedom::Engine::kMinBassBoostMillibels ||
                configuration->boost_millibels >
                        audiofreedom::Engine::kMaxBassBoostMillibels ||
                configuration->cutoff_hz < audiofreedom::Engine::kMinBassCutoffHz ||
                configuration->cutoff_hz > audiofreedom::Engine::kMaxBassCutoffHz ||
                configuration->dynamics_percent <
                        audiofreedom::Engine::kMinBassDynamicsPercent ||
                configuration->dynamics_percent >
                        audiofreedom::Engine::kMaxBassDynamicsPercent) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_dynamic_bass_enabled(configuration->enabled);
            mEngine.set_bass_boost_millibels(configuration->boost_millibels);
            mEngine.set_bass_cutoff_hz(configuration->cutoff_hz);
            mEngine.set_bass_dynamics_percent(configuration->dynamics_percent);
            if (getSessionId() == 0) {
                gDynamicBassEnabled.store(configuration->enabled, std::memory_order_release);
                gBassBoostMillibels.store(configuration->boost_millibels,
                                           std::memory_order_release);
                gBassCutoffHz.store(configuration->cutoff_hz, std::memory_order_release);
                gBassDynamicsPercent.store(configuration->dynamics_percent,
                                            std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kDetailRecoveryConfiguration: {
            const auto configuration =
                    audiofreedom::protocol::read_detail_recovery_configuration(*message);
            if (!configuration.has_value() ||
                configuration->amount_percent <
                        audiofreedom::Engine::kMinDetailAmountPercent ||
                configuration->amount_percent >
                        audiofreedom::Engine::kMaxDetailAmountPercent ||
                configuration->focus_hz < audiofreedom::Engine::kMinDetailFocusHz ||
                configuration->focus_hz > audiofreedom::Engine::kMaxDetailFocusHz ||
                configuration->transients_percent <
                        audiofreedom::Engine::kMinDetailTransientsPercent ||
                configuration->transients_percent >
                        audiofreedom::Engine::kMaxDetailTransientsPercent) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_detail_recovery_enabled(configuration->enabled);
            mEngine.set_detail_amount_percent(configuration->amount_percent);
            mEngine.set_detail_focus_hz(configuration->focus_hz);
            mEngine.set_detail_transients_percent(configuration->transients_percent);
            if (getSessionId() == 0) {
                gDetailRecoveryEnabled.store(configuration->enabled,
                                             std::memory_order_release);
                gDetailAmountPercent.store(configuration->amount_percent,
                                           std::memory_order_release);
                gDetailFocusHz.store(configuration->focus_hz, std::memory_order_release);
                gDetailTransientsPercent.store(configuration->transients_percent,
                                               std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        case ParameterId::kImmersiveFieldConfiguration: {
            const auto configuration =
                    audiofreedom::protocol::read_immersive_field_configuration(*message);
            if (!configuration.has_value() ||
                configuration->amount_percent > audiofreedom::Engine::kMaxImmersivePercent ||
                configuration->width_percent > audiofreedom::Engine::kMaxImmersivePercent ||
                configuration->center_percent > audiofreedom::Engine::kMaxImmersivePercent ||
                configuration->room_percent > audiofreedom::Engine::kMaxImmersivePercent) {
                return RetCode::ERROR_ILLEGAL_PARAMETER;
            }
            mEngine.set_immersive_field_enabled(configuration->enabled);
            mEngine.set_immersive_amount_percent(configuration->amount_percent);
            mEngine.set_immersive_width_percent(configuration->width_percent);
            mEngine.set_immersive_center_percent(configuration->center_percent);
            mEngine.set_immersive_room_percent(configuration->room_percent);
            if (getSessionId() == 0) {
                gImmersiveFieldEnabled.store(configuration->enabled,
                                             std::memory_order_release);
                gImmersiveAmountPercent.store(configuration->amount_percent,
                                               std::memory_order_release);
                gImmersiveWidthPercent.store(configuration->width_percent,
                                              std::memory_order_release);
                gImmersiveCenterPercent.store(configuration->center_percent,
                                               std::memory_order_release);
                gImmersiveRoomPercent.store(configuration->room_percent,
                                             std::memory_order_release);
                gSettingsRevision.fetch_add(1, std::memory_order_acq_rel);
            }
            return RetCode::SUCCESS;
        }
        default:
            return RetCode::ERROR_ILLEGAL_PARAMETER;
    }
}

std::optional<std::vector<uint8_t>> AudioFreedomEffectContext::getParams(
        const std::vector<uint8_t>& id) const {
    const auto query = decodeQueryWire(id);
    if (!query.has_value() || query->payload_size != 0) {
        return std::nullopt;
    }

    using audiofreedom::protocol::DriverStatus;
    using audiofreedom::protocol::ParameterId;
    using audiofreedom::protocol::ProcessingState;
    switch (query->parameter_id) {
        case ParameterId::kProtocolVersion:
            return encodeWireResponse(id, audiofreedom::protocol::make_protocol_version());
        case ParameterId::kEnabled:
            return encodeWireResponse(id,
                                      audiofreedom::protocol::make_enabled(mEngine.enabled()));
        case ParameterId::kPreampMillibels:
            return encodeWireResponse(
                    id, audiofreedom::protocol::make_preamp(mEngine.preamp_millibels()));
        case ParameterId::kEqualizerEnabled:
            return encodeWireResponse(
                    id, audiofreedom::protocol::make_equalizer_enabled(
                                mEngine.equalizer_enabled()));
        case ParameterId::kEqualizerBandMillibels:
        case ParameterId::kEqualizerConfiguration:
        case ParameterId::kLimiterConfiguration:
        case ParameterId::kDynamicBassConfiguration:
        case ParameterId::kDetailRecoveryConfiguration:
        case ParameterId::kImmersiveFieldConfiguration:
            return std::nullopt;
        case ParameterId::kDriverStatus: {
            const auto config = mEngine.stream_config();
            DriverStatus status;
            status.state = !mPrepared
                                   ? ProcessingState::kUnavailable
                                   : (mRunning && mEngine.enabled() ? ProcessingState::kProcessing
                                                                   : ProcessingState::kIdle);
            status.sample_rate_hz = config.sample_rate_hz;
            status.channel_count = config.channel_count;
            status.processed_frames = mEngine.processed_frames();
            return encodeWireResponse(id, audiofreedom::protocol::make_driver_status(status));
        }
        case ParameterId::kOutputMetrics: {
            audiofreedom::protocol::OutputMetrics metrics{
                    .input_peak_millibels =
                            gInputPeakMillibels.load(std::memory_order_acquire),
                    .output_peak_millibels =
                            gOutputPeakMillibels.load(std::memory_order_acquire),
                    .gain_reduction_millibels =
                            gGainReductionMillibels.load(std::memory_order_acquire),
            };
            return encodeWireResponse(id, audiofreedom::protocol::make_output_metrics(metrics));
        }
        default:
            return std::nullopt;
    }
}

IEffect::Status AudioFreedomEffectContext::process(float* in, float* out, const int samples) {
    const auto channelCount = mEngine.stream_config().channel_count;
    if (!mPrepared || in == nullptr || out == nullptr || samples <= 0 || channelCount == 0 ||
        samples % channelCount != 0) {
        return {EX_ILLEGAL_ARGUMENT, 0, 0};
    }
    if (in != out) {
        std::copy_n(in, samples, out);
    }
    syncGlobalSettings();
    mEngine.set_enabled(gProcessingEnabled.load(std::memory_order_acquire));
    if (!mEngine.process(out, static_cast<std::size_t>(samples) / channelCount)) {
        return {EX_ILLEGAL_STATE, 0, 0};
    }
    const auto metrics = mEngine.output_metrics();
    gInputPeakMillibels.store(metrics.input_peak_millibels, std::memory_order_release);
    gOutputPeakMillibels.store(metrics.output_peak_millibels, std::memory_order_release);
    gGainReductionMillibels.store(metrics.gain_reduction_millibels,
                                   std::memory_order_release);
    if (!mLoggedFirstProcess.exchange(true, std::memory_order_relaxed)) {
        LOG(ERROR) << "first PCM callback session=" << getSessionId() << " samples=" << samples
                   << " channels=" << channelCount << " preampMb="
                   << mEngine.preamp_millibels() << " eqEnabled="
                   << mEngine.equalizer_enabled() << " bassEnabled="
                   << mEngine.dynamic_bass_enabled() << " detailEnabled="
                   << mEngine.detail_recovery_enabled() << " immersiveEnabled="
                   << mEngine.immersive_field_enabled() << " globalEnabled="
                   << gProcessingEnabled.load(std::memory_order_relaxed);
    }
    return {STATUS_OK, samples, samples};
}

const std::string AudioFreedomEffect::kEffectName = "AudioFreedom";
const Descriptor AudioFreedomEffect::kDescriptor = {
        .common = {.id = {.type = getAudioFreedomTypeUuid(),
                          .uuid = getAudioFreedomImplUuid(),
                          .proxy = std::nullopt},
                   .flags = {.type = Flags::Type::INSERT, .insert = Flags::Insert::FIRST},
                   .name = AudioFreedomEffect::kEffectName,
                   .implementor = "AudioFreedom"},
        .capability = {}};

ndk::ScopedAStatus AudioFreedomEffect::getDescriptor(Descriptor* aidlReturn) {
    RETURN_IF(aidlReturn == nullptr, EX_NULL_POINTER, "nullDescriptor");
    *aidlReturn = kDescriptor;
    LOG(INFO) << "getDescriptor returned AudioFreedom descriptor";
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AudioFreedomEffect::setParameterSpecific(
        const Parameter::Specific& specific) {
    RETURN_IF(specific.getTag() != Parameter::Specific::vendorEffect, EX_ILLEGAL_ARGUMENT,
              "effectNotSupported");
    RETURN_IF(!mContext, EX_NULL_POINTER, "nullContext");

    const auto& vendorEffect = specific.get<Parameter::Specific::vendorEffect>();
    std::optional<DefaultExtension> extension;
    RETURN_IF(STATUS_OK != vendorEffect.extension.getParcelable(&extension), EX_ILLEGAL_ARGUMENT,
              "getParcelableFailed");
    RETURN_IF(!extension.has_value(), EX_ILLEGAL_ARGUMENT, "parcelableNull");
    RETURN_IF(mContext->setParams(extension->bytes) != RetCode::SUCCESS, EX_ILLEGAL_ARGUMENT,
              "parameterNotSupported");
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AudioFreedomEffect::getParameterSpecific(const Parameter::Id& id,
                                                            Parameter::Specific* specific) {
    RETURN_IF(id.getTag() != Parameter::Id::vendorEffectTag, EX_ILLEGAL_ARGUMENT, "wrongIdTag");
    RETURN_IF(!mContext, EX_NULL_POINTER, "nullContext");
    RETURN_IF(specific == nullptr, EX_NULL_POINTER, "nullSpecific");

    const auto& vendorId = id.get<Parameter::Id::vendorEffectTag>();
    std::optional<DefaultExtension> idExtension;
    RETURN_IF(STATUS_OK != vendorId.extension.getParcelable(&idExtension), EX_ILLEGAL_ARGUMENT,
              "getIdParcelableFailed");
    RETURN_IF(!idExtension.has_value(), EX_ILLEGAL_ARGUMENT, "parcelableIdNull");

    const auto response = mContext->getParams(idExtension->bytes);
    RETURN_IF(!response.has_value(), EX_ILLEGAL_ARGUMENT, "parameterNotSupported");
    VendorExtension vendorResponse;
    DefaultExtension responseExtension;
    responseExtension.bytes = *response;
    RETURN_IF(STATUS_OK != vendorResponse.extension.setParcelable(responseExtension),
              EX_ILLEGAL_ARGUMENT, "setParcelableFailed");
    specific->set<Parameter::Specific::vendorEffect>(vendorResponse);
    return ndk::ScopedAStatus::ok();
}

std::shared_ptr<EffectContext> AudioFreedomEffect::createContext(
        const Parameter::Common& common) {
    if (!mContext) {
        mContext = std::make_shared<AudioFreedomEffectContext>(1, common);
        if (!mContext->prepared()) {
            mContext.reset();
        }
    }
    return mContext;
}

RetCode AudioFreedomEffect::releaseContext() {
    mContext.reset();
    return RetCode::SUCCESS;
}

ndk::ScopedAStatus AudioFreedomEffect::commandImpl(const CommandId id) {
    RETURN_IF(!mContext, EX_NULL_POINTER, "nullContext");
    switch (id) {
        case CommandId::START:
            RETURN_IF(mContext->enable() != RetCode::SUCCESS, EX_ILLEGAL_STATE, "enableFailed");
            break;
        case CommandId::STOP:
            RETURN_IF(mContext->disable() != RetCode::SUCCESS, EX_ILLEGAL_STATE, "disableFailed");
            break;
        case CommandId::RESET:
            RETURN_IF(mContext->disable() != RetCode::SUCCESS, EX_ILLEGAL_STATE, "disableFailed");
            RETURN_IF(mContext->reset() != RetCode::SUCCESS, EX_ILLEGAL_STATE, "resetFailed");
            break;
        default:
            return ndk::ScopedAStatus::fromExceptionCodeWithMessage(
                    EX_ILLEGAL_ARGUMENT, "commandNotSupported");
    }
    return ndk::ScopedAStatus::ok();
}

IEffect::Status AudioFreedomEffect::effectProcessImpl(float* in, float* out, int samples) {
    RETURN_VALUE_IF(!mContext, (IEffect::Status{EX_NULL_POINTER, 0, 0}), "nullContext");
    return mContext->process(in, out, samples);
}

}  // namespace aidl::android::hardware::audio::effect
