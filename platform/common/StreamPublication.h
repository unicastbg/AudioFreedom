#pragma once

#include "audiofreedom/protocol.h"
#include <atomic>

namespace audiofreedom::android {

// No allocation or waiting on the audio thread. Concurrent writers may skip a
// telemetry update; readers only accept a complete, consistent snapshot.
class StreamPublication final {
public:
    void publish(std::uint32_t rate, std::uint32_t channels, std::uint32_t io_format) noexcept {
        if (writer_.test_and_set()) return;
        sequence_.fetch_add(1);
        rate_.store(rate);
        channels_.store(channels);
        io_format_.store(io_format);
        sequence_.fetch_add(1);
        writer_.clear();
    }

    protocol::ActiveStream read() const noexcept {
        for (unsigned attempt = 0; attempt < 3; ++attempt) {
            const auto sequence = sequence_.load();
            if (sequence == 0 || (sequence & 1)) continue;
            const protocol::ActiveStream value{sequence, rate_.load(), channels_.load(), 1,
                                                io_format_.load()};
            if (sequence == sequence_.load()) return value;
        }
        return {};
    }

private:
    static_assert(std::atomic<std::uint32_t>::is_always_lock_free);
    std::atomic_flag writer_ = ATOMIC_FLAG_INIT;
    std::atomic<std::uint32_t> sequence_{0}, rate_{0}, channels_{0}, io_format_{1};
};

}  // namespace audiofreedom::android
