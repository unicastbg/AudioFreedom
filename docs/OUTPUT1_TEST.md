# Output1 device test

`0.3.0-output1` adds a clean-room, zero-latency output limiter after the preamp and
ten-band equalizer. It uses one gain value for every channel in a frame so stereo
balance is preserved. Attack is immediate; release is adjustable from 20 to 1000 ms.

The app keeps Processing visible and places Equalizer and Output protection in
independent sections that start collapsed. Their switches remain available in each
section header. Equalizer owns its preset headroom preamp. Output protection exposes
only limiter ceiling, release, input peak, output peak, and gain reduction.

## Installation

1. Leave Meta-OverlayFS uninstalled.
2. Install `AudioFreedom-xuanyuan-eea-0.3.0-output1-directbind.zip` over the current
   AudioFreedom module and reboot.
3. Install `AudioFreedom-0.3.0-output1-debug.apk`.
4. Confirm module status reports `direct_bind_mounts=3/3`, `direct_bind=active`,
   `effect=available`, and `dsp=preamp,10-band-eq,linked-limiter`.
5. Open the app and confirm Output protection and Equalizer are collapsed.
6. Expand Output protection, set Preamp to `0.0 dB`, Ceiling to `-6.0 dB`, and
   Release to `120 ms`, then play a loud track.
7. Toggle Output protection. Enabled output should become clearly quieter on loud
   passages, Output peak should remain near the ceiling, and Gain reduction should
   rise above `0.0 dB`.
8. Restore the ceiling to `-1.0 dB`. Choose Headphone energy, then toggle Equalizer
   while music plays and confirm the tonal change remains immediate.
9. Collapse both sections, close and reopen the app, and confirm their compact state
   and all DSP settings persist.
10. Make or receive a call. Processing should pause for the call and reattach with the
    saved settings after Android returns to normal audio mode.

Bluetooth, USB DAC, hardware-offloaded playback, and additional OEM devices remain
separate acceptance tests. If normal audio fails, disable AudioFreedom in KernelSU or
Magisk and reboot. The direct binds disappear after reboot; reinstall the confirmed
`directbind1` package if a known-good rollback is needed.

## Reference-device result

Confirmed on Xiaomi `xuanyuan_eea` on 2026-07-17:

- Processing and the ten-band EQ work.
- Input peak, output peak, and gain-reduction meters respond during playback.
- The audible effect of the `-6 dB` ceiling remained inconclusive because the Output1
  UI misleadingly placed the EQ preset preamp inside Output protection. Bass1 corrects
  that ownership before repeating limiter acceptance.
- Call bypass was not repeated for Output1; it remains provisionally accepted from the
  previously verified audio-mode handling.
