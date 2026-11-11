# Air Mouse (Created by AXVin)

**Air Mouse** turns any Android 9.0+ (API 28+) phone into a native, zero-receiver **Bluetooth HID Multi-Controller** for Smart TVs (Android TV, Google TV, Amazon Fire TV, LG webOS, Samsung Tizen), PCs, and tablets. Because it uses the standard USB/Bluetooth Human Interface Device (HID) specification via Android's `BluetoothHidDevice` profile, your TV recognizes your phone as a hardware combo mouse, keyboard, media controller, and gamepad with zero software installation required on the TV.

---

## Complete Feature Guide

### 1. Bluetooth Connection & Multi-Device Management
- **Collapsible Connection Bar**: Displays real-time connection status (`Connected`, `Connecting`, `HID Ready`, `Disconnected`) at the top of the app and **automatically collapses** as soon as a device connects to maximize screen workspace.
- **Instant Multi-Device Switching**: Switch between multiple paired TVs, PCs, or streaming boxes in a single tap using the quick-switch device pills or the high-contrast device dropdown.
- **Connection Reset (`Reset Conn`)**: Re-registers the Bluetooth HID service and reconnects immediately if the host TV wakes from standby.
- **Confirmed Unpair (`Unpair`)**: Includes a safety confirmation dialog before permanently removing a paired device bond (`removeBond`) from your phone.

### 2. Air Mouse & Laptop Multi-Gesture Trackpad (`🖱️ Air Mouse`)
- **60Hz Gyroscope Air-Mouse**: Hold the **Hold to Move Air Pointer (Gyro)** trigger button—or toggle **Lock Air-Mouse**—and point your phone in the air like a laser pointer to move the TV cursor with sub-pixel residual smoothing and adjustable speed. Automatically falls back to **Accelerometer Tilt** on phones without a hardware gyroscope.
- **Enlarged Multi-Finger Gesture Trackpad (Auto Scroll-Lock)**:
  - **Automatic Scroll-Lock**: Touching the trackpad or scroll strip locks screen scrolling so the app UI never shifts vertically or horizontally.
  - **1-Finger Move & Tap**: Move the pointer smoothly and tap to left-click.
  - **2-Finger Tap**: Triggers a hardware **Right Click**.
  - **2-Finger Vertical & Horizontal Scroll**: Drag two fingers up/down for vertical mouse wheel scrolling or left/right for horizontal `AC Pan` scrolling.
  - **2-Finger Pinch-to-Zoom**: Pinch in or out with two fingers to send `Ctrl + Scroll Wheel` zoom steps for browsers, maps, and photos.
  - **3-Finger Laptop Swipes**:
    - **Swipe Left / Right**: Navigate **Back** (`AC Back`) and **Forward** (`AC Forward`).
    - **Swipe Up**: Open **App Switcher / Multitasking** (`Alt + Tab`).
    - **Swipe Down**: Return to **TV Home** (`AC Home`).
- **Hardware Volume Button Mouse Clicks**: Use your phone's physical **Volume Up** button as **Left Click** and **Volume Down** button as **Right Click** (supports holding to drag; can be inverted or disabled in **Settings**).
- **Inline Quick Keyboard**: Type directly into the inline bar to send characters immediately as they are typed, including `Backspace` and `Enter`.

### 3. Full Landscape PC Keyboard (`⌨️ Keyboard`)
- **Automatic Landscape Orientation & Fullscreen Mode**: Rotates automatically to widescreen landscape and includes a **`⛶ Fullscreen`** toggle that hides system bars and navigation chrome so the keyboard fills 100% of the screen.
- **Full 6-Row PC Layout**: Includes `ESC`, `F1–F12`, `DEL`, number row with symbols, full `QWERTY` block, `TAB`, `CAPS` (with active lock indicator), `ENTER`, `SPACE`, and dedicated `▲/◀/▼/▶` arrow cluster.
- **Sticky Shortcut Modifiers (`CTRL`, `WIN`, `ALT`, `SHIFT`)**: Tap any modifier key once to latch it (highlighted in your active theme's primary accent), then tap any letter or function key to send multi-key combinations such as `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Alt+F4`, or `Ctrl+Alt+Del` with one hand.
- **One-Tap Shortcut Bar**: Dedicated quick buttons for `Copy`, `Paste`, `Undo`, `Select All`, and `Alt+Tab`.

### 4. Media & TV Remote with Velocity-Accelerated Dials (`📺 Media`)
- **Volume Rotary Dial with Live Volume % Readout**: Spin clockwise/counter-clockwise to adjust TV volume with live `0%–100%` tracking displayed in the center of the dial (tap the center to re-sync to `50%`), paired with a dedicated **`🔊 Mute`** button directly above the dial.
- **Velocity-Accelerated Seek Jog Wheel**:
  - **Slow Rotation (`1x` Precision)**: Fine $7.2^\circ$ detents send single-step frame/5-second seeks for pinpoint timeline control.
  - **Fast Rotation (`2x`–`6x` High-Speed Scrubbing)**: Spinning the wheel faster scales the angular velocity multiplier up to `6x` per detent to scrub rapidly through long movies and streams.
  - **Switchable Seek Mode**: Toggle between **`Arrow ◀/▶`** (universal seek for YouTube, Netflix, Prime Video, Kodi, VLC) and **`Media ⏪/⏩`** (`Consumer Fast Forward / Rewind`), and tap the center of the wheel for `Play / Pause`.
- **Full TV Transport & App Keys**: Dedicated buttons for `Vol +/-`, `Mute`, `Prev`, `Play/Pause`, `Next`, `Seek -/+`, `Stop`, `Channel +/-`, `TV Home`, `TV Back`, `Voice / Search`, and `TV Browser`.

### 5. Widescreen Landscape Gamepad (`🎮 Gamepad`)
- **Composite 16-Button + Dual Stick HID Gamepad**: Registers as a standard Bluetooth HID Gamepad (`Report ID 4`) with 16 buttons, an 8-way Hat Switch D-Pad, and dual 8-bit `X/Y` and `Z/Rz` analog thumbsticks.
- **3 Controller Layout Presets**:
  - **Xbox 360**: Asymmetric left stick (top-left) and D-Pad (bottom-left) with color-coded `Y / X / B / A` buttons and `LT / LB / RB / RT` triggers.
  - **PS5 DualSense**: Symmetric bottom analog sticks, top-left D-Pad, `△ / □ / ○ / ✕` action cluster, and `L2 / L1 / R1 / R2` triggers.
  - **Mobile Pro**: High-contrast ergonomic touch layout styled with your active Material theme.
- **Gyro Tilt Steering**: Toggle **Gyro Steer** to map phone tilt to the left analog stick for racing and arcade games.
- **Immersive Fullscreen Toggle**: Tap **`⛶ Fullscreen`** in the top trigger bar to play across the entire screen and **`✕ Exit FS`** to leave fullscreen mode.

### 6. System Power & Host Controls (`⚡ System Power`)
- **Hardware HID System Controls (`Report ID 5`)**: Send `TV Power Toggle`, `System Sleep`, `System Wake Up`, and `System Power Off`.
- **Host Session Shortcuts**: One-tap buttons for `Media Sleep`, `Lock Screen (GUI + L)`, `Close Active App (Alt + F4)`, and `Switch App (Alt + Tab)`.

### 7. Material Design 3, Material You & Mihon Themes (`⚙️ Settings`)
- **Material You Dynamic Color (`Monet`)**: Automatically adapts to your Android 12+ wallpaper palette.
- **12 Built-In Themes Inspired by [Mihon](https://github.com/mihonapp/mihon)**: Switch live between **Mihon Default Blue**, **Catppuccin Mocha**, **Green Apple**, **Lavender**, **Midnight Dusk**, **Nord**, **Strawberry Daiquiri**, **Tako**, **Teal & Turquoise**, **Tidal Wave**, **Yin & Yang**, and **Yotsuba**.
- **Pure Black Dark Mode (AMOLED)**: Pitch-black `#000000` background option for OLED battery savings.
- **Granular Gesture & Button Controls**: High-visibility Material 3 checkboxes with `ON / OFF` badges to toggle individual multi-finger trackpad gestures and hardware Volume button mouse-click behavior.

---

## Project Structure

| File | Responsibility |
| :--- | :--- |
| `app/src/main/java/com/example/airmouse/MainActivity.kt` | Main UI scaffold, permanent bottom navigation footer, multi-gesture trackpad handler, landscape keyboard, gamepad, and settings screen |
| `app/src/main/java/com/example/airmouse/AppTheme.kt` | Material Design 3 / Material You (`Monet`) dynamic color resolver, [Mihon](https://github.com/mihonapp/mihon) color palettes, and AMOLED mode |
| `app/src/main/java/com/example/airmouse/AppSettings.kt` | `SharedPreferences` persistence for theme, AMOLED mode, trackpad gestures, and hardware volume button clicks |
| `app/src/main/java/com/example/airmouse/HidDescriptor.kt` | 5-report Composite USB/Bluetooth HID descriptor (Keyboard, Mouse, Consumer Media, Gamepad, System Power) and ASCII key map |
| `app/src/main/java/com/example/airmouse/BluetoothHidController.kt` | `BluetoothHidDevice` registration, instant multi-device switching, connection reset, device unpairing, and HID packet transmission |
| `app/src/main/java/com/example/airmouse/CustomViews.kt` | `LockableScrollView`, velocity-accelerated `VelocityRotaryDialView`, and `VirtualJoystickView` |

---

## Building `Air Mouse.apk`

1. **Cloud Build & Automatic Release via GitHub Actions**: Push the project to a GitHub repository; `.github/workflows/build-apk.yml` automatically compiles **`Air Mouse.apk`** and publishes a tagged GitHub Release matching `versionName` in `app/build.gradle.kts`.
2. **Local Build in Android Studio**: Open the project folder in Android Studio and select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## Support the Creator (AXVin)
- **Patreon**: [https://www.patreon.com/axvin](https://www.patreon.com/axvin)
- **Ko-fi**: [https://ko-fi.com/axvin](https://ko-fi.com/axvin)
