package de.autosugar.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import de.autosugar.data.model.GlucoseEntry
import de.autosugar.data.model.GlucoseUnit
import java.util.Locale

/**
 * Renders the 3-hour glucose history graph as a [CarIcon] bitmap.
 *
 * @param entries      Glucose readings sorted by time (oldest first).
 * @param unit         Display unit for labels and colour thresholds.
 * @param bgTargetBottom Lower bound of the target range band (mg/dL).
 * @param bgTargetTop    Upper bound of the target range band (mg/dL).
 */
internal fun glucoseGraphIcon(
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    bgTargetBottom: Float,
    bgTargetTop: Float,
): CarIcon {
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
        drawTimeLabelsAndDropPins(
            canvas, entries, unit, yGridMgdl, edgeMargin,
            tMin, tMax, pad, plotW, plotH, labelPaint, gridPaint,
            ::xOf, ::yOf,
        )
    }

    drawColorCodedLine(canvas, entries, ::xOf, ::yOf)
    drawNewestDot(canvas, entries, ::xOf, ::yOf)

    return CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()
}

private fun drawTimeLabelsAndDropPins(
    canvas: Canvas,
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    yGridMgdl: List<Float>,
    edgeMargin: Float,
    tMin: Long,
    tMax: Long,
    pad: Float,
    plotW: Float,
    plotH: Float,
    labelPaint: Paint,
    gridPaint: Paint,
    xOf: (Long) -> Float,
    yOf: (Float) -> Float,
) {
    val msPerHour = 3_600_000L
    val ms20      = 20 * 60_000L
    val ms30      = 30 * 60_000L

    // Horizontal line labels: only at full 100s (mg/dL) / every-other step (mmol/L),
    // placed at the most recent :30 mark (~80% across for a 3-hour window).
    val tMaxHour = (tMax / msPerHour) * msPerHour
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

    drawDropPinsAndHourLines(
        canvas, entries, unit, tMin, tMax, ms20, msPerHour,
        pad, plotH, labelPaint, gridPaint, xOf, yOf,
    )
}

private fun drawDropPinsAndHourLines(
    canvas: Canvas,
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    tMin: Long,
    tMax: Long,
    ms20: Long,
    msPerHour: Long,
    pad: Float,
    plotH: Float,
    labelPaint: Paint,
    gridPaint: Paint,
    xOf: (Long) -> Float,
    yOf: (Float) -> Float,
) {
    val dropTailH  = 10f
    val dropBodyW  = 46f
    val dropBodyH  = 28f
    val dropCorner = 8f
    val dropTailW  = 14f
    val dropFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val dropText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.rgb(20, 20, 20)
        textSize  = 18f
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    val minTipY = pad + dropTailH + dropBodyH + 4f

    var t = (tMin / ms20 + 1) * ms20
    while (t <= tMax) {
        val x = xOf(t)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
        val minute = cal.get(java.util.Calendar.MINUTE)

        if (minute == 0) {
            canvas.drawLine(x, pad, x, pad + plotH, gridPaint)
            labelPaint.color = Color.argb(160, 200, 200, 200)
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "%02d:00".format(cal.get(java.util.Calendar.HOUR_OF_DAY)),
                x, pad + plotH - 4f, labelPaint,
            )
        }

        val closest = entries.minByOrNull { kotlin.math.abs(it.dateMs - t) }
        if (closest != null) {
            drawDropPin(canvas, closest, unit, x, dropFill, dropText, dropBodyW, dropBodyH,
                dropCorner, dropTailW, dropTailH, minTipY, yOf)
        }

        t += ms20
    }
}

private fun drawDropPin(
    canvas: Canvas,
    entry: GlucoseEntry,
    unit: GlucoseUnit,
    x: Float,
    dropFill: Paint,
    dropText: Paint,
    dropBodyW: Float,
    dropBodyH: Float,
    dropCorner: Float,
    dropTailW: Float,
    dropTailH: Float,
    minTipY: Float,
    yOf: (Float) -> Float,
) {
    val valueLabel = when (unit) {
        GlucoseUnit.MG_DL  -> entry.sgv.toString()
        GlucoseUnit.MMOL_L -> "%.1f".format(Locale.US, entry.sgv / 18.0)
    }
    dropFill.color = glucoseColor(entry.sgv)

    val tipY       = (yOf(entry.sgv.toFloat()) - 3f).coerceAtLeast(minTipY)
    val bodyBottom = tipY - dropTailH
    val bodyTop    = bodyBottom - dropBodyH
    val bL         = x - dropBodyW / 2f
    val bR         = x + dropBodyW / 2f
    val r          = dropCorner

    val drop = Path().apply {
        moveTo(bL + r, bodyTop)
        lineTo(bR - r, bodyTop)
        arcTo(RectF(bR - 2 * r, bodyTop, bR, bodyTop + 2 * r), -90f, 90f)
        lineTo(bR, bodyBottom - r)
        arcTo(RectF(bR - 2 * r, bodyBottom - 2 * r, bR, bodyBottom), 0f, 90f)
        lineTo(x + dropTailW / 2f, bodyBottom)
        lineTo(x, tipY)
        lineTo(x - dropTailW / 2f, bodyBottom)
        lineTo(bL + r, bodyBottom)
        arcTo(RectF(bL, bodyBottom - 2 * r, bL + 2 * r, bodyBottom), 90f, 90f)
        lineTo(bL, bodyTop + r)
        arcTo(RectF(bL, bodyTop, bL + 2 * r, bodyTop + 2 * r), 180f, 90f)
        close()
    }
    canvas.drawPath(drop, dropFill)
    canvas.drawText(valueLabel, x, bodyTop + dropBodyH / 2f + dropText.textSize / 3f, dropText)
}

private fun drawColorCodedLine(
    canvas: Canvas,
    entries: List<GlucoseEntry>,
    xOf: (Long) -> Float,
    yOf: (Float) -> Float,
) {
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    for (i in 0 until entries.lastIndex) {
        linePaint.color = glucoseColor(entries[i].sgv)
        canvas.drawLine(
            xOf(entries[i].dateMs),     yOf(entries[i].sgv.toFloat()),
            xOf(entries[i + 1].dateMs), yOf(entries[i + 1].sgv.toFloat()),
            linePaint,
        )
    }
}

private fun drawNewestDot(
    canvas: Canvas,
    entries: List<GlucoseEntry>,
    xOf: (Long) -> Float,
    yOf: (Float) -> Float,
) {
    val last = entries.last()
    canvas.drawCircle(
        xOf(last.dateMs), yOf(last.sgv.toFloat()), 7f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glucoseColor(last.sgv)
            style = Paint.Style.FILL
        },
    )
}

/** Returns the display colour for a glucose value in mg/dL. */
internal fun glucoseColor(sgv: Int): Int = when {
    sgv < 70  -> Color.rgb(255, 80,  80)
    sgv > 180 -> Color.rgb(255, 200, 60)
    else      -> Color.WHITE
}

// region Trend arrow

/** Renders a trend direction arrow as a [CarIcon] bitmap. */
internal fun trendArrowIcon(direction: String): CarIcon {
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

// endregion
