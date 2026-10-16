# Sync AI overhaul status

This document tracks the isolated `huge-overhaul-sync-ai-15-10-2026` branch. It is not a release-readiness claim. Device testing is intentionally deferred until the planned implementation pass is complete.

## Implemented first passes

- Natural-language app-launch phrasing is more tolerant of conversational verbs and wrapper words.
- Camera inventory can report Android-exposed front/rear/external camera IDs.
- Camera commands route to Android's photo, video, or ordinary camera intent. Front/rear selection is a best-effort hint because external camera apps may ignore it.
- Audio recording can be handed off to an installed system recorder when one handles Android's recording intent.
- Basic media transport sends play, pause, next, and previous key events to the active Android media session. Acceptance is not verified.
- Settings > Default apps lets users choose preferred music, file manager, camera, and audio recorder apps from installed launchable apps. Default selection is UI-only, not a chat command.
- Natural-language routing can open selected music, file manager, camera, and recorder apps. App launching is not proof that playback, recording, or capture has started.

## Still outstanding

- A dedicated in-Sync camera UI with preview, front/rear switching, photo/video capture, and saving media.
- A built-in Sync recorder experience and a recording-only foreground notification.
- A provider/capability abstraction for music apps, including explicit support detection for shuffle, repeat, playlists, and app-specific controls.
- Category-aware validation so the default-app picker can distinguish apps that support each role.
- Speech recognition and text-to-speech evaluation/configuration, multilingual routing, and language overrides.
- Audit and completion of productivity, Workspace/file context, developer-safe copy-and-diff workflows, memory/personalization, and proactive notifications.
- Automated build/CI verification and end-to-end device verification.

## Verification rules

- Do not report a requested action as completed unless the relevant Android/app outcome is observable.
- Treat camera-facing extras and media-key dispatch as best-effort platform handoffs, not proof that capture or playback state changed.
- Keep implementation on this branch until the overhaul is complete and reviewed. Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the whole implementation pass is ready, then test features individually.
