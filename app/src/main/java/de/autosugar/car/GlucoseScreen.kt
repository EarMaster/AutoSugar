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

    // Monotonically increasing token identifying the most recent fetch. A fetch whose
    // token no longer matches has been superseded (e.g. by a profile switch) and must
    // not write its results, so profile A's data can never render under profile B.
    private var fetchGeneration = 0

    private data class GraphCacheKey(
        val timestamps: List<Long>,
        val sgvs: List<Double>,
        val unit: GlucoseUnit,
        val bgTargetBottom: Float,
        val bgTargetTop: Float,
        val bgLow: Int,
        val bgHigh: Int,
    )
    private var cachedGraphKey: GraphCacheKey? = null
    private var cachedGraphIcon: CarIcon? = null

    // onGetTemplate can fire frequently; cache the small generated bitmaps so they are
    // not re-rendered on the main thread on every rebuild.
    private val trendIconCache = mutableMapOf<String, CarIcon>()
    private val numberIconCache = mutableMapOf<Pair<Int, Boolean>, CarIcon>()

    // A reading older than this is considered stale (≥2 missed 5-min CGM readings) and is
    // labelled as such in the UI. Alerting itself is handled by BackgroundAlertMonitor.
    private val staleAfterMs = 12 * 60_000L

    init {
        lifecycleScope.launch {
            repository.profilesFlow.collect { updated ->
                if (updated.isEmpty()) {
                    // All profiles were removed — return to the no-profiles screen instead
                    // of rendering an orphaned reading.
                    replaceStackWith(NoProfilesScreen(carContext, repository, appPrefs))
                    return@collect
                }
                profiles = updated
                if (profiles.none { it.id == activeProfileId }) {
                    // The active profile was deleted; fall back to the first remaining one.
                    switchTo(profiles.first().id)
                } else {
                    invalidate()
                }
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
        val gen = ++fetchGeneration
        isLoading = entry == null
        errorMessage = null
        coroutineScope {
            val entryResult = async { repository.getCurrentEntry(activeProfileId) }
            val historyResult = async { repository.getHistory(activeProfileId, count = 36) }
            val thresholdsResult = async { repository.getThresholds(activeProfileId) }
            val entryRes = entryResult.await()
            val historyRes = historyResult.await()
            val thresholdsRes = thresholdsResult.await()

            // Discard results if a newer fetch has started (e.g. after switchTo),
            // otherwise the just-switched-away profile could overwrite the current one.
            if (gen != fetchGeneration) return@coroutineScope

            entryRes
                .onSuccess { result ->
                    entry = result
                    lastFetchedMs = System.currentTimeMillis()
                    isLoading = false
                }
                .onFailure { e ->
                    isLoading = false
                    errorMessage = e.message ?: carContext.getString(R.string.error_fetch_failed)
                }
            historyRes
                .onSuccess { h -> history = h.sortedBy { it.dateMs } }
            thresholdsRes
                .onSuccess { t -> thresholds = t }
        }
        if (gen != fetchGeneration) return
        invalidate()
    }

    override fun onGetTemplate(): Template {
        return if (carContext.carAppApiLevel >= 6 && profiles.size in 2..4) {
            buildTabTemplate()
        } else {
            buildPaneTemplate()
        }
    }

    // region TabTemplate (CarApi >= 6, 2–4 profiles)

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
                // Numbered icon fallback when TabTemplate is unavailable (CarApi < 6).
                // The upper bound of 5 here is unreachable in practice since the ">4"
                // branch above already matches size 5 — this only ever runs for 2..4.
                profiles.forEachIndexed { index, profile ->
                    val active = profile.id == activeProfileId
                    builder.addAction(
                        Action.Builder()
                            .setIcon(numberIconCache.getOrPut(index + 1 to active) {
                                profileNumberIcon(index + 1, active)
                            })
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
            val stale = errorMessage != null || now - e.dateMs > staleAfterMs
            val statsRow = Row.Builder()
                .setTitle(
                    if (stale)
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
                        .setImage(
                            trendIconCache.getOrPut(e.direction) { trendArrowIcon(e.direction) },
                            Row.IMAGE_TYPE_LARGE,
                        )
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
                    sgvs = history.map { it.sgv },
                    unit = unit,
                    bgTargetBottom = thresholds.bgTargetBottom.toFloat(),
                    bgTargetTop = thresholds.bgTargetTop.toFloat(),
                    bgLow = thresholds.bgLow,
                    bgHigh = thresholds.bgHigh,
                )
                if (cachedGraphKey != key) {
                    cachedGraphIcon = glucoseGraphIcon(
                        history, unit, key.bgTargetBottom, key.bgTargetTop, key.bgLow, key.bgHigh,
                    )
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
        // Invalidate any in-flight fetch for the old profile immediately, synchronously —
        // otherwise it could still be awaiting its network call when activeProfileId flips,
        // and fetch()'s own generation bump (which only happens once its coroutine actually
        // starts running) may not have happened yet, letting the old profile's data slip
        // through the gen check and get evaluated/alerted under the new profile's name.
        fetchGeneration++
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
