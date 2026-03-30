package com.autosugar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosugar.data.model.GlucoseUnit
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.model.ProfileIcon
import com.autosugar.data.repository.NightscoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileEditUiState {
    data object Idle : ProfileEditUiState
    data object Loading : ProfileEditUiState
    data class TestSuccess(val message: String) : ProfileEditUiState
    data class Error(val message: String) : ProfileEditUiState
    data object Saved : ProfileEditUiState
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val repository: NightscoutRepository,
) : ViewModel() {

    var displayName = MutableStateFlow("")
    var baseUrl = MutableStateFlow("")
    var apiToken = MutableStateFlow("")
    var unit = MutableStateFlow(GlucoseUnit.MG_DL)
    var icon = MutableStateFlow(ProfileIcon.PERSON)

    private var editingId: String? = null

    private val _uiState = MutableStateFlow<ProfileEditUiState>(ProfileEditUiState.Idle)
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    fun loadProfile(profileId: String) {
        viewModelScope.launch {
            val profile = repository.profilesFlow.first().find { it.id == profileId } ?: return@launch
            editingId = profile.id
            displayName.value = profile.displayName
            baseUrl.value = profile.baseUrl
            apiToken.value = profile.apiToken
            unit.value = profile.unit
            icon.value = profile.icon
        }
    }

    fun testConnection() {
        _uiState.value = ProfileEditUiState.Loading
        val tempProfile = buildProfile()
        viewModelScope.launch {
            // Save temporarily so the repository can fetch using it
            repository.saveProfile(tempProfile)
            repository.getCurrentEntry(tempProfile.id)
                .onSuccess { entry ->
                    _uiState.value = ProfileEditUiState.TestSuccess(
                        "${entry.displayValue(tempProfile.unit)} ${when (tempProfile.unit) {
                            GlucoseUnit.MG_DL -> "mg/dL"
                            GlucoseUnit.MMOL_L -> "mmol/L"
                        }}"
                    )
                    // Revert if we were just testing (don't keep the temp profile if it was new)
                    if (editingId == null) {
                        repository.deleteProfile(tempProfile.id)
                    }
                }
                .onFailure { e ->
                    _uiState.value = ProfileEditUiState.Error(e.message ?: "Unknown error")
                    if (editingId == null) repository.deleteProfile(tempProfile.id)
                }
        }
    }

    fun save() {
        _uiState.value = ProfileEditUiState.Loading
        viewModelScope.launch {
            repository.saveProfile(buildProfile())
            _uiState.value = ProfileEditUiState.Saved
        }
    }

    fun clearState() {
        _uiState.value = ProfileEditUiState.Idle
    }

    private fun buildProfile() = NightscoutProfile(
        id = editingId ?: java.util.UUID.randomUUID().toString(),
        displayName = displayName.value.trim(),
        baseUrl = baseUrl.value.trim(),
        apiToken = apiToken.value.trim(),
        unit = unit.value,
        icon = icon.value,
    )
}
