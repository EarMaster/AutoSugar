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

## Verifying background alerts

Alerts must fire while AutoSugar is *not* the visible car app — that is the whole point of them, and
it is the one thing the DHU makes easy to get wrong, because the app is on screen the entire time
you are looking at it.

1. Open AutoSugar on the DHU once, with at least one alert-enabled profile. This is what starts
   `GlucoseMonitorService`; confirm it is running:

   ```bash
   adb shell dumpsys activity services de.autosugar | grep -i "GlucoseMonitorService\|isForeground"
   ```

   A silent "Monitoring glucose" notification also appears on the phone.

2. Switch the DHU to another app (Maps, or the launcher) so AutoSugar leaves the screen. The service
   must stay in the list above — it is no longer tied to the session.

3. Drive the reading past a threshold (point the profile at a test Nightscout instance, or lower
   `bgHigh` in Nightscout) and confirm the alert still appears as a heads-up notification on the car
   screen while another app is in the foreground.

4. Disconnect the head unit (quit the DHU, unplug the phone). The service must stop within a few
   seconds — re-run the `dumpsys` command and confirm it is gone, along with the phone notification.

Note that Android 15+ caps a `dataSync` foreground service at six hours per 24-hour window. On
timeout the app posts a "glucose monitoring stopped" alert and stops the service; reopening
AutoSugar on the car screen starts it again.
