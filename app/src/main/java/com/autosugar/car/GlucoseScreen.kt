package com.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.autosugar.R
import com.autosugar.data.model.GlucoseEntry
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 60_000L

class GlucoseScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private var activeProfileId: String,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var profiles: List<NightscoutProfile> = emptyList()
    private var entry: GlucoseEntry? = null
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        scope.launch {
            repository.profilesFlow.collect { updated ->
                profiles = updated
                invalidate()
            }
        }
        startPolling()
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                fetch()
                delay(POLL_INTERVAL_MS)
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
        val activeProfile = profiles.find { it.id == activeProfileId }
        val title = activeProfile?.displayName ?: carContext.getString(R.string.app_name)
        val unit = activeProfile?.unit

        val pane = when {
            isLoading -> Pane.Builder()
                .setLoading(true)
                .build()

            errorMessage != null -> Pane.Builder()
                .addRow(Row.Builder()
                    .setTitle(carContext.getString(R.string.error_fetch_failed))
                    .addText(errorMessage ?: "")
                    .build())
                .addAction(Action.Builder()
                    .setTitle(carContext.getString(R.string.action_retry))
                    .setOnClickListener { scope.launch { fetch() } }
                    .build())
                .build()

            else -> {
                val e = entry!!
                val displayUnit = unit ?: com.autosugar.data.model.GlucoseUnit.MG_DL
                Pane.Builder()
                    .addRow(Row.Builder()
                        .setTitle(carContext.getString(R.string.label_glucose_value))
                        .addText("${e.displayValue(displayUnit)} ${displayUnit.label(carContext)}")
                        .build())
                    .addRow(Row.Builder()
                        .setTitle(carContext.getString(R.string.label_trend))
                        .addText(e.trendArrow)
                        .build())
                    .addRow(Row.Builder()
                        .setTitle(carContext.getString(R.string.label_delta))
                        .addText(e.displayDelta(displayUnit) ?: "-")
                        .build())
                    .build()
            }
        }

        val actionStrip = buildActionStrip()

        return PaneTemplate.Builder(pane)
            .setTitle(title)
            .apply { if (actionStrip != null) setActionStrip(actionStrip) }
            .build()
    }

    private fun buildActionStrip(): ActionStrip? {
        // ActionStrip supports max 4 actions.
        if (profiles.size <= 1) return null

        return if (profiles.size <= 4) {
            // One button per source
            val actions = profiles.map { profile ->
                Action.Builder()
                    .setTitle(profile.displayName)
                    .setOnClickListener { switchTo(profile.id) }
                    .build()
            }
            ActionStrip.Builder().apply { actions.forEach { addAction(it) } }.build()
        } else {
            // Too many profiles for the strip — push a selection screen
            ActionStrip.Builder()
                .addAction(Action.Builder()
                    .setTitle(carContext.getString(R.string.action_switch_source))
                    .setOnClickListener {
                        screenManager.push(SourceSelectScreen(carContext, repository) { id ->
                            switchTo(id)
                        })
                    }
                    .build())
                .build()
        }
    }

    private fun switchTo(profileId: String) {
        if (profileId == activeProfileId) return
        activeProfileId = profileId
        repository.setActiveProfile(profileId)
        entry = null
        isLoading = true
        invalidate()
        scope.launch { fetch() }
    }

    override fun onDestroy() {
        scope.cancel()
    }
}

private fun com.autosugar.data.model.GlucoseUnit.label(context: android.content.Context): String =
    when (this) {
        com.autosugar.data.model.GlucoseUnit.MG_DL  -> context.getString(R.string.label_unit_mgdl)
        com.autosugar.data.model.GlucoseUnit.MMOL_L -> context.getString(R.string.label_unit_mmoll)
    }
