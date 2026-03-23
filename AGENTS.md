# Project Agents

This document defines specific roles for AI agents or developers to ensure a modular and scalable implementation of AutoSugar.

## Agent 1: Android Auto UX/UI Specialist
* **Focus:** Implementing the `CarAppService` and `Screen` hierarchy.
* **Tasks:** * Design a dynamic `PaneTemplate` that updates based on the selected source.
    * Implement an `ActionStrip` that scales from 1 to N sources.
    * Ensure all UI strings utilize the Android `res/strings` system for i18n support.
* **Constraint:** Use only standard templates from the `androidx.car.app` library.

## Agent 2: Dynamic Data & Network Architect
* **Focus:** Managing N-Nightscout connections.
* **Tasks:** * Build a repository layer that handles a list of API configurations (URL + API Token).
    * Implement secure networking using Retrofit/OkHttp.
    * Handle edge cases like mixed units (mg/dL vs. mmol/L) across different sources.

## Agent 3: Mobile Configuration & I18n Manager
* **Focus:** The smartphone-based settings interface.
* **Tasks:** * Create a "Settings" UI (Jetpack Compose) to add, edit, or delete Nightscout sources.
    * Manage local data storage (e.g., Room or DataStore) for persistent source configuration.
    * Handle system-level language switching logic.
