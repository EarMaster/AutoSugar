package de.autosugar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : ViewModel() {

    val profiles: StateFlow<List<NightscoutProfile>> = repository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val refreshIntervalSeconds: StateFlow<Int> = appPrefs.refreshIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)

    /** Shows or hides a source in the car. Alerts are a separate switch, on the edit screen. */
    fun setProfileEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setProfileEnabled(id, enabled) }
    }

    fun saveOrder(ordered: List<NightscoutProfile>) {
        viewModelScope.launch { repository.saveAll(ordered) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch { appPrefs.setRefreshInterval(seconds) }
    }
}
