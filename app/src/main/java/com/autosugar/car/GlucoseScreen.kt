package com.autosugar.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import com.autosugar.R
import com.autosugar.data.model.GlucoseEntry
import com.autosugar.data.model.GlucoseUnit
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.repository.NightscoutRepository
import com.autosugar.data.storage.AppPreferencesDataStore
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
    private var bgTargetBottom: Float = 70f
    private var bgTargetTop: Float = 180f
    private var errorMessage: String? = null
    private var pollingJob: Job? = null

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
            val thresholdsResult = async { repository.getTargetRange(activeProfileId) }
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
                .onSuccess { (bottom, top) ->
                    bgTargetBottom = bottom.toFloat()
                    bgTargetTop    = top.toFloat()
                }
        }
        invalidate()
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

        errorMessage != null -> Pane.Builder()
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
                .setTitle(carContext.getString(R.string.label_reading, ageString(now - e.dateMs)))
            if (lastFetchedMs > 0) {
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
            if (history.size >= 2) {
                pane.setImage(graphIcon(history, unit, bgTargetBottom, bgTargetTop))
            }
            pane.build()
        }
    }

    private fun ageString(ageMs: Long): String {
        val min = (ageMs / 60_000L).coerceAtLeast(0L).toInt()
        return if (min < 1) carContext.getString(R.string.label_just_now)
               else carContext.getString(R.string.label_n_min_ago, min)
    }

    private fun graphIcon(entries: List<GlucoseEntry>, unit: GlucoseUnit, bgTargetBottom: Float, bgTargetTop: Float): CarIcon {
        val w = 600
        val h = 400
        val pad = 16f
        val plotW = w - 2 * pad
        val plotH = h - 2 * pad

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val offset = (bgTargetTop - bgTargetBottom) * 0.25f
        val yMin = minOf(entries.minOf { it.sgv }.toFloat(), bgTargetBottom - offset).coerceAtLeast(0f)
        val yMax = maxOf(entries.maxOf { it.sgv }.toFloat(), bgTargetTop + offset)
        val tMin = entries.first().dateMs
        val tMax = entries.last().dateMs
        val tRange = (tMax - tMin).coerceAtLeast(1L).toFloat()

        fun xOf(ms: Long) = pad + (ms - tMin).toFloat() / tRange * plotW
        fun yOf(sgv: Float) = pad + plotH * (1f - (sgv.coerceIn(yMin, yMax) - yMin) / (yMax - yMin))

        // Target range band
        canvas.drawRect(
            pad, yOf(bgTargetTop), pad + plotW, yOf(bgTargetBottom),
            Paint().apply { color = Color.argb(55, 100, 220, 100); style = Paint.Style.FILL },
        )

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 200, 200, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }
        // Labels are drawn inside the plot so they can't be cropped by the host's image slot
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 200, 200, 200)
            textSize = 22f
        }

        // Horizontal grid lines
        val yGridMgdl: List<Float> = when (unit) {
            GlucoseUnit.MG_DL  -> (1..7).map { it * 50f }       // 50, 100, …, 350
            GlucoseUnit.MMOL_L -> (1..7).map { it * 3f * 18f }  // 3, 6, …, 21 mmol/L → ×18
        }
        val edgeMargin = 20f
        for (sgv in yGridMgdl) {
            val y = yOf(sgv)
            if (y < pad + edgeMargin || y > pad + plotH - edgeMargin) continue
            canvas.drawLine(pad, y, pad + plotW, y, gridPaint)
        }

        if (tMax > tMin) {
            val msPerHour = 3_600_000L
            val ms20      = 20 * 60_000L
            val ms30      = 30 * 60_000L

            // Horizontal line labels: only at full 100s (mg/dL) / every-other step (mmol/L),
            // placed at the most recent :30 mark (~80% across for a 3-hour window).
            val tMaxHour = (tMax / msPerHour) * msPerHour
            // Prefer the :30 mark of the *previous* hour (e.g. 22:30 for a window ending at 23:xx)
            // so labels never sit at the right edge of the graph.
            val yLabelT = when {
                tMaxHour - ms30 in tMin..tMax -> tMaxHour - ms30
                tMaxHour + ms30 in tMin..tMax -> tMaxHour + ms30
                else -> (tMin + tMax) / 2
            }
            val yLabelX = xOf(yLabelT)
            labelPaint.textAlign = Paint.Align.CENTER
            yGridMgdl.forEachIndexed { i, sgv ->
                val y = yOf(sgv)
                if (y < pad + edgeMargin || y > pad + plotH - edgeMargin) return@forEachIndexed
                // Show label every other line, starting at index 1 (100, 200, 300 for mg/dL)
                if (i % 2 != 1) return@forEachIndexed
                val label = when (unit) {
                    GlucoseUnit.MG_DL  -> sgv.toInt().toString()
                    GlucoseUnit.MMOL_L -> "%.0f".format(sgv / 18f)
                }
                canvas.drawText(label, yLabelX, y - 5f, labelPaint)
            }

            // Half-hour labels at the bottom (HH:30) — no vertical line
            val tMinHour = (tMin / msPerHour) * msPerHour
            var tHalf = tMinHour + ms30
            if (tHalf < tMin) tHalf += msPerHour
            while (tHalf <= tMax) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = tHalf }
                labelPaint.color = Color.argb(160, 200, 200, 200)
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    "%02d:30".format(cal.get(java.util.Calendar.HOUR_OF_DAY)),
                    xOf(tHalf), pad + plotH - 4f, labelPaint,
                )
                tHalf += msPerHour
            }

            // Vertical lines at full hours only; drop-pin labels at every 20-min mark.
            val dropTailH  = 10f
            val dropBodyW  = 46f
            val dropBodyH  = 28f
            val dropCorner = 8f
            val dropTailW  = 14f
            // Paints reused across marks
            val dropFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val dropText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = Color.rgb(20, 20, 20)
                textSize  = 18f
                textAlign = Paint.Align.CENTER
                typeface  = Typeface.DEFAULT_BOLD
            }
            // Minimum tipY: keep the whole drop within the plot area
            val minTipY = pad + dropTailH + dropBodyH + 4f

            var t = (tMin / ms20 + 1) * ms20
            while (t <= tMax) {
                val x = xOf(t)
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
                val minute = cal.get(java.util.Calendar.MINUTE)

                // Dotted vertical line + time label at full hours
                if (minute == 0) {
                    canvas.drawLine(x, pad, x, pad + plotH, gridPaint)
                    labelPaint.color = Color.argb(160, 200, 200, 200)
                    labelPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(
                        "%02d:00".format(cal.get(java.util.Calendar.HOUR_OF_DAY)),
                        x, pad + plotH - 4f, labelPaint,
                    )
                }

                // Drop-pin at every 20-min mark
                val closest = entries.minByOrNull { kotlin.math.abs(it.dateMs - t) }
                if (closest != null) {
                    val valueLabel = when (unit) {
                        GlucoseUnit.MG_DL  -> closest.sgv.toString()
                        GlucoseUnit.MMOL_L -> "%.1f".format(closest.sgv / 18.0)
                    }
                    dropFill.color = when {
                        closest.sgv < 70  -> Color.rgb(255, 80,  80)
                        closest.sgv > 180 -> Color.rgb(255, 200, 60)
                        else              -> Color.WHITE
                    }

                    val tipY       = (yOf(closest.sgv.toFloat()) - 3f).coerceAtLeast(minTipY)
                    val bodyBottom = tipY - dropTailH
                    val bodyTop    = bodyBottom - dropBodyH
                    val bL         = x - dropBodyW / 2f
                    val bR         = x + dropBodyW / 2f
                    val r          = dropCorner

                    val drop = Path().apply {
                        moveTo(bL + r, bodyTop)
                        lineTo(bR - r, bodyTop)
                        arcTo(RectF(bR - 2*r, bodyTop, bR, bodyTop + 2*r), -90f, 90f)
                        lineTo(bR, bodyBottom - r)
                        arcTo(RectF(bR - 2*r, bodyBottom - 2*r, bR, bodyBottom), 0f, 90f)
                        lineTo(x + dropTailW / 2f, bodyBottom)
                        lineTo(x, tipY)
                        lineTo(x - dropTailW / 2f, bodyBottom)
                        lineTo(bL + r, bodyBottom)
                        arcTo(RectF(bL, bodyBottom - 2*r, bL + 2*r, bodyBottom), 90f, 90f)
                        lineTo(bL, bodyTop + r)
                        arcTo(RectF(bL, bodyTop, bL + 2*r, bodyTop + 2*r), 180f, 90f)
                        close()
                    }
                    canvas.drawPath(drop, dropFill)
                    canvas.drawText(valueLabel, x, bodyTop + dropBodyH / 2f + dropText.textSize / 3f, dropText)
                }

                t += ms20
            }
        }

        // Colour-coded line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until entries.lastIndex) {
            val sgv = entries[i].sgv
            linePaint.color = when {
                sgv < 70  -> Color.rgb(255, 80,  80)
                sgv > 180 -> Color.rgb(255, 200, 60)
                else      -> Color.WHITE
            }
            canvas.drawLine(
                xOf(entries[i].dateMs),     yOf(sgv.toFloat()),
                xOf(entries[i + 1].dateMs), yOf(entries[i + 1].sgv.toFloat()),
                linePaint,
            )
        }

        // Dot at newest reading
        val last = entries.last()
        canvas.drawCircle(
            xOf(last.dateMs), yOf(last.sgv.toFloat()), 7f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    last.sgv < 70  -> Color.rgb(255, 80,  80)
                    last.sgv > 180 -> Color.rgb(255, 200, 60)
                    else           -> Color.WHITE
                }
                style = Paint.Style.FILL
            },
        )

        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    private fun trendArrowIcon(direction: String): CarIcon {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val rotDeg = when (direction) {
            "DoubleUp", "SingleUp"     -> -90f
            "FortyFiveUp"              -> -45f
            "Flat"                     ->   0f
            "FortyFiveDown"            ->  45f
            "DoubleDown", "SingleDown" ->  90f
            else                       ->   0f
        }
        canvas.save()
        canvas.translate(size / 2f, size / 2f)
        canvas.rotate(rotDeg)
        canvas.drawPath(arrowPath(size * 0.38f), paint)
        canvas.restore()
        return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
    }

    private fun arrowPath(r: Float): Path {
        val hh = r * 0.52f
        val sh = r * 0.20f
        val hd = r * 0.50f
        return Path().apply {
            moveTo(r, 0f)
            lineTo(r - hd, -hh)
            lineTo(r - hd, -sh)
            lineTo(-r, -sh)
            lineTo(-r, sh)
            lineTo(r - hd, sh)
            lineTo(r - hd, hh)
            close()
        }
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
        invalidate()
        lifecycleScope.launch { fetch() }
    }
}
