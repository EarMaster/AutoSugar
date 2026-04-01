package de.autosugar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore

class AutoSugarSession(
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen =
        LoadingScreen(carContext, repository, appPrefs)
}
