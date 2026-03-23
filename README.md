# AutoSugar

AutoSugar is an unofficial Android Auto application designed to monitor Nightscout CGM data. It allows users to track blood glucose levels from one or multiple sources (e.g., self and children) directly on their vehicle's head unit.

## Features
* **N-Source Support:** Monitor a single source or switch between multiple Nightscout instances seamlessly.
* **Multilingual UI:** Initial support for English and German, designed for easy localization (i18n).
* **Glanceable Data:** Displays current glucose values, trend arrows, and deltas using the `PaneTemplate`.
* **Background Updates:** Automated data polling while driving to ensure real-time accuracy.

## Prerequisites & Installation
1. **Android Studio:** Required to compile the APK from source.
2. **Developer Mode:** - Open the Android Auto app on your smartphone.
   - Tap the "Version" footer 10 times to unlock Developer Settings.
   - In the Developer Settings (top-right menu), enable **"Unknown sources"**.
3. **Sideloading:** Install the compiled APK directly via ADB or file transfer.

## Technical Beleg (Google Documentation)
According to the official Android for Cars documentation:
- **Category:** The app uses the `IOT` category (`androidx.car.app.category.IOT`), which is permitted to display information from connected sensors/services.
- **Testing:** Local testing of sideloaded apps is supported via the "Unknown sources" toggle ([developer.android.com/training/cars/testing](https://developer.android.com/training/cars/testing)).
