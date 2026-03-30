package com.autosugar.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private var isLoading = true
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
        repository.getCurrentEntry(activeProfileId)
            .onSuccess { result ->
                entry = result
                isLoading = false
            }
            .onFailure { e ->
                isLoading = false
                errorMessage = e.message ?: carContext.getString(R.string.error_fetch_failed)
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
            Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle("${e.displayValue(unit)} ${unitLabel(unit)}")
                        .setImage(trendArrowIcon(e.direction), Row.IMAGE_TYPE_LARGE)
                        .addText("${e.displayDelta(unit) ?: "-"} ${unitLabel(unit)}")
                        .build()
                )
                .build()
        }
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
        isLoading = true
        invalidate()
        lifecycleScope.launch { fetch() }
    }
}
