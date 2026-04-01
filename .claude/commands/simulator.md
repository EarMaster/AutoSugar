---
description: Launch the Android Auto Desktop Head Unit (DHU) simulator, guiding through all required setup steps.
allowed-tools: Bash(adb devices:*), Bash(adb forward:*), Bash(./gradlew assembleDebug:*), Bash(adb install:*), Bash(echo:*), AskUserQuestion
model: haiku
---

Guide the user through launching the Android Auto Desktop Head Unit (DHU) simulator step by step.

## Steps

### 1. Check device connection

Run `adb devices` and inspect the output.

- If no device is listed (or only `List of devices attached` with nothing below), tell the user no device is connected and ask them to connect via USB or enable wireless debugging, then use `AskUserQuestion` to ask when they are ready to continue.
- If a device is listed, confirm to the user which device was found and proceed.

### 2. Build and install the debug APK

Run:
```
./gradlew assembleDebug
```

If the build fails, show the error and stop. Do not proceed.

After a successful build, run:
```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If installation fails, show the error and stop.

### 3. Enable Development server in Android Auto

This step cannot be automated. Use `AskUserQuestion` to ask the user to confirm they have completed the following manual steps:

> **On your Android device:**
> 1. Open the **Android Auto** app.
> 2. Tap the version number in the footer **10 times** to unlock Developer Settings.
> 3. Tap the three-dot menu → **Developer settings**.
> 4. Enable **"Start development server"** (or "Unknown sources" if shown).

Ask: "Have you enabled the Development server in Android Auto on your device?"

Only continue after the user confirms.

### 4. Forward the DHU port

Run:
```
adb forward tcp:5277 tcp:5277
```

If this fails, show the error and stop.

Confirm to the user that port 5277 has been forwarded.

### 5. Launch the Desktop Head Unit

Tell the user you are launching the DHU now, then print the command they should run themselves (the DHU is an interactive GUI and cannot be launched as a background process via this command):

```
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

Instruct the user to run this in a terminal. If `$ANDROID_HOME` is not set, suggest the common paths:
- macOS (Android Studio default): `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`
- Linux: `~/Android/Sdk/extras/google/auto/desktop-head-unit`

Let the user know: once the DHU window opens, Android Auto should connect automatically. If it does not connect within a few seconds, try tapping the Android Auto notification on the device.
