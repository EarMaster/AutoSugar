package com.autosugar.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.autosugar.R
import com.autosugar.data.model.GlucoseUnit

class GlucoseAlertManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "glucose_alerts"
        private const val NOTIF_HIGH = 1001
        private const val NOTIF_LOW = 1002
        private const val NOTIF_PREDICTED_HIGH = 1003
        private const val NOTIF_PREDICTED_LOW = 1004
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

    fun sendHighAlert(sgv: Int, unit: GlucoseUnit) {
        post(
            id = NOTIF_HIGH,
            title = context.getString(R.string.notif_title_high),
            text = formatValue(sgv, unit),
        )
    }

    fun sendLowAlert(sgv: Int, unit: GlucoseUnit) {
        post(
            id = NOTIF_LOW,
            title = context.getString(R.string.notif_title_low),
            text = formatValue(sgv, unit),
        )
    }

    fun sendPredictedHighAlert(projectedSgv: Int, unit: GlucoseUnit) {
        post(
            id = NOTIF_PREDICTED_HIGH,
            title = context.getString(R.string.notif_title_predicted_high),
            text = context.getString(R.string.notif_text_predicted, formatValue(projectedSgv, unit)),
        )
    }

    fun sendPredictedLowAlert(projectedSgv: Int, unit: GlucoseUnit) {
        post(
            id = NOTIF_PREDICTED_LOW,
            title = context.getString(R.string.notif_title_predicted_low),
            text = context.getString(R.string.notif_text_predicted, formatValue(projectedSgv, unit)),
        )
    }

    private fun formatValue(sgv: Int, unit: GlucoseUnit): String {
        val value = when (unit) {
            GlucoseUnit.MG_DL  -> sgv.toString()
            GlucoseUnit.MMOL_L -> "%.1f".format(sgv / 18.0)
        }
        val label = when (unit) {
            GlucoseUnit.MG_DL  -> context.getString(R.string.label_unit_mgdl)
            GlucoseUnit.MMOL_L -> context.getString(R.string.label_unit_mmoll)
        }
        return "$value $label"
    }

    private fun post(id: Int, title: String, text: String) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_profile_medical)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+ — alerts silently suppressed
        }
    }
}
