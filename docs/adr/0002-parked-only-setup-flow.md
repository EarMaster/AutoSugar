# ADR 002: Parked-Only Setup Flow

## Status
Accepted

## Context
Google Play rejected AutoSugar 1.2.5 with: *"The app does not disable features requiring phone interaction (initial setup) while in driving mode."*

ADR 001 chose the IoT category (`androidx.car.app.category.IOT`) for background activity and Coolwalk split-screen, but did not record the driver-distraction obligations that category carries. Two car app quality requirements apply:

* **IT-1** — an IoT app must not allow "tasks related to app setup of any kind" while driving.
* **VI-1** — if the user must go to the phone screen, the app must instruct them to only look at it when it is safe to do so.

Until now, an unconfigured app showed a plain `MessageTemplate` whose entire body read *"No sources configured. Open AutoSugar on your phone to add a Nightscout source."* That is a setup instruction pointing at the phone, rendered unconditionally while driving, with no parked-only gate anywhere in the app.

Configuring a Nightscout source needs a URL and an API token. Entering those in the car is not realistic, so the setup itself stays on the phone; only its *discoverability from the car* has to change.

## Decision
1. **The always-visible car screen states status, not instructions.** `NoProfilesScreen` now renders `label_not_set_up` — "AutoSugar isn't set up yet. Setup isn't available while driving." — with no reference to the phone.
2. **Setup instructions sit behind a host-enforced parked-only gate.** The screen's single action is wrapped in `ParkedOnlyOnClickListener`, so Android Auto runs it only when parked and otherwise tells the user it is unavailable while driving.
3. **The instructions themselves use `LongMessageTemplate`.** That template is parked-only by contract — the host refuses to render it while driving, and its builder rejects any action not backed by a `ParkedOnlyOnClickListener`. Its text carries the VI-1 "only look at your phone when it is safe" wording.
4. **`minCarApiLevel` moves from 1 to 2**, which `LongMessageTemplate` and `ConstraintManager` require. Level 2 shipped with Android Auto 6.7 in 2021.
5. **Switching between already-configured sources stays available while driving.** IT-1 explicitly permits viewing device state, and `TabTemplate` is the sanctioned mechanism for it; gating it would break the core use case of monitoring more than one person on a drive.

## Consequences
* **Pros:** The car app no longer surfaces any setup task or phone instruction while driving. The parked-only flag is asserted in `NoProfilesScreenTest`/`SetupInstructionsScreenTest`, and the string content in `DriverDistractionStringsTest`, so the rejection cannot silently regress. `ConstraintManager` becoming available also let `SourceSelectScreen` clamp its list to the host's content limit.
* **Cons:** Head units below car API level 2 can no longer run the app. A driver who has not set up the app gets no actionable path until they park — which is the point, but it does mean the car screen is inert for them.
* **Follow-up:** Any future feature that touches configuration must be checked against IT-1 before it is given a car-side entry point.
