package de.autosugar.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.autosugar.R
import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.MG_DL_PER_MMOL_L
import java.util.Locale
import kotlin.math.roundToInt

class GlucoseAlertManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "glucose_alerts"
        private const val NOTIF_HIGH = 1001
        private const val NOTIF_LOW = 1002
        private const val NOTIF_PREDICTED_HIGH = 1003
        private const val NOTIF_PREDICTED_LOW = 1004
        private const val NOTIF_MONITORING_STOPPED = 1005

        /**
         * Derives a stable notification id unique per (alert type, profile) so that two
         * profiles' alerts of the same type do not replace one another. The per-type base
         * is scaled well above the 16-bit profile hash so different types never collide.
         */
        internal fun notifId(base: Int, profileId: String): Int =
            base * 100_000 + (profileId.hashCode() and 0xFFFF)

        /**
         * Whether an alert posted right now would actually reach the driver.
         *
         * Two independent switches can swallow every alert without any error surfacing: the
         * app-level notification permission (denied by default on Android 13+, and revoked
         * automatically for apps the user has not opened in months) and the alert channel itself,
         * which the user can block on its own. Alerting is opt-in per profile, so a profile can
         * sit with its toggle on for months while neither the phone nor the car ever shows
         * anything — callers use this to say so instead of letting it fail silently.
         */
        fun alertsDeliverable(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Null means the channel has not been created yet — the car app has never run on this
            // install — which is not the same as the user having blocked it.
            val channel = nm.getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val carNm = CarNotificationManager.from(context)

    init {
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
        )
    }

    fun sendHighAlert(profileId: String, profileName: String, sgv: Double, unit: GlucoseUnit) {
        post(
            id = notifId(NOTIF_HIGH, profileId),
            title = titled(profileName, R.string.notif_title_high),
            text = formatValue(sgv, unit),
        )
    }

    fun sendLowAlert(profileId: String, profileName: String, sgv: Double, unit: GlucoseUnit) {
        post(
            id = notifId(NOTIF_LOW, profileId),
            title = titled(profileName, R.string.notif_title_low),
            text = formatValue(sgv, unit),
        )
    }

    fun sendPredictedHighAlert(profileId: String, profileName: String, projectedSgv: Double, unit: GlucoseUnit) {
        post(
            id = notifId(NOTIF_PREDICTED_HIGH, profileId),
            title = titled(profileName, R.string.notif_title_predicted_high),
            text = context.getString(R.string.notif_text_predicted, formatValue(projectedSgv, unit)),
        )
    }

    fun sendPredictedLowAlert(profileId: String, profileName: String, projectedSgv: Double, unit: GlucoseUnit) {
        post(
            id = notifId(NOTIF_PREDICTED_LOW, profileId),
            title = titled(profileName, R.string.notif_title_predicted_low),
            text = context.getString(R.string.notif_text_predicted, formatValue(projectedSgv, unit)),
        )
    }

    /**
     * Tells the driver that alerting itself has stopped — currently only when Android caps the
     * monitor's foreground-service budget. Not tied to a profile: monitoring stops for all of them
     * at once, so it carries no profile name and a single notification id.
     */
    fun sendMonitoringStoppedAlert() {
        post(
            id = notifId(NOTIF_MONITORING_STOPPED, ""),
            title = context.getString(R.string.notif_title_monitoring_stopped),
            text = context.getString(R.string.notif_text_monitoring_stopped),
        )
    }

    /** Prefixes the alert title with the profile name so the driver knows whose reading it is. */
    private fun titled(profileName: String, titleRes: Int): String {
        val title = context.getString(titleRes)
        return if (profileName.isBlank()) title else "$profileName · $title"
    }

    private fun formatValue(sgv: Double, unit: GlucoseUnit): String {
        val value = when (unit) {
            GlucoseUnit.MG_DL  -> sgv.roundToInt().toString()
            GlucoseUnit.MMOL_L -> "%.1f".format(Locale.US, sgv / MG_DL_PER_MMOL_L)
        }
        val label = when (unit) {
            GlucoseUnit.MG_DL  -> context.getString(R.string.label_unit_mgdl)
            GlucoseUnit.MMOL_L -> context.getString(R.string.label_unit_mmoll)
        }
        return "$value $label"
    }

    internal open fun buildNotification(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_profile_medical)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

    // Posted via CarNotificationManager (not the plain NotificationManager) so the alert is
    // mirrored onto the Android Auto screen, not just the phone's notification tray.
    internal open fun post(id: Int, title: String, text: String) {
        try {
            carNm.notify(id, buildNotification(title, text))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+ — alerts silently suppressed
        }
    }
}
