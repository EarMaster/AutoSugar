package de.autosugar.data.repository

import de.autosugar.data.model.GlucoseEntry
import de.autosugar.data.model.GlucoseThresholds
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.network.NightscoutApiFactory
import de.autosugar.data.network.dto.EntryDto
import de.autosugar.data.storage.ProfileDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

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

    suspend fun testConnection(profile: NightscoutProfile): Result<GlucoseEntry> = runCatching {
        val api = apiFactory.get(profile.baseUrl)
        val entries = api.getCurrentEntry(
            token = profile.apiToken.ifBlank { null },
            count = 2,
        )
        entries.toGlucoseEntry()
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
        entries.toGlucoseEntry()
    }

    /** Builds the latest [GlucoseEntry], ignoring non-sgv records (mbg/cal). */
    private fun List<EntryDto>.toGlucoseEntry(): GlucoseEntry {
        val sgvEntries = filter { it.sgv != null }
        val latest = sgvEntries.firstOrNull() ?: error("No entries returned")
        val previous = sgvEntries.getOrNull(1)
        val delta = previous?.let { latest.sgv!! - it.sgv!! }
        return GlucoseEntry(
            sgv = latest.sgv!!,
            direction = latest.direction ?: "NOT COMPUTABLE",
            dateIso = latest.dateString ?: latest.date.toString(),
            delta = latest.delta ?: delta,
            dateMs = latest.date,
        )
    }

    /** Returns all four Nightscout threshold values in mg/dL. */
    suspend fun getThresholds(profileId: String): Result<GlucoseThresholds> = runCatching {
        val profiles = dataStore.profilesFlow.first()
        val profile = profiles.find { it.id == profileId }
            ?: error("Profile $profileId not found")
        val api = apiFactory.get(profile.baseUrl)
        val t = api.getStatus(token = profile.apiToken.ifBlank { null }).settings?.thresholds
        GlucoseThresholds(
            bgLow          = t?.bgLow?.roundToInt()          ?: 70,
            bgTargetBottom = t?.bgTargetBottom?.roundToInt() ?: 70,
            bgTargetTop    = t?.bgTargetTop?.roundToInt()    ?: 180,
            bgHigh         = t?.bgHigh?.roundToInt()         ?: 180,
        )
    }

    suspend fun getHistory(profileId: String, count: Int = 24): Result<List<GlucoseEntry>> = runCatching {
        val profiles = dataStore.profilesFlow.first()
        val profile = profiles.find { it.id == profileId }
            ?: error("Profile $profileId not found")

        val api = apiFactory.get(profile.baseUrl)
        api.getEntries(token = profile.apiToken.ifBlank { null }, count = count)
            .filter { it.sgv != null }
            .map { dto ->
                GlucoseEntry(
                    sgv = dto.sgv!!,
                    direction = dto.direction ?: "NOT COMPUTABLE",
                    dateIso = dto.dateString ?: dto.date.toString(),
                    delta = dto.delta,
                    dateMs = dto.date,
                )
            }
    }

    /**
     * Returns true if the token grants more than read-only access.
     * Returns false for blank tokens (public instances) or when the server does not return
     * permission information (older Nightscout versions).
     */
    suspend fun hasElevatedPermissions(profile: NightscoutProfile): Boolean {
        if (profile.apiToken.isBlank()) return false
        val api = apiFactory.get(profile.baseUrl)
        val authorized = api.getStatus(token = profile.apiToken).authorized ?: return false
        return authorized.permissionGroups.flatten().any { grantsWrite(it) }
    }

    /**
     * True if a Shiro permission string grants anything beyond read. Format is
     * `domain:resource:action`; the action segment being absent (e.g. `*` or `api:*`) or
     * anything other than `read` means the token can write.
     */
    private fun grantsWrite(permission: String): Boolean {
        val action = permission.split(":").getOrNull(2) ?: return true
        return action != "read"
    }

    suspend fun saveProfile(profile: NightscoutProfile) {
        dataStore.update { profiles ->
            val idx = profiles.indexOfFirst { it.id == profile.id }
            if (idx >= 0) profiles.toMutableList().also { it[idx] = profile }
            else profiles + profile
        }
        // Invalidate cached API instance in case URL changed
        apiFactory.invalidate(profile.baseUrl)
    }

    suspend fun deleteProfile(id: String) {
        dataStore.update { profiles -> profiles.filter { it.id != id } }
        if (_activeProfileId.value == id) _activeProfileId.value = null
    }

    /** Atomically toggles the alerts flag for a single profile without clobbering concurrent edits. */
    suspend fun setAlertsEnabled(id: String, enabled: Boolean) {
        dataStore.update { profiles ->
            profiles.map { if (it.id == id) it.copy(alertsEnabled = enabled) else it }
        }
    }

    suspend fun saveAll(profiles: List<NightscoutProfile>) {
        dataStore.save(profiles)
    }
}
