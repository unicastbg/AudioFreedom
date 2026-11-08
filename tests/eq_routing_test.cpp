#include "audiofreedom/engine.h"
#include "audiofreedom/wire.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <vector>

static void check(bool ok, const char* message) {
    if (!ok) { std::cerr << message << '\n'; std::exit(1); }
}

static void ready(audiofreedom::Engine& e, unsigned channels = 2, bool verified = true,
                  unsigned rate = 48000) {
    check(e.prepare({rate, channels, verified}), "prepare");
    e.set_enabled(true);
    e.set_preamp_millibels(0);
    e.set_limiter_enabled(false);
}

static float tone(unsigned q, float frequency, unsigned rate = 48000, int gain = 600) {
    audiofreedom::Engine e;
    ready(e, 1, false, rate);
    e.set_equalizer_enabled(true);
    check(e.set_eq_band_millibels(5, gain), "gain");
    check(e.set_eq_band_q(5, q), "q");
    std::vector<float> pcm(rate);
    double input = 0, output = 0;
    for (unsigned n = 0; n < rate; ++n) pcm[n] = 0.01F * std::sin(6.283185307 * frequency * n / rate);
    for (unsigned n = rate / 2; n < rate; ++n) input += pcm[n] * pcm[n];
    check(e.process(pcm.data(), pcm.size()), "tone process");
    for (unsigned n = rate / 2; n < rate; ++n) {
        check(std::isfinite(pcm[n]), "nonfinite Q response");
        output += pcm[n] * pcm[n];
    }
    return 10 * std::log10(output / input);
}

int main() {
    using namespace audiofreedom::protocol;
    std::array<std::uint32_t, 10> qs{30, 50, 75, 100, 140, 200, 300, 400, 450, 500};
    check(read_equalizer_q(make_equalizer_q(qs)) == qs, "Q wire round trip");
    auto bad = make_equalizer_q(qs); bad.payload_size = 19;
    check(!read_equalizer_q(bad), "Q payload length");
    qs[9] = 501;
    check(!read_equalizer_q(make_equalizer_q(qs)), "Q validation");
    check(!read_stereo_routing(make_stereo_routing(5, 0)), "mode validation");
    check(!read_stereo_routing(make_stereo_routing(0, 101)), "balance validation");
    for (unsigned mode = 0; mode <= 4; ++mode) {
        const auto wire = read_stereo_routing(make_stereo_routing(mode, -50));
        check(wire && wire->first == mode && wire->second == -50, "routing wire");
        audiofreedom::Engine e; ready(e); e.set_stereo_routing(mode, 0);
        float pair[]{0.2F, 0.6F};
        check(e.process(pair, 1), "routing process");
        const float expected[][2]{{0.2F,0.6F},{0.6F,0.2F},{0.4F,0.4F},{0.4F,0},{0,0.4F}};
        check(std::abs(pair[0]-expected[mode][0]) < 1e-6F &&
              std::abs(pair[1]-expected[mode][1]) < 1e-6F, "routing mapping");
    }
    for (unsigned channels : {1U, 2U, 6U}) {
        audiofreedom::Engine e; ready(e, channels, false); e.set_stereo_routing(4, 100);
        std::vector<float> pcm(channels, 0.2F);
        check(e.process(pcm.data(), 1), "unverified process");
        check(std::all_of(pcm.begin(), pcm.end(), [](float f){return f == 0.2F;}), "unverified layout changed");
    }
    for (int balance : {-100, -50, 0, 50, 100}) {
        audiofreedom::Engine e; ready(e); e.set_stereo_routing(0, balance);
        float pcm[]{0.4F, 0.4F}; check(e.process(pcm, 1), "balance process");
        check(std::abs(pcm[0] - 0.4F*(1-std::max(balance,0)/100.F)) < 1e-6F &&
              std::abs(pcm[1] - 0.4F*(1+std::min(balance,0)/100.F)) < 1e-6F, "balance mapping");
    }
    {
        audiofreedom::Engine a, b; ready(a); ready(b);
        a.set_equalizer_enabled(true); b.set_equalizer_enabled(true);
        check(a.eq_band_q(5) == 140, "default Q");
        check(!a.set_eq_band_q(10, 100) && !a.set_eq_band_q(0, 29) &&
              !a.set_eq_band_q(0, 501), "invalid Q setter");
        check(a.set_eq_band_millibels(5, 600) && b.set_eq_band_millibels(5, 600), "default gain");
        check(b.set_eq_band_q(5, 140), "explicit default");
        std::vector<float> x(2000, .001F), y = x;
        check(a.process(x.data(), 1000) && b.process(y.data(), 1000), "default process");
        check(x == y, "default Q changes original sound");
        a.set_enabled(false); a.set_stereo_routing(4, 0);
        float pair[]{.2F, .3F}; check(a.process(pair, 1), "bypass");
        check(pair[0] == .2F && pair[1] == .3F, "routing active in bypass");
    }
    check(tone(30, 1800) > tone(500, 1800) + 2, "broad Q must affect nearby frequencies more");
    for (unsigned rate : {44100U,48000U,96000U,192000U}) {
        for (unsigned q : {30U,140U,500U}) {
            check(std::abs(tone(q, 1000, rate) - 6) < .05F, "Q changed center gain");
            (void)tone(q, 1800, rate, 2000);
            (void)tone(q, 1800, rate, -2000);
        }
    }
    {
        audiofreedom::Engine e; ready(e); e.set_reverb_enabled(true); e.set_stereo_routing(3, 0);
        std::vector<float> pcm(48000 * 2); pcm[0] = .5F;
        check(e.process(pcm.data(), 48000), "reverb routing");
        for (unsigned i=1;i<pcm.size();i+=2) check(pcm[i] == 0, "reverb leaked into muted output");
    }
    std::cout << "EQ bandwidth and stereo routing passed\n";
}
