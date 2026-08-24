#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <signal.h>
#include <unistd.h>

namespace {

constexpr char kControllerVersion[] = "0.2.0";
constexpr char kEffectTypeUuid[] = "a7e03c90-7c3d-4f48-9c8d-497c8f1b1201";
constexpr char kEffectImplementationUuid[] =
        "2f6e8c10-8d44-4b42-b110-16f3a729ef01";
constexpr char kPackageName[] = "com.svetlio.audiofreedom";
constexpr std::int32_t kControlPriority = 1000;
constexpr std::int32_t kAudioUsageMedia = 1;
constexpr std::int32_t kAllocateUniqueId = 0;

// These symbols are verified against the target's copied Android 16 libraries at build time.
// String16 is opaque here so the controller does not need Android's generated platform headers.
struct alignas(std::max_align_t) String16Storage {
    unsigned char bytes[64];
};

extern "C" void string16Construct(String16Storage*, const char*)
        asm("_ZN7android8String16C1EPKc");
extern "C" void string16Destroy(String16Storage*)
        asm("_ZN7android8String16D1Ev");
extern "C" std::int32_t addStreamDefaultEffect(
        const char*, const String16Storage*, const char*, std::int32_t, std::int32_t,
        std::int32_t*)
        asm("_ZN7android11AudioEffect22addStreamDefaultEffectEPKcRKNS_8String16ES2_i13audio_usage_tPi");
extern "C" std::int32_t removeStreamDefaultEffect(std::int32_t)
        asm("_ZN7android11AudioEffect25removeStreamDefaultEffectEi");

volatile sig_atomic_t gStopRequested = 0;

void handleSignal(int) { gStopRequested = 1; }

int runStreamDefaultMedia(bool probeOnly) {
    String16Storage packageName{};
    string16Construct(&packageName, kPackageName);

    std::int32_t registrationId = kAllocateUniqueId;
    const auto addStatus = addStreamDefaultEffect(
            kEffectTypeUuid, &packageName, kEffectImplementationUuid, kControlPriority,
            kAudioUsageMedia, &registrationId);
    string16Destroy(&packageName);

    if (addStatus != 0) {
        std::fprintf(stderr, "AudioFreedom media-default registration failed: %d\n", addStatus);
        return 5;
    }

    std::printf("AudioFreedom media-default registration succeeded: id=%d\n", registrationId);
    if (!probeOnly) {
        while (!gStopRequested) {
            pause();
        }
    }

    const auto removeStatus = removeStreamDefaultEffect(registrationId);
    if (removeStatus != 0) {
        std::fprintf(stderr, "AudioFreedom media-default cleanup failed: %d\n", removeStatus);
        return 6;
    }
    return 0;
}

void printUsage(const char* executable) {
    std::fprintf(stderr,
                 "Usage: %s [--probe] stream-default-media\n"
                 "       %s --version\n",
                 executable, executable);
}

}  // namespace

int main(int argc, char** argv) {
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    std::setvbuf(stderr, nullptr, _IONBF, 0);

    if (argc == 2 && std::strcmp(argv[1], "--version") == 0) {
        std::printf("audiofreedom-controller=%s android_api=36 attachment=stream-default-media\n",
                    kControllerVersion);
        return 0;
    }

    bool probeOnly = false;
    const char* mode = nullptr;
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

    if (mode == nullptr || std::strcmp(mode, "stream-default-media") != 0) {
        printUsage(argv[0]);
        return 1;
    }

    signal(SIGINT, handleSignal);
    signal(SIGTERM, handleSignal);
    return runStreamDefaultMedia(probeOnly);
}
