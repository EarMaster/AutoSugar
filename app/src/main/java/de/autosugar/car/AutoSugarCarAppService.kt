package de.autosugar.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AutoSugarCarAppService : CarAppService() {

    @Inject lateinit var repository: NightscoutRepository
    @Inject lateinit var appPrefs: AppPreferencesDataStore

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = AutoSugarSession(repository, appPrefs)
}
