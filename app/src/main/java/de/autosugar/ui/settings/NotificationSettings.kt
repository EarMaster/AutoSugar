package de.autosugar.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens AutoSugar's notification settings, where both the app-level switch and the glucose alert
 * channel can be turned back on.
 *
 * This is the only route back once notifications are off: a runtime permission the user has denied
 * twice can no longer be requested — `launch()` returns "denied" without ever showing a dialog —
 * and a channel the user blocked by hand cannot be unblocked by the app at all.
 */
internal fun openNotificationSettings(context: Context) {
    val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(appNotifications)
    } catch (_: ActivityNotFoundException) {
        // Some OEM builds do not expose the per-app notification screen; the app details page
        // always exists and gets the user to the same switches in one more tap.
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
