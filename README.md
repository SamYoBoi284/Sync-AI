# Sync//AI

Offline-first local AI shell for Android.

## Core idea

Sync//AI does **not** store model files in GitHub.

Models are imported by the user on-device through Android's file picker and copied into app-private storage. The app keeps a local model registry so imported models can be loaded, switched, inspected, and removed.

## Current foundation

- Model import through Android Storage Access Framework
- Local model registry with persistent metadata
- Model switch/remove UI
- Format-agnostic local model backend interface
- Mock backend so the app is usable before a native inference runtime is added
- Chat shell with streaming-ready backend API
- Tool registry foundation for Android actions
- Model files excluded from Git via `.gitignore`
- GitHub Actions debug APK build

## Planned runtime layer

The UI and model-management layer is intentionally independent from the inference engine. A native local inference backend can be plugged in later without changing the model-management UX.

