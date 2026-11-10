# Air Mouse (Created by AXVin)

Turns an Android 9+ (API 28+) phone into a native **Bluetooth HID Combo Air-Mouse & Keyboard** for your Smart TV (Android TV, Google TV, Fire TV, LG webOS, Samsung Tizen, or PC) with zero software required on the TV.

## What's New in v2.0 (by AXVin)
- **Trackpad Auto Scroll-Lock Mode**: Touching the trackpad or scroll strip automatically locks vertical screen scrolling (`LockableScrollView` + `requestDisallowInterceptTouchEvent`) so the app UI never shifts up or down while moving your fingers.
- **Real-Time Live Keyboard**: Every character, Space, Backspace, and Enter key is sent immediately to the TV as you type—no "Send Text" button required.
- **Custom "Air Mouse" Logo & Branding**: Includes the custom **Air Mouse** launcher icon, in-app header logo, **Created by AXVin** attribution badge, and outputs **`Air Mouse.apk`**.
- **Gyroscope 60Hz Air-Mouse**: Hold the **Hold to Move Air Pointer** button (or toggle **Lock Air-Mouse On**) to move the TV cursor with your phone's gyroscope (with automatic accelerometer fallback).

---

## How to Get `Air Mouse.apk`

### Option 1: Build Automatically in the Cloud via GitHub Actions (No PC Setup Needed)
1. Create a new repository on [GitHub](https://github.com/new).
2. Extract `AirMouseProject.zip` and upload all files (including `.github/workflows/build-apk.yml`) to your repository.
3. Open the **Actions** tab in your GitHub repository and select the **Build Air Mouse APK** workflow run.
4. Once completed, download **`Air-Mouse-APK`** (containing **`Air Mouse.apk`**) under **Artifacts**.

### Option 2: Build Locally in Android Studio
1. Extract `AirMouseProject.zip` and open the folder in **Android Studio**.
2. Select **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** to produce **`Air Mouse-debug.apk`**.
