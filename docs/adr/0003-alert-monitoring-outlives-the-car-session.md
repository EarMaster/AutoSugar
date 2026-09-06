# ADR 003: Alert Monitoring Outlives the Car Session

## Status
Accepted

## Context
Glucose alerts only ever appeared while AutoSugar was the app currently on the car screen — which
is precisely when they are useless, because the driver can already see the reading. The moment the
user switched to Maps, alerting went silent, and that is the case the feature exists for.

The cause was lifecycle ownership. Since 1.2.5 the alert loop has run on `AutoSugarSession`'s
`lifecycleScope` (ADR 001 chose the IoT category partly to "stay active in the background", but the
`Session` is not the thing that stays active). A `Session`'s lifecycle tracks *car-screen
visibility*: the host stops it when another car app takes the screen and destroys it thereafter, and
`lifecycleScope` is cancelled at `ON_DESTROY`. With no started component of its own, the app's
process is then a cached background process — no polling, no network, no alerts.

Android's documented mechanism for work that must continue while the app is not visible is a started
foreground service. The remaining question was which foreground service type, since Android 14
gates each type behind prerequisites:

* **`connectedDevice`** describes the situation best ("interacting with an external device such as
  … a car") but requires the app to hold one of `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE`,
  `CHANGE_WIFI_MULTICAST_STATE`, `NFC`, `TRANSMIT_IR`, a Bluetooth/UWB runtime permission, or a USB
  grant. AutoSugar needs none of those and declaring one purely to pass the gate would be a false
  permission declaration on the Play listing.
* **`specialUse`** requires a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declaration and a Play review
  justification — extra review friction on an app whose reviews are already slow.
* **`dataSync`** has no permission prerequisites and describes exactly what the loop does: fetch
  readings from Nightscout on a timer. Its cost is the Android 15+ budget of six hours per 24-hour
  window, after which the system calls `Service.onTimeout()` and kills the service if it does not
  stop itself.

## Decision
1. **Polling moves into `GlucoseMonitorService`**, a started foreground service of type `dataSync`.
   `BackgroundAlertMonitor` is unchanged apart from taking a plain `Context`.
2. **Its lifetime is the car *connection*, not the car *screen*.** The service observes
   `CarConnection.getType()` and stops itself on `CONNECTION_TYPE_NOT_CONNECTED`. The car app library
   already declares the `<queries>` entry that provider lookup needs, and a missing or unreadable
   provider reports "not connected", so the service can never outlive a drive.
3. **`AutoSugarSession` only starts it.** The session stays the trigger — the host creating a session
   is the app's signal that Android Auto is up — but owns none of the polling. Repeat starts are
   idempotent.
4. **A refused start falls back to the old in-session loop.** Android 12+ can reject a
   foreground-service start it judges to come from the background; rather than lose alerting
   outright, the session then polls on its own scope exactly as before.
5. **The six-hour cap fails loudly.** `onTimeout()` posts a "glucose monitoring stopped" alert
   through `CarNotificationManager` — so it reaches the car screen — before calling `stopSelf()`.
6. **The ongoing service notification is silent** (`IMPORTANCE_LOW`, no badge) and is not extended
   for the car, so it stays a phone-side status indicator and never competes with an actual alert.

## Consequences
* **Pros:** Alerts now fire in the situation they were written for — AutoSugar in the background,
  Android Auto connected. Alert cooldowns and per-profile state live in one place for a whole drive
  instead of being reset every time the session is rebuilt.
* **Cons:** A persistent notification appears on the phone for the duration of a drive. Continuous
  monitoring is capped at six hours per 24 hours by the platform; beyond that the driver is told
  monitoring stopped and has to reopen AutoSugar on the car screen to restart it.
* **Follow-up:** `POST_NOTIFICATIONS` is declared but never requested at runtime, so on Android 13+
  every alert is silently swallowed until the user grants notifications by hand. That gap is
  orthogonal to this ADR but blocks the same feature.
