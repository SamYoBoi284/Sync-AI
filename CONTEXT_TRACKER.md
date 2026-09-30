# Sync AI — Context Tracker

> Living handoff/context document for the Sync AI project.
> Updated: 2026-09-30
> Latest verified source branch: `sync-ai-context-visual-813e999-v2`
> This tracker records implemented work and known verification status. Source code / CI remains authoritative.

## Project

- Repository: `SamYoBoi284/Sync-AI`
- Android offline-first local AI assistant.
- Core architecture: **local model = brain, deterministic Android tools = hands**.
- Android Java + C++ JNI + llama.cpp, CPU-first, arm64-v8a.
- GGUF model files remain on-device and are not committed to GitHub.
- App name is **Sync AI**; the old `Sync//AI` naming is no longer the desired user-facing name.

## Deterministic Sync Tool Core — implemented

Device-known requests are routed before the local LLM. Implemented tools include:
- current time/date/local timezone
- battery and charging state
- Wi-Fi/network state
- Bluetooth state
- media volume and brightness
- RAM/storage/device information
- flashlight/torch
- app launching
- contact lookup + dialer handoff
- calculator
- timers/alarms
- compound commands
- contextual follow-ups

Examples such as `what time is it?`, `turn on my flashlight`, `open Discord`, and follow-ups such as `turn it off` are intended to execute through Android rather than consuming an LLM generation.

## Conversational fallback — implemented

Routing order:
1. deterministic Android tool
2. recent contextual tool resolution
3. bounded local-model conversation
4. small offline fallback when no model is loaded

The local prompt is designed for natural slang, shorthand, typos, casual/bro wording, and conversational replies without unnecessary robotic identity disclaimers.

## Persistent chat history — implemented

Chats persist in app-private `chats.json` and contain:
- ID/title/created/updated timestamps
- user and assistant messages
- optional per-message diagnostics

The app supports multiple chats, New Chat, reopening chats, automatic initial titles, rename/delete, and restoring the active chat after restart.

Historical chats are not all injected into inference; inference receives a bounded recent slice.

## Chat / composer stabilization — implemented

Previously fixed:
- follow-up messages being blocked after the first generation
- queued follow-up generations
- sticky composer above the Android keyboard
- keyboard remaining available after Send
- AI messages failing to persist while user messages did persist

## Inference diagnostics — implemented

Diagnostics track native request evidence including:
- route
- total request time
- prompt token count
- context size
- CPU threads
- prompt evaluation time
- generation time
- generated token count
- tokens/sec
- model information
- failure stage/message

Diagnostics are available through Runtime / About & Diagnostics and can be attached to individual assistant messages. The diagnostics are intended to investigate the recurring ~40-second response latency instead of guessing.

## Important diagnostics ANR fix — implemented

The earlier Diagnostics/Runtime ANR was traced to a JNI mutex deadlock: native generation held `g_mutex`, called Java completion while holding it, and Java then queried native diagnostics, attempting to lock the same mutex again.

The fix changed completion to pass already-computed diagnostics through JNI and persist them without re-entering native diagnostics during the callback.

Relevant commits include:
- `263f27f394cdb46b1541aaafe6e506ab0c794290`
- `de1e447b61278b3d667ac9261879af5dadc667c8`
- `6b25d074ba5961d702200aff15909b19cddee816`
- `3186102208f172a927c89cde5d19dd18c79313f7`

## Voice system — implemented, device verification ongoing

Implemented components include:
- `SyncVoiceInteractionService`
- `SyncVoiceSessionService`
- `SyncVoiceSession`
- assistant metadata / default-digital-assistant integration
- SpeechRecognizer voice flow
- Android TextToSpeech
- repeated listening after response
- explicit voice exit controls
- voice-mode chat persistence

### Wake word — intentionally removed

There is **no wake-word detector** and no background hotword loop.

Microphone use is intended to occur only while an explicit voice session is active. `RECORD_AUDIO` exists because SpeechRecognizer needs it; Sync does not intentionally keep a background speech listener running outside voice mode.

### Voice overlay — implemented

The assistant voice session uses a transparent/nearly transparent root with a central Sync panel so the underlying app remains visible. It includes transcript/response UI, microphone/power control, and a close button.

### Voice output — implemented

Android TextToSpeech speaks voice-mode responses and returns to listening after completion. Leaving voice mode stops voice interaction/TTS behavior.

### Voice-mode chat integration — implemented

Voice conversations are persisted into the selected chat so spoken user input and Sync responses remain available after leaving voice mode.

### Side-button / default-assistant path — NOT YET VERIFIED

The target behavior is:
- Samsung/Android can select Sync AI as the default digital assistant
- side button invokes Sync voice mode from Home, another app, and lock screen where Android/Samsung permits it
- voice mode can then return to the previously active app when closed

The side-button path has repeatedly been the unstable portion of the project and is being instrumented rather than repeatedly rewritten blindly.

## Latest side-button diagnostic instrumentation — implemented

Added lifecycle logging around the assistant entry path:
- `VoiceInteractionService.onCreate`
- `onReady`
- `onPrepareToShowSession`
- `onShowSessionFailed`
- `onLaunchVoiceAssistFromKeyguard`
- `onShutdown`
- `VoiceInteractionSessionService.onCreate`
- `onNewSession`
- `onDestroy`
- `VoiceModeActivity.onStart`
- `VoiceModeActivity.onStop`

Existing session logging also covers preparation/show, assistant/activity launch attempts, task start/finish, hide, and related fallback behavior.

These logs are intended to identify exactly where Samsung's side-button path stops instead of guessing.

## Recent CI failure and fix — completed

Three diagnostic commits initially failed the Debug APK build. The latest failure was inspected from the GitHub Actions job logs and was a genuine Java compile error:

- `SyncVoiceSession.java` contained a duplicate `onHide()` method.
- The duplicate was introduced during the diagnostic logging work.
- It was removed in commit `9d7ef4f1b494f18e2dd1b60a7715ebeae9669688` (`fix: remove duplicate voice session onHide`).

Earlier diagnostic commits:
- `9619c7b1a20c280f770d1ed4d8a834260b56eed7`
- `a8f1e2d69e7a9e6aa8ca0ca7b0fd49534fc51aa2`
- `9ee15e81194b7120c1441d9d9c951647e29cea0c`

One earlier attempt used `getComponentName()` in `VoiceInteractionService`; that was replaced with a simpler compile-safe log entry. The final known compile issue was the duplicate `onHide()`.

## Native model context / performance

The native runtime on the current branch is already using a **4096-token inference context**, so no redundant 128→4096 context change should be made based only on the earlier plan.

The project previously had smaller generation/context safety bounds and the recurring long-response problem remains an investigation target. Diagnostics should be used to measure prompt evaluation versus generation time before further tuning.

## Settings / Personalization — implemented

Settings contains:
- Personalization
- About & Diagnostics
- Voice Mode
- Local Models

Personalization contains editable local Memory and persisted accent selection with 8 choices:
1. Purple
2. Blue
3. Cyan
4. Green
5. Lime
6. Orange
7. Red
8. Pink

## About & Diagnostics — implemented

About includes:
- app/version information
- `Created By Sam`
- `صنعه حسام`
- GGUF / llama.cpp runtime information
- ABI/runtime information
- latest diagnostics
- diagnostics access/copy support
- Android assistant settings shortcut

## Visual / animation revamp history

The visual revamp work was based on the existing Sync AI ToolCore + voice/history implementation rather than replacing the project architecture. User-provided animation/visual files were brought into the project from the visual-revamp work, including the referenced `813e999` visual commit lineage.

Important project rule: **never delete something that already works unless it directly contradicts a new requirement or the thing being fixed.** Preserve working functionality during future visual/voice changes.

## Full Sync AI flight recorder + Export Logs — implemented

Added a persistent app-private **flight recorder** in `SyncEventLogger.java`. It records structured timestamped events with event ID, process ID, thread, component, event name, severity, and details, and keeps a bounded rolling file so diagnostics survive ordinary app restarts and can still be inspected after a crash.

The recorder is installed from every important Sync entry point and captures:
- MainActivity lifecycle and intent/activity-result transitions
- VoiceModeActivity lifecycle, voice text, SpeechRecognizer callbacks/errors, TTS initialization/completion/errors, tool routing, model generation start/complete/error, and voice exit
- SyncVoiceInteractionService lifecycle and assistant entry callbacks including `onReady`, `onPrepareToShowSession`, `onShowSessionFailed`, and `onLaunchVoiceAssistFromKeyguard`
- SyncVoiceSessionService creation/destruction and session creation
- SyncVoiceSession preparation/show/hide, task start/finish, assistant-activity launch, voice-activity fallback, direct activity fallback, and launch exceptions
- MicPermissionActivity lifecycle and permission results
- SyncRecognitionService lifecycle/listening callbacks
- notification receiver events
- model import/load/error events
- deterministic tool routing events
- persistent uncaught Java exceptions including stack traces and the last persisted event timeline

The global uncaught-exception handler records fatal Java exceptions before delegating to Android's existing crash handler. Native process-level crashes are not converted into Java exceptions, so the export also includes best-effort Android logcat to preserve system/native crash evidence when Android exposes it to the app.

**Settings → About & Diagnostics** now has an **EXPORT LOGS** button. The generated `sync-ai-full-debug.txt` contains:
- app/device/Android/ABI/process metadata
- personalization memory and active-chat metadata
- local model registry + loaded model metadata
- runtime/native diagnostics
- latest request diagnostics
- every saved chat with per-AI-message diagnostics
- the persistent Sync AI flight-recorder timeline
- best-effort Android logcat
- explicit availability/error messages where Android restricts log access

Existing chat export and working voice/tool/model behavior remain separate from the recorder and were not intentionally removed or replaced.

## Chat export — implemented

Chats can be exported with AI-message diagnostics so exported conversations can be shared for debugging. This is intended to preserve the exact conversational context and request diagnostics needed to investigate crashes/latency.

## Known / next verification targets

Physical-device verification remains required for:
- side-button assistant invocation from Home
- side-button invocation while another app is foreground
- side-button invocation on the lock screen
- Samsung default-digital-assistant behavior
- microphone permission and SpeechRecognizer behavior
- TTS behavior and repeated listening
- voice session return-to-previous-app behavior
- exact visual opacity/placement
- actual local-model latency on the target device
- chat stability after long conversations / larger context windows

## Current working branch

Development/visual branch:
`sync-ai-context-visual-813e999-v2`

The older core branch remains:
`sync-ai-toolcore-voice-history`

## Project rules / handoff rules

- **Do not delete working functionality** unless it conflicts with a new requirement or is the direct subject of a fix.
- Do not manually bump package versions when the release script owns versioning.
- Keep deterministic Android tools independent from the LLM.
- Preserve diagnostic evidence instead of replacing errors with generic failure text.
- Keep persistent chat storage separate from bounded inference context.
- Keep wake-word/background microphone behavior out of the project.
- Keep model files out of GitHub.
- Treat source code + CI + physical-device tests as the authority; tracker text alone never proves runtime behavior.
