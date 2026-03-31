package de.autosugar.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import de.autosugar.BuildConfig
import de.autosugar.R
import de.autosugar.data.model.NightscoutProfile
import kotlin.math.roundToInt

// Items before the profile list in the LazyColumn (RefreshSection + Divider)
private const val HEADER_COUNT = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAddProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val refreshInterval by viewModel.refreshIntervalSeconds.collectAsState()

    val lazyListState = rememberLazyListState()
    val localProfiles = remember { mutableStateListOf<NightscoutProfile>() }
    var isDragging by remember { mutableStateOf(false) }

    // Keep local list in sync with repository, but not during an active drag
    LaunchedEffect(profiles) {
        if (!isDragging) {
            localProfiles.clear()
            localProfiles.addAll(profiles)
        }
    }

    val dragState = remember(lazyListState) {
        DragDropState(lazyListState, HEADER_COUNT) { from, to ->
            val item = localProfiles.removeAt(from)
            localProfiles.add(to, item)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.label_settings)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.btn_add_profile))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            isDragging = true
                            dragState.onDragStart(offset.y)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragState.onDrag(dragAmount.y)
                        },
                        onDragEnd = {
                            isDragging = false
                            dragState.onDragEnd()
                            viewModel.saveOrder(localProfiles.toList())
                        },
                        onDragCancel = {
                            isDragging = false
                            dragState.onDragEnd()
                            localProfiles.clear()
                            localProfiles.addAll(profiles)
                        },
                    )
                }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    RefreshIntervalSection(
                        currentSeconds = refreshInterval,
                        onSelect = { viewModel.setRefreshInterval(it) },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                if (localProfiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.label_no_sources),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else {
                    itemsIndexed(localProfiles, key = { _, p -> p.id }) { index, profile ->
                        val isDraggingThis = index == dragState.draggingIndex
                        ProfileCard(
                            profile = profile,
                            isDragging = isDraggingThis,
                            modifier = Modifier
                                .zIndex(if (isDraggingThis) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDraggingThis) dragState.dragOffset else 0f
                                    shadowElevation = if (isDraggingThis) 16f else 0f
                                },
                            onAlertsToggled = { enabled ->
                                viewModel.setAlertsEnabled(profile.id, enabled)
                            },
                            onClick = { onEditProfile(profile.id) },
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.label_app_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val REFRESH_OPTIONS = listOf(
    30 to R.string.label_refresh_30s,
    60 to R.string.label_refresh_1min,
    120 to R.string.label_refresh_2min,
    300 to R.string.label_refresh_5min,
)

@Composable
private fun RefreshIntervalSection(currentSeconds: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_refresh_interval),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REFRESH_OPTIONS.forEach { (seconds, labelRes) ->
                FilterChip(
                    selected = currentSeconds == seconds,
                    onClick = { onSelect(seconds) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: NightscoutProfile,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onAlertsToggled: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "cardElevation",
    )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(profile.icon.resId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = profile.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(
                checked = profile.alertsEnabled,
                onCheckedChange = onAlertsToggled,
            )
        }
    }
}

private class DragDropState(
    private val lazyListState: LazyListState,
    private val headerCount: Int,
    private val onSwap: (Int, Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableStateOf(0f)
        private set

    fun onDragStart(touchY: Float) {
        val item = lazyListState.layoutInfo.visibleItemsInfo
            .filter { it.index >= headerCount }
            .firstOrNull { touchY.roundToInt() in it.offset..(it.offset + it.size) }
        draggingIndex = item?.let { it.index - headerCount }
        dragOffset = 0f
    }

    fun onDrag(dy: Float) {
        val idx = draggingIndex ?: return
        dragOffset += dy
        val absoluteIdx = idx + headerCount
        val dragged = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == absoluteIdx } ?: return
        val centerY = dragged.offset.toFloat() + dragOffset + dragged.size / 2f
        val target = lazyListState.layoutInfo.visibleItemsInfo
            .filter { it.index >= headerCount && it.index != absoluteIdx }
            .firstOrNull { centerY.roundToInt() in it.offset..(it.offset + it.size) }
        if (target != null) {
            val targetIdx = target.index - headerCount
            dragOffset += dragged.offset - target.offset
            onSwap(idx, targetIdx)
            draggingIndex = targetIdx
        }
    }

    fun onDragEnd() {
        draggingIndex = null
        dragOffset = 0f
    }
}
