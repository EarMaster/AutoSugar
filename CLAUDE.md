# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AutoSugar is an unofficial Android Auto app for monitoring Nightscout CGM (blood glucose) data while driving.

## Key Documents

- **`AGENTS.md`** — source of truth for role definitions and implementation responsibilities
- **`docs/adr/`** — architectural decision records; consult before making structural changes

## Build

Android project — build via Android Studio or Gradle wrapper once implementation begins:

```bash
./gradlew assembleDebug
./gradlew test
adb install app/build/outputs/apk/debug/app-debug.apk
```

Testing on Android Auto requires enabling **Unknown sources** in the Android Auto app (tap "Version" footer 10 times → Developer Settings).

## Target API Level

The app must always target the **newest stable Android API level** (currently API 37 /
Android 17) rather than waiting for Google Play's compliance deadline — Play reviews for
this app are slow, and a late bump risks missing the deadline. Check for a newer API level
at the start of every release; see the *Maintenance Policy: Target API Level* section in
`AGENTS.md` for the full checklist.
