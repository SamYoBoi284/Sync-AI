# Sync AI — Context Tracker

## Current project
Repository: `SamYoBoi284/Sync-AI`
Active branch: `feature/voice-settings-ui`

Sync AI is a local Android assistant targeting the Samsung Galaxy A16 4G (4 GB RAM, Android 16, arm64-v8a, Shizuku, no root). The runtime is Java + C++ JNI + llama.cpp with imported GGUF models.

## What we accomplished

### Local AI/runtime
- GGUF model import and app-private model storage.
- llama.cpp CPU inference through the native backend.
- Model loading/unloading and runtime diagnostics.
- Qwen2.5-0.5B-Instruct Q4_K_M tested on-device.
- Native tuning for a 4 GB phone: compact context/history, capped generation, CPU threading, backend loading, and diagnostics.

### Tool system
- Deterministic intent parsing now supports compound actions instead of only one tool call.
- Calculator accepts natural operator wording (`times`, `multiplied by`, `divided by`, `over`, `plus`, `minus`, `mod`) and arbitrary arithmetic chains/parentheses.
- Alarm parsing supports natural times plus multiple alarms in one utterance and labels such as `titled work`, `called gym`, or `named school`.
- Added deterministic `call_contact` for phrases such as `call Mama`, `phone Abdulqader`, and `ring my mom`, with contact lookup + direct calling.
- Fast-tool execution now supports a queue of deterministic actions and requests only the permissions required by those actions.
- `SyncTool` + `ToolRegistry` architecture.
- Existing independent tools include calculator, flashlight, alarm, timer, contact calling, app/settings opening, workspace files, and Canvas.
- `ToolIntentRouter` runs before model inference for deterministic commands.
- Recent successful tool/action context supports contextual commands such as “turn it off” → previous flashlight.
- App launching supports aliases/fuzzy matching and confirmation when a match is uncertain.
- Flashlight uses Android CameraManager + permission handling.
- Calculator uses a local expression parser.
- Alarm/timer use Android AlarmClock intents.

### Locked tool architecture
**Tier 1 — Deterministic tools:** execute unambiguous Android commands directly, with no LLM dependency. Examples: flashlight, calculator, alarm, timer, app launch, settings, files, clipboard, volume/media, camera, etc.

**Tier 2 — Contextual resolution:** resolve clear references using recent successful tool context, e.g. “turn it off” or “open it again”. Still deterministic; do not invoke the LLM for an unambiguous referent.

**Tier 3 — LLM reasoning:** only for real language reasoning, planning, drafting, ambiguity resolution, or conversation.

Target flow:
`USER → Tier 1 → Tier 2 → LLM fallback → tool/result loop → response`

The model is the brain; Sync AI's tool registry is the deterministic hands.

### Tool protocol
- Model tool calls use `<tool_call>...</tool_call>`.
- Tool execution + tool-result continuation already exists.
- Recent fix: incomplete `<tool_call>` output without the closing tag is now parsed.
- Recent fix: raw tool-call protocol is hidden from the user-facing streaming bubble.
- Recent fix: common calculator, alarm, and timer phrases route directly through Tier 1.
- System prompt now reinforces Sync AI's identity and keeps tool protocol internal.

### UI / workspace
- Side dashboard with chats, workspace, Canvas, and nested settings.
- Canvas is AI-owned working space for planning/drafting/organization rather than a second manual chat box.
- Canvas read/write/replace/open tools exist.
- File attachment and imported memory flows exist.
- Keyboard-safe composer insets are implemented.
- Full-screen voice-mode overlay exists.

### Assistant / voice integration
- Android assistant-role selection is implemented.
- Samsung recognizes Sync AI as the selected default assistant option.
- `VoiceInteractionService` + `VoiceInteractionSessionService` integration is implemented.
- Dedicated assistant settings activity and invocation metadata are present.
- Assistant intents enter the same Sync AI voice/tool pipeline.
- openWakeWord integration with the bundled HEY_JARVIS model has been started.
- Samsung side-button invocation and wake-word reliability still need real-device fixes/testing.

## What's next

### 1. Finish Tier 1
Continue expanding deterministic parsing and execution for:
- flashlight
- calculator (more natural unit/percent/voice phrasing)
- alarm (repeat days, dismiss/snooze/show alarms)
- timer (more natural duration phrasing)
- contact calls
- app launch
- Android settings
- files/ZArchiver
- clipboard
- volume/media
- camera
- other safe Android intents

Add regression coverage for natural wording, aliases, punctuation, and command lead-ins.

### 2. Finish Tier 2
- Preserve the last successful tool target for each deterministic domain where pronoun resolution is safe.
- Support contextual variants like `turn that off`, `set another one for 9`, `call him again`, and `open that again` only when the referent is unambiguous.

### 2b. Tool coverage
- Generalize recent-action state beyond flashlight/open-app.
- Store only the small target context needed for resolution.
- Resolve pronouns only when unambiguous.
- Prevent stale context from hijacking new requests.
- Clear context on new chat where appropriate.

### 3. Harden Tier 3
- Robust local tool-call parsing.
- Never display raw protocol tags.
- Feed actual tool results back into the model.
- Cap recursive tool depth.
- Keep the prompt compact enough for 4 GB RAM.
- Improve conversational personality without relying on a larger model.

### 4. Add more independent tools
Volume, media controls, clipboard, camera, notification/settings navigation, and other permitted Android actions.

### 5. Finish continuous voice mode
- Recognizer uses up to 5 seconds of silence to decide the utterance ended.
- Then submit immediately; do not add another 5-second delay.
- Automatically listen again after Sync responds.
- Exit after about 7 seconds of complete silence after a response.
- Explicit farewells exit immediately.
- Restore wake-word detection after exiting.

### 6. Fix Samsung assistant + wake word
- Make Side-button long-press actually launch Sync AI voice mode through the VoiceInteractionService/session path.
- Resolve Samsung's “None” invocation display.
- Verify microphone ownership and HEY_JARVIS detection while backgrounded and locked.

### 7. Tune model/runtime
- Measure native inference latency independently of model size.
- Compare Qwen2.5-1.5B Q4_K_M against 0.5B after routing is solid.
- Deterministic commands must remain instant even if the model is unloaded or slow.

## Immediate real-device test plan
1. `yo, whats 29 * 2` → direct calculator → `58`.
2. `set an alarm for 6:30 AM` → direct alarm tool.
3. `set a timer for 5 minutes` → direct timer tool.
4. `turn on my flashlight` → direct flashlight.
5. `turn it off` → Tier 2 flashlight context.
6. `how ya doin bro` → normal conversational response.
7. LLM tool output → no raw `<tool_call>` visible as the final message.
8. Samsung side button / wake word → Sync AI voice mode.

## Baseline
The last known successful CI state before this fix pass was Actions build #199 on this branch. Rebuild and device-test after the current commits; a passing compile alone does not prove runtime behavior.


## Latest fix-pass changes
- Generalized deterministic routing to `parseAll()` so compound requests can produce multiple ToolCalls.
- Added `CallContactTool` with `READ_CONTACTS` + `CALL_PHONE` runtime permissions.
- Alarm execution now requests `EXTRA_SKIP_UI` and returns the actual alarm label/time result.
- MainActivity now executes deterministic tool queues sequentially and aggregates their results.
- Current GitHub Actions build was triggered after these changes; verify the latest run before installing.
