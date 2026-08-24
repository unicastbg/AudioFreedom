#include <aidl/android/hardware/audio/effect/IFactory.h>
#include <aidl/android/media/audio/common/AudioChannelLayout.h>
#include <aidl/android/media/audio/common/AudioFormatDescription.h>

#include <cstdio>
#include <memory>
#include <optional>
#include <vector>

extern "C" AIBinder* AServiceManager_waitForService(const char* instance);

using aidl::android::hardware::audio::effect::Descriptor;
using aidl::android::hardware::audio::effect::IEffect;
using aidl::android::hardware::audio::effect::IFactory;
using aidl::android::hardware::audio::effect::Parameter;
using aidl::android::hardware::audio::effect::Processing;
using aidl::android::media::audio::common::AudioChannelLayout;
using aidl::android::media::audio::common::AudioFormatDescription;
using aidl::android::media::audio::common::AudioFormatType;
using aidl::android::media::audio::common::AudioStreamType;
using aidl::android::media::audio::common::PcmType;

namespace {

void printProcessing(const char* label, const std::vector<Processing>& processing) {
    std::printf("%s=%zu\n", label, processing.size());
    for (const auto& chain : processing) {
        std::printf("chain %s effects=%zu\n", chain.type.toString().c_str(), chain.ids.size());
        for (const auto& effect : chain.ids) {
            std::printf("  name=%s uuid=%s type=%s\n", effect.common.name.c_str(),
                        effect.common.id.uuid.toString().c_str(),
                        effect.common.id.type.toString().c_str());
        }
    }
}

void printFailure(const char* operation, const ndk::ScopedAStatus& status) {
    std::fprintf(stderr, "%s failed: status=%d exception=%d service=%d\n", operation,
                 status.getStatus(), status.getExceptionCode(),
                 status.getServiceSpecificError());
}

bool runCreateOpenProbe(const std::shared_ptr<IFactory>& factory,
                        const Descriptor& descriptor) {
    std::shared_ptr<IEffect> effect;
    auto status = factory->createEffect(descriptor.common.id.uuid, &effect);
    if (!status.isOk() || !effect) {
        printFailure("createEffect", status);
        return false;
    }
    std::printf("createEffect=ok\n");

    const AudioFormatDescription format = {
            .type = AudioFormatType::PCM,
            .pcm = PcmType::FLOAT_32_BIT,
            .encoding = "",
    };
    const auto stereo = AudioChannelLayout::make<AudioChannelLayout::layoutMask>(
            AudioChannelLayout::LAYOUT_STEREO);
    Parameter::Common common;
    common.session = 0;
    common.ioHandle = 1;
    common.input.base.sampleRate = 48000;
    common.input.base.channelMask = stereo;
    common.input.base.format = format;
    common.input.frameCount = 256;
    common.output.base.sampleRate = 48000;
    common.output.base.channelMask = stereo;
    common.output.base.format = format;
    common.output.frameCount = 256;

    IEffect::OpenEffectReturn openReturn;
    status = effect->open(common, std::nullopt, &openReturn);
    const bool opened = status.isOk();
    if (opened) {
        std::printf("openStereoFloat48k=ok\n");
        const auto closeStatus = effect->close();
        if (!closeStatus.isOk()) {
            printFailure("closeEffect", closeStatus);
        } else {
            std::printf("closeEffect=ok\n");
        }
    } else {
        printFailure("openStereoFloat48k", status);
    }

    const auto destroyStatus = factory->destroyEffect(effect);
    if (!destroyStatus.isOk()) {
        printFailure("destroyEffect", destroyStatus);
        return false;
    }
    std::printf("destroyEffect=ok\n");
    return opened;
}

}  // namespace

int main() {
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    std::setvbuf(stderr, nullptr, _IONBF, 0);

    constexpr const char* kService = "android.hardware.audio.effect.IFactory/default";
    ndk::SpAIBinder binder(AServiceManager_waitForService(kService));
    if (!binder.get()) {
        std::fprintf(stderr, "factory service not found\n");
        return 2;
    }

    const auto factory = IFactory::fromBinder(binder);
    if (!factory) {
        std::fprintf(stderr, "factory binder conversion failed\n");
        return 3;
    }
    std::printf("factory=connected\n");

    std::vector<Descriptor> effects;
    std::printf("queryEffects=begin\n");
    auto status = factory->queryEffects(std::nullopt, std::nullopt, std::nullopt, &effects);
    if (!status.isOk()) {
        printFailure("queryEffects", status);
        return 4;
    }
    std::printf("effects=%zu\n", effects.size());
    const Descriptor* audioFreedom = nullptr;
    for (const auto& effect : effects) {
        std::printf("effect name=%s uuid=%s type=%s\n", effect.common.name.c_str(),
                    effect.common.id.uuid.toString().c_str(),
                    effect.common.id.type.toString().c_str());
        if (effect.common.name == "AudioFreedom") {
            audioFreedom = &effect;
        }
    }

    std::vector<Processing> processing;
    std::printf("queryProcessingAll=begin\n");
    status = factory->queryProcessing(std::nullopt, &processing);
    if (!status.isOk()) {
        printFailure("queryProcessingAll", status);
        return 5;
    }
    printProcessing("processingAll", processing);

    const auto musicType = Processing::Type::make<Processing::Type::streamType>(
            AudioStreamType::MUSIC);
    processing.clear();
    std::printf("queryProcessingMusic=begin\n");
    status = factory->queryProcessing(musicType, &processing);
    if (!status.isOk()) {
        printFailure("queryProcessingMusic", status);
        return 6;
    }
    printProcessing("processingMusic", processing);

    if (!audioFreedom) {
        std::fprintf(stderr, "AudioFreedom descriptor not found\n");
        return 7;
    }
    return runCreateOpenProbe(factory, *audioFreedom) ? 0 : 8;
}
