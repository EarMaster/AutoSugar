package de.autosugar.car

import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoProfilesScreenTest {

    private lateinit var carContext: TestCarContext
    private val mockRepository = mockk<NightscoutRepository>()
    private val mockAppPrefs = mockk<AppPreferencesDataStore>()

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        every { mockRepository.profilesFlow } returns emptyFlow()
    }

    @Test
    fun onGetTemplate_returnsMessageTemplate() {
        val controller = ScreenController(NoProfilesScreen(carContext, mockRepository, mockAppPrefs))
        controller.moveToState(Lifecycle.State.STARTED)

        assertTrue(controller.getTemplatesReturned().last() is MessageTemplate)
    }

    @Test
    fun onGetTemplate_templateHasTitle() {
        val controller = ScreenController(NoProfilesScreen(carContext, mockRepository, mockAppPrefs))
        controller.moveToState(Lifecycle.State.STARTED)

        val template = controller.getTemplatesReturned().last() as MessageTemplate
        assertFalse(template.title?.toString().isNullOrBlank())
    }

    @Test
    fun onGetTemplate_templateHasNonEmptyMessage() {
        val controller = ScreenController(NoProfilesScreen(carContext, mockRepository, mockAppPrefs))
        controller.moveToState(Lifecycle.State.STARTED)

        val template = controller.getTemplatesReturned().last() as MessageTemplate
        assertFalse(template.message.toString().isBlank())
    }

    /**
     * Setup is a phone-side task and must not be reachable while driving (car app quality
     * guidelines IT-1/VI-1). `isParkedOnly` is the flag the host keys off to block the
     * action, so this is the regression guard for the Play rejection that introduced it.
     */
    @Test
    fun onGetTemplate_setupActionIsParkedOnly() {
        val controller = ScreenController(NoProfilesScreen(carContext, mockRepository, mockAppPrefs))
        controller.moveToState(Lifecycle.State.STARTED)

        val template = controller.getTemplatesReturned().last() as MessageTemplate
        assertEquals(1, template.actions.size)
        assertTrue(template.actions.single().onClickDelegate!!.isParkedOnly)
    }
}
