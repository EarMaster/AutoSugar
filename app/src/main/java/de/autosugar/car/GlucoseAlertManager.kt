package de.autosugar.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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

        /**
         * Derives a stable notification id unique per (alert type, profile) so that two
         * profiles' alerts of the same type do not replace one another. The per-type base
         * is scaled well above the 16-bit profile hash so different types never collide.
         */
        internal fun notifId(base: Int, profileId: String): Int =
            base * 100_000 + (profileId.hashCode() and 0xFFFF)
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

    internal open fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_profile_medical)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

    internal open fun post(id: Int, title: String, text: String) {
        try {
            nm.notify(id, buildNotification(title, text))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+ — alerts silently suppressed
        }
    }
}
