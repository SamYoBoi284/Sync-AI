# Sync AI overhaul status

This document is the persistent handoff tracker for the isolated `huge-overhaul-sync-ai-15-10-2026` branch. It is not a release-readiness claim. Device testing is intentionally deferred until the planned implementation pass is complete.

## Working rules

- Work through the queue below one item at a time, update this tracker after each meaningful step, and keep the handoff state in this file so a new chat can resume without guessing.
- Preserve working functionality. Make the smallest targeted changes that satisfy each requirement.
- Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the implementation pass is complete; then test features individually.
- Never claim an action succeeded unless its outcome is observable. Record unverified behavior honestly.

## Ordered implementation queue

1. **Built-in Sync recorder + recording-only foreground notification** — NEXT. Provide an in-app recording tab/experience, explicit start/stop state, saved audio, microphone permission handling, and a foreground notification only while recording.
2. **Dedicated in-Sync camera** — preview, front/rear switching, photo/video capture, and saving media, while preserving external/system camera handoffs.
3. **Provider-aware music controls** — capability-aware app/provider layer; only advertise shuffle, repeat, playlists, or app-specific controls where supported.
4. **Default-app picker validation** — improve role/category matching beyond the current all-launchable-apps list without removing an intentional override.
5. **Speech and language settings** — evaluate STT options and configure recognition/TTS, multilingual behavior, and language overrides based on actual engine capability.
6. **Broader app integration and routing** — audit installed-app/provider routing and make ambiguous requests ask rather than guess.
7. **Productivity audit/completion** — reminders, tasks, notes, calendar/schedule; reuse existing features instead of duplicating them.
8. **Workspace/file context** — contextual retrieval, summaries, and safe editing of imported files.
9. **Developer-safe workflows** — work on copies, show diffs/review, never modify the original file in place.
10. **Memory/personalization and proactive assistance** — audit current behavior and add useful, permission-respecting proactive notifications.
11. **Implementation review and verification prep** — source-level audit, build/CI where available, and a feature-by-feature device test checklist. Device testing starts only after implementation items are complete.

## Implemented first passes

- Natural-language app-launch phrasing is more tolerant of conversational verbs and wrapper words.
- Camera inventory can report Android-exposed front/rear/external camera IDs.
- Camera commands route to Android's photo, video, or ordinary camera intent. Front/rear selection is a best-effort hint because external camera apps may ignore it.
- Audio recording can be handed off to an installed system recorder when one handles Android's recording intent.
- Basic media transport sends play, pause, next, and previous key events to the active Android media session. Acceptance is not verified.
- Settings > Default apps lets users choose preferred music, file manager, camera, and audio recorder apps from installed launchable apps. Default selection is UI-only, not a chat command.
- Natural-language routing can open selected music, file manager, camera, and recorder apps. App launching is not proof that playback, recording, or capture has started.

## Still outstanding

- Dedicated in-Sync camera and built-in Sync recorder with recording-only foreground notification.
- Music provider/capability abstraction and category-aware default-app validation.
- STT/TTS evaluation/configuration, multilingual routing, and language overrides.
- Audit/completion of productivity, Workspace/file context, developer-safe copy-and-diff workflows, memory/personalization, and proactive notifications.
- Automated build/CI verification and end-to-end device verification.

## Verification rules

- Treat camera-facing extras and media-key dispatch as best-effort platform handoffs, not proof that capture or playback state changed.
- Keep implementation on this branch until the overhaul is complete and reviewed. Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the whole implementation pass is ready, then test features individually.
