package com.autosugar.data.repository

import com.autosugar.data.model.GlucoseUnit
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.network.NightscoutApi
import com.autosugar.data.network.NightscoutApiFactory
import com.autosugar.data.network.dto.EntryDto
import com.autosugar.data.storage.ProfileDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val repository = NightscoutRepository(mockDataStore, mockFactory)

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
}
