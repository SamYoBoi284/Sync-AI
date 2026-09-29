# Sync AI — Context Tracker

## Current project
Repository: `SamYoBoi284/Sync-AI`
Active branch: `feature/voice-settings-ui`

Sync AI is a local Android assistant targeting the Samsung Galaxy A16 4G (4 GB RAM, Android 16, arm64-v8a). Runtime: Java + C++ JNI + llama.cpp with imported GGUF models.

## Completed

### Core runtime
- GGUF model import + app-private model storage.
- llama.cpp CPU inference through the native backend.
- Runtime/native diagnostics for prompt tokens, context size, CPU threads, prompt evaluation time, generation time, generated token count, and tokens/sec.
- Compact prompt/history limits tuned for a 4 GB phone.
- Deterministic commands do not require a loaded LLM.

### Deterministic tool architecture
Target flow:
`USER → Tier 1 → Tier 2 → LLM fallback → tool/result loop → response`

**Tier 1:** direct Android tools execute immediately when intent is unambiguous.
**Tier 2:** recent successful action context resolves explicit follow-ups without the LLM.
**Tier 3:** the local model handles real conversation, ambiguity, planning, drafting, and language reasoning.

Current independent tools include:
- calculator
- time/date
- system info
- flashlight
- native alarm scheduling
- timer
- contact calls
- app/settings opening
- workspace files
- Canvas

Recent routing coverage includes natural lead-ins, slang, calculator word operators, percentages, compound alarms, contact calling, app aliases, and contextual follow-ups such as `turn it off`.

### System/device tools
Deterministic local system queries cover:
battery/charging, Wi-Fi, Bluetooth, volume, brightness, RAM, storage, device info, network status, flashlight state, current-app status where Android Usage Access permits it, and Sync AI scheduled alarms/timers.

### Chat history
- Persistent `ChatHistoryStore` saves up to 100 chats and 2,000 messages per chat.
- Active chat is restored after app restart.
- New chat creates a separate persistent session.
- Chat history UI can reopen saved conversations.
- Stored assistant messages retain request diagnostics, including generation/tool errors, so failures can be reviewed or screenshotted later.
- The model still receives only the recent working context rather than the entire stored archive.

### Diagnostics
- Every routed request now has a request ID, route, stage, model, total/routing/tool/LLM timings, native runtime details, and error stage/message when available.
- Diagnostics are visible from Runtime and the About screen.
- About supports copying the diagnostics text to the clipboard.
- Stored chat responses retain the diagnostic block.

### Voice / assistant integration
- Android `RoleManager` assistant selection is implemented.
- Samsung can use Sync AI as the default assistant.
- `VoiceInteractionService` + `VoiceInteractionSessionService` invoke the same Sync AI pipeline.
- Voice Mode is a translucent overlay over the existing chat, with transcript, response, central control, continuous re-listening, and a power/exit control.
- Voice Mode may operate on the lock screen using the existing assistant launch path.
- Speech recognition is created lazily only when Voice Mode starts and is explicitly released when Voice Mode exits.
- Microphone permission is requested from the Voice Mode path; the app no longer registers a custom background recognition service.

### Wake-word removal
- Wake-word functionality has been removed from the current branch.
- Custom `SyncRecognitionService` and its recognition-service XML metadata were deleted.
- `voice_interaction_service.xml` no longer points at a custom recognition service.
- No background wake-word detector is part of the current architecture.
- Default-assistant functionality remains available through Android's VoiceInteractionService.

### Personalization
- Eight accent colors: Purple, Cyan, Blue, Green, Orange, Red, Pink, White.
- Accent changes apply without recreating/reloading the model.
- Assistant tone setting: Casual, Balanced, Technical.
- Tone preference is injected into the local model system prompt.
- Memory import is grouped under Personalization in Settings.

### Settings / About
Settings now contain:
- Assistant: Voice output
- Workspace: Files, Models, Import model, Runtime
- Personalization: Accent colors, Assistant tone, Memory
- About: version/build details and diagnostics tools

About identifies:
- Sync AI version + version code
- Created by Sam
- Arabic credit: `أنشأه حسام`
- local CPU / arm64 runtime
- current loaded model
- microphone use limited to Voice Mode
- latest request diagnostics
- Runtime diagnostics + Copy actions

### Canvas / workspace
- Canvas is an AI-owned workspace for planning, drafting, organizing, and revision.
- Canvas read/write/replace/open tools are implemented.
- File attachment/imported memory flows remain available.

### Regression tests
- Added JUnit dependency and deterministic routing tests covering:
  - time queries
  - direct flashlight commands
  - contextual flashlight follow-up
  - compound alarms
  - natural-language calculator percentages
  - conversational fall-through

## What remains
- Real-device test of microphone behavior after removing the custom recognition service.
- Real-device test of the translucent voice overlay and lock-screen assistant path.
- Verify current GitHub Actions build after the latest commits.
- Continue expanding safe Tier 1 Android tools (volume/media, clipboard, camera, notification/settings navigation, etc.).
- Continue refining Tier 2 contextual state and preventing stale context from hijacking unrelated requests.
- Measure and optimize actual llama.cpp latency on the A16; deterministic commands should remain instant regardless of model latency.
- Compare a slightly larger local model after routing/runtime diagnostics are stable.

## Current real-device regression checklist
1. `yo, whats 29 * 2` → direct calculator → 58.
2. `what is 29 / 2 * 7` → direct calculator.
3. `10 percent of 200` → direct calculator.
4. `set an alarm for 6:30 AM` → native alarm tool.
5. `set an alarm for 2:45 am and another one for 10:30 pm titled work` → two direct alarms.
6. `set a timer for 5 minutes` → direct timer.
7. `turn on my flashlight` / `switch flashlight off` → direct flashlight.
8. `turn it off` → Tier 2 flashlight context.
9. `call Mama` / `phone Abdulqader` → deterministic contact lookup + call after permissions.
10. `how ya doin bro` → normal conversation.
11. LLM tool output → no raw `<tool_call>` protocol visible to the user.
12. Side button/default assistant → translucent Sync AI Voice Mode; microphone is active only while that mode is running.
13. Exit Voice Mode → recognizer is released and app no longer maintains a recognition session.
14. Close/reopen app → active chat and prior errors/diagnostics are still present.
15. About → version, credits, diagnostics, and Copy action.
16. Personalization → all 8 accent colors + tone changes persist across app restarts.

## Baseline / verification
- Current branch head: `65333bce7acc90428a3162c98072dbf73a955866` (`Run deterministic routing tests in CI`).
- GitHub Actions **Build Sync//AI APK #393** completed successfully for the current branch head.
- The current branch contains the requested wake-word removal, voice-only microphone lifecycle, persistent chats, diagnostics, Personalization, About, 8-color accent selection, and deterministic routing regression tests.
- Remaining verification is primarily real-device behavior: microphone permission/session lifecycle, translucent assistant overlay/lock-screen path, persistent-chat migration on an installed build, and measured llama.cpp latency on the A16.
