# Sync AI — Animator Handoff

## Project

**App:** Sync AI  
**Platform:** Android  
**Package:** `com.sam.syncai`  
**Current UI implementation:** Programmatic Java views (not XML-based layouts)  
**Current working branch:** `sync-ai-toolcore-voice-history`

This document is for an AI animator / UI-motion designer who will modify the visual motion and transitions of Sync AI.

---

## 1. Files the animator should inspect first

### Primary UI
`app/src/main/java/com/sam/syncai/MainActivity.java`

This is the main application screen. It currently creates the UI programmatically.

It controls:
- top app header
- hamburger/menu button
- Sync AI title
- model/status card
- Import Model button
- Runtime button
- chat/message area
- message rendering
- composer
- text input
- Voice button
- Send button
- loading/progress state
- keyboard behavior
- chat scrolling
- dialogs such as models, runtime, diagnostics, About, errors
- opening the voice mode
- opening the sidebar
- edge-swipe gesture for the sidebar

### Sidebar / navigation
`app/src/main/java/com/sam/syncai/SideDashboard.java`

This is the **sliding left dashboard**.

Current structure:
- full-screen transparent overlay
- dark scrim
- rounded left-side panel
- main navigation screen
- nested Settings screen
- slide-in / slide-out animation
- close-on-scrim tap
- menu item interactions

### Android configuration
`app/src/main/AndroidManifest.xml`

Contains:
- app label: `Sync AI`
- launcher icon references
- voice interaction service
- voice session service
- activity configuration

### Visual resources
`app/src/main/res/drawable/`
`app/src/main/res/values/`
`app/src/main/res/mipmap-*/`

The launcher icon is already established and should **not be redesigned or replaced** unless explicitly requested.

---

## 2. Current main-screen layout

The current screen is roughly:

1. Header
   - hamburger button on the left
   - SYNC AI title

2. Status card
   - current model
   - runtime status

3. Control row
   - IMPORT MODEL
   - RUNTIME

4. Chat area
   - vertically scrolling conversation
   - user and assistant messages
   - streaming assistant response

5. Composer
   - multiline text input
   - VOICE button
   - SEND button

The UI uses dark surfaces and rounded cards/buttons.

---

## 3. Current sidebar

The sidebar is intentionally a **left sliding navigation drawer**, not a permanently visible sidebar.

### Main sidebar

Sections:

**CHATS**
- New chat
- Chat history

**WORKSPACE**
- Voice mode
- Workspace / Files

**SETTINGS**
- Settings

There is also:
- SYNC AI branding
- LOCAL AGENT subtitle
- explanatory local-data text
- CLOSE button

### Settings screen inside sidebar

Sections:

**ASSISTANT**
- Voice output

**WORKSPACE**
- Files / memory
- Local models
- Import model
- Runtime

**PERSONALIZATION**
- Accent colors + memory
- Assistant settings

**ABOUT**
- About & diagnostics

The Settings screen has a back button that returns to the main sidebar.

---

## 4. Existing motion implementation

### Sidebar opening

Current implementation:

- panel starts approximately `-340dp` offscreen
- scrim starts at alpha 0
- panel moves to x = 0
- scrim fades to alpha 1
- duration: approximately 260ms for panel
- scrim duration: approximately 180ms
- interpolator: `DecelerateInterpolator`

### Sidebar closing

Current implementation:

- scrim fades out
- panel moves back to approximately `-340dp`
- panel duration: approximately 220ms
- scrim duration: approximately 160ms
- interpolator: `DecelerateInterpolator`
- panel becomes invisible after the animation finishes

### Sidebar interaction

The main screen also supports a left-edge swipe:

- gesture begins within approximately 32dp of the left edge
- horizontal movement of approximately 56dp opens the sidebar
- horizontal movement must dominate vertical movement

### Scrim

The sidebar uses a translucent black scrim:

`Color.argb(150, 0, 0, 0)`

Tapping the scrim closes the dashboard.

---

## 5. Important animation opportunities

The current implementation is functional but intentionally simple. The animator may improve:

### Sidebar
- more natural spring/ease-out movement
- subtle overshoot or settle
- scrim fade synchronized with panel movement
- panel content stagger
- menu item entrance
- press feedback
- smoother Settings-to-Main transition
- smoother back transition
- gesture-following drawer motion if practical

### Main screen
Potential animation targets:
- app startup
- status/model card appearance
- chat message entrance
- assistant streaming response
- loading indicator
- button press feedback
- Send button state changes
- Voice button transition
- keyboard/composer movement
- model-loading state
- error dialogs
- About / Runtime dialogs

### Chat messages
Potential motion:
- subtle upward/fade entrance
- assistant response streaming without excessive movement
- smooth autoscroll
- diagnostic expansion if implemented visually

### Voice mode
Voice mode is a major animation opportunity.

Potential effects:
- transition from normal chat into voice mode
- microphone activation
- listening state
- speaking/TTS state
- return to chat
- audio-reactive visualizer if supported
- smooth overlay entrance/exit

Do not add a wake word. Voice input is intended to activate only when voice mode is explicitly used.

---

## 6. Constraints

The animator must preserve application behavior.

### Do NOT break:
- persistent chat history
- local model importing
- GGUF model handling
- local llama.cpp runtime
- deterministic Android tools
- tool permissions
- contextual follow-up commands
- memory/personalization
- Android default-assistant integration
- voice interaction service
- SpeechRecognizer/TTS behavior
- diagnostics
- model management
- settings functionality

### Do NOT:
- replace the launcher icon
- rename the app away from **Sync AI**
- add a wake word
- require cloud inference
- remove the existing chat system
- replace working Java functionality with fake prototype screens
- hardcode animations in a way that prevents accessibility or reduced-motion handling

---

## 7. Visual direction

The current visual language is:

- dark / near-black background
- dark charcoal/navy surfaces
- rounded corners
- bright readable text
- muted secondary text
- configurable accent color
- futuristic/local-agent aesthetic
- restrained UI rather than excessive decoration

(note from user: if you can add a fade out from nothing animation to both messages sent by user or ai when sent, that would be good, kinda like how the WhatsApp typing "..." appears and dots jump one by one)

The goal is **premium, fluid, futuristic motion**, not flashy motion for its own sake.

Motion should communicate hierarchy and state.

---

## 8. Recommended motion principles

Use:
- short, responsive transitions
- consistent easing
- subtle opacity + translation combinations
- spring motion where appropriate
- staggered navigation-item entrances
- clear pressed/active states
- animation durations generally around 150–350ms for normal UI
- longer motion only for major screen transitions

Avoid:
- slow 500ms+ animations for ordinary buttons
- excessive bounce
- constant glowing/pulsing
- animations that obscure text
- motion during every tiny state change
- blocking the UI while animation runs
- changing the existing app architecture without necessity

---

## 9. What the animator is allowed to change

The animator may modify:
- animation timing
- interpolators/easing
- translations
- alpha
- scale
- elevation/shadows where appropriate
- item stagger
- transition sequencing
- button press animations
- screen-transition animations
- loading animations
- visual state transitions

If structural UI changes are necessary, preserve the existing callbacks and functionality.

---

## 10. What should remain functional

The existing callbacks in `SideDashboard.Actions` are important.

They connect navigation to the real app:

- `newChat()`
- `chats()`
- `models()`
- `importModel()`
- `runtime()`
- `memory()`
- `personalization()`
- `tone()`
- `about()`
- `assistant()`
- `files()`
- `canvas()`
- `toggleVoiceOutput()`

Do not delete or disconnect these simply to make an animation prototype.

---

# AI Animator Prompt

You are modifying the **existing Sync AI Android application**, not creating a mockup.

Your task is to redesign and improve the app's **motion design, transitions, and interaction animations** while preserving all existing functionality.

First inspect:
- `MainActivity.java`
- `SideDashboard.java`
- relevant drawable/value resources
- voice-mode implementation
- existing settings/model/chat code

The UI is currently implemented primarily with programmatic Android Java views.

## Primary goal

Make Sync AI feel like a polished, premium, futuristic local AI assistant.

The motion should feel:
- smooth
- responsive
- intentional
- modern
- slightly futuristic
- restrained
- fast enough for everyday use

Do not turn it into an overly flashy sci-fi interface.

## Most important animation: sidebar

The left dashboard must remain a **sliding drawer**.

Current behavior:
- hamburger button opens it
- left-edge swipe doesn't open it, while it should, please make it do so
- scrim fades in
- drawer slides from the left
- scrim tap closes it
- CLOSE closes it
- Settings opens as a nested dashboard screen
- back returns to the main dashboard

Improve this significantly.

Desired result:
1. Drawer follows the user's gesture naturally when possible.
2. Opening should feel like the panel has physical weight.
3. Use a polished ease/spring rather than a generic linear slide.
4. Scrim should synchronize with drawer movement.
5. Navigation items should enter subtly after the drawer begins moving.
6. Do not make the animation slow.
7. Closing should feel equally polished.
8. Settings transition should feel like navigation within the same surface rather than an unrelated screen appearing.

## Main-screen motion

Add tasteful motion to:
- initial screen appearance
- status card
- buttons
- chat messages
- assistant response appearance
- loading states
- Voice button
- Send button
- model loading state
- error states
- dialogs where appropriate

Chat messages should enter subtly, preferably with a combination of small translation + opacity.

Do not animate every token of a streaming response independently.

## Voice mode

Improve the transition between chat mode and voice mode.

Voice mode should clearly communicate:
- entering voice mode
- listening
- processing
- speaking
- exiting voice mode

If an audio visualizer is added, it must not require a wake word.

Microphone access must remain limited to active voice interaction.

## Interaction feedback

Buttons should have subtle:
- pressed scale/alpha/elevation response
- release response
- disabled state
- loading state

Avoid excessive bouncing.

## Technical requirements

Preserve:
- all existing click listeners
- all existing callbacks
- chat persistence
- model importing
- GGUF model support
- local inference
- Android tools
- tool permissions
- memory
- personalization
- diagnostics
- voice assistant integration
- SpeechRecognizer
- TTS
- Android default assistant support

Do not replace real functionality with placeholder UI.

Do not add a wake word.

Do not change the launcher icon.

Keep the app name exactly:

**Sync AI**

## Animation architecture

Prefer reusable animation helpers rather than duplicating dozens of animation blocks.

Where practical, create reusable helpers for:
- fade/translate entrance
- button press
- drawer transition
- staggered children
- screen transitions
- loading state

Keep durations and interpolators centralized so the motion system can be tuned later.

Use Android-native animation APIs unless the existing project already contains an appropriate animation dependency.

Avoid adding a large third-party animation library solely for simple transitions.

## Accessibility

Animations must not:
- prevent interaction
- trap focus
- make text unreadable
- interfere with keyboard input
- interfere with edge gestures

If practical, respect Android reduced-motion/accessibility preferences.

## Final result

The finished app should feel like:

**"A real premium AI assistant with a deliberate motion system"**

rather than:

**"A normal Android app with random animations added to it."**

Before changing architecture, understand the existing code and preserve the real application behavior.


so long story short it should be a good visual revamp, not touching any code UNLESS its for the visual fix.
also one more thing cuz i cant find its specific section: the status bar of my phone blocks some stuff at the top, can u lower em down a liiiiittle bit?
