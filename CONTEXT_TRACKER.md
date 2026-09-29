# Sync//AI — Context Tracker

> Living handoff/context document for the Sync//AI project.
> Updated: 2026-09-29
> This tracker describes the code that is actually present on the `sync-ai-toolcore-voice-history` branch. It is not a substitute for source verification.

## Project

- Repository: `SamYoBoi284/Sync-AI`
- Android offline-first local AI assistant.
- Core architecture: **local model = brain, deterministic Android tools = hands**.
- Primary runtime: Android Java + C++ JNI + llama.cpp, CPU-first, arm64-v8a.
- Model files remain on-device and are not committed to GitHub.

## Baseline audit

Before this milestone, the default `main` branch contained:
- the GGUF model/import layer
- the native llama.cpp runtime
- a basic chat Activity
- the initial `SyncTool` / `ToolRegistry` foundation

The baseline did **not** contain:
- a `VoiceInteractionService`
- a `SpeechRecognizer` voice controller
- a voice overlay
- a wake-word detector
- persistent chat history
- a real deterministic Android request router

The milestone below was therefore implemented as new functionality rather than assuming tracker text represented existing code.

## Deterministic Sync//Tool Core — implemented

Direct device-known requests are routed before the local LLM.

Current deterministic tools include:
- current time
- current date
- local timezone
- battery percentage / charging state
- Wi-Fi state
- network/internet state
- Bluetooth state
- media volume
- screen brightness
- RAM usage
- storage availability
- device information
- flashlight/torch on/off/toggle
- app launch
- contact lookup + dialer handoff
- calculator
- timers
- alarms
- compound commands
- contextual follow-ups

Examples:
- `what time is it?` → Android clock
- `how much battery do I have?` → BatteryManager
- `turn on my flashlight` → CameraManager torch
- `open Discord` → Android launcher intent
- `turn it off` after flashlight control → remembered flashlight context
- `what time is it and turn on the flashlight` → multiple deterministic actions

Deterministic routing does not ask the LLM to classify basic Android commands.

## Contextual tool state — implemented

The tool engine remembers recent successful deterministic context for follow-ups such as:
- `turn it off`
- `turn it on`
- `open it`
- `call again`
- `call them`
- `do that again`
- `repeat that`

Starting or switching to a new chat clears the deterministic context.

## Persistent chat history — implemented

Chats are stored in app-private `chats.json`.

Each chat stores:
- ID
- title
- created time
- updated time
- messages

Each saved message stores:
- role
- text
- timestamp
- optional diagnostics

The main app now supports:
- multiple chats
- New Chat
- opening previous chats
- automatic initial titles from the first user message
- rename
- delete
- restoring the active chat after app restart

Persistent chat storage is separate from model context. Historical chats are **not** all injected into inference; only a bounded recent slice is sent to the local model.

This preserves error messages and diagnostics so a failed request can still be screenshotted after the app is reopened.

## Inference diagnostics — implemented

Every local-model request now records native timing evidence.

Tracked items include:
- route
- total request time
- prompt token count
- context size
- CPU thread count
- prompt evaluation time
- generation time
- generated token count
- tokens/sec
- model information
- error stage/message when generation fails

The UI exposes diagnostics through:
- Runtime
- About & Diagnostics
- per-message `⌁ DIAGNOSTICS` entries

Diagnostics are persisted with the corresponding assistant message.

The native runtime also keeps the latest request diagnostic record so the 40-second issue can be investigated with actual measurements instead of guesses.

## Local inference tuning — implemented on this milestone

Current safety/performance bounds:
- generation default: 128 tokens
- native generation hard bound: 128 tokens
- context size: 1024–2048 tokens
- native batch / ubatch: 256
- CPU threads: bounded to 2–6 based on device hardware

The runtime still creates an inference context per request. The new diagnostics are intended to show whether the dominant latency is prompt evaluation, generation, or another stage before further optimization.

## Conversational fallback — implemented

The main chat and voice paths now use this routing order:

1. deterministic Android tool
2. recent contextual tool resolution
3. bounded local-model conversation
4. small offline fallback response when no model is loaded

The local system prompt instructs Sync to:
- speak naturally
- understand slang, shorthand, contractions, typos, and bro/bfam wording
- avoid unnecessary robotic identity disclaimers
- leave direct device actions to deterministic tools

Common basic greetings / casual questions have a lightweight fallback when no model is loaded.

## Voice / Android assistant integration — implemented

The branch adds:
- `SyncVoiceInteractionService`
- `SyncVoiceSessionService`
- `SyncVoiceSession`
- Android voice-interaction service metadata
- default-assistant manifest declarations

The intended entry point is the Android assistant/side-button path.

When Sync is selected as the default digital assistant, the voice session provides:
- speech recognition
- deterministic tool execution
- local LLM fallback
- text-to-speech response
- repeated listening after the response
- explicit exit phrases
- an always-visible close button

## Wake-word status — removed by design

There is **no wake-word detector in this branch**.

There is no:
- “Hey Jarvis” style trigger
- background hotword loop
- always-listening speech service

Voice mode begins from the explicit Android assistant entry point.

Microphone use:
- `RECORD_AUDIO` is declared because the voice session needs speech recognition.
- Sync only starts `SpeechRecognizer` while the active voice session is running.
- Outside voice mode, Sync does not start a speech listener.

Android still controls the actual runtime permission grant at the system level.

## Voice overlay — implemented

The assistant session uses a transparent session root with a nearly transparent central Sync panel.

Current behavior:
- underlying app remains visible
- no opaque full-screen chat page is opened for assistant invocation
- central mic/power control
- transcript area
- response area
- close `×` button
- no background dim
- back/close session exits voice mode

Voice flow:

```
IDLE
  ↓
LISTENING
  ↓
THINKING
  ↓
TOOL / LLM
  ↓
RESPONDING
  ↓
LISTENING
```

Explicit exit phrases include:
- stop listening
- goodbye
- exit
- cancel
- close sync
- that’s all

## Voice output — implemented

- Text-to-speech is provided through Android `TextToSpeech`.
- Voice output can be toggled from Settings.
- After TTS completes, Sync returns to listening while the session remains open.

## Settings / Personalization — implemented

Settings now contains:
- Personalization
- About & Diagnostics
- Voice Mode
- Local Models

Personalization contains:
- editable local Memory
- text-file Memory import
- 8 persisted accent choices

Accent choices:
1. Purple
2. Blue
3. Cyan
4. Green
5. Lime
6. Orange
7. Red
8. Pink

Memory remains app-local and is added as optional system context for local model generation.

## About — implemented

About & Diagnostics includes:
- app name
- version name + version code
- `Created By Sam`
- **`صنعه حسام`**
- local GGUF + llama.cpp runtime information
- target ABI
- latest request diagnostics
- copy-to-clipboard diagnostics access
- Android assistant settings shortcut

## Permissions / Android capabilities

Declared for the current deterministic/voice feature set:
- RECORD_AUDIO
- CAMERA
- READ_CONTACTS
- BLUETOOTH_CONNECT
- POST_NOTIFICATIONS
- SCHEDULE_EXACT_ALARM

Runtime permissions are requested only when the relevant operation needs them.

Contact handling currently opens the Android dialer for the matched contact rather than silently placing a call.

## Timers / alarms

The deterministic tool core can schedule timers and alarms through Android AlarmManager.

A notification receiver posts the resulting Sync//AI notification.

Exact-alarm behavior can fall back to an inexact idle-safe schedule when the exact-alarm capability is unavailable.

## Native runtime diagnostics

`GgufNative.nativeDiagnostics()` now exposes:
- model information
- latest request timing
- prompt tokens
- context size
- thread count
- prompt evaluation
- generation time
- token count
- tokens/sec
- total inference time

This is the primary instrumentation for investigating the recurring ~40 second responses.

## What is intentionally not claimed as fully verified

The code is implemented, but these parts still require physical device validation:
- selecting Sync//AI as the default assistant in Samsung/Android settings
- side-button invocation behavior on the Galaxy A16 / One UI 8.5
- first-run microphone permission flow
- actual SpeechRecognizer behavior on the device
- TTS behavior on the device
- exact visual opacity/placement of the translucent overlay
- real-world flashlight/contact/Bluetooth permission behavior
- actual local-model latency measurements on the target phone
- Android timer/alarm notification behavior under device power-management rules

CI build verification is tracked separately below.

## CI / branch

Development branch:
`sync-ai-toolcore-voice-history`

Pull request:
`Sync-AI #6 — feat: Sync//AI tool core, persistent chats, diagnostics, voice overlay`

The GitHub Actions workflow is configured to build this branch.

## Project rules

- Do not manually bump package versions when the release script owns versioning.
- Keep deterministic device tools independent from the LLM.
- Preserve diagnostic evidence instead of replacing errors with a generic failure.
- Keep persistent chat storage separate from bounded inference context.
- Keep wake-word/background microphone behavior out of the project.
- Keep model files out of GitHub.
- Treat source code and CI/device verification as the authority for completed features, not tracker text alone.
