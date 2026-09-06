package de.autosugar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.lifecycleScope
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoSugarSession(
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Session() {

    private var monitorStarted = false

    override fun onCreateScreen(intent: Intent): Screen {
        if (!monitorStarted) {
            monitorStarted = true
            startAlertMonitoring()
        }
        return LoadingScreen(carContext, repository, appPrefs)
    }

    /**
     * Hands alert polling to [GlucoseMonitorService], which outlives this session.
     *
     * The session's own lifecycle only covers the car app being *on screen* — the host stops and
     * eventually destroys it once the user switches to Maps — so running the loop here meant alerts
     * fired only while the driver could already see the readings. The service is tied to the car
     * connection instead, and stops itself when Android Auto disconnects.
     */
    private fun startAlertMonitoring() {
        if (GlucoseMonitorService.start(carContext)) return

        // The platform refused the foreground-service start (Android 12+ background-start rules).
        // Fall back to polling on the session scope: alerts are then limited to the time the app is
        // on screen, as before, which is degraded but still better than no alerting at all.
        val monitor = BackgroundAlertMonitor(carContext, repository)
        lifecycleScope.launch {
            appPrefs.refreshIntervalSeconds.collectLatest { intervalSeconds ->
                while (isActive) {
                    monitor.checkAll()
                    delay(intervalSeconds * 1000L)
                }
            }
        }
    }
}
