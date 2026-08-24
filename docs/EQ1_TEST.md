# EQ1 device test

`0.2.0-eq1` is the first clean-room equalizer milestone. It retains the confirmed
Meta-OverlayFS-free direct-bind installation and replaces the fixed-gain-only driver with:

- a global preamp from -24 dB to 0 dB;
- ten constant-Q peaking bands at 31.25, 62.5, 125, 250, 500, 1000, 2000, 4000,
  8000, and 16000 Hz;
- per-band gain from -12 dB to +12 dB;
- persistent Flat, Balanced, Deep bass, and Headphone energy presets;
- settings restoration whenever the foreground service reattaches after a call or restart.

The filters use RBJ peaking-biquad equations with independent state for every channel.
Bands at or above 49% of the current sample rate are bypassed. Flat settings are
bit-exact, and the preamp provides explicit headroom for boosted presets. A limiter is
not part of EQ1; it belongs to the following dynamics/output-protection milestone.

## Installation

1. Leave Meta-OverlayFS uninstalled and keep the confirmed direct-bind module enabled.
2. Install `AudioFreedom-xuanyuan-eea-0.2.0-eq1-directbind.zip` over the existing
   AudioFreedom module, then reboot.
3. Install `AudioFreedom-0.2.0-eq1-debug.apk`.
4. Confirm module status reports `direct_bind_mounts=3/3`, `direct_bind=active`,
   `effect=available`, and `dsp=preamp,10-band-eq`.
5. Enable Processing, choose Flat, and verify output is unchanged at 0 dB preamp.
6. Choose Headphone energy and toggle Equalizer while music is playing. The tonal
   difference should be immediate while overall level remains controlled by its -6 dB
   preamp.
7. Move the 1 kHz slider between -12 dB and +12 dB, releasing it at each end. Midrange
   content must change clearly without changing the Processing switch.
8. Reboot once more and verify the selected settings return when Processing is enabled.

If the module prevents normal audio, disable AudioFreedom in KernelSU and reboot. The
direct binds disappear with that reboot. Keep the confirmed `directbind1` package as the
known-good rollback driver.
