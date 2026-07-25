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
            startBackgroundAlertMonitor()
        }
        return LoadingScreen(carContext, repository, appPrefs)
    }

    // Started once carContext is available and runs only while Android Auto is connected:
    // lifecycleScope is cancelled automatically when the session's lifecycle is destroyed
    // (car disconnected), so alert-enabled profiles are checked even when they aren't the
    // one currently shown on screen, without any polling happening while the app isn't in use.
    private fun startBackgroundAlertMonitor() {
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
