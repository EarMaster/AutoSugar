package de.autosugar.ui.settings

import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val profile1 = NightscoutProfile(
        id = "id-1", displayName = "Alice", baseUrl = "https://alice.ns.io",
        apiToken = "", unit = GlucoseUnit.MG_DL, alertsEnabled = false,
    )
    private val profile2 = NightscoutProfile(
        id = "id-2", displayName = "Bob", baseUrl = "https://bob.ns.io",
        apiToken = "", unit = GlucoseUnit.MMOL_L, alertsEnabled = true,
    )

    private val mockRepository = mockk<NightscoutRepository>()
    private val mockAppPrefs = mockk<AppPreferencesDataStore>()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.profilesFlow } returns flowOf(listOf(profile1, profile2))
        every { mockAppPrefs.refreshIntervalSeconds } returns flowOf(60)
        viewModel = SettingsViewModel(mockRepository, mockAppPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `profiles StateFlow emits repository profiles`() = runTest {
        backgroundScope.launch { viewModel.profiles.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(profile1, profile2), viewModel.profiles.value)
    }

    @Test
    fun `refreshIntervalSeconds StateFlow emits appPrefs value`() = runTest {
        backgroundScope.launch { viewModel.refreshIntervalSeconds.collect {} }
        advanceUntilIdle()
        assertEquals(60, viewModel.refreshIntervalSeconds.value)
    }

    @Test
    fun `setAlertsEnabled saves all profiles with toggled flag`() = runTest {
        backgroundScope.launch { viewModel.profiles.collect {} }
        advanceUntilIdle()

        val savedSlot = slot<List<NightscoutProfile>>()
        coJustRun { mockRepository.saveAll(capture(savedSlot)) }

        viewModel.setAlertsEnabled("id-1", enabled = true)
        advanceUntilIdle()

        val saved = savedSlot.captured
        assertEquals(true, saved.find { it.id == "id-1" }?.alertsEnabled)
        assertEquals(true, saved.find { it.id == "id-2" }?.alertsEnabled)
    }

    @Test
    fun `setAlertsEnabled does not change other profiles`() = runTest {
        backgroundScope.launch { viewModel.profiles.collect {} }
        advanceUntilIdle()

        val savedSlot = slot<List<NightscoutProfile>>()
        coJustRun { mockRepository.saveAll(capture(savedSlot)) }

        viewModel.setAlertsEnabled("id-2", enabled = false)
        advanceUntilIdle()

        val saved = savedSlot.captured
        assertEquals(false, saved.find { it.id == "id-1" }?.alertsEnabled)
        assertEquals(false, saved.find { it.id == "id-2" }?.alertsEnabled)
    }

    @Test
    fun `saveOrder delegates to repository saveAll`() = runTest {
        val orderedSlot = slot<List<NightscoutProfile>>()
        coJustRun { mockRepository.saveAll(capture(orderedSlot)) }

        val reordered = listOf(profile2, profile1)
        viewModel.saveOrder(reordered)
        advanceUntilIdle()

        assertEquals(reordered, orderedSlot.captured)
        coVerify(exactly = 1) { mockRepository.saveAll(reordered) }
    }

    @Test
    fun `setRefreshInterval delegates to appPrefs`() = runTest {
        val intervalSlot = slot<Int>()
        coJustRun { mockAppPrefs.setRefreshInterval(capture(intervalSlot)) }

        viewModel.setRefreshInterval(120)
        advanceUntilIdle()

        assertEquals(120, intervalSlot.captured)
        coVerify(exactly = 1) { mockAppPrefs.setRefreshInterval(120) }
    }
}
