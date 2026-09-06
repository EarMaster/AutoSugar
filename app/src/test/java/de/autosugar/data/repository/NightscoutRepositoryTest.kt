package de.autosugar.data.repository

import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.network.NightscoutApi
import de.autosugar.data.network.NightscoutApiFactory
import de.autosugar.data.network.dto.AuthorizedDto
import de.autosugar.data.network.dto.EntryDto
import de.autosugar.data.network.dto.SettingsDto
import de.autosugar.data.network.dto.StatusDto
import de.autosugar.data.network.dto.ThresholdsDto
import de.autosugar.data.storage.ProfileDataStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NightscoutRepositoryTest {

    private val profile = NightscoutProfile(
        id = "test-id",
        displayName = "Test",
        baseUrl = "https://example.nightscout.io",
        apiToken = "secret",
        unit = GlucoseUnit.MG_DL,
    )

    private val mockApi = mockk<NightscoutApi>()
    private val mockDataStore = mockk<ProfileDataStore>()
    private val mockFactory = mockk<NightscoutApiFactory>()

    private lateinit var repository: NightscoutRepository

    @Before
    fun setUp() {
        // profilesFlow is accessed during NightscoutRepository construction; provide a default
        every { mockDataStore.profilesFlow } returns flowOf(emptyList())
        repository = NightscoutRepository(mockDataStore, mockFactory)
    }

    // region getCurrentEntry

    @Test
    fun `getCurrentEntry maps dto to GlucoseEntry correctly`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = "Flat", date = 1_000_000L, dateString = "2024-01-01T12:00:00Z", delta = -3.0),
        )

        val result = repository.getCurrentEntry("test-id")
        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals(120.0, entry.sgv, 0.0)
        assertEquals("Flat", entry.direction)
        assertEquals("→", entry.trendArrow)
        assertEquals("-3", entry.displayDelta(GlucoseUnit.MG_DL))
    }

    @Test
    fun `getCurrentEntry dto delta takes priority over calculated delta`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = "Flat", date = 1_000_000L, dateString = null, delta = -3.0),
            EntryDto(sgv = 100.0, direction = "Flat", date = 900_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        // dto.delta = -3.0 should win over calculated 120-100 = 20
        assertEquals(-3.0, entry.delta)
    }

    @Test
    fun `getCurrentEntry calculates delta from two entries when dto delta is null`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = "Flat", date = 1_000_000L, dateString = null, delta = null),
            EntryDto(sgv = 110.0, direction = "Flat", date = 900_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals(10.0, entry.delta)
    }

    @Test
    fun `getCurrentEntry formats epoch as ISO-8601 when dateString is null`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = "Flat", date = 1_000_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals(java.time.Instant.ofEpochMilli(1_000_000L).toString(), entry.dateIso)
    }

    @Test
    fun `getCurrentEntry falls back to NOT COMPUTABLE when direction is null`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = null, date = 1_000_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals("NOT COMPUTABLE", entry.direction)
    }

    @Test
    fun `getCurrentEntry returns failure when profile not found`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(emptyList())

        val result = repository.getCurrentEntry("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getCurrentEntry returns failure on network error`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } throws RuntimeException("timeout")

        val result = repository.getCurrentEntry("test-id")
        assertTrue(result.isFailure)
        assertEquals("timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getCurrentEntry returns failure when entries list is empty`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns emptyList()

        val result = repository.getCurrentEntry("test-id")
        assertTrue(result.isFailure)
    }

    // endregion

    // region getThresholds

    @Test
    fun `getThresholds maps all threshold values from status response`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(
            settings = SettingsDto(
                thresholds = ThresholdsDto(bgHigh = 180.0, bgTargetTop = 160.0, bgTargetBottom = 80.0, bgLow = 70.0),
            ),
        )

        val thresholds = repository.getThresholds("test-id").getOrThrow()
        assertEquals(70, thresholds.bgLow)
        assertEquals(80, thresholds.bgTargetBottom)
        assertEquals(160, thresholds.bgTargetTop)
        assertEquals(180, thresholds.bgHigh)
    }

    @Test
    fun `getThresholds uses default bgLow and bgHigh when null in response`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(
            settings = SettingsDto(
                thresholds = ThresholdsDto(bgHigh = null, bgTargetTop = 160.0, bgTargetBottom = 80.0, bgLow = null),
            ),
        )

        val thresholds = repository.getThresholds("test-id").getOrThrow()
        assertEquals(70, thresholds.bgLow)
        assertEquals(180, thresholds.bgHigh)
    }

    @Test
    fun `getThresholds rounds fractional threshold values`() = runTest {
        // Regression for #13: Nightscout settings.thresholds can also carry non-integer values
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(
            settings = SettingsDto(
                thresholds = ThresholdsDto(bgHigh = 180.6, bgTargetTop = 160.4, bgTargetBottom = 80.5, bgLow = 70.2),
            ),
        )

        val thresholds = repository.getThresholds("test-id").getOrThrow()
        assertEquals(70, thresholds.bgLow)
        assertEquals(81, thresholds.bgTargetBottom)
        assertEquals(160, thresholds.bgTargetTop)
        assertEquals(181, thresholds.bgHigh)
    }

    @Test
    fun `getThresholds falls back to defaults when settings is null`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(settings = null)

        val thresholds = repository.getThresholds("test-id").getOrThrow()
        assertEquals(70, thresholds.bgLow)
        assertEquals(70, thresholds.bgTargetBottom)
        assertEquals(180, thresholds.bgTargetTop)
        assertEquals(180, thresholds.bgHigh)
    }

    @Test
    fun `getThresholds returns failure when profile not found`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(emptyList())

        val result = repository.getThresholds("nonexistent")
        assertTrue(result.isFailure)
    }

    // endregion

    // region getHistory

    @Test
    fun `getHistory maps list of DTOs to GlucoseEntry list`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getEntries(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = "Flat", date = 1_000_000L, dateString = "2024-01-01T12:00:00Z", delta = -2.0),
            EntryDto(sgv = 115.0, direction = "FortyFiveDown", date = 900_000L, dateString = null, delta = null),
        )

        val history = repository.getHistory("test-id").getOrThrow()
        assertEquals(2, history.size)
        assertEquals(120.0, history[0].sgv, 0.0)
        assertEquals("Flat", history[0].direction)
        assertEquals(-2.0, history[0].delta)
        assertEquals(115.0, history[1].sgv, 0.0)
        assertNull(history[1].delta)
    }

    @Test
    fun `getHistory skips non-sgv records instead of failing`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getEntries(any(), any()) } returns listOf(
            EntryDto(sgv = 120.0, direction = "Flat", date = 1_000_000L, dateString = null, delta = null),
            EntryDto(sgv = null, direction = null, date = 950_000L, dateString = null, delta = null), // e.g. cal/mbg record
            EntryDto(sgv = 110.0, direction = "Flat", date = 900_000L, dateString = null, delta = null),
        )

        val history = repository.getHistory("test-id").getOrThrow()
        assertEquals(2, history.size)
        assertEquals(120.0, history[0].sgv, 0.0)
        assertEquals(110.0, history[1].sgv, 0.0)
    }

    @Test
    fun `getCurrentEntry skips leading non-sgv record`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = null, direction = null, date = 1_000_000L, dateString = null, delta = null),
            EntryDto(sgv = 118.0, direction = "Flat", date = 900_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals(118.0, entry.sgv, 0.0)
    }

    @Test
    fun `getHistory returns failure when profile not found`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(emptyList())

        val result = repository.getHistory("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getCurrentEntry preserves fractional sgv from sources like Juggluco`() = runTest {
        // Regression for #13: Juggluco sends sgv as a float; Nightscout stores it as-is.
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 136.86445264101732, direction = "Flat", date = 1_000_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals(136.86445264101732, entry.sgv, 0.0)
    }

    // endregion

    // region saveProfile

    @Test
    fun `saveProfile adds new profile when id not in list`() = runTest {
        val transformSlot = slot<(List<NightscoutProfile>) -> List<NightscoutProfile>>()
        coEvery { mockDataStore.update(capture(transformSlot)) } just Runs
        justRun { mockFactory.invalidate(any()) }

        repository.saveProfile(profile)

        val result = transformSlot.captured(emptyList())
        assertEquals(1, result.size)
        assertEquals(profile, result[0])
    }

    @Test
    fun `saveProfile updates existing profile in place`() = runTest {
        val updated = profile.copy(displayName = "Updated")
        val transformSlot = slot<(List<NightscoutProfile>) -> List<NightscoutProfile>>()
        coEvery { mockDataStore.update(capture(transformSlot)) } just Runs
        justRun { mockFactory.invalidate(any()) }

        repository.saveProfile(updated)

        val result = transformSlot.captured(listOf(profile))
        assertEquals(1, result.size)
        assertEquals("Updated", result[0].displayName)
    }

    @Test
    fun `saveProfile invalidates api factory cache`() = runTest {
        coJustRun { mockDataStore.update(any()) }
        justRun { mockFactory.invalidate(any()) }

        repository.saveProfile(profile)

        verify { mockFactory.invalidate(profile.baseUrl) }
    }

    // endregion

    // region deleteProfile

    @Test
    fun `deleteProfile removes profile from dataStore`() = runTest {
        val transformSlot = slot<(List<NightscoutProfile>) -> List<NightscoutProfile>>()
        coEvery { mockDataStore.update(capture(transformSlot)) } just Runs

        repository.deleteProfile("test-id")

        assertTrue(transformSlot.captured(listOf(profile)).isEmpty())
    }

    @Test
    fun `deleteProfile clears activeProfileId when deleted profile was active`() = runTest {
        coJustRun { mockDataStore.update(any()) }
        repository.setActiveProfile("test-id")

        repository.deleteProfile("test-id")

        assertNull(repository.activeProfileId.value)
    }

    @Test
    fun `deleteProfile does not clear activeProfileId when different profile is deleted`() = runTest {
        coJustRun { mockDataStore.update(any()) }
        repository.setActiveProfile("test-id")

        repository.deleteProfile("other-id")

        assertEquals("test-id", repository.activeProfileId.value)
    }

    @Test
    fun `setAlertsEnabled toggles only the matching profile`() = runTest {
        val other = profile.copy(id = "other-id", alertsEnabled = false)
        val transformSlot = slot<(List<NightscoutProfile>) -> List<NightscoutProfile>>()
        coEvery { mockDataStore.update(capture(transformSlot)) } just Runs

        repository.setAlertsEnabled("test-id", true)

        val result = transformSlot.captured(listOf(profile, other))
        assertTrue(result.first { it.id == "test-id" }.alertsEnabled)
        assertFalse(result.first { it.id == "other-id" }.alertsEnabled)
    }

    @Test
    fun `setProfileEnabled toggles only the matching profile`() = runTest {
        val other = profile.copy(id = "other-id")
        val transformSlot = slot<(List<NightscoutProfile>) -> List<NightscoutProfile>>()
        coEvery { mockDataStore.update(capture(transformSlot)) } just Runs

        repository.setProfileEnabled("test-id", false)

        val result = transformSlot.captured(listOf(profile, other))
        assertFalse(result.first { it.id == "test-id" }.enabled)
        assertTrue(result.first { it.id == "other-id" }.enabled)
    }

    @Test
    fun `setProfileEnabled clears activeProfileId when the active profile is disabled`() = runTest {
        // The car can no longer show it, so leaving it selected would strand the next session
        // on a source that is not in its list.
        coJustRun { mockDataStore.update(any()) }
        repository.setActiveProfile("test-id")

        repository.setProfileEnabled("test-id", false)

        assertNull(repository.activeProfileId.value)
    }

    @Test
    fun `setProfileEnabled keeps activeProfileId when re-enabling`() = runTest {
        coJustRun { mockDataStore.update(any()) }
        repository.setActiveProfile("test-id")

        repository.setProfileEnabled("test-id", true)

        assertEquals("test-id", repository.activeProfileId.value)
    }

    @Test
    fun `enabledProfilesFlow hides disabled profiles from the car`() = runTest {
        val disabled = profile.copy(id = "off-id", enabled = false)
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile, disabled))
        val repo = NightscoutRepository(mockDataStore, mockFactory)

        assertEquals(listOf(profile), repo.enabledProfilesFlow.first())
        assertEquals(2, repo.profilesFlow.first().size)
    }

    // endregion

    // region hasElevatedPermissions

    @Test
    fun `hasElevatedPermissions is false for a read-only token`() = runTest {
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(
            settings = null,
            authorized = AuthorizedDto(permissionGroups = listOf(listOf("*:*:read"))),
        )

        assertFalse(repository.hasElevatedPermissions(profile))
    }

    @Test
    fun `hasElevatedPermissions is true for an admin token`() = runTest {
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(
            settings = null,
            authorized = AuthorizedDto(permissionGroups = listOf(listOf("*"))),
        )

        assertTrue(repository.hasElevatedPermissions(profile))
    }

    @Test
    fun `hasElevatedPermissions is true when any group grants write`() = runTest {
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(
            settings = null,
            authorized = AuthorizedDto(
                permissionGroups = listOf(listOf("*:*:read"), listOf("api:treatments:create")),
            ),
        )

        assertTrue(repository.hasElevatedPermissions(profile))
    }

    @Test
    fun `hasElevatedPermissions is false for a blank token`() = runTest {
        assertFalse(repository.hasElevatedPermissions(profile.copy(apiToken = "")))
    }

    // endregion

    // region setActiveProfile / saveAll

    @Test
    fun `setActiveProfile updates activeProfileId state`() {
        repository.setActiveProfile("test-id")
        assertEquals("test-id", repository.activeProfileId.value)
    }

    @Test
    fun `saveAll delegates to dataStore save`() = runTest {
        val profiles = listOf(profile)
        coJustRun { mockDataStore.save(any()) }

        repository.saveAll(profiles)

        coVerify { mockDataStore.save(profiles) }
    }

    // endregion
}
