# Sync AI overhaul status

This document is the persistent handoff tracker for the isolated `huge-overhaul-sync-ai-15-10-2026` branch. It is not a release-readiness claim. Device testing is intentionally deferred until the planned implementation pass is complete.

## Working rules

- Work through the queue below one item at a time, update this tracker after each meaningful step, and keep the handoff state in this file so a new chat can resume without guessing.
- Preserve working functionality. Make the smallest targeted changes that satisfy each requirement.
- Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the implementation pass is complete; then test features individually.
- Never claim an action succeeded unless its outcome is observable. Record unverified behavior honestly.

## Ordered implementation queue

1. **Built-in Sync recorder + recording-only foreground notification** — IMPLEMENTED IN SOURCE; build/device verification deferred until the full implementation pass. Provide an in-app recording tab/experience, explicit start/stop state, saved audio, microphone permission handling, and a foreground notification only while recording.
2. **Dedicated in-Sync camera** — IMPLEMENTED IN SOURCE; build/device verification deferred. Includes preview, front/rear switching, photo/video capture, MediaStore saving on Android 10+, app-specific storage on older Android, and a composer button LEFT of the message field. Existing external/system camera routing remains in place.
3. **Provider-aware music controls** — IMPLEMENTED IN SOURCE. Added a conservative provider registry: generic play/pause/next/previous remain best-effort Android media-key handoffs; shuffle, repeat, playlist management, and app-specific controls are not claimed unless a verified provider adapter supports them. No advanced provider adapters are verified yet.
4. **Default-app picker validation** — IMPLEMENTED IN SOURCE. Each category now prioritizes apps that resolve the relevant Android intent, while retaining a clearly labeled manual-override list of launchable apps.
5. **Speech and language settings** — IMPLEMENTED IN SOURCE. Added separate recognition/output language preferences (system default, English, Arabic, Russian, Spanish, French, German); Voice Mode requests the selected recognition locale, while TTS checks support and falls back to the device default. Actual engine support remains device/provider-dependent.
6. **Broader app integration and routing** — FIRST AUDIT PASS IMPLEMENTED. App launching now considers launchable packages and asks when two fuzzy matches are too close. Remaining integration coverage still needs a broader audit; existing explicit camera/recorder/music handoffs remain best-effort where Android does not report acceptance.
7. **Productivity audit/completion** — FIRST PASS COMPLETE. Existing reminders/alarms/timers retained; local tasks and notes added; calendar button opens an installed calendar app but does not create events inside Sync AI.
8. **Workspace/file context** — AUDITED; existing implementation retained. File Library supports filename/contextual reference resolution, ambiguity confirmation, bounded text excerpts, summaries via prefilled chat prompts, Workspace artifacts, and separate Workspace copies. PDFs/images/office formats still need format-specific extraction before model analysis.
9. **Developer-safe workflows** — IMPLEMENTED IN SOURCE. Workspace edits now require a review step showing changed line positions and before/after counts. Saving an artifact to File Library never offers replacement of an existing imported original; FileLibraryStore treats legacy replaceId inputs as source references and creates a uniquely named copy instead. Review summary is a quick line-position comparison, not a semantic diff.
10. **Memory/personalization and proactive assistance** — FIRST PASS COMPLETE. Existing explicit memory/personalization controls retained. Added task reminders only after a user taps Remind and chooses a time; Android 13+ notification permission is requested at that moment, not on launch. No unsolicited task notifications are scheduled.
11. **Implementation review and verification prep** — SOURCE REVIEW COMPLETE; build/device verification pending. No CI run was associated with the current branch commits, and no PR was opened just to trigger CI. The device-test checklist is below; run it only after the source/build review is accepted.

## Implemented first passes

- Natural-language app-launch phrasing is more tolerant of conversational verbs and wrapper words.
- Camera inventory can report Android-exposed front/rear/external camera IDs.
- Camera commands route to Android's photo, video, or ordinary camera intent. Front/rear selection is a best-effort hint because external camera apps may ignore it.
- Audio recording can be handed off to an installed system recorder when one handles Android's recording intent.
- Basic media transport sends play, pause, next, and previous key events to the active Android media session. Acceptance is not verified. Advanced music controls route through a conservative capability registry and are not claimed supported without a provider-specific adapter.
- The default-app picker now puts category-matched apps first (music/audio handlers, document handlers, camera handlers, recorder handlers) and retains other launchable apps as explicit manual overrides.
- App-launch routing now ignores non-launchable packages and asks the user when two fuzzy matches are nearly tied, rather than opening an ambiguous guess.
- Added local-only Tasks & Notes storage with task completion, deletion, notes, and a calendar-app handoff. Existing deterministic chat reminders, alarms, and timers remain the timed-notification path; the new screen does not claim calendar events were created.
- Audited Workspace/File Library: contextual file matching and ambiguity confirmation, bounded text injection, summary prompts, and separate Workspace artifacts already exist. Non-text PDF/image/office extraction remains unsupported.
- Developer safety: Workspace edits now require a pre-save review of changed line positions and counts. Existing File Library items can no longer be overwritten by saving a Workspace artifact; generated output is stored as a separate, uniquely named copy.
- Memory/personalization audit found existing explicit controls; task reminders now require a deliberate tap, a selected delay, and Android 13+ notification permission before scheduling.
- Voice Mode settings now expose separate speech-recognition and voice-output language choices. TTS checks whether the selected locale is supported and falls back to the system default; STT language support depends on Android’s active recognition provider.
- Settings > Default apps lets users choose preferred music, file manager, camera, and audio recorder apps from installed launchable apps. Default selection is UI-only, not a chat command.
- Added a dedicated Sync Recorder screen as a separate sidebar item immediately below Chat history (not inside Chat history and not under Settings). Recording requires an explicit tap and microphone permission; the foreground notification is intended only for an active recording and includes Stop. Audio is listed and playable in the recorder screen.
- Added a dedicated Sync Camera screen with photo/video mode, front/rear switching, and media saving; its camera button is left of the composer message field. Camera uses Android’s legacy Camera API for broad compatibility; verify preview, orientation, and device-specific video support during the deferred device-test pass.
- Natural-language routing can open selected music, file manager, camera, and recorder apps. App launching is not proof that playback, recording, or capture has started.

## Still outstanding

- Build and device verification are pending. The GitHub connector reported no workflow runs associated with the current branch commit; no pull request was opened merely to trigger CI.
- Provider-specific shuffle/repeat/playlist adapters are not implemented; advanced controls intentionally return a capability limitation instead of claiming success.
- STT support depends on the installed Android recognition provider. TTS fallback is source-implemented but needs device verification. PDF/image/office extraction is not implemented.
- Task reminders are opt-in, but Calendar events are not created inside Sync AI; the Calendar control only opens an installed calendar app.
- Recorder files are stored in app-specific external Music storage and can be played from the recorder screen; they are not yet published to the system media library.

## Verification rules

- Treat camera-facing extras and media-key dispatch as best-effort platform handoffs, not proof that capture or playback state changed.
- Keep implementation on this branch until the overhaul is complete and reviewed. Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the whole implementation pass is ready, then test features individually.

## Feature-by-feature device test checklist (not yet run)

- [ ] **Recorder navigation:** Sidebar > CHATS > Recorder; verify it is a separate row below Chat history and not inside Settings or the Chat history button.
- [ ] **Recorder permissions/state:** deny microphone permission, then grant it; confirm no recording starts automatically. Start recording and verify the foreground notification appears; stop from the screen and from the notification; confirm the notification disappears and the saved file plays from the list.
- [ ] **Camera placement:** verify the camera control is to the LEFT of the message field and opens the in-Sync camera.
- [ ] **Camera capture:** check preview, front/rear switching, photo capture, video start/stop, media appearing in Photos/Gallery on Android 10+, and interruption behavior when leaving the screen. Confirm failed captures do not leave empty media entries.
- [ ] **Default apps:** verify each category prioritizes intent-matched apps, manual override apps remain selectable, and saved labels do not include the “Manual override” prefix. Confirm selection is changed only in the in-app picker, not by chat commands.
- [ ] **Music controls:** verify play/pause/next/previous are described as best-effort; shuffle/repeat/playlist requests clearly report unsupported/unverified capability without claiming they changed.
- [ ] **Speech languages:** test System default, English, Arabic, and at least one unsupported TTS language; confirm unsupported TTS falls back and Voice Mode requests the selected STT language without crashing.
- [ ] **App routing:** test exact app names, known aliases, fuzzy names, and two near-equal names; ambiguous matches should ask which app instead of launching a guess.
- [ ] **Tasks & notes:** add, complete, delete, and relaunch to verify persistence. Schedule each reminder delay, deny/grant Android 13+ notification permission, and confirm reminders only schedule after explicit user action. Confirm Calendar opens an app but does not claim to create an event.
- [ ] **File Library / Workspace:** reference imported text by filename and contextual description; confirm ambiguous matches ask for confirmation and truncated/non-text files are not presented as complete model context.
- [ ] **Copy safety/review:** edit a Workspace artifact, inspect the review summary, cancel once, then save. Save to File Library when a same-named original exists and confirm the original remains unchanged and a separate copy is created.
- [ ] **Existing behavior/regression:** Hey Sync wake-word mic handoff, Voice Mode, chat composer Send/Stop states, status-bar/safe-area layout, keyboard/IME movement, reminders/alarms/timers, file import/export, and Workspace generation.
