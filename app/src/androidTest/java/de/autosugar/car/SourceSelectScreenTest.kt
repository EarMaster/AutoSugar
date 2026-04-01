package de.autosugar.car

import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSelectScreenTest {

    private lateinit var carContext: TestCarContext
    private val mockRepository = mockk<NightscoutRepository>()

    private val profile1 = NightscoutProfile(
        id = "id-1", displayName = "Alice", baseUrl = "https://alice.ns.io",
        apiToken = "", unit = GlucoseUnit.MG_DL, alertsEnabled = false,
    )
    private val profile2 = NightscoutProfile(
        id = "id-2", displayName = "Bob", baseUrl = "https://bob.ns.io",
        apiToken = "", unit = GlucoseUnit.MMOL_L, alertsEnabled = true,
    )

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun onGetTemplate_returnsListTemplate() {
        every { mockRepository.profilesFlow } returns emptyFlow()

        val controller = ScreenController(SourceSelectScreen(carContext, mockRepository) {})
        controller.moveToState(Lifecycle.State.STARTED)

        assertTrue(controller.getTemplatesReturned().last() is ListTemplate)
    }

    @Test
    fun onGetTemplate_hasBackHeaderAction() {
        every { mockRepository.profilesFlow } returns emptyFlow()

        val controller = ScreenController(SourceSelectScreen(carContext, mockRepository) {})
        controller.moveToState(Lifecycle.State.STARTED)

        val template = controller.getTemplatesReturned().last() as ListTemplate
        assertEquals(Action.BACK, template.headerAction)
    }

    @Test
    fun onGetTemplate_withProfiles_showsCorrectItemCount() {
        every { mockRepository.profilesFlow } returns flowOf(listOf(profile1, profile2))

        val controller = ScreenController(SourceSelectScreen(carContext, mockRepository) {})
        controller.moveToState(Lifecycle.State.STARTED)

        Thread.sleep(200)

        val template = controller.getTemplatesReturned().last() as ListTemplate
        assertEquals(2, template.singleList?.items?.size)
    }

    @Test
    fun onGetTemplate_profileRowsShowDisplayName() {
        every { mockRepository.profilesFlow } returns flowOf(listOf(profile1, profile2))

        val controller = ScreenController(SourceSelectScreen(carContext, mockRepository) {})
        controller.moveToState(Lifecycle.State.STARTED)

        Thread.sleep(200)

        val template = controller.getTemplatesReturned().last() as ListTemplate
        val titles = template.singleList?.items
            ?.filterIsInstance<Row>()
            ?.map { it.title.toString() }
        assertTrue(titles?.contains("Alice") == true)
        assertTrue(titles?.contains("Bob") == true)
    }
}
