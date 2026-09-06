package de.autosugar.car

import android.content.Context
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Checks every alert-enabled source against the thresholds configured on that source's own
 * Nightscout server, independent of which source is currently shown on the Android Auto screen.
 * Keyed throughout by each profile's own id, so (unlike the screen, which only ever tracks the
 * single active profile) a profile that isn't on screen still gets checked, and profiles can never
 * cross-contaminate each other's data or cooldowns.
 *
 * Deliberately performs no interpretation of its own. It compares the reading Nightscout reported
 * against the bounds Nightscout reported and notifies when one is crossed — nothing here decides
 * what counts as high or low, and nothing here forecasts where a reading is heading. AutoSugar is
 * a display for the user's own Nightscout data, so every number it acts on has to come from there.
 */
class BackgroundAlertMonitor(
    context: Context,
    private val repository: NightscoutRepository,
) {
    private val alertManager = GlucoseAlertManager(context)
    private val alertCooldownMs = 15 * 60_000L

    // A reading older than this is considered stale (≥2 missed 5-min CGM readings) and never
    // triggers a notification, since surfacing outdated glucose data is worse than staying quiet.
    private val staleAfterMs = 12 * 60_000L

    private val lastHighAlertMs = mutableMapOf<String, Long>()
    private val lastLowAlertMs = mutableMapOf<String, Long>()

    suspend fun checkAll() = coroutineScope {
        val profiles = repository.enabledProfilesFlow.first().filter { it.alertsEnabled }
        profiles.map { profile -> async { checkProfile(profile) } }.awaitAll()
    }

    private suspend fun checkProfile(profile: NightscoutProfile) = coroutineScope {
        val entryResult = async { repository.getCurrentEntry(profile.id) }
        val thresholdsResult = async { repository.getThresholds(profile.id) }

        val entry = entryResult.await().getOrNull() ?: return@coroutineScope
        val thresholds = thresholdsResult.await().getOrNull() ?: return@coroutineScope

        val sgv = entry.sgv
        val now = System.currentTimeMillis()
        if (now - entry.dateMs > staleAfterMs) return@coroutineScope

        val id = profile.id
        if (sgv >= thresholds.bgHigh && now - (lastHighAlertMs[id] ?: 0L) > alertCooldownMs) {
            alertManager.sendHighAlert(id, profile.displayName, sgv, profile.unit)
            lastHighAlertMs[id] = now
        }
        if (sgv <= thresholds.bgLow && now - (lastLowAlertMs[id] ?: 0L) > alertCooldownMs) {
            alertManager.sendLowAlert(id, profile.displayName, sgv, profile.unit)
            lastLowAlertMs[id] = now
        }
    }
}
