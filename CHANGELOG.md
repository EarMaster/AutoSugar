# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed
- Google Play deployment: changed default release track from beta to internal

## [1.1.0] - 2026-04-01

### Added
- API token permission detection and read-only security warning in profile configuration
- CodeQL security analysis workflow (runs on push/PR to main and develop, weekly schedule)
- Website: favicon using logo SVG
- Website: lightbox for screenshots (GLightbox, keyboard/swipe nav)
- CI: new google-play.yml workflow for independent Google Play deployments

### Changed
- Website: use SVG logo (right-aligned), reorder screenshots by context
- Website: fix duplicate nav items, add logo, auto dark mode, move screenshots under Configuration section
- Removed `context: fork` from commit and release command frontmatter
- CI: split release workflow into build (release.yml) and deployment (google-play.yml) stages
- CI: build artifacts now include app name, version, and commit SHA (e.g. autosugar-v1.0.0-c749008.apk)
- CI: GitHub releases now include both APK and AAB files
- CI: google-play.yml now triggered automatically from release.yml via workflow_call (no longer needs release: event)
- CI: CodeQL runs only on PRs and weekly schedule (removed redundant push trigger)
- CI: main CI workflow now runs only on PRs (removed redundant push trigger)
- Claude commands: commit and release commands now explicitly use haiku model for faster execution
- Release command now automatically asks for push confirmation instead of leaving it to manual execution
- Commit command now uses `git fetch --no-tags origin && git rebase @{u}` for syncing with remote (avoids tag conflicts)
- Release process: what's new text for Google Play now stored in `docs/whatsnew/` per version instead of in GitHub release notes

### Fixed
- Website: GLightbox not loading; fix by overriding head.html (custom-head.html unsupported in Minima 2.5.x)
- Website: footer showed site title twice; now shows title + description once each
- Website: Android Auto screenshots now always display side-by-side
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
