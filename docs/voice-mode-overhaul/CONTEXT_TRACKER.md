# Offline Voice Mode Overhaul — Context Tracker

Branch: `dev-testing-new-voice-mode-overhaul-16-10-2026`
Base: `huge-overhaul-sync-ai-15-10-2026` at `44d5139aa77abe82d9764f50173069d15ddfd411`
Status: experimental branch only; not merged or released. Code changes are committed; build and device testing are still pending.

## Work completed in this session

- `78893e16852f2d71fe0fca67452f593e2e66365e` — added this tracker and the test protocol.
- `44fe94308d5ae0ce8f01a4a5e97a43a7e2ab109b` — added an isolated `VoskVoiceRecognitionSession` prototype using the already-present Vosk Android library. It exposes partial/final/error/timeout callbacks and documents microphone ownership.
- `7d80af306ebdd36f1d4d04bb5351ad4e6c66dc1f` — enabled the debug APK workflow on this branch.
- `5f027bd4f56c217cce88d74327d507866837a704` — hardened Vosk adapter start/stop/error handling and duplicate-final suppression.
- `aab37c8360ea143820f90fcc66360f154908dd48` — changed the Vosk session to release the microphone between turns while keeping its model resident; model closes on session cleanup.
- `38a40cb183703d20f83202fd32e85b394e17ae5d` — wired Vosk conversational STT into Voice Mode behind a visible Android/Vosk switch; kept Android STT as the default; added asynchronous local model loading, final-result debounce, and per-turn mic release.
- `7e600b4a74d0193f7e40b660815383bb03d107c5` — updated this tracker for the integration.

The adapter is now wired into VoiceModeActivity behind an explicit on-screen STT switch. Android SpeechRecognizer remains the default; Vosk is opt-in for this experimental branch. The Vosk model is loaded off the UI thread, kept resident between turns, and microphone capture is stopped before Sync processes a recognized utterance. Build and device testing are still required.

## Goal

Evaluate a genuinely offline voice pipeline for Sync AI on the Galaxy A16 (4 GB RAM, Helio G99, Android 16), while preserving existing working behavior and providing a CPU-first path for budget devices. Do not assume NPUs are available or supported.

## Baseline found in source

- `SyncAssistantService` uses Vosk via `org.vosk.android.SpeechService` for the “Hey Sync” / “Hey Nullverox” wake phrase.
- Wake-word handoff stops the Vosk speech pipeline before launching `VoiceModeActivity`; voice mode announces start/finish so the wake service releases and later resumes the microphone.
- `VoiceModeActivity` currently uses Android `SpeechRecognizer` for conversational STT and Android `TextToSpeech` for speech output.
- The LLM uses existing llama.cpp through JNI and the `LocalModelBackend` callback interface; token callbacks are already streamed to the response UI, but speech output currently waits for completed generation.
- `app/build.gradle` includes `com.alphacephei:vosk-android:0.3.75`; the app-private Vosk model is installed by `VoskModelInstaller` and currently capped at 150 MiB unpacked.
- The project is Java/native C++, not React Native. PocketPal's React Native architecture is not a drop-in dependency.
- CI workflow now includes this branch in its push trigger. No successful build has yet been confirmed for the new branch.

## Proposed approach (test before replacing)

1. Keep current Android STT + Android TTS as the baseline/fallback.
2. Compile and review the isolated Vosk conversational-STT session using the already-imported local model. Integrate behind an explicit experimental selector, not as an unconditional replacement.
3. Compare Vosk with Sherpa-ONNX streaming STT only after checking Android/Java integration, model licensing, language support, APK size, RAM, CPU load, and offline operation. Do not add a large model bundle blindly.
4. Keep llama.cpp/JNI as the initial LLM runtime. Do not migrate to RunAnywhere or LiteRT without a measured benefit and a clear model/backend path.
5. Prototype phrase-buffered TTS: consume streamed LLM tokens, split at safe phrase boundaries, and speak completed chunks while generation continues. Preserve stop/cancel handling and prevent old chunks from playing after interruption.
6. Evaluate Piper (or a compatible neural TTS engine) against Android TTS for naturalness, model footprint, startup time, CPU/RAM cost, language/voice availability, and licensing. Keep system TTS fallback.
7. Evaluate lightweight VAD (e.g. Silero) only after baseline timing; it is not zero-cost. Ensure the VAD and recognizer do not compete for the microphone.
8. Treat NPU acceleration as an optional backend capability, not a guarantee. CPU fallback must remain viable.

## Evaluation protocol

Run on the same phone and use the same phrase set for each candidate:
- Accuracy: short commands, natural multi-clause sentences, slang, names, and noisy-room speech.
- Latency: time to partial transcript, end-of-speech to final transcript, first LLM token, first audible response, and total turn.
- Resource cost: peak/steady RAM, CPU load, thermal throttling, app size/model storage, startup time.
- Reliability: repeated turns, empty/noisy input, permission denial, screen-off/overlay use, interruptions, errors, and returning mic ownership to wake-word mode.
- Compatibility: verify WhatsApp/Instagram voice notes and the app's own Recorder still work after voice mode exits.
- Offline: enable airplane mode and verify every selected stage works without cloud fallback.

Do not claim a latency target (including <1.5 s) before measuring it. Do not add CPU affinity pinning or aggressive simultaneous inference without profiling.

## Session handoff / next steps

- [ ] Confirm CI build result for the latest branch head; fix any compile/API errors found.
- [x] Add an explicit experimental STT selector (Android SpeechRecognizer vs Vosk) and backend diagnostics.
- [x] Integrate Vosk with mic lifecycle: one conversational STT backend at a time, stop capture before handling a final transcript, retain the loaded model between turns, and close it on activity cleanup.
- [ ] Add phrase-buffered TTS with cancellation/interrupt guards, retaining the existing system TTS path.
- [ ] Investigate Sherpa-ONNX Android integration and identify one small, supported streaming model before adding dependencies/assets.
- [ ] Investigate Piper or another suitable offline neural TTS model; verify Android packaging and license.
- [ ] Add timing/resource diagnostics without recording raw audio or sensitive transcript content.
- [ ] Build the latest branch head and fix any compile/API issues before device testing.
- [ ] Install the debug APK. **Debug uses application ID `com.sam.syncaitest`**, so it has separate private storage: import the Vosk model into the debug app itself before selecting Vosk.
- [ ] In Voice Mode, leave **STT: ANDROID** selected for baseline, then tap the STT switch to select **STT: VOSK OFFLINE**. Confirm partial transcript, final transcript, response, and repeated turns.
- [ ] Install and benchmark on the Galaxy A16.
- [ ] Re-check wake-word microphone release/resume, hardware assistant overlay, Recorder, WhatsApp, and Instagram voice notes.
- [ ] Update this tracker with exact commits, build results, measurements, blockers, and next action.

## Safety / scope rules

- Experimental branch only. No merge, tag, or release without explicit approval.
- Preserve existing working behavior; prefer small, reversible changes.
- Do not silently download or bundle large models. Model import should be explicit and storage-aware.
- Do not store raw microphone audio. Log timings, backend names, and error categories only.
- Do not run two mic consumers at once.
