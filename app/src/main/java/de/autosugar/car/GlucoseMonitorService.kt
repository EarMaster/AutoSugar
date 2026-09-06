package de.autosugar.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import dagger.hilt.android.AndroidEntryPoint
import de.autosugar.R
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import de.autosugar.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Polls every alert-enabled profile for as long as Android Auto is *connected*, rather than only
 * while AutoSugar happens to be the car app currently on screen.
 *
 * The alert loop used to live on [AutoSugarSession]'s lifecycle scope, which the host tears down as
 * soon as the user switches to Maps or any other car app. That inverted the feature: alerts fired
 * only while the driver was already looking at the readings, and went silent exactly when a
 * notification was the only way to surface a high or low. Polling therefore runs here, in a started
 * foreground service whose lifetime is tied to the car *connection* reported by [CarConnection],
 * not to screen visibility.
 */
@AndroidEntryPoint
class GlucoseMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "glucose_monitor"
        private const val ONGOING_NOTIF_ID = 1

        /**
         * Starts the monitor, returning whether it was accepted. Android 12+ rejects
         * foreground-service starts it considers to come from the background, so callers are
         * expected to handle `false` rather than lose alerting outright.
         */
        fun start(context: Context): Boolean {
            val appContext = context.applicationContext
            val intent = Intent(appContext, GlucoseMonitorService::class.java)
            return try {
                ContextCompat.startForegroundService(appContext, intent)
                true
            } catch (_: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException.
                false
            } catch (_: SecurityException) {
                // Foreground service type not permitted for this app.
                false
            }
        }
    }

    @Inject lateinit var repository: NightscoutRepository
    @Inject lateinit var appPrefs: AppPreferencesDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoring = false

    private val carConnection by lazy { CarConnection(this) }

    private val connectionObserver = Observer<Int> { type ->
        // The head unit is gone, so there is no driver left to alert: stop rather than keep
        // polling Nightscout — and holding a foreground service — for the rest of the day.
        if (type == CarConnection.CONNECTION_TYPE_NOT_CONNECTED) stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First thing, before anything that could throw or block: the system kills the process if a
        // service started with startForegroundService() has not posted its notification within ~5s.
        ServiceCompat.startForeground(
            this,
            ONGOING_NOTIF_ID,
            buildOngoingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        // Repeated starts (the session is recreated every time the user re-opens the car app) must
        // not stack up a second polling loop on top of the running one.
        if (!monitoring) {
            monitoring = true
            carConnection.type.observeForever(connectionObserver)
            startMonitoring()
        }
        return START_STICKY
    }

    /**
     * Android 15+ caps a `dataSync` foreground service at six hours per 24-hour window. Stop
     * cleanly when that budget runs out — the system raises a fatal `RemoteServiceException`
     * otherwise — and say so on the car screen, so alerting never goes quiet unannounced.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        GlucoseAlertManager(this).sendMonitoringStoppedAlert()
        stopSelf()
    }

    override fun onDestroy() {
        if (monitoring) carConnection.type.removeObserver(connectionObserver)
        scope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        // One monitor instance for the life of the service, so per-profile alert cooldowns survive
        // a refresh-interval change restarting the loop below.
        val monitor = BackgroundAlertMonitor(this, repository)
        scope.launch {
            appPrefs.refreshIntervalSeconds.collectLatest { intervalSeconds ->
                while (isActive) {
                    monitor.checkAll()
                    delay(intervalSeconds * 1000L)
                }
            }
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_monitor_name),
                // The ongoing notification is a status indicator, not an alert: keep it silent so
                // it never competes with the glucose alerts themselves.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_monitor_desc)
                setShowBadge(false)
            }
        )
    }

    private fun buildOngoingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_profile_medical)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(getString(R.string.notif_monitor_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
}
