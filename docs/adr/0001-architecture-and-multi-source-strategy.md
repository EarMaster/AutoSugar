# ADR 001: Architecture, Category, and Multi-Source Strategy

## Status
Accepted

## Context
The user requires a solution to monitor blood glucose levels via Android Auto. The solution must support a variable number of sources (1 to N) and be available in multiple languages (starting with English and German). Due to the medical nature and unofficial API usage, the app will be distributed via sideloading.

## Decision
1.  **App Category:** We will use the IoT category (`androidx.car.app.category.IOT`). This allows the app to stay active in the background and coexist with Google Maps in split-screen layouts.
2.  **Multilingual Support:** All UI elements will be decoupled from code using standard Android String resources.
3.  **Source Management:**
    * The app will store an array of "Profiles."
    * In the Car UI, if multiple profiles exist, an `ActionStrip` or a `ListTemplate` will be used for selection.
    * If only one profile exists, the app defaults to that source immediately.
4.  **UI Library:** We use the `androidx.car.app` library to ensure distraction-free driving compliance, utilizing `PaneTemplate` for primary data display.

## Consequences
* **Pros:** Scalable for families with multiple T1D members; compliant with Android Auto's "Coolwalk" multi-tasking; easier for international users.
* **Cons:** UI complexity increases as the number of sources grows (templates have limits on button counts). Sideloading requires manual updates.
