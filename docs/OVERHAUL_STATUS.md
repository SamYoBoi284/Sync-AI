# Sync AI overhaul status

This document tracks the isolated `huge-overhaul-sync-ai-15-10-2026` branch. It is not a release-readiness claim. Device testing is intentionally deferred until the planned implementation pass is complete.

## Implemented first passes

- Natural-language app-launch phrasing is more tolerant of conversational verbs and wrapper words.
- Camera inventory can report Android-exposed front/rear/external camera IDs.
- Camera commands route to Android's photo, video, or ordinary camera intent. Front/rear selection is a best-effort hint because external camera apps may ignore it.
- Audio recording can be handed off to an installed system recorder when one handles Android's recording intent.
- Basic media transport sends play, pause, next, and previous key events to the active Android media session. Acceptance is not verified.
- A Settings > Default apps screen lets users choose preferred music, file manager, camera, and audio recorder apps from installed launchable apps. Defaults are selected in the UI, not by a chat command.
- Natural-language routing can open the selected music player, file manager, camera app, or recorder. The camera/recorder app launch is best-effort; it does not prove capture/recording has started.

## Still outstanding

- A dedicated in-Sync camera UI with preview, front/rear switching, photo/video capture, and saving media.
- A built-in Sync recorder experience and a recording-only foreground notification.
- A provider/capability abstraction for music apps, including explicit support detection for shuffle, repeat, playlists, and app-specific controls.
- Provider/capability-aware filtering for default-app choices, including validating that each selected app actually supports its category.
- Speech recognition and text-to-speech evaluation/configuration, multilingual routing, and language overrides.
- Audit and completion of productivity, Workspace/file context, developer-safe copy-and-diff workflows, memory/personalization, and proactive notifications.
- Automated build/CI verification and end-to-end device verification.

## Verification rules

- Do not report a requested action as completed unless the relevant Android/app outcome is observable.
- Treat camera-facing extras and media-key dispatch as best-effort platform handoffs, not proof that capture or playback state changed.
- Keep implementation on this branch until the overhaul is complete and reviewed. Do not merge, tag, or release without explicit approval.
- Defer on-device testing until the whole implementation pass is ready, then test features individually.
