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
    fun `setProfileEnabled delegates to repository atomic toggle`() = runTest {
        val idSlot = slot<String>()
        val enabledSlot = slot<Boolean>()
        coJustRun { mockRepository.setProfileEnabled(capture(idSlot), capture(enabledSlot)) }

        viewModel.setProfileEnabled("id-1", enabled = false)
        advanceUntilIdle()

        assertEquals("id-1", idSlot.captured)
        assertEquals(false, enabledSlot.captured)
        coVerify(exactly = 1) { mockRepository.setProfileEnabled("id-1", false) }
    }

    @Test
    fun `profiles StateFlow keeps disabled profiles so they stay editable`() = runTest {
        // The car reads enabledProfilesFlow; the settings list must still show a source the
        // user switched off, or there would be no way to switch it back on.
        every { mockRepository.profilesFlow } returns
            flowOf(listOf(profile1.copy(enabled = false), profile2))
        val vm = SettingsViewModel(mockRepository, mockAppPrefs)
        backgroundScope.launch { vm.profiles.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.profiles.value.size)
        assertEquals(false, vm.profiles.value[0].enabled)
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
