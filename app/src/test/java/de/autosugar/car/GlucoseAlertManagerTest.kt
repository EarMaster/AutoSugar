package de.autosugar.car

import android.app.NotificationManager
import android.content.Context
import de.autosugar.R
import de.autosugar.data.model.GlucoseUnit
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GlucoseAlertManagerTest {

    private val mockNm = mockk<NotificationManager>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        every { mockContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockNm
        every { mockContext.getString(R.string.notif_channel_name) } returns "Glucose Alerts"
        every { mockContext.getString(R.string.notif_channel_desc) } returns "desc"
        every { mockContext.getString(R.string.label_unit_mgdl) } returns "mg/dL"
        every { mockContext.getString(R.string.label_unit_mmoll) } returns "mmol/L"
        every { mockContext.getString(R.string.notif_title_high) } returns "High glucose"
        every { mockContext.getString(R.string.notif_title_low) } returns "Low glucose"
        every { mockContext.getString(R.string.notif_title_predicted_high) } returns "Glucose trending high"
        every { mockContext.getString(R.string.notif_title_predicted_low) } returns "Glucose trending low"
        every { mockContext.getString(R.string.notif_text_predicted, any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val formatArgs = it.invocation.args[1] as Array<Any>
            "Predicted in 15 min: ${formatArgs[0]}"
        }
        every { mockContext.packageName } returns "de.autosugar"
        every { mockContext.applicationInfo } returns mockk(relaxed = true)
        justRun { mockNm.createNotificationChannel(any()) }
    }

    private fun buildManager(): GlucoseAlertManager =
        spyk(GlucoseAlertManager(mockContext)).also { justRun { it.post(any(), any(), any()) } }

    // region value formatting

    @Test
    fun `sendHighAlert formats mg dL value as integer`() {
        val manager = buildManager()
        manager.sendHighAlert(sgv = 210, unit = GlucoseUnit.MG_DL)

        verify { manager.post(1001, any(), match { it.contains("210 mg/dL") }) }
    }

    @Test
    fun `sendLowAlert formats mmol L value with one decimal`() {
        val manager = buildManager()
        manager.sendLowAlert(sgv = 63, unit = GlucoseUnit.MMOL_L)

        verify { manager.post(1002, any(), match { it.contains("3.5 mmol/L") }) }
    }

    // endregion

    // region notification IDs

    @Test
    fun `sendHighAlert and sendLowAlert use distinct notification IDs`() {
        val manager = buildManager()
        val idSlots = mutableListOf<Int>()
        justRun { manager.post(capture(idSlots), any(), any()) }

        manager.sendHighAlert(180, GlucoseUnit.MG_DL)
        manager.sendLowAlert(60, GlucoseUnit.MG_DL)

        assertEquals(2, idSlots.size)
        assertTrue("Expected distinct notification IDs", idSlots[0] != idSlots[1])
    }

    @Test
    fun `all four alert types use distinct notification IDs`() {
        val manager = buildManager()
        val idSlots = mutableListOf<Int>()
        justRun { manager.post(capture(idSlots), any(), any()) }

        manager.sendHighAlert(200, GlucoseUnit.MG_DL)
        manager.sendLowAlert(55, GlucoseUnit.MG_DL)
        manager.sendPredictedHighAlert(195, GlucoseUnit.MG_DL)
        manager.sendPredictedLowAlert(65, GlucoseUnit.MG_DL)

        assertEquals(4, idSlots.distinct().size)
    }

    // endregion

    // region predicted alerts

    @Test
    fun `sendPredictedHighAlert includes predicted value in notification text`() {
        val manager = buildManager()
        manager.sendPredictedHighAlert(projectedSgv = 200, unit = GlucoseUnit.MG_DL)

        verify { manager.post(1003, any(), match { it.contains("200 mg/dL") }) }
    }

    @Test
    fun `sendPredictedLowAlert includes predicted value in notification text`() {
        val manager = buildManager()
        manager.sendPredictedLowAlert(projectedSgv = 60, unit = GlucoseUnit.MG_DL)

        verify { manager.post(1004, any(), match { it.contains("60 mg/dL") }) }
    }

    // endregion

    // region security exception

    @Test
    fun `notify does not throw when SecurityException is raised`() {
        val manager = spyk(GlucoseAlertManager(mockContext))
        every { manager.buildNotification(any(), any()) } returns mockk(relaxed = true)
        every { mockNm.notify(any(), any()) } throws SecurityException("No permission")

        // Should not throw — SecurityException from nm.notify is suppressed inside post()
        manager.sendHighAlert(200, GlucoseUnit.MG_DL)
    }

    // endregion
}
