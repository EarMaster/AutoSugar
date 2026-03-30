package com.autosugar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.repository.NightscoutRepository
import com.autosugar.data.storage.AppPreferencesDataStore
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

    fun deleteProfile(id: String) {
        viewModelScope.launch { repository.deleteProfile(id) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch { appPrefs.setRefreshInterval(seconds) }
    }
}
