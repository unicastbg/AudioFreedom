#pragma once

#include "audiofreedom/engine.h"
#include "audiofreedom/wire.h"
#include "effect-impl/EffectImpl.h"

#include <aidl/android/hardware/audio/effect/BnEffect.h>
#include <fmq/AidlMessageQueue.h>

#include <memory>
#include <atomic>
#include <optional>
#include <vector>

namespace aidl::android::hardware::audio::effect {

class AudioFreedomEffectContext final : public EffectContext {
  public:
    AudioFreedomEffectContext(int statusDepth, const Parameter::Common& common);

    RetCode setCommon(const Parameter::Common& common) override;
    RetCode enable();
    RetCode disable();
    RetCode reset();

    RetCode setParams(const std::vector<uint8_t>& params);
    std::optional<std::vector<uint8_t>> getParams(const std::vector<uint8_t>& id) const;
    IEffect::Status process(float* in, float* out, int samples);
    bool prepared() const { return mPrepared; }

  private:
    RetCode prepareEngine(const Parameter::Common& common);
    void syncGlobalSettings();

    audiofreedom::Engine mEngine;
    std::atomic<bool> mLoggedFirstProcess{false};
    std::uint64_t mAppliedSettingsRevision = 0;
    bool mPrepared = false;
    bool mRunning = false;
};

class AudioFreedomEffect final : public EffectImpl {
  public:
    static const std::string kEffectName;
    static const Descriptor kDescriptor;

    AudioFreedomEffect() = default;
    ~AudioFreedomEffect() override { cleanUp(); }

    ndk::ScopedAStatus getDescriptor(Descriptor* aidlReturn) override;
    ndk::ScopedAStatus setParameterSpecific(const Parameter::Specific& specific)
            REQUIRES(mImplMutex) override;
    ndk::ScopedAStatus getParameterSpecific(const Parameter::Id& id,
                                            Parameter::Specific* specific)
            REQUIRES(mImplMutex) override;

    std::shared_ptr<EffectContext> createContext(const Parameter::Common& common)
            REQUIRES(mImplMutex) override;
    RetCode releaseContext() REQUIRES(mImplMutex) override;
    IEffect::Status effectProcessImpl(float* in, float* out, int samples)
            REQUIRES(mImplMutex) override;
    std::string getEffectName() override { return kEffectName; }

  protected:
    ndk::ScopedAStatus commandImpl(CommandId id) REQUIRES(mImplMutex) override;

  private:
    std::shared_ptr<AudioFreedomEffectContext> mContext GUARDED_BY(mImplMutex);
};

}  // namespace aidl::android::hardware::audio::effect
