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
