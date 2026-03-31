# Testing with Android Auto Desktop Head Unit (DHU)

## Prerequisites

- Physical Android phone (emulators do not support Android Auto)
- Android SDK installed (`$ANDROID_HOME` set to `~/Library/Android/sdk`)
- ADB connected to your phone (USB or WiFi)

## One-time Setup

### 1. Enable Developer Mode in Android Auto

On your phone, open the **Android Auto** app:
- Tap the **version number** in the footer **10 times**
- Go to **Developer Settings** → enable **Unknown sources**

### 2. Set ANDROID_HOME (if not already set)

Add to `~/.zshrc`:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

Then reload: `source ~/.zshrc`

## Running a Test Session

### 1. Install the debug APK

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Start Head Unit Server on your phone

In the Android Auto app: tap the **three-dot menu** → **Head Unit Server**

### 3. Forward the ADB port

```bash
adb forward tcp:5277 tcp:5277
```

If connected via WiFi, specify the device ID:

```bash
adb -s <device-id> forward tcp:5277 tcp:5277
```

> Your device ID can be found via `adb devices`.

### 4. Launch the DHU

```bash
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```
