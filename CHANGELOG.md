# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- CodeQL security analysis workflow (runs on push/PR to main and develop, weekly schedule)
- Website: favicon using logo SVG

### Changed
- Website: use SVG logo (right-aligned), reorder screenshots by context
- Website: fix duplicate nav items, add logo, auto dark mode, move screenshots under Configuration section
- Removed `context: fork` from commit and release command frontmatter

### Fixed
- Website: logo 404 (SVG was untracked); replace PNG with SVG
- Website: screenshots now appear directly under their respective Features and Configuration section headings
- Release signing keystore path now resolved relative to project root via `rootProject.file()`

## [1.0.0] - 2026-03-31

### Added
- Android Auto app for monitoring Nightscout CGM blood glucose data while driving
- Multi-profile support: monitor multiple Nightscout sources simultaneously with configurable tab icons and per-profile settings
- Car UI: current glucose value, trend arrow, delta, reading age, and 3-hour history graph with target range band
- Glucose alerts: high/low notifications with 15-minute cooldown and predictive alerts based on 15-min linear extrapolation
- Configurable auto-refresh interval (30 s, 1 min, 2 min, 5 min)
- Profile icon picker with male, female, boy, girl, baby, elderly man, and elderly woman variants
- Monochrome adaptive icon for Android 13+ themed icon support
- App UI translated into English, German, Spanish, French, Dutch, Italian, Portuguese, Arabic, Japanese, Chinese (Simplified), and Hindi
- Unit tests for GlucoseEntry display logic, ProfileSerializer JSON round-trip, and NightscoutRepository
- GitHub Actions CI workflow (Build, Unit Tests, Lint) and release workflow with signed APK and AAB artifacts
- Dedicated installation page and configuration guide on the project website

### Fixed
- App not appearing in Android Auto launcher (missing `com.google.android.gms.car.application` meta-data)
- Glucose unit labels now correctly shown as `mg/dL` / `mmol/L`
