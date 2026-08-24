#pragma once

#include <aidl/android/media/audio/common/AudioUuid.h>

#include <cstdio>

namespace aidl::android::hardware::audio::effect {

inline ::aidl::android::media::audio::common::AudioUuid stringToUuid(const char* value) {
    ::aidl::android::media::audio::common::AudioUuid uuid{};
    unsigned int parts[10]{};
    if (value == nullptr ||
        std::sscanf(value, "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x", parts,
                    parts + 1, parts + 2, parts + 3, parts + 4, parts + 5, parts + 6,
                    parts + 7, parts + 8, parts + 9) != 10) {
        return uuid;
    }

    uuid.timeLow = parts[0];
    uuid.timeMid = static_cast<uint16_t>(parts[1]);
    uuid.timeHiAndVersion = static_cast<uint16_t>(parts[2]);
    uuid.clockSeq = static_cast<uint16_t>(parts[3]);
    for (int index = 4; index < 10; ++index) {
        uuid.node.push_back(static_cast<uint8_t>(parts[index]));
    }
    return uuid;
}

}  // namespace aidl::android::hardware::audio::effect
