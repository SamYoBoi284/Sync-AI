# Sync//AI

Offline-first local AI shell for Android.

## Core idea

**Sync//AI = local model brain + deterministic Android hands.**

Device-known requests are handled directly by Android APIs instead of wasting local LLM inference on things the phone already knows.

## Current foundation

- GGUF model import through Android Storage Access Framework
- persistent on-device model registry
- native llama.cpp CPU inference
- streaming local chat
- deterministic Android tool core
- persistent multi-chat history
- per-request diagnostics
- Android default-assistant VoiceInteractionService
- translucent voice overlay
- SpeechRecognizer + TextToSpeech
- no wake-word / no background hotword listener
- Personalization with local Memory and 8 accent colors
- About & Diagnostics section
- GitHub Actions debug APK build

## Deterministic tools

Current direct tools cover:

- time/date/timezone
- battery/charging
- Wi-Fi/network
- Bluetooth
- media volume
- brightness
- RAM/storage
- device information
- flashlight
- app launching
- contact dialer handoff
- calculator
- timers
- alarms
- compound commands
- recent contextual follow-ups

## Chat history

Chats are stored privately on the device. Multiple conversations can be created, reopened, renamed, or deleted. Error diagnostics are stored with the relevant assistant message so failures survive an app restart.

Persistent history is separate from model context; only a bounded recent slice is sent to the local model.

## Voice mode

Voice mode is entered through Android's explicit assistant path.

There is no wake-word detector. Sync does not run a background speech listener.

The voice session:
- shows as a transparent/translucent overlay
- keeps the underlying app visible
- activates speech recognition only while the voice session is active
- routes deterministic device commands before the local model
- can speak responses with Android TTS
- can return to listening or exit explicitly

## Model diagnostics

Native inference diagnostics currently include:
- prompt token count
- context size
- CPU thread count
- prompt evaluation time
- generation time
- generated token count
- tokens/sec
- total inference time
- error stage/message

Use About & Diagnostics or the per-message diagnostics entry when investigating slow responses or generation failures.

## Personalization

The Personalization section contains:
- local editable/importable Memory
- eight Sync//AI accent choices:
  Purple, Blue, Cyan, Green, Lime, Orange, Red, Pink

## Ownership

Created By Sam  
صنعه حسام

## Models

Sync//AI does **not** store model binaries in GitHub.

Models are imported by the user on-device and copied into app-private storage.