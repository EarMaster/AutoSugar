# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Security

- The Nightscout API token is now redacted from debug HTTP logs, so it can no longer leak into logcat or a captured bug report even when HTTP logging is enabled

### Fixed

- The "Test Connection" button is now enabled only for a valid URL (matching the Save button), avoiding an opaque error when testing a malformed URL
- Reordering profiles no longer writes to storage when the order did not actually change
- Android Auto now returns to the no-profiles screen when the last profile is deleted, and falls back to a valid profile if the active one was removed or points at a since-deleted profile
- Added a dark (`-night`) app theme so the phone UI's window background and system bars follow the system dark mode instead of flashing white on launch
- The `dateIso` field now holds a real ISO-8601 timestamp when the entry has no dateString, instead of the raw epoch-millis digits
- mmol/L conversion now uses the exact glucose factor (18.0156) via a shared constant, and near-zero deltas no longer render as "-0"/"-0.0"
- Predictive (trending high/low) alerts now scale the 15-minute projection to the source's actual reading cadence instead of assuming 5-minute intervals, reducing false/missed predictions for 1- or 15-minute sources
- The history graph no longer renders blank/garbled when the target range collapses to a single value (division-by-zero guard), and swapped target bounds from the server are normalised instead of inverting the band
- Denying the notification permission when enabling alerts now turns the alerts toggle back off, instead of leaving it on while alerts silently never fire
- The "token has write permissions" warning now works: the Nightscout `authorized` object was modelled as an integer bitmask that never matched the real response, so over-privileged tokens were never flagged. It now parses the actual Shiro permission strings (`permissionGroups`)
- The connection test success and failure messages now use the existing translated string resources instead of a hard-coded English "BG:"/raw error, so they appear in the app's language
- The profile URL validator no longer rejects dot-less hosts, so `https://localhost`, internal DNS names, and IPv6 literals — the documented LAN/VPN self-hosting scenario — can now be saved
- The refresh interval is now clamped to a minimum of 30 seconds when read or written, preventing a stored 0/negative value from causing a tight, server-hammering fetch loop
- Concurrent profile edits (e.g. toggling an alert while another change is saving) can no longer clobber each other; profile add/remove/alert-toggle now apply atomically within a single storage transaction
- The history graph's line, dots, and value pins are now coloured using the profile's own low/high alert thresholds instead of hard-coded 70/180, so the at-a-glance colour matches when an alert would actually fire
- The graph is now redrawn when a reading's value changes for an existing timestamp (e.g. a backfilled correction), not only when timestamps change
- Non-sgv Nightscout records (calibration/meter-BG entries) in the feed no longer break history/current-reading loading; such records are now skipped instead of failing the whole response
- CI, release, and CodeQL workflows now run on JDK 21 to match the project's Java 21 source/target level; previously they provisioned JDK 17, which cannot compile the app
- Glucose readings that are stale (older than 12 minutes) are now labelled as stale in Android Auto even when the network fetch itself succeeded, and no longer trigger high/low/predicted alerts — acting on outdated CGM data is worse than not alerting
- Switching between profiles can no longer momentarily display one profile's glucose data under another profile's name; an in-flight fetch that has been superseded now discards its results
- Glucose alerts are now tracked per profile: one profile's recent alert no longer suppresses a genuine alert for another, each profile's alerts use distinct notification IDs so they no longer replace each other, and the notification now names the profile it is about
- A single corrupt, legacy, or truncated stored profile no longer makes the entire profile list inaccessible: an unknown glucose-unit value falls back to mg/dL, and malformed profile JSON falls back to an empty list instead of crashing every screen

## [1.2.3] - 2026-07-23

### Security

- Profile credentials (Nightscout API tokens) are now excluded from Android backup so they are no longer included in cloud/adb backups
- Android Auto host validation now restricts to Google's known Auto/Automotive hosts in release builds instead of accepting any host
- Cleartext HTTP is now explicitly permitted via a network security config for self-hosted Nightscout instances reached over a VPN/LAN without TLS; the profile editor warns when an `http://` URL is entered

### Fixed

- Duplicate glucose screens no longer get pushed onto the navigation stack when profiles change while a screen is covered by another
- mmol/L values and graph time labels now render consistently regardless of device locale
- Threshold fetch no longer fails entirely when target-range values are missing from the Nightscout status response; sensible defaults are used instead
- mg/dL delta display now rounds instead of truncating, matching the main reading
- Release workflow no longer breaks when changelog notes contain an apostrophe (shell quoting fix)

### Changed

- `NightscoutApiFactory`'s per-host API client cache is now thread-safe

## [1.2.2] - 2026-07-22

### Fixed

- Nightscout entries with a fractional `sgv` (e.g. from Juggluco/Libre 2 sources, which don't round the value) no longer crash JSON parsing; `sgv` is now handled as a `Double` throughout and rounded for mg/dL display (#13)
- Nightscout `settings.thresholds` (bgLow/bgHigh/bgTargetBottom/bgTargetTop) with fractional values no longer crash JSON parsing; values are now parsed as `Double` and rounded to the nearest mg/dL (#13)

## [1.2.1] - 2026-07-22

### Changed

- Updated to the latest Android SDK version (API 36)
- Updated toolchain: Kotlin 2.1.10, Hilt 2.60.1, Gradle Plugin 9.1.1
- Added edge-to-edge support for mobile settings and profile edit screens

### Fixed

- Google Play release notes: shortened v1.2.0 what's new text in all 11 languages to stay under 500-character limit (was 672 chars in Arabic)
- Release command: updated `release.md` to specify max 300 characters for English whatsnew text to ensure translations remain under 500-character GitHub limit

## [1.2.0] - 2026-04-01

### Added

- Instrumented tests (androidTest) for Car app screens: LoadingScreenTest, NoProfilesScreenTest, SourceSelectScreenTest verify correct template types and UI elements
- Unit tests for GlucoseAlertManager (notification IDs, value formatting, predictive alerts, security exception handling)
- Unit tests for SettingsViewModel (profiles/refreshInterval StateFlows, alerts/order/interval updates)
- Unit tests for ProfileEditViewModel (profile loading, connection testing, save/delete operations)
- Google Play: localized "what's new" text for all 11 supported languages (es-ES, fr-FR, it-IT, nl-NL, pt-PT, ja-JP, zh-CN, hi-IN, ar) for both v1.0.0 and v1.1.0

### Changed

- Google Play deployment: changed default release track from beta to internal
- Dependencies: bumped hilt 2.52→2.57, coroutines 1.9→1.10.2, datastore 1.1.1→1.2.1, mockk 1.13.12→1.14.7
- CI: release and deploy workflows now upload native debug symbols to Google Play
- Refactored GlucoseScreen: extracted graph and trend-arrow rendering into GlucoseGraphRenderer (GlucoseScreen reduced from 568 to 316 lines)
- Glucose history graph is now cached per data snapshot; re-rendered only when readings change
- Network errors now show stale cached data (⚠️ indicator) with retry action instead of blank error pane

### Fixed

- Glucose unit conversions now use US locale (3.5 mmol/L, not 3,5 for German devices)
- Unit tests: JVM tests now return default values for unmocked Android framework APIs
- Google Play warning about missing native debug symbols for crash and ANR analysis
- CI: artifact retention now explicitly set to 14 days (was defaulting to 90 days)
- CI: google-play.yml workflow now uses correct default track (internal, not beta) for automatic releases
- CI: google-play.yml now dynamically discovers localized "what's new" files by version, supporting all locales
- Car app: removed blocking call (runBlocking) from session initialization to prevent thread stalls
- Build: lint errors now cause build failure (previously only warned)

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
