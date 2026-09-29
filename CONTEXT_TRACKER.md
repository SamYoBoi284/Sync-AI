# Sync//AI — Context Tracker

> Living handoff/context document for the Sync//AI project.
> Last updated: 2026-09-30

## Project
- Repository: `SamYoBoi284/Sync-AI`
- Android local AI assistant for the user's Samsung Galaxy A16 4G.
- Core architecture: **local model = brain, deterministic Android tools = hands**.
- Primary runtime: Android Java + C++ JNI + llama.cpp, arm64-v8a, CPU-first.
- Models are imported on-device; model files are not committed to GitHub.

## Device / Runtime Target
- Samsung Galaxy A16 4G (SM-A165M)
- 4 GB RAM / 128 GB storage
- MediaTek Helio G99
- Android 16 / One UI 8.5
- arm64-v8a
- Shizuku available
- Device is not rooted

## Architecture Decisions

### Three-tier request routing
1. **Tier 1 — deterministic tools**
   - Direct Android commands execute immediately without the LLM.
   - Natural-language variants and compound commands are supported.
2. **Tier 2 — deterministic contextual resolution**
   - Recent successful tool context resolves unambiguous follow-ups.
   - Example: `turn on my flashlight` → `turn it off` resolves `it` to flashlight.
3. **Tier 3 — local LLM fallback**
   - Used only for genuine language reasoning, ambiguity, complex planning, or unsupported wording.
   - Direct commands must not be sent through an LLM classifier.

### Tool registry
- Tool abstraction exists through `SyncTool` / `ToolRegistry`.
- Fast execution path supports multiple deterministic tools in one request.
- Runtime permissions are aggregated before queued tool execution.
- Successful tool calls are remembered for contextual follow-ups.

## Deterministic Tool Work Completed
- Flashlight on/off/toggle parsing.
- Open/launch app parsing with natural aliases.
- Contact calling with natural command lead-ins.
- Calculator parsing:
  - arithmetic chains
  - parentheses
  - word operators
  - percentages
  - common phrasing such as “what is”, “how much is”, “work out”, etc.
- Timers:
  - seconds/minutes/hours
  - natural variants such as “remind me in 15 minutes”
- Alarms:
  - multiple alarms in one message
  - hour/minute + AM/PM
  - titles
  - natural “wake me”, “set”, “schedule”, and “remind me” forms
- Compound-command boundaries were fixed so one command does not accidentally consume the next command.
- Contextual follow-ups implemented for:
  - flashlight
  - opening/launching the previous app
  - calling the previous contact again
  - restarting the previous timer
  - repeating the previous calculation
- “New chat” clears deterministic command context.

## Alarm Runtime
- Replaced dependency on `AlarmClock.ACTION_SET_ALARM` because some devices returned:
  “No alarm application can handle this request.”
- Native alarm scheduling now uses Android `AlarmManager`.
- Uses exact alarm scheduling when available and an idle-safe fallback otherwise.
- Added:
  - `AlarmReceiver`
  - `AlarmActivity`
  - alarm notification channel
  - alarm ringtone/vibration
  - full-screen alarm UI
  - dismiss/stop controls
- Manifest includes required alarm/full-screen notification permissions.
- Notification permission is requested at runtime for alarm tools.
- Exact-alarm and full-screen behavior still needs device-level verification.

## Voice / Assistant Integration
- Sync//AI is integrated as the Android digital assistant / voice interaction service.
- Samsung Settings can select Sync//AI as the default digital assistant.
- Side-button assistant invocation works.
- VoiceController uses Android SpeechRecognizer + TTS.
- Voice state flow is designed around:
  IDLE → LISTENING → THINKING → TOOL/LLM → RESPONDING → LISTENING.
- Voice mode supports continuous listening/restart behavior and explicit exit/farewell phrases.
- Voice output setting is persisted.

## Wake Word Status / Direction
- Previous wake-word implementation used built-in wake-word support such as “Hey Jarvis”.
- **Project direction is now to remove wake-word functionality entirely.**
- Microphone access should be restricted to active Sync//AI voice mode.
- No always-listening/background wake-word detector should remain.
- The Android default-assistant / side-button path remains the entry point for voice mode.

## Voice UI Direction
- Replace the current opaque/separate-feeling voice page/overlay with a nearly transparent overlay.
- Underlying app should remain visibly present.
- Central voice/power control remains available.
- User can exit voice mode at any time.
- Transcript and Sync response remain visible.
- Voice mode is the only place that should activate microphone access.

## Conversation / Personality
- Added fast-path handling for common greetings and casual “how are you/how ya doing” phrases.
- Casual language, slang, shorthand, contractions, typos, and bro/bfam-style wording are intended to be understood naturally.
- Sync should not respond to ordinary “how are you doing?” as though it were a medical/health question.
- Sync should not volunteer “I’m just a bot/AI” during normal conversation.
- Direct identity questions can identify the app as Sync//AI without pretending to be human.

## Local Model Runtime
- GGUF + llama.cpp native runtime is integrated.
- CPU-only arm64 runtime.
- Runtime tuning already attempted:
  - bounded context size
  - bounded generation length
  - CPU thread tuning
  - batch/ubatch tuning
  - sampler configuration
  - UTF-8/error handling
  - ggml backend loading/diagnostics
- Tested small local model sizes because 4 GB RAM is restrictive.
- Current investigation target is **why some LLM fallback requests still take ~40 seconds**, rather than assuming model size alone is responsible.

## Native Diagnostics Already Added
`syncai_native.cpp` tracks:
- prompt evaluation time
- generation time
- generated token count
- native runtime/model information
- architecture
- parameters
- tensor size
- training context
- backend

`GgufModelBackend.diagnostics()` exposes native diagnostics.

### Required next diagnostics layer
Each request should eventually record:
- request ID
- routing path: deterministic / contextual / LLM
- total request duration
- prompt token count
- context size
- prompt evaluation ms
- generation ms
- generated token count
- tokens/sec
- model name
- CPU thread count
- deterministic tool execution time
- error stage
- error message

Diagnostics should be visible in Runtime/About and preserved with chat history so failures can be screenshotted and investigated later.

## Canvas / AI Workspace
- Canvas is an **AI-owned workspace**, not a second place for the user to manually type.
- Sync can use it for substantial:
  - planning
  - drafting
  - outlining
  - organizing
  - revision
- Canvas tools exist:
  - read canvas
  - write canvas
  - replace canvas text
  - open canvas
- Workspace is stored as `workspace/canvas.md` in app-private storage.
- Canvas UI has Sync//AI branding and AI-owned-workspace labeling.

## Side Dashboard / Settings
- Settings moved into the side dashboard.
- Side dashboard is opened from the left edge.
- Main sections include:
  - CHATS
  - WORKSPACE
  - SETTINGS
- Settings currently has areas for:
  - Assistant / voice output
  - Workspace / files
  - memory
  - models
  - import model
  - runtime
- Top-right standalone Settings button was removed.

## Persistent Chat History — Required / In Progress
Current architecture previously kept the active conversation only in an in-memory:
`List<ChatMessage> conversation`.

Required persistent system:
- Save multiple chats.
- Restore the last active chat after app restart.
- CHATS section should list saved conversations.
- Open/switch between previous chats.
- New Chat creates a separate persistent conversation.
- Save:
  - user messages
  - Sync responses
  - tool results
  - errors
  - diagnostic metadata
- Do **not** feed every historical chat into the LLM. Persistent storage and model context are separate; inference should still use a bounded recent context.
- This history is specifically important for preserving error messages/diagnostics for screenshots and debugging.

## Personalization — Required / In Progress
Add a dedicated Personalization settings section containing:
- Memory controls / memory import access
- 8 selectable Sync//AI accent colors
- UI personalization hooks for future settings
- Keep memory under Personalization rather than general Workspace settings.

Suggested eight accent choices:
1. Purple
2. Blue
3. Cyan
4. Green
5. Lime
6. Orange
7. Red
8. Pink

The selected accent should be persisted and applied consistently across Sync//AI UI.

## About — Required / In Progress
Add an About section under Settings containing:
- App name: Sync//AI
- App version
- Build/version information
- Created by Sam
- Arabic creator label: **أنشأه حسام**
- Runtime/model information
- Diagnostics/debug access
- Relevant project/runtime details useful when reporting bugs
- A compact “copy diagnostics” / shareable diagnostic view is desirable.

## Regression Requirements
Before considering the current milestone complete, verify:
- Direct flashlight command is instant.
- Flashlight contextual follow-up actually toggles the device.
- Time/date commands are deterministic and do not invoke the LLM.
- Compound commands execute all intended tools.
- Alarms work natively.
- Calling/contact permissions behave correctly.
- App launching works.
- Calculator/timer/alarm parsing does not consume adjacent commands.
- Conversation small talk does not invoke the tool/LLM path unnecessarily.
- Persistent chats survive app close/reopen.
- Switching chats restores their messages.
- Errors and diagnostics remain attached to the relevant chat.
- Mic permission is absent/inactive outside voice mode.
- Voice mode can start/stop cleanly.
- Wake-word/background microphone behavior is gone.
- Voice overlay is translucent and dismissible.
- Personalization color persists.
- About/diagnostics display correctly.
- Debug APK / GitHub Actions build succeeds.

## Recent Known CI State
- Recent voice/settings work reached successful GitHub Actions builds.
- Earlier overlapping runs had transient log retrieval issues, but the latest verified build state was successful.

## Important Project Rules
- Do not manually bump package versions when the project release script owns versioning.
- Keep deterministic tools independent of the LLM.
- Prefer native Android APIs for simple device actions.
- Preserve diagnostic evidence instead of replacing failures with generic “Generation Failed”.
- Keep the local model optional; the deterministic tool layer must remain useful without it.
- User wants a plan stated before major code changes.
