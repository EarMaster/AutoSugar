# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Monochrome adaptive icon (`ic_launcher_monochrome.xml`) for Android 13+ themed icon support
- `<monochrome>` layer added to `ic_launcher.xml` adaptive icon definition

### Changed
- Adaptive icon background color updated from `#F4EFE4` to `#302919`

### Added
- Fetch glucose target thresholds (`bgTargetBottom`/`bgTargetTop`) from Nightscout `/api/v1/status.json` and use them for the graph's target band and dynamic Y-axis scaling
- `StatusDto`, `SettingsDto`, `ThresholdsDto` DTOs for Nightscout status response
- `getStatus()` endpoint in `NightscoutApi` and `getTargetRange()` in `NightscoutRepository`

### Changed
- Graph Y-axis now scales dynamically to fit actual readings plus the target range (with padding), instead of a fixed 40–400 mg/dL range
- Time labels moved to the bottom of the graph; grid lines skip values too close to chart edges

### Added
- Glucose history graph in Car UI: 3-hour bitmap rendered via `Pane.setImage()` with target range band, dotted grid lines, drop-pin value labels at 20-min marks, and half-hour time labels
- Reading age ("X min ago") and received timestamp shown as secondary row in Car UI
- Expanded profile icon set: male, female, boy, girl, baby, elderly man, elderly woman variants
- German translations for all new strings

### Fixed
- Profile icon now shown in Settings overview list
- Glucose unit displayed as `mg/dL` / `mmol/L` (was `MG/DL` / `MMOL/L`)

### Changed
- Icon picker in profile edit screen uses `FlowRow` for dynamic wrapping instead of fixed 4-per-row

- TabTemplate for multi-profile switching (CarApi >= 6, 2–4 profiles); falls back to numbered icon ActionStrip
- Trend arrow rendered as bitmap image in Car UI row; delta shown below glucose value
- Configurable auto-refresh interval (30 s / 1 min / 2 min / 5 min) via Settings
- Configurable tab icon per profile (Person, Home, Heart, Star, Car, Medical) with icon picker in profile edit screen
- App launcher icon and mipmap assets
- DHU simulator setup guide (`TESTING.md`)
- `AppPreferencesDataStore` for persisting app-level preferences
- History endpoint (`getEntries`) in Nightscout API client

### Fixed
- App not appearing in Android Auto (missing `com.google.android.gms.car.application` meta-data)
- Crash on `ActionStrip` exceeding max 1 titled action
- Glucose unit labels shown in uppercase (now `mg/dL` / `mmol/L`)
- Java 17 toolchain required for Kotlin 2.0.21 compatibility

### Added
- Gradle wrapper (8.9) so CI runners can build without a local Gradle install
- GitHub Actions CI workflow with separate Build, Unit Tests, and Lint jobs
- Branch strategy documented in AGENTS.md: `main` is protected, all work on `develop`

### Changed
- Replaced deprecated `kotlinOptions { jvmTarget }` with `kotlin { jvmToolchain(17) }` in app build script
- MIT License (Copyright 2026 Nico Wiedemann)
- Full Android project scaffold: Gradle version catalog, root and app-level build files, ProGuard rules
- `AndroidManifest.xml` with `CarAppService` (IOT category), `MainActivity`, internet and foreground-service permissions
- Data layer: `NightscoutProfile` and `GlucoseEntry` domain models, `EntryDto` Moshi DTO, `NightscoutApi` Retrofit interface, `NightscoutApiFactory` with per-URL instance caching and OkHttp logging interceptor
- Persistence: `ProfileDataStore` (DataStore<Preferences>) and `ProfileSerializer` (Moshi JSON) for storing N Nightscout profiles
- `NightscoutRepository` singleton (Hilt) exposing profile CRUD, active-profile state, and coroutine-based `getCurrentEntry`
- Car UI screens: `GlucoseScreen` (PaneTemplate, 60 s polling, ActionStrip for ≤4 sources), `SourceSelectScreen` (ListTemplate for >4 sources), `NoProfilesScreen` (watches for profiles added on phone)
- Phone settings UI: `SettingsScreen` (profile list), `ProfileEditScreen` (form with Test Connection), `SettingsViewModel`, `ProfileEditViewModel`
- `MainActivity` with Compose navigation host
- String resources in English and German (full i18n coverage)
- ADR 001: architecture, IOT category, and multi-source strategy
- Unit tests for `NightscoutRepository` (MockK + coroutines-test)
