#include <android/content/AttributionSourceState.h>
#include <binder/Binder.h>
#include <binder/ProcessState.h>
#include <media/AudioEffect.h>
#include <system/audio.h>
#include <utils/Errors.h>
#include <utils/String16.h>

#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <unistd.h>

#ifndef AUDIOFREEDOM_CONTROLLER_ANDROID_API
#define AUDIOFREEDOM_CONTROLLER_ANDROID_API 0
#endif

namespace {

constexpr char kControllerVersion[] = "0.1.0";
constexpr char kEffectTypeUuid[] = "a7e03c90-7c3d-4f48-9c8d-497c8f1b1201";
constexpr char kEffectImplementationUuid[] =
    "2f6e8c10-8d44-4b42-b110-16f3a729ef01";
constexpr char kPackageName[] = "com.svetlio.audiofreedom";
constexpr std::int32_t kControlPriority = 1000;

volatile std::sig_atomic_t gStopRequested = 0;

void handleSignal(int) { gStopRequested = 1; }

android::content::AttributionSourceState makeAttributionSource() {
  android::content::AttributionSourceState source;
  source.uid = getuid();
  source.pid = getpid();
  source.packageName = std::string(kPackageName);
  source.token = android::sp<android::BBinder>::make();
  return source;
}

void waitForStop() {
  while (!gStopRequested) {
    pause();
  }
}

int runOutputMix(const bool probeOnly) {
  const auto source = makeAttributionSource();
  const auto effect = android::sp<android::AudioEffect>::make(source);
  const auto setStatus = effect->set(
      kEffectTypeUuid, kEffectImplementationUuid, kControlPriority,
      static_cast<android::AudioEffect::legacy_callback_t>(nullptr), nullptr,
      AUDIO_SESSION_OUTPUT_MIX, AUDIO_IO_HANDLE_NONE, {}, probeOnly, false);
  if (setStatus != android::NO_ERROR && setStatus != android::ALREADY_EXISTS) {
    std::fprintf(stderr, "AudioFreedom output-mix creation failed: %d\n",
                 setStatus);
    return 2;
  }

  const auto initStatus = effect->initCheck();
  if (initStatus != android::NO_ERROR &&
      initStatus != android::ALREADY_EXISTS) {
    std::fprintf(stderr, "AudioFreedom output-mix initialization failed: %d\n",
                 initStatus);
    return 3;
  }

  if (probeOnly) {
    std::printf("AudioFreedom output-mix probe succeeded.\n");
    return 0;
  }

  const auto enableStatus = effect->setEnabled(true);
  if (enableStatus != android::NO_ERROR) {
    std::fprintf(stderr, "AudioFreedom output-mix enable failed: %d\n",
                 enableStatus);
    return 4;
  }

  std::printf("AudioFreedom is enabled on the global output mix.\n");
  std::fflush(stdout);
  waitForStop();
  effect->setEnabled(false);
  return 0;
}

int runStreamDefaultMedia(const bool probeOnly) {
  audio_unique_id_t registrationId = AUDIO_UNIQUE_ID_ALLOCATE;
  const auto status = android::AudioEffect::addStreamDefaultEffect(
      kEffectTypeUuid, android::String16(kPackageName),
      kEffectImplementationUuid, kControlPriority, AUDIO_USAGE_MEDIA,
      &registrationId);
  if (status != android::NO_ERROR) {
    std::fprintf(stderr, "AudioFreedom media-default registration failed: %d\n",
                 status);
    return 5;
  }

  std::printf(
      "AudioFreedom is registered as the default media effect (id %d).\n",
      registrationId);
  std::fflush(stdout);
  if (!probeOnly) {
    waitForStop();
  }

  const auto removeStatus =
      android::AudioEffect::removeStreamDefaultEffect(registrationId);
  if (removeStatus != android::NO_ERROR) {
    std::fprintf(stderr, "AudioFreedom media-default cleanup failed: %d\n",
                 removeStatus);
    return 6;
  }
  return 0;
}

void printUsage(const char *executable) {
  std::fprintf(stderr,
               "Usage: %s [--probe] output-mix|stream-default-media\n"
               "       %s --version\n",
               executable, executable);
}

} // namespace

int main(int argc, char **argv) {
  std::setvbuf(stdout, nullptr, _IONBF, 0);
  std::setvbuf(stderr, nullptr, _IONBF, 0);

  if (argc == 2 && std::strcmp(argv[1], "--version") == 0) {
    std::printf("audiofreedom-controller=%s android_api=%d\n",
                kControllerVersion, AUDIOFREEDOM_CONTROLLER_ANDROID_API);
    return 0;
  }

  bool probeOnly = false;
  const char *mode = nullptr;
  for (int index = 1; index < argc; ++index) {
    if (std::strcmp(argv[index], "--probe") == 0) {
      probeOnly = true;
    } else if (mode == nullptr) {
      mode = argv[index];
    } else {
      printUsage(argv[0]);
      return 1;
    }
  }
  if (mode == nullptr) {
    printUsage(argv[0]);
    return 1;
  }

  std::signal(SIGINT, handleSignal);
  std::signal(SIGTERM, handleSignal);
  android::ProcessState::self()->startThreadPool();

  if (std::strcmp(mode, "output-mix") == 0) {
    return runOutputMix(probeOnly);
  }
  if (std::strcmp(mode, "stream-default-media") == 0) {
    return runStreamDefaultMedia(probeOnly);
  }

  printUsage(argv[0]);
  return 1;
}
