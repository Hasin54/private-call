# Global Call Installation Guide

This guide describes how to import, compile, and run the **Global Call** real-time WebRTC audio/video communication app on standard Android Studio or emulator platforms.

---

## Technical Prerequisites

To compile and launch the application, ensure your workspace meets the following minimum standards:
1. **Operating System:** Windows, macOS, or Linux.
2. **Android Studio:** Bumblebee (2021.1) or newer (Ladybug/Meadowlark recommended).
3. **Android SDK Platform:** Android API 36 / 35 (minSDK 24, targetSDK 36).
4. **Gradle Version:** Gradle 8.5+ with Kotlin DSL support.
5. **Internet Access:** Necessary to download WebRTC & Firebase dependencies.

---

## Step-by-Step Installation

### Step 1: Open the Project in Android Studio
1. Select **File** -> **New** -> **Import Project...** or click **Open an Existing Project** on the welcome screen.
2. Navigate to the root directory containing the project sources.
3. Confirm the selection. Gradle will automatically sync and fetch files.

### Step 2: Configure Local Credentials
If you are running the app without active Google Services configuration files yet:
1. **Notice:** This app is designed with a **fail-safe dual core repository**. If `google-services.json` is missing or Firebase is not initialized, the app automatically transitions into **Mock Signal Mode** instead of crashing.
2. To test calling immediately, launch the app.
3. Sign in using the default testing credentials:
   - **Email:** `test@globalcall.com`
   - **Password:** *Any characters* (e.g., `123456`)
4. You can also sign up a new account using the interactive custom registration form containing scrollable avatar selector presets.

### Step 3: Fast Local Execution
To run on your connected device or emulation container:
1. Click the **Run** button (green play icon) in the Android Studio top toolbar, or press `Shift + F10` (`Control + R` on macOS).
2. Choose either a hardware device with Developer Options enabled, or a Virtual Device (AVD).
3. *Recommendation:* If using the emulator, make sure microphone and front-camera accesses are enabled inside the developer settings! The app includes camera try-catches to run gracefully even on emulators that lack camera streams.

---

## In-App Operations Guide

Once the app is running:
1. **Sign Up:** Register your identity with a preferred avatar from our select lists.
2. **Contact search & Add Friends:** Click the floating action button in "Contacts", search `sophia@globalcall.com` or `alex@globalcall.com`, and add them to your roster.
3. **Direct Chat:** Select their contact card to start an encrypted text chat.
4. **Trigger calls:** Note the two actions (Voice Call 📞 and Video Call 📹) in the conversation header bar. Tap them to trigger a high-fidelity WebRTC call.
5. **Accept Incoming Calls:** When a mock remote peer triggers a ringing event after a simulation delay, an incoming call panel arches across your screen with **Decline** and **Accept** triggers! Tap Accept to establish peer-to-peer tunnels.
