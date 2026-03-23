# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
