# Global effect controller

`audiofreedom-controller` is an Android platform binary built against Android 16's
`libaudioclient`. It exists because the generic Java `AudioEffect` constructor for a
custom UUID is hidden from ordinary applications on current Android releases.

The controller supports two attachment strategies:

- `output-mix`: owns and enables AudioFreedom on `AUDIO_SESSION_OUTPUT_MIX`. The process
  remains alive for as long as processing should remain attached. This is the closest
  equivalent to ViPER4Android's legacy/session-0 behavior.
- `stream-default-media`: registers AudioFreedom as a default effect for
  `AUDIO_USAGE_MEDIA` and removes that registration during a clean shutdown.

Both modes accept `--probe`, which verifies creation or registration and immediately
cleans up. On Android 16, the output-mix mode uses AudioEffect's probe-only creation flag
so it does not create a live processing interface. `--version` reports the controller
build identity and target Android API. Installers must probe on every target ROM rather
than assuming that root implies AudioFlinger permission or compatible routing.

The binary is platform-version-specific because `libaudioclient` is not a stable vendor
API. It shares no Xiaomi, Qualcomm, Dolby, or other manufacturer library dependencies.
Android 15 and later releases should receive separately built and tested controller
binaries even when they share the same AudioFreedom DSP and effect UUIDs.
