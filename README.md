# TV Air-Mouse & Keyboard (Bluetooth HID)

Turns an Android 9+ (API 28+) phone into a native **Bluetooth HID Combo Air-Mouse & Keyboard** for your Smart TV (Android TV, Google TV, Fire TV, LG webOS, Samsung Tizen, or PC) with zero software required on the TV.

## Features
- **Gyroscope 60Hz Air-Mouse**: Hold the **Hold to Move Air Pointer** button (or toggle **Lock Air-Mouse On**) and point your phone like a laser pointer to move the TV cursor smoothly. Automatically falls back to accelerometer tilt if your phone lacks a hardware gyroscope.
- **Multi-Function Touchpad & Scroll Strip**: Slide one finger to move the pointer, tap to left-click, or slide along the right scroll strip to scroll pages vertically.
- **TV Remote & Navigation Keys**: Dedicated D-Pad (`UP`, `DOWN`, `LEFT`, `RIGHT`, `OK / ENTER`), `ESC / BACK`, `SPACE`, `TAB`, and `BACKSPACE` buttons.
- **Keyboard Input**: Type or paste text and tap **Send Text** (or press the soft keyboard's Send key) to type directly into TV search bars, browsers, and login fields.

---

## How to Get the `.apk` File (2 Easy Ways)

### Option 1: Build Automatically in the Cloud via GitHub Actions (No PC Setup Needed)
1. Create a new repository on [GitHub](https://github.com/new).
2. Extract `TvAirMouseHidProject.zip` and upload all files (including the `.github/workflows/build-apk.yml` folder) to your repository.
3. Open the **Actions** tab in your GitHub repository and click the **Build TV Air-Mouse APK** workflow run.
4. Once it finishes (~2 minutes), scroll down to **Artifacts** and download **`TvAirMouse-debug-apk`** (`app-debug.apk`) directly to your phone.

### Option 2: Build Locally in Android Studio
1. Extract `TvAirMouseHidProject.zip` and open the folder in **Android Studio**.
2. Wait for Gradle sync to finish, then select **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android phone.
