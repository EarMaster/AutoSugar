package de.autosugar.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import de.autosugar.R
import de.autosugar.data.model.GlucoseUnit
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class GlucoseAlertManagerTest {

    private companion object {
        const val PROFILE_ID = "test-profile"
        const val PROFILE_NAME = "Test User"
    }

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
        every { mockContext.getString(R.string.notif_title_monitoring_stopped) } returns "Glucose monitoring stopped"
        every { mockContext.getString(R.string.notif_text_monitoring_stopped) } returns "Open AutoSugar on the car screen to resume alerts"
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
        manager.sendHighAlert(PROFILE_ID, PROFILE_NAME, sgv = 210.0, unit = GlucoseUnit.MG_DL)

        verify { manager.post(any(), any(), match { it.contains("210 mg/dL") }) }
    }

    @Test
    fun `sendLowAlert formats mmol L value with one decimal`() {
        val manager = buildManager()
        manager.sendLowAlert(PROFILE_ID, PROFILE_NAME, sgv = 63.0, unit = GlucoseUnit.MMOL_L)

        verify { manager.post(any(), any(), match { it.contains("3.5 mmol/L") }) }
    }

    @Test
    fun `sendHighAlert rounds fractional sgv for mg dL`() {
        // Regression for #13: fractional sgv values (e.g. from Juggluco) must round for display
        val manager = buildManager()
        manager.sendHighAlert(PROFILE_ID, PROFILE_NAME, sgv = 210.6, unit = GlucoseUnit.MG_DL)

        verify { manager.post(any(), any(), match { it.contains("211 mg/dL") }) }
    }

    @Test
    fun `alert title includes the profile name`() {
        val manager = buildManager()
        manager.sendHighAlert(PROFILE_ID, PROFILE_NAME, sgv = 210.0, unit = GlucoseUnit.MG_DL)

        verify { manager.post(any(), match { it.contains(PROFILE_NAME) }, any()) }
    }

    // endregion

    // region notification IDs

    @Test
    fun `sendHighAlert and sendLowAlert use distinct notification IDs`() {
        val manager = buildManager()
        val idSlots = mutableListOf<Int>()
        justRun { manager.post(capture(idSlots), any(), any()) }

        manager.sendHighAlert(PROFILE_ID, PROFILE_NAME, 180.0, GlucoseUnit.MG_DL)
        manager.sendLowAlert(PROFILE_ID, PROFILE_NAME, 60.0, GlucoseUnit.MG_DL)

        assertEquals(2, idSlots.size)
        assertTrue("Expected distinct notification IDs", idSlots[0] != idSlots[1])
    }


    @Test
    fun `same alert type for different profiles uses distinct notification IDs`() {
        val manager = buildManager()
        val idSlots = mutableListOf<Int>()
        justRun { manager.post(capture(idSlots), any(), any()) }

        manager.sendLowAlert("profile-a", "Alice", 60.0, GlucoseUnit.MG_DL)
        manager.sendLowAlert("profile-b", "Bob", 60.0, GlucoseUnit.MG_DL)

        assertEquals("Two profiles must not share a notification ID", 2, idSlots.distinct().size)
    }

    // endregion

    // region monitoring stopped

    @Test
    fun `sendMonitoringStoppedAlert does not collide with any per-profile alert id`() {
        val manager = buildManager()
        val idSlots = mutableListOf<Int>()
        justRun { manager.post(capture(idSlots), any(), any()) }

        manager.sendHighAlert(PROFILE_ID, PROFILE_NAME, 200.0, GlucoseUnit.MG_DL)
        manager.sendLowAlert(PROFILE_ID, PROFILE_NAME, 55.0, GlucoseUnit.MG_DL)
        manager.sendMonitoringStoppedAlert()

        assertEquals(3, idSlots.distinct().size)
    }

    @Test
    fun `sendMonitoringStoppedAlert carries no profile name`() {
        val manager = buildManager()
        manager.sendMonitoringStoppedAlert()

        verify { manager.post(any(), match { !it.contains("·") }, any()) }
    }

    // endregion

    // region alertsDeliverable

    private fun mockNotificationsEnabled(enabled: Boolean) {
        val compat = mockk<NotificationManagerCompat>(relaxed = true)
        every { compat.areNotificationsEnabled() } returns enabled
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(mockContext) } returns compat
    }

    @After
    fun tearDown() {
        unmockkStatic(NotificationManagerCompat::class)
    }

    @Test
    fun `alertsDeliverable is false when notifications are switched off for the app`() {
        mockNotificationsEnabled(false)

        assertFalse(GlucoseAlertManager.alertsDeliverable(mockContext))
    }

    @Test
    fun `alertsDeliverable is false when the alert channel itself is blocked`() {
        mockNotificationsEnabled(true)
        val channel = mockk<NotificationChannel>()
        every { channel.importance } returns NotificationManager.IMPORTANCE_NONE
        every { mockNm.getNotificationChannel(any()) } returns channel

        assertFalse(GlucoseAlertManager.alertsDeliverable(mockContext))
    }

    @Test
    fun `alertsDeliverable is true when the channel has not been created yet`() {
        // The car app has never run on this install, so there is no channel to be blocked —
        // that must not be reported as the user having turned alerts off.
        mockNotificationsEnabled(true)
        every { mockNm.getNotificationChannel(any()) } returns null

        assertTrue(GlucoseAlertManager.alertsDeliverable(mockContext))
    }

    @Test
    fun `alertsDeliverable is true when the app and the channel are both enabled`() {
        mockNotificationsEnabled(true)
        val channel = mockk<NotificationChannel>()
        every { channel.importance } returns NotificationManager.IMPORTANCE_HIGH
        every { mockNm.getNotificationChannel(any()) } returns channel

        assertTrue(GlucoseAlertManager.alertsDeliverable(mockContext))
    }

    // endregion

    // region security exception

    @Test
    fun `notify does not throw when SecurityException is raised`() {
        val manager = spyk(GlucoseAlertManager(mockContext))
        every { manager.buildNotification(any(), any()) } returns mockk(relaxed = true)
        every { mockNm.notify(any(), any()) } throws SecurityException("No permission")

        // Should not throw — SecurityException from nm.notify is suppressed inside post()
        manager.sendHighAlert(PROFILE_ID, PROFILE_NAME, 200.0, GlucoseUnit.MG_DL)
    }

    // endregion
}
