package de.autosugar.car

import androidx.car.app.CarContext
import de.autosugar.data.model.GlucoseEntry
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Evaluates alert thresholds for every alert-enabled profile, independent of which profile is
 * currently shown on the Android Auto screen. Keyed throughout by each profile's own id, so
 * (unlike the screen, which only ever tracks the single active profile) a profile that isn't
 * on screen still gets its alerts checked, and profiles can never cross-contaminate each other's
 * data or cooldowns.
 */
class BackgroundAlertMonitor(
    carContext: CarContext,
    private val repository: NightscoutRepository,
) {
    private val alertManager = GlucoseAlertManager(carContext)
    private val alertCooldownMs = 15 * 60_000L

    // A reading older than this is considered stale (≥2 missed 5-min CGM readings) and never
    // triggers an alert, since acting on outdated glucose data is worse than not alerting.
    private val staleAfterMs = 12 * 60_000L

    private val lastHighAlertMs = mutableMapOf<String, Long>()
    private val lastLowAlertMs = mutableMapOf<String, Long>()
    private val lastPredictedHighAlertMs = mutableMapOf<String, Long>()
    private val lastPredictedLowAlertMs = mutableMapOf<String, Long>()

    // Recent history per profile, kept only to derive that profile's own reading cadence for
    // the 15-minute-ahead projection.
    private val historyByProfile = mutableMapOf<String, List<GlucoseEntry>>()

    suspend fun checkAll() = coroutineScope {
        val profiles = repository.profilesFlow.first().filter { it.alertsEnabled }
        profiles.map { profile -> async { checkProfile(profile) } }.awaitAll()
    }

    private suspend fun checkProfile(profile: NightscoutProfile) = coroutineScope {
        val entryResult = async { repository.getCurrentEntry(profile.id) }
        val thresholdsResult = async { repository.getThresholds(profile.id) }
        val historyResult = async { repository.getHistory(profile.id, count = 36) }

        val entry = entryResult.await().getOrNull() ?: return@coroutineScope
        val thresholds = thresholdsResult.await().getOrNull() ?: return@coroutineScope
        historyResult.await().getOrNull()?.let { h ->
            historyByProfile[profile.id] = h.sortedBy { it.dateMs }
        }

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

        val delta = entry.delta ?: return@coroutineScope
        // delta is the change over one reading interval; project 15 minutes ahead using
        // the profile's actual sampling cadence rather than assuming a fixed 5-minute interval.
        val projected15 = sgv + delta * projectionSteps(id)

        if (projected15 > thresholds.bgHigh && sgv < thresholds.bgHigh &&
            now - (lastPredictedHighAlertMs[id] ?: 0L) > alertCooldownMs
        ) {
            alertManager.sendPredictedHighAlert(id, profile.displayName, projected15, profile.unit)
            lastPredictedHighAlertMs[id] = now
        }
        if (projected15 < thresholds.bgLow && sgv > thresholds.bgLow &&
            now - (lastPredictedLowAlertMs[id] ?: 0L) > alertCooldownMs
        ) {
            alertManager.sendPredictedLowAlert(id, profile.displayName, projected15, profile.unit)
            lastPredictedLowAlertMs[id] = now
        }
    }

    private fun projectionSteps(profileId: String): Double {
        val history = historyByProfile[profileId] ?: emptyList()
        val gaps = history.zipWithNext { a, b -> b.dateMs - a.dateMs }
            .filter { it in 60_000L..15 * 60_000L }
            .sorted()
        val intervalMs = if (gaps.isEmpty()) 5 * 60_000L else gaps[gaps.size / 2]
        return 15 * 60_000.0 / intervalMs
    }
}
