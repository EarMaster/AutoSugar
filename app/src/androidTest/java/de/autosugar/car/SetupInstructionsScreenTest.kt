package de.autosugar.car

import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupInstructionsScreenTest {

    private lateinit var carContext: TestCarContext

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    }

    private fun template(): LongMessageTemplate {
        val controller = ScreenController(SetupInstructionsScreen(carContext))
        controller.moveToState(Lifecycle.State.STARTED)
        return controller.getTemplatesReturned().last() as LongMessageTemplate
    }

    /**
     * LongMessageTemplate is the host-enforced parked-only template. Using anything else
     * here would let the phone-setup instructions render while driving.
     */
    @Test
    fun onGetTemplate_returnsLongMessageTemplate() {
        assertTrue(template().message.toString().isNotBlank())
    }

    @Test
    fun onGetTemplate_hasBackHeaderAction() {
        assertEquals(Action.BACK, template().headerAction)
    }

    @Test
    fun onGetTemplate_allActionsAreParkedOnly() {
        val actions = template().actions
        assertFalse(actions.isEmpty())
        assertTrue(actions.all { it.onClickDelegate!!.isParkedOnly })
    }
}
