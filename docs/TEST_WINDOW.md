# Xiaomi Android 16 test window

This runbook is for the temporary `meta-overlayfs` test on the Xiaomi 15 Ultra
(`xuanyuan_eea`). It keeps persistent phone changes limited to modules and the
companion app, and keeps ADB binaries under `/data/local/tmp` only while they run.

## Before connecting

1. Do not open Revolut, Microsoft Authenticator, or other root-sensitive apps during
   the test window.
2. Install the official `meta-overlayfs` module in KernelSU Next and reboot.
3. Install `AudioFreedom-xuanyuan-eea-0.1.0-dev9.zip` and reboot.
4. Install `AudioFreedom-0.1.0-service-debug.apk`.
5. Connect USB debugging and confirm the computer's RSA prompt if Android shows it.

The AudioFreedom module registers the self-contained AIDL V3 effect library. The
companion app owns the working session-0 attachment through a foreground service.

## Baseline checks

Run these from the repository root:

```powershell
.\tools\audit-test-window.ps1 -Serial <phone-serial>
.\tools\run-native-tests.ps1 -Serial <phone-serial>
```

The audit should show Android SDK 36, enforcing SELinux, both temporary modules, the
AudioFreedom UUID in the active audio configuration, and `TEMP_CLEAN`. The native
test should end with `All AudioFreedom native tests passed`, exit code 0, and
`TEMP_CLEAN`.

## Effect test

1. Start a local music track and keep it playing through the phone speaker.
2. Open AudioFreedom. Merely opening or closing the Activity must not change volume.
3. Turn Processing on. The fixed -12 dB attenuation should be obvious.
4. Close AudioFreedom. Processing must remain active and its notification must remain.
5. Reopen AudioFreedom and turn Processing off. Normal volume should return immediately.
6. Repeat with one other route, preferably wired USB or Bluetooth, only after the
   speaker result is understood.

The speaker proof passed on July 17, 2026: repeated ON/OFF tests produced a clearly
audible difference, the foreground service survived Activity closure, and AudioFlinger
reported an enabled AudioFreedom effect on session 0.

The companion service releases the session-0 effect whenever Android enters any audio
mode other than `MODE_NORMAL`, then reattaches when normal mode returns. Calls,
communication mode, ringing, call screening, and redirects are therefore bypassed.
Cellular call audio may also use a modem or direct path that never enters AudioFlinger.

## Cleanup

1. Uninstall the AudioFreedom module and app.
2. Uninstall `meta-overlayfs`.
3. Reboot before opening root-sensitive apps.
4. Connect ADB and run `.\tools\audit-test-window.ps1 -Serial <phone-serial>` again.

The final audit should report both modules absent, the companion app not installed,
AudioFreedom not active in the vendor configuration, and `TEMP_CLEAN`.
