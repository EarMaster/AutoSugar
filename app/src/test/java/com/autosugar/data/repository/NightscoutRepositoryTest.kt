package com.autosugar.data.repository

import com.autosugar.data.model.GlucoseUnit
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.network.NightscoutApi
import com.autosugar.data.network.NightscoutApiFactory
import com.autosugar.data.network.dto.EntryDto
import com.autosugar.data.network.dto.SettingsDto
import com.autosugar.data.network.dto.StatusDto
import com.autosugar.data.network.dto.ThresholdsDto
import com.autosugar.data.storage.ProfileDataStore
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            EntryDto(sgv = 120, direction = "Flat", date = 1_000_000L, dateString = "2024-01-01T12:00:00Z", delta = -3.0),
        )

        val result = repository.getCurrentEntry("test-id")
        assertTrue(result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals(120, entry.sgv)
        assertEquals("Flat", entry.direction)
        assertEquals("→", entry.trendArrow)
        assertEquals("-3", entry.displayDelta(GlucoseUnit.MG_DL))
    }

    @Test
    fun `getCurrentEntry dto delta takes priority over calculated delta`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120, direction = "Flat", date = 1_000_000L, dateString = null, delta = -3.0),
            EntryDto(sgv = 100, direction = "Flat", date = 900_000L, dateString = null, delta = null),
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
            EntryDto(sgv = 120, direction = "Flat", date = 1_000_000L, dateString = null, delta = null),
            EntryDto(sgv = 110, direction = "Flat", date = 900_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals(10.0, entry.delta)
    }

    @Test
    fun `getCurrentEntry uses date toString when dateString is null`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120, direction = "Flat", date = 1_000_000L, dateString = null, delta = null),
        )

        val entry = repository.getCurrentEntry("test-id").getOrThrow()
        assertEquals("1000000", entry.dateIso)
    }

    @Test
    fun `getCurrentEntry falls back to NOT COMPUTABLE when direction is null`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getCurrentEntry(any(), any()) } returns listOf(
            EntryDto(sgv = 120, direction = null, date = 1_000_000L, dateString = null, delta = null),
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
                thresholds = ThresholdsDto(bgHigh = 180, bgTargetTop = 160, bgTargetBottom = 80, bgLow = 70),
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
                thresholds = ThresholdsDto(bgHigh = null, bgTargetTop = 160, bgTargetBottom = 80, bgLow = null),
            ),
        )

        val thresholds = repository.getThresholds("test-id").getOrThrow()
        assertEquals(70, thresholds.bgLow)
        assertEquals(180, thresholds.bgHigh)
    }

    @Test
    fun `getThresholds returns failure when bgTargetBottom is missing`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        every { mockFactory.get(any()) } returns mockApi
        coEvery { mockApi.getStatus(any()) } returns StatusDto(settings = null)

        val result = repository.getThresholds("test-id")
        assertTrue(result.isFailure)
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
            EntryDto(sgv = 120, direction = "Flat", date = 1_000_000L, dateString = "2024-01-01T12:00:00Z", delta = -2.0),
            EntryDto(sgv = 115, direction = "FortyFiveDown", date = 900_000L, dateString = null, delta = null),
        )

        val history = repository.getHistory("test-id").getOrThrow()
        assertEquals(2, history.size)
        assertEquals(120, history[0].sgv)
        assertEquals("Flat", history[0].direction)
        assertEquals(-2.0, history[0].delta)
        assertEquals(115, history[1].sgv)
        assertNull(history[1].delta)
    }

    @Test
    fun `getHistory returns failure when profile not found`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(emptyList())

        val result = repository.getHistory("nonexistent")
        assertTrue(result.isFailure)
    }

    // endregion

    // region saveProfile

    @Test
    fun `saveProfile adds new profile when id not in list`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(emptyList())
        val savedSlot = slot<List<NightscoutProfile>>()
        coEvery { mockDataStore.save(capture(savedSlot)) } just Runs
        justRun { mockFactory.invalidate(any()) }

        repository.saveProfile(profile)

        assertEquals(1, savedSlot.captured.size)
        assertEquals(profile, savedSlot.captured[0])
    }

    @Test
    fun `saveProfile updates existing profile in place`() = runTest {
        val updated = profile.copy(displayName = "Updated")
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        val savedSlot = slot<List<NightscoutProfile>>()
        coEvery { mockDataStore.save(capture(savedSlot)) } just Runs
        justRun { mockFactory.invalidate(any()) }

        repository.saveProfile(updated)

        assertEquals(1, savedSlot.captured.size)
        assertEquals("Updated", savedSlot.captured[0].displayName)
    }

    @Test
    fun `saveProfile invalidates api factory cache`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        coJustRun { mockDataStore.save(any()) }
        justRun { mockFactory.invalidate(any()) }

        repository.saveProfile(profile)

        verify { mockFactory.invalidate(profile.baseUrl) }
    }

    // endregion

    // region deleteProfile

    @Test
    fun `deleteProfile removes profile from dataStore`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        val savedSlot = slot<List<NightscoutProfile>>()
        coEvery { mockDataStore.save(capture(savedSlot)) } just Runs

        repository.deleteProfile("test-id")

        assertTrue(savedSlot.captured.isEmpty())
    }

    @Test
    fun `deleteProfile clears activeProfileId when deleted profile was active`() = runTest {
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile))
        coJustRun { mockDataStore.save(any()) }
        repository.setActiveProfile("test-id")

        repository.deleteProfile("test-id")

        assertNull(repository.activeProfileId.value)
    }

    @Test
    fun `deleteProfile does not clear activeProfileId when different profile is deleted`() = runTest {
        val other = profile.copy(id = "other-id", displayName = "Other")
        every { mockDataStore.profilesFlow } returns flowOf(listOf(profile, other))
        coJustRun { mockDataStore.save(any()) }
        repository.setActiveProfile("test-id")

        repository.deleteProfile("other-id")

        assertEquals("test-id", repository.activeProfileId.value)
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
