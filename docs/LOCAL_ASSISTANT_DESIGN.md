# AudioFreedom Local Assistant

## Boundaries

The assistant runs in the companion app process. It is not part of the audio effect,
controller daemon, or root module, and it never runs on the real-time audio thread.

The assistant may propose changes, but `AudioFreedomSettings` and its validated commit
path remain authoritative. Generated text must never be interpreted as shell commands,
file paths, Android intents, or raw driver messages.

## Processing path

```text
Typed text or push-to-talk audio
              |
       Optional Whisper model
              |
       Optional Qwen model
              |
      constrained tool request
              |
       AudioFreedom validator
              |
       preview / apply / undo
              |
            DSP API
```

The built-in command engine implements the same request/result interface as the future
Qwen backend. It provides a deterministic fallback and a way to test safety behavior
without loading a model.

## Model packs

Model packs are independent from the APK and Magisk module. They are installed into
app-private storage and require neither root nor a reboot. Removing a pack must leave
profiles, DSP settings, and the built-in command engine intact.

Initial packs:

| Pack | Model | Approximate download |
| --- | --- | ---: |
| Language | Qwen3-0.6B Q4 GGUF | 400 MB |
| Voice | Whisper Tiny multilingual | 75 MB |

The signed APK embeds a reviewed catalog entry for each managed model pack:

```json
{
  "schema": 1,
  "id": "audiofreedom-assistant-qwen3-0.6b-q4",
  "version": "1.0.0",
  "runtime": "llama.cpp",
  "minimumAppVersionCode": 20,
  "abi": ["arm64-v8a"],
  "fileName": "AudioFreedom-Assistant-Qwen3-0.6B-Q4.gguf",
  "size": 0,
  "sha256": "",
  "license": "Apache-2.0"
}
```

`size`, `sha256`, and the download URL are pinned in the app. Installation uses a temporary
file, verifies both values, validates the model header where applicable, and then atomically
moves the file into the active model directory. Arbitrary remote model URLs are not accepted.
Local GGUF import is available as an advanced option and is recorded as a custom model rather
than a managed pack.

## Runtime policy

- Pin llama.cpp to a reviewed commit rather than tracking its default branch.
- Use a short context sized for current DSP state and recent commands.
- Run Qwen in non-thinking mode with deterministic sampling and a small output limit.
- Ground every model request with the fixed AudioFreedom control manual and current DSP state.
  Explicit controls and saved-profile names use the deterministic path before model inference;
  the model grammar cannot emit profile indexes.
- Keep the model loaded by default and retain the shared manual prompt prefix in the llama.cpp
  KV cache. Offer one-minute and five-minute idle unloading for memory-constrained phones;
  unloading discards the cache and rebuilds it on the next request.
- Cancel inference when the app leaves the assistant flow or the user submits a new request.
- Reject a proposal if DSP settings changed after the request began.
- Never pass media titles, artist names, cover text, or other untrusted metadata into the prompt.
- Push-to-talk records at 16 kHz mono and runs Whisper entirely in the app process. Recording
  cannot start during Android call/communication mode and stops if that mode begins.
- Voice sessions can request transient exclusive audio focus so compatible players pause while
  the command is captured. An installed offline system voice can read back the validated DSP
  action before it is applied; audio focus is then released so playback resumes.
- Spoken read-back is independent from automatic application. Assistant settings include a local
  voice test and report when Android has no suitable offline system voice installed.
- A 1x1 home-screen widget opens a compact voice panel with microphone-level feedback,
  end-of-speech detection, interpretation review, Apply, and persistent one-step Undo.
- Assistant changes save their previous DSP state in app-private storage. **Undo the last
  change** and the widget Undo button restore the same snapshot across app launches.
- A future wake-phrase mode uses a separate low-power keyword detector. Full Whisper inference
  is not kept running continuously as a hotword detector. The proposed trigger is **Audio
  Freedom**; after detection, the keyword runtime hands the same command session to Whisper and
  the constrained DSP planner.

## Safety policy

- Conservative mode allows at most 1.5 dB per EQ band per command.
- Balanced mode allows at most 3 dB per EQ band per command.
- Tonal commands preserve the user's preamp setting and change only the requested DSP controls.
  Output protection remains an independent user choice.
- Every applied assistant change stores one undo snapshot.
- Profile changes always require preview and confirmation.
- Invalid bands, unknown effects, out-of-range values, and malformed output are rejected.
- The model cannot create new tools or address the driver directly.
- Optional thumbs-up/down records stay in app-private storage. Recent accepted and rejected
  outcomes become bounded personalization examples for future local model decisions. They are
  never uploaded and are not on-device weight updates; the inference runtime remains read-only.

## Delivery order

1. Command contract, deterministic fallback, settings, preview, and undo.
2. Verified model-pack installer and removal flow.
3. Pinned llama.cpp runtime and Qwen JSON tool adapter.
4. Whisper push-to-talk pack, microphone permission flow, and private result feedback.
5. Low-power wake-phrase pack and foreground microphone service.
6. Media-session-aware deferred profile changes.
7. Optional spectrum analysis supplied as measurements to the assistant.
