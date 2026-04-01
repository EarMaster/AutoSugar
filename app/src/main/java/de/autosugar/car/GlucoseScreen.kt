package de.autosugar.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import de.autosugar.R
import de.autosugar.data.model.GlucoseEntry
import de.autosugar.data.model.GlucoseThresholds
import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GlucoseScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
    private var activeProfileId: String,
) : Screen(carContext) {

    private var profiles: List<NightscoutProfile> = emptyList()
    private var entry: GlucoseEntry? = null
    private var history: List<GlucoseEntry> = emptyList()
    private var lastFetchedMs: Long = 0L
    private var isLoading = true
    private var thresholds: GlucoseThresholds = GlucoseThresholds(
        bgLow = 70, bgTargetBottom = 70, bgTargetTop = 180, bgHigh = 180,
    )
    private var errorMessage: String? = null
    private var pollingJob: Job? = null

    private data class GraphCacheKey(
        val timestamps: List<Long>,
        val unit: GlucoseUnit,
        val bgTargetBottom: Float,
        val bgTargetTop: Float,
    )
    private var cachedGraphKey: GraphCacheKey? = null
    private var cachedGraphIcon: CarIcon? = null

    private val alertManager = GlucoseAlertManager(carContext)
    private val alertCooldownMs = 15 * 60_000L
    private var lastHighAlertMs = 0L
    private var lastLowAlertMs = 0L
    private var lastPredictedHighAlertMs = 0L
    private var lastPredictedLowAlertMs = 0L

    init {
        lifecycleScope.launch {
            repository.profilesFlow.collect { updated ->
                profiles = updated
                invalidate()
            }
        }

        lifecycleScope.launch {
            appPrefs.refreshIntervalSeconds.collect { intervalSeconds ->
                pollingJob?.cancel()
                pollingJob = lifecycleScope.launch {
                    while (isActive) {
                        fetch()
                        delay(intervalSeconds * 1000L)
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        isLoading = entry == null
        errorMessage = null
        coroutineScope {
            val entryResult = async { repository.getCurrentEntry(activeProfileId) }
            val historyResult = async { repository.getHistory(activeProfileId, count = 36) }
            val thresholdsResult = async { repository.getThresholds(activeProfileId) }
            entryResult.await()
                .onSuccess { result ->
                    entry = result
                    lastFetchedMs = System.currentTimeMillis()
                    isLoading = false
                }
                .onFailure { e ->
                    isLoading = false
                    errorMessage = e.message ?: carContext.getString(R.string.error_fetch_failed)
                }
            historyResult.await()
                .onSuccess { h -> history = h.sortedBy { it.dateMs } }
            thresholdsResult.await()
                .onSuccess { t -> thresholds = t }
        }
        checkAlerts()
        invalidate()
    }

    private fun checkAlerts() {
        val currentEntry = entry ?: return
        val profile = profiles.find { it.id == activeProfileId } ?: return
        if (!profile.alertsEnabled) return

        val sgv = currentEntry.sgv
        val now = System.currentTimeMillis()

        if (sgv > thresholds.bgHigh && now - lastHighAlertMs > alertCooldownMs) {
            alertManager.sendHighAlert(sgv, profile.unit)
            lastHighAlertMs = now
        }
        if (sgv < thresholds.bgLow && now - lastLowAlertMs > alertCooldownMs) {
            alertManager.sendLowAlert(sgv, profile.unit)
            lastLowAlertMs = now
        }

        val delta = currentEntry.delta ?: return
        val projected15 = sgv + delta * 3  // 3 readings × ~5 min = 15 min ahead

        if (projected15 > thresholds.bgHigh && sgv <= thresholds.bgHigh &&
            now - lastPredictedHighAlertMs > alertCooldownMs
        ) {
            alertManager.sendPredictedHighAlert(projected15.toInt(), profile.unit)
            lastPredictedHighAlertMs = now
        }
        if (projected15 < thresholds.bgLow && sgv >= thresholds.bgLow &&
            now - lastPredictedLowAlertMs > alertCooldownMs
        ) {
            alertManager.sendPredictedLowAlert(projected15.toInt(), profile.unit)
            lastPredictedLowAlertMs = now
        }
    }

    override fun onGetTemplate(): Template {
        return if (carContext.carAppApiLevel >= 6 && profiles.size in 2..4) {
            buildTabTemplate()
        } else {
            buildPaneTemplate()
        }
    }

    // region TabTemplate (CarApi >= 6, 2–5 profiles)

    @RequiresCarApi(6)
    private fun buildTabTemplate(): Template {
        val activeProfile = profiles.find { it.id == activeProfileId }
        val unit = activeProfile?.unit ?: GlucoseUnit.MG_DL

        val tabContents = TabContents.Builder(
            PaneTemplate.Builder(buildPane(unit))
                .setTitle(activeProfile?.displayName ?: "")
                .build()
        ).build()

        val callback = object : TabTemplate.TabCallback {
            override fun onTabSelected(contentId: String) = switchTo(contentId)
        }
        val builder = TabTemplate.Builder(callback)
            .setHeaderAction(Action.APP_ICON)
            .setActiveTabContentId(activeProfileId)
            .setTabContents(tabContents)

        profiles.forEach { profile ->
            builder.addTab(
                Tab.Builder()
                    .setTitle(profile.displayName)
                    .setIcon(CarIcon.Builder(
                        IconCompat.createWithResource(carContext, profile.icon.resId)
                    ).build())
                    .setContentId(profile.id)
                    .build()
            )
        }

        return builder.build()
    }

    // endregion

    // region PaneTemplate (fallback: 1 profile, >5 profiles, or CarApi < 6)

    private fun buildPaneTemplate(): Template {
        val activeProfile = profiles.find { it.id == activeProfileId }
        val unit = activeProfile?.unit ?: GlucoseUnit.MG_DL
        val title = activeProfile?.displayName ?: carContext.getString(R.string.app_name)

        return PaneTemplate.Builder(buildPane(unit))
            .setTitle(title)
            .setActionStrip(buildActionStrip())
            .build()
    }

    private fun buildActionStrip(): ActionStrip {
        val builder = ActionStrip.Builder()
        return when {
            profiles.size > 4 -> builder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_switch_source))
                    .setOnClickListener {
                        screenManager.push(
                            SourceSelectScreen(carContext, repository) { id -> switchTo(id) }
                        )
                    }
                    .build()
            ).build()
            profiles.size in 2..5 -> {
                // Numbered icon fallback when TabTemplate is unavailable
                profiles.forEachIndexed { index, profile ->
                    builder.addAction(
                        Action.Builder()
                            .setIcon(profileNumberIcon(index + 1, profile.id == activeProfileId))
                            .setOnClickListener { switchTo(profile.id) }
                            .build()
                    )
                }
                builder.build()
            }
            else -> builder.addAction(Action.APP_ICON).build()
        }
    }

    private fun profileNumberIcon(number: Int, active: Boolean): CarIcon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (active) Color.WHITE else Color.argb(150, 200, 200, 200)
            textSize = size * 0.65f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textY = size / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        c.drawText(number.toString(), size / 2f, textY, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    // endregion

    // region Pane content (shared by both templates)

    private fun buildPane(unit: GlucoseUnit): Pane = when {
        isLoading -> Pane.Builder().setLoading(true).build()

        errorMessage != null && entry == null -> Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.error_fetch_failed))
                    .addText(errorMessage ?: "")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_retry))
                    .setOnClickListener { lifecycleScope.launch { fetch() } }
                    .build()
            )
            .build()

        else -> {
            val e = entry!!
            val now = System.currentTimeMillis()
            val statsRow = Row.Builder()
                .setTitle(
                    if (errorMessage != null)
                        carContext.getString(R.string.label_stale_reading, ageString(now - e.dateMs))
                    else
                        carContext.getString(R.string.label_reading, ageString(now - e.dateMs))
                )
            if (errorMessage != null) {
                statsRow.addText(errorMessage!!)
            } else if (lastFetchedMs > 0) {
                statsRow.addText(carContext.getString(R.string.label_received, ageString(now - lastFetchedMs)))
            }
            val pane = Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle("${e.displayValue(unit)} ${unitLabel(unit)}")
                        .setImage(trendArrowIcon(e.direction), Row.IMAGE_TYPE_LARGE)
                        .addText("${e.displayDelta(unit) ?: "-"} ${unitLabel(unit)}")
                        .build()
                )
                .addRow(statsRow.build())
            if (errorMessage != null) {
                pane.addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.action_retry))
                        .setOnClickListener { lifecycleScope.launch { fetch() } }
                        .build()
                )
            }
            if (history.size >= 2) {
                val key = GraphCacheKey(
                    timestamps = history.map { it.dateMs },
                    unit = unit,
                    bgTargetBottom = thresholds.bgTargetBottom.toFloat(),
                    bgTargetTop = thresholds.bgTargetTop.toFloat(),
                )
                if (cachedGraphKey != key) {
                    cachedGraphIcon = glucoseGraphIcon(history, unit, key.bgTargetBottom, key.bgTargetTop)
                    cachedGraphKey = key
                }
                pane.setImage(cachedGraphIcon!!)
            }
            pane.build()
        }
    }

    private fun ageString(ageMs: Long): String {
        val min = (ageMs / 60_000L).coerceAtLeast(0L).toInt()
        return if (min < 1) carContext.getString(R.string.label_just_now)
               else carContext.getString(R.string.label_n_min_ago, min)
    }

    private fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_DL  -> carContext.getString(R.string.label_unit_mgdl)
        GlucoseUnit.MMOL_L -> carContext.getString(R.string.label_unit_mmoll)
    }

    // endregion

    private fun switchTo(profileId: String) {
        if (profileId == activeProfileId) return
        activeProfileId = profileId
        repository.setActiveProfile(profileId)
        entry = null
        history = emptyList()
        lastFetchedMs = 0L
        isLoading = true
        cachedGraphKey = null
        cachedGraphIcon = null
        invalidate()
        lifecycleScope.launch { fetch() }
    }
}
