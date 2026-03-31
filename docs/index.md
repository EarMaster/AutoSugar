---
layout: page
---

<img src="assets/logo.svg" alt="AutoSugar" height="80" style="float:right; margin-left:1rem; margin-bottom:0.5rem;">

# AutoSugar

Blood glucose monitoring for Android Auto — see your [Nightscout](https://nightscout.github.io) CGM data at a glance, right on your car's display.

<a href="https://play.google.com/store/apps/details?id=de.autosugar"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60"></a>

[Other installation options →](install)

## Features

<div style="display:flex; gap:12px; flex-wrap:nowrap; margin:1rem 0; clear:both;">
  <a href="screenshots/android-auto-glucose-monitor-profile-1.png" class="glightbox" data-gallery="screenshots" data-title="Glucose monitor — profile 1" style="flex:1; min-width:0;"><img src="screenshots/android-auto-glucose-monitor-profile-1.png" alt="Glucose monitor — profile 1" style="width:100%; height:auto; display:block;"></a>
  <a href="screenshots/android-auto-glucose-monitor-profile-2.png" class="glightbox" data-gallery="screenshots" data-title="Glucose monitor — profile 2" style="flex:1; min-width:0;"><img src="screenshots/android-auto-glucose-monitor-profile-2.png" alt="Glucose monitor — profile 2" style="width:100%; height:auto; display:block;"></a>
</div>

- Monitor one or multiple [Nightscout](https://nightscout.github.io) sources (e.g. yourself and your children)
- Current glucose value, trend arrow, and delta
- Configurable glucose alerts
- Available in English, German, Spanish, French, Dutch, Italian, Portuguese, Arabic, Japanese, Chinese, and Hindi

## Configuration

<div style="display:flex; gap:12px; flex-wrap:wrap; margin:1rem 0;">
  <a href="screenshots/android-settings-list-profiles.png" class="glightbox" data-gallery="screenshots" data-title="Settings — profiles list"><img src="screenshots/android-settings-list-profiles.png" alt="Settings — profiles list" height="220" style="display:block;"></a>
  <a href="screenshots/android-settings-edit-profile.png" class="glightbox" data-gallery="screenshots" data-title="Settings — edit profile"><img src="screenshots/android-settings-edit-profile.png" alt="Settings — edit profile" height="220" style="display:block;"></a>
</div>

1. **Open AutoSugar** on your Android phone and tap **Add profile**.
2. Enter your [Nightscout](https://nightscout.github.io) URL (e.g. `https://yoursite.herokuapp.com`) and an optional display name.
3. If your Nightscout instance requires authentication, enter your API secret.
4. Set your preferred glucose unit (mg/dL or mmol/L) and alert thresholds.
5. **Connect Android Auto** — AutoSugar will appear in the app launcher on your car's display.

You can add multiple profiles to monitor different Nightscout sources (e.g. one for yourself and one for your child). Use the profile list in the app to switch or reorder them.

## Source code

Open source — find the code and report issues on [GitHub](https://github.com/EarMaster/AutoSugar).
