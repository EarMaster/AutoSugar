package com.autosugar.data.repository

import com.autosugar.data.model.GlucoseEntry
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.network.NightscoutApiFactory
import com.autosugar.data.storage.ProfileDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NightscoutRepository @Inject constructor(
    private val dataStore: ProfileDataStore,
    private val apiFactory: NightscoutApiFactory,
) {
    val profilesFlow: Flow<List<NightscoutProfile>> = dataStore.profilesFlow

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    fun setActiveProfile(id: String) {
        _activeProfileId.value = id
    }

    suspend fun getCurrentEntry(profileId: String): Result<GlucoseEntry> = runCatching {
        val profiles = dataStore.profilesFlow.first()
        val profile = profiles.find { it.id == profileId }
            ?: error("Profile $profileId not found")

        val api = apiFactory.get(profile.baseUrl)
        val entries = api.getCurrentEntry(
            token = profile.apiToken.ifBlank { null },
            count = 2,
        )
        val latest = entries.firstOrNull() ?: error("No entries returned")
        val previous = entries.getOrNull(1)

        val delta = previous?.let { (latest.sgv - it.sgv).toDouble() }

        GlucoseEntry(
            sgv = latest.sgv,
            direction = latest.direction ?: "NOT COMPUTABLE",
            dateIso = latest.dateString ?: latest.date.toString(),
            delta = latest.delta ?: delta,
            dateMs = latest.date,
        )
    }

    /** Returns Pair(bgTargetBottom, bgTargetTop) in mg/dL. */
    suspend fun getTargetRange(profileId: String): Result<Pair<Int, Int>> = runCatching {
        val profiles = dataStore.profilesFlow.first()
        val profile = profiles.find { it.id == profileId }
            ?: error("Profile $profileId not found")
        val api = apiFactory.get(profile.baseUrl)
        val t = api.getStatus(token = profile.apiToken.ifBlank { null }).settings?.thresholds
        val bottom = t?.bgTargetBottom ?: error("bgTargetBottom not in status response")
        val top    = t.bgTargetTop    ?: error("bgTargetTop not in status response")
        bottom to top
    }

    suspend fun getHistory(profileId: String, count: Int = 24): Result<List<GlucoseEntry>> = runCatching {
        val profiles = dataStore.profilesFlow.first()
        val profile = profiles.find { it.id == profileId }
            ?: error("Profile $profileId not found")

        val api = apiFactory.get(profile.baseUrl)
        api.getEntries(token = profile.apiToken.ifBlank { null }, count = count)
            .map { dto ->
                GlucoseEntry(
                    sgv = dto.sgv,
                    direction = dto.direction ?: "NOT COMPUTABLE",
                    dateIso = dto.dateString ?: dto.date.toString(),
                    delta = dto.delta,
                    dateMs = dto.date,
                )
            }
    }

    suspend fun saveProfile(profile: NightscoutProfile) {
        val profiles = dataStore.profilesFlow.first().toMutableList()
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx >= 0) profiles[idx] = profile else profiles.add(profile)
        dataStore.save(profiles)
        // Invalidate cached API instance in case URL changed
        apiFactory.invalidate(profile.baseUrl)
    }

    suspend fun deleteProfile(id: String) {
        val profiles = dataStore.profilesFlow.first().filter { it.id != id }
        dataStore.save(profiles)
        if (_activeProfileId.value == id) _activeProfileId.value = null
    }
}
