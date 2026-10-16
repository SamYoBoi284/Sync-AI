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
7. **Productivity audit/completion** — reminders, tasks, notes, calendar/schedule; reuse existing features instead of duplicating them.
8. **Workspace/file context** — AUDITED; existing implementation retained. File Library supports filename/contextual reference resolution, ambiguity confirmation, bounded text excerpts, summaries via prefilled chat prompts, Workspace artifacts, and separate Workspace copies. PDFs/images/office formats still need format-specific extraction before model analysis.
9. **Developer-safe workflows** — IMPLEMENTED IN SOURCE. Workspace edits now require a review step showing changed line positions and before/after counts. Saving an artifact to File Library never offers replacement of an existing imported original; FileLibraryStore treats legacy replaceId inputs as source references and creates a uniquely named copy instead. Review summary is a quick line-position comparison, not a semantic diff.
10. **Memory/personalization and proactive assistance** — NEXT. Audit current memory/personalization behavior and add useful, permission-respecting proactive notifications.
11. **Implementation review and verification prep** — source-level audit, build/CI where available, and a feature-by-feature device test checklist. Device testing starts only after implementation items are complete.

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
- Voice Mode settings now expose separate speech-recognition and voice-output language choices. TTS checks whether the selected locale is supported and falls back to the system default; STT language support depends on Android’s active recognition provider.
- Settings > Default apps lets users choose preferred music, file manager, camera, and audio recorder apps from installed launchable apps. Default selection is UI-only, not a chat command.
- Added a dedicated Sync Recorder screen as a separate sidebar item immediately below Chat history (not inside Chat history and not under Settings). Recording requires an explicit tap and microphone permission; the foreground notification is intended only for an active recording and includes Stop. Audio is listed and playable in the recorder screen.
- Added a dedicated Sync Camera screen with photo/video mode, front/rear switching, and media saving; its camera button is left of the composer message field. Camera uses Android’s legacy Camera API for broad compatibility; verify preview, orientation, and device-specific video support during the deferred device-test pass.
- Natural-language routing can open selected music, file manager, camera, and recorder apps. App launching is not proof that playback, recording, or capture has started.

## Still outstanding

- Automated source review and verification remain; recorder and camera are now implemented in source, but are not yet build- or device-verified.
- Verified provider-specific music adapters are still outstanding; current registry intentionally declines to claim advanced controls. Build/CI and device verification remain outstanding.
- STT/TTS engine capability evaluation beyond the conservative locale request/fallback path; additional languages and engine-specific validation remain outstanding.
- Broader app integration audit; deeper Workspace/file context, developer-safe copy-and-diff workflows, memory/personalization, and proactive notifications.
- Automated build/CI verification and end-to-end device verification. Recorder currently saves to app-specific external Music storage; recordings can be played from the recorder screen. Build and device verification for both recorder and camera is still pending.

## Verification rules

- Treat camera-facing extras and media-key dispatch as best-effort platform handoffs, not proof that capture or playback state changed.
- Keep implementation on this branch until the overhaul is complete and reviewed. Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the whole implementation pass is ready, then test features individually.
