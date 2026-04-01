package de.autosugar.ui.settings

import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.model.ProfileIcon
import de.autosugar.data.repository.NightscoutRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository = mockk<NightscoutRepository>()

    private val existingProfile = NightscoutProfile(
        id = "profile-abc",
        displayName = "Test User",
        baseUrl = "https://test.nightscout.io",
        apiToken = "secret-token",
        unit = GlucoseUnit.MMOL_L,
        icon = ProfileIcon.PERSON,
        alertsEnabled = true,
    )

    private lateinit var viewModel: ProfileEditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProfileEditViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region loadProfile

    @Test
    fun `loadProfile populates all form fields from repository`() = runTest {
        every { mockRepository.profilesFlow } returns flowOf(listOf(existingProfile))

        viewModel.loadProfile("profile-abc")
        advanceUntilIdle()

        assertEquals("Test User", viewModel.displayName.value)
        assertEquals("https://test.nightscout.io", viewModel.baseUrl.value)
        assertEquals("secret-token", viewModel.apiToken.value)
        assertEquals(GlucoseUnit.MMOL_L, viewModel.unit.value)
        assertEquals(ProfileIcon.PERSON, viewModel.icon.value)
        assertTrue(viewModel.alertsEnabled.value)
    }

    @Test
    fun `loadProfile with unknown id leaves fields at defaults`() = runTest {
        every { mockRepository.profilesFlow } returns flowOf(emptyList())

        viewModel.loadProfile("non-existent-id")
        advanceUntilIdle()

        assertEquals("", viewModel.displayName.value)
        assertEquals("", viewModel.baseUrl.value)
    }

    // endregion

    // region testConnection

    @Test
    fun `testConnection sets Loading then TestSuccess on success`() = runTest {
        viewModel.baseUrl.value = existingProfile.baseUrl
        viewModel.apiToken.value = existingProfile.apiToken
        viewModel.unit.value = GlucoseUnit.MG_DL

        val fakeEntry = de.autosugar.data.model.GlucoseEntry(
            sgv = 110, direction = "Flat", dateIso = "2024-01-01T00:00:00Z",
            delta = 2.0, dateMs = 0L,
        )
        coEvery { mockRepository.testConnection(any()) } returns Result.success(fakeEntry)
        coEvery { mockRepository.hasElevatedPermissions(any()) } returns false

        viewModel.testConnection()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProfileEditUiState.TestSuccess)
        assertFalse(viewModel.tokenOverpowered.value)
    }

    @Test
    fun `testConnection sets Error on failure`() = runTest {
        coEvery { mockRepository.testConnection(any()) } returns
            Result.failure(Exception("Network timeout"))

        viewModel.testConnection()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileEditUiState.Error)
        assertEquals("Network timeout", (state as ProfileEditUiState.Error).message)
    }

    @Test
    fun `testConnection sets tokenOverpowered when token has elevated permissions`() = runTest {
        val fakeEntry = de.autosugar.data.model.GlucoseEntry(
            sgv = 90, direction = "Flat", dateIso = "2024-01-01T00:00:00Z",
            delta = null, dateMs = 0L,
        )
        coEvery { mockRepository.testConnection(any()) } returns Result.success(fakeEntry)
        coEvery { mockRepository.hasElevatedPermissions(any()) } returns true

        viewModel.testConnection()
        advanceUntilIdle()

        assertTrue(viewModel.tokenOverpowered.value)
    }

    // endregion

    // region save

    @Test
    fun `save calls repository saveProfile and sets Saved state`() = runTest {
        viewModel.displayName.value = "New Profile"
        viewModel.baseUrl.value = "https://new.nightscout.io"

        val savedSlot = slot<NightscoutProfile>()
        coJustRun { mockRepository.saveProfile(capture(savedSlot)) }

        viewModel.save()
        advanceUntilIdle()

        assertEquals(ProfileEditUiState.Saved, viewModel.uiState.value)
        assertEquals("New Profile", savedSlot.captured.displayName)
        assertEquals("https://new.nightscout.io", savedSlot.captured.baseUrl)
    }

    @Test
    fun `save uses editingId when editing an existing profile`() = runTest {
        every { mockRepository.profilesFlow } returns flowOf(listOf(existingProfile))
        viewModel.loadProfile("profile-abc")
        advanceUntilIdle()

        val savedSlot = slot<NightscoutProfile>()
        coJustRun { mockRepository.saveProfile(capture(savedSlot)) }

        viewModel.save()
        advanceUntilIdle()

        assertEquals("profile-abc", savedSlot.captured.id)
    }

    // endregion

    // region delete

    @Test
    fun `delete calls repository deleteProfile and sets Deleted state`() = runTest {
        every { mockRepository.profilesFlow } returns flowOf(listOf(existingProfile))
        viewModel.loadProfile("profile-abc")
        advanceUntilIdle()

        coJustRun { mockRepository.deleteProfile(any()) }

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(ProfileEditUiState.Deleted, viewModel.uiState.value)
        coVerify(exactly = 1) { mockRepository.deleteProfile("profile-abc") }
    }

    @Test
    fun `delete does nothing when no profile is loaded`() = runTest {
        viewModel.delete()
        advanceUntilIdle()

        assertEquals(ProfileEditUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 0) { mockRepository.deleteProfile(any()) }
    }

    // endregion

    // region clearState

    @Test
    fun `clearState resets uiState to Idle`() = runTest {
        coEvery { mockRepository.testConnection(any()) } returns
            Result.failure(Exception("err"))

        viewModel.testConnection()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProfileEditUiState.Error)

        viewModel.clearState()
        assertEquals(ProfileEditUiState.Idle, viewModel.uiState.value)
    }

    // endregion
}
