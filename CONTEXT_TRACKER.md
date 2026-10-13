### CI failure and follow-up — 2026-10-13
- Run `38334541554` initially failed at CMake configure because llama.cpp could not find `SPIRV-HeadersConfig.cmake`.
- Run `38334950000` still failed during configure because Android's CMake invocation did not discover the installed SPIR-V package. Follow-up commits `f65682a` and `a80fa5e` passed the package path explicitly.
- Run `38335294670` got past configure and generated Vulkan shaders, then failed compiling `ggml-vulkan.cpp`: `fatal error: 'vulkan/vulkan.hpp' file not found`. The Android NDK's Vulkan headers do not include the Vulkan-Hpp C++ wrapper needed by this llama.cpp backend.
- Commit `a323bbd5ebb34c29221c577b9fdaa686851cfc88` adds the official KhronosGroup/Vulkan-Headers v1.3.290 source to native CMake and adds its include directory to `ggml-vulkan` only. The next CI run must verify this dependency fix; further compiler errors may still surface.
- Keep the Vulkan path experimental and CPU-only as default. Do not merge or release until CI succeeds and the Galaxy A16 device checks are complete.

## 2026-10-13 — Experimental CPU + Vulkan inference (new work)

Branch: `feat/cpu-vulkan-hybrid-inference` (build and device verification pending)

- Read the full Library log `sync-ai-full-logs-gemma.txt` before changing inference. Gemma 3 4B Q4_K_M latest request: 262,231 ms total; 123,534 ms prompt evaluation; 137,763 ms generation; 26 generated tokens; 0.19 tokens/sec. Device snapshot: 823 MB available RAM out of 3,653 MB before the request.
- Current native code hardcoded `n_gpu_layers=0`; this branch enables the llama.cpp Vulkan backend and adds an opt-in persisted setting: 0 layers (CPU-only default), 2 GPU layers, or 4 GPU layers.
- Native loader checks for a registered Vulkan backend/device. If no device is detected, it keeps CPU-only inference. If Vulkan model loading fails, it retries once on CPU. Diagnostics include requested/active GPU layers, device summary, and fallback status.
- Context cap is memory-aware: 2,048 tokens below 5 GiB reported physical RAM; 4,096 tokens at or above that threshold. This should reduce memory pressure on the 4 GB Galaxy A16; performance benefit must be measured.
- CI now installs `glslc` and `spirv-headers` for the Vulkan backend build. The first successful native build and a real-device Vulkan enumeration are required before calling GPU acceleration supported.
- Keep CPU-only as the default, preserve all existing flows, and do not merge/release before CI review and phone testing.

---


> Living handoff/context document for the Sync AI project.
> Updated: 2026-10-13
> Current feature branch: `feat/cpu-vulkan-hybrid-inference` (build and phone verification pending)
> Stable development base: `fix/generation-timer-alarm-export`
> This tracker records implemented work and known verification status. Source code / CI remains authoritative.

## 2026-10-13 — Current File Library work (in progress)

Branch: `feat/file-library-context-generation-1024`

- PR #18: [Fix file import routing and add persistent File Library](https://github.com/SamYoBoi284/Sync-AI/pull/18) (merged into `fix/generation-timer-alarm-export` on 2026-10-12).
- Drawer → Settings → File Library now uses its own activity/import flow; it must not reuse the Memory import result code. The existing Personalization → Import Memory File action remains separate.
- `FileLibraryStore` keeps user-imported file copies and metadata inside app-private storage, detects exact-content duplicates by SHA-256, permits import/export/delete, and previews supported text files.
- `MainActivity.buildModelContext` attaches only specifically mentioned (or clearly referenced in an immediate follow-up) library files, up to two items and a bounded text excerpt. It treats file contents as user data, not instructions, and tells the model not to claim a truncated excerpt is a full file.
- Unambiguous small single-file text edits (up to 3,000 bytes) route to Workspace with the original filename; saving back requires an explicit Replace or Save as Copy action. Multiple file mentions are available for analysis, but full-file replacement is one source file at a time. Original files are not silently overwritten.
- Artifact generation is now configured for 1,024 tokens; normal chat remains at 512. The native hard ceiling is already 1,024. 4 GB device memory/latency needs physical testing.
- Workspace artifacts now have an **EDIT SOURCE** action that saves changes in place. Basic completeness checks can keep an edited source marked failed if it still appears incomplete.
- Failed/incomplete artifacts expose **CONTINUE WITH SYNC**. It passes the partial source to the model, expects only the continuation fragment in a fenced block, appends that fragment to the same artifact, and rechecks completeness. If the model gives no usable fenced fragment, the existing artifact is preserved unchanged. Repeated continuation is available if it still appears incomplete.
- Workspace updates reload persisted artifacts before modifying them to avoid stale activity instances overwriting edits.
- Binary files (PDF/office/images/etc.) can be stored and exported but their content is not extracted for local-model context in this initial iteration.
- Do not publish a release. Run CI, inspect source diffs/build failures, and test File Library/import, text-file context/edit/save paths, old Memory import, Workspace, updater, voice/wake-word, model import, and SAF export flows on the Galaxy A16.


- Repository: `SamYoBoi284/Sync-AI`
- Android offline-first local AI assistant.
- Core architecture: **local model = brain, deterministic Android tools = hands**.
- Android Java + C++ JNI + llama.cpp, CPU-first, arm64-v8a.
- GGUF model files remain on-device and are not committed to GitHub.
- App name is **Sync AI**; the old `Sync//AI` naming is no longer the desired user-facing name.

## Project

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

### Wake word — opt-in Vosk implementation added in this branch

- The new `SyncAssistantService` is an opt-in foreground microphone service using Vosk's local `vosk-model-small-en-us-0.15` model and strict `["hey sync"]` grammar.
- Wake-word listening is disabled by default and is controlled from Settings → Voice & Wake Word. A visible ongoing notification includes a Stop action.
- `VoskModelInstaller` imports the ZIP through Android's document picker, validates expected model files, extracts into app-private storage, and preserves an existing installed model if replacement fails.
- The service does not initialize the GGUF/LLM while idle. On wake, it stops the Vosk recognizer before handing off through `Intent.ACTION_ASSIST`; voice-mode start/finish broadcasts pause and resume the wake listener to avoid microphone contention.
- The Vosk ZIP is **not bundled in the APK or committed as a binary in this branch**; it must be imported once on-device. The archive supplied in ChatGPT Library was inspected and has the expected `vosk-model-small-en-us-0.15/` root structure.
- This is source/CI work pending a successful build and real-device validation. Android microphone foreground-service and background activity launch rules may affect wake-to-overlay handoff; no auto-start-after-reboot behavior is added.

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


## 2026-10-13 — PR #19 Vulkan build follow-up

- Latest failed Actions run before this fix: #732, run ID `38336103642`, commit `a323bbd5ebb34c29221c577b9fdaa686851cfc88`.
- Build progressed further: CMake configuration succeeded and the compiler found `vulkan/vulkan.hpp`; compilation then failed at `ggml-vulkan.cpp:48` with `fatal error: 'spirv/unified1/spirv.hpp' file not found`.
- Root cause: the host CI SPIRV-Headers package config does not ensure that its include directory reaches the Android cross-compiled `ggml-vulkan` target.
- Fix pushed to PR branch in commit `390cf4673b4eee04020b96201f71a237d182dd76`: fetch Khronos SPIRV-Headers at `vulkan-sdk-1.3.290.0` and add `${spirv_headers_SOURCE_DIR}/include` to `ggml-vulkan` only.
- Next step: inspect CI for commit `390cf4673b4eee04020b96201f71a237d182dd76`. If it fails again, inspect the first actual compiler/CMake error rather than treating the final Gradle stack trace as the cause. No merge/release; device validation is still pending.


## 2026-10-13 — PR #19 Android Vulkan link fix

- Latest failed Actions run: #737, ID `38336543038`, at commit `583863baa48c2ac6d85aff88b81c356f0cc89f31`.
- Header issues are resolved in that run: the Vulkan backend compiled through the final link step. The actual link failure is `ld.lld: error: undefined symbol: vkGetPhysicalDeviceFeatures2`, referenced from `ggml-vulkan.cpp`.
- This is an Android Vulkan loader/linkage issue: Vulkan 1.1 entry points should be resolved through Vulkan-Hpp's initialized dynamic dispatcher instead of directly linked from the NDK's `libvulkan.so`.
- Added patch `app/src/main/cpp/patches/llama-vulkan-android-dispatch.patch` and set the llama.cpp FetchContent `PATCH_COMMAND` in `app/src/main/cpp/CMakeLists.txt` to apply it to the pinned source.
- Fix commit: `c021d4bf268daa2fcb2f5d039e8bf0f0887515bd`. Next: inspect its Actions run and validate the patch actually applies. If another failure occurs, identify the first real error. No merge/release; device testing remains pending.


## 2026-10-13 — PR #19 follow-up: patch all Vulkan 1.1 calls

- Actions run #744 (ID `38338028422`) successfully applied the source-matching patch and compiled the Vulkan backend, but linking still failed with `undefined symbol: vkGetPhysicalDeviceFeatures2` at both lines 7636 and 20098 in the pinned `ggml-vulkan.cpp`.
- Root cause: the prior script changed only the first matching call; a second direct call remained and was still emitted as a link-time reference.
- Updated `patch_llama_vulkan_android.py` to replace every matching raw `vkGetPhysicalDeviceFeatures2(device->physical_device, &device_features2);` call, and to fail safely if none match. Fix commit: `53b65b7c33bb3dacfcad8deb0fe388595579a7dd`.
- Await CI for the updated head. APK build and on-device Vulkan behavior remain unverified. CPU-only default and fallback are preserved; PR #19 remains draft; do not merge/release without Sam's approval.


## 2026-10-13 — PR #19 follow-up: ensure Vulkan patch runs with cached native sources

- Actions run #747 (ID `38338813787`, head `ddd4a44d`) failed again at the `libggml-vulkan.so` link step with `undefined symbol: vkGetPhysicalDeviceFeatures2` at both `ggml-vulkan.cpp:7636` and `:20098`. The output showed the same raw-symbol link failure, so the previous all-call replacement was not reflected in the compiled source.
- Likely cause: CMake's `FetchContent` `PATCH_COMMAND` is skipped when Gradle/CMake reuses an already-populated llama.cpp source directory. This is a diagnosis based on the unchanged linker references; it still needs confirmation from the next build.
- Commits `4de7490` and `fd86aad` make the patch script idempotent and explicitly apply/verify it after `FetchContent_MakeAvailable(llama)` on every CMake configure, including cached-source builds.
- Await CI for the new head `fd86aad110421ac5b1fd55d41cac398bfc87bebb`. No APK/device verification yet. CPU-only remains default; PR #19 stays draft; no merge or release without Sam's approval.


## 2026-10-13 — PR #19 follow-up: force rebuild of patched Vulkan source

- Actions run #751 (ID `38340037015`, head `7e6b0ad4`) failed again with the same `undefined symbol: vkGetPhysicalDeviceFeatures2` at `ggml-vulkan.cpp:7636` and `:20098` while linking `libggml-vulkan.so`.
- The latest build still emitted references from the original raw calls. The current working theory is that a cached native object was not rebuilt after the source patch; this is not yet proven.
- Commit `13552c6f8a757d1d423a98e4b19b74bbcd45844e` updates the patch script to verify no raw calls remain and touch `ggml-vulkan.cpp` after patching, forcing Ninja to recompile it even when the source is already patched.
- Next checkpoint: inspect the new CI log for the script's verification message and whether `ggml-vulkan.cpp.o` is rebuilt. If the symbol remains after a confirmed rebuild, revise the dispatcher implementation instead of repeating this cache hypothesis. APK and device tests remain pending; CPU-only remains default; PR #19 stays draft.
