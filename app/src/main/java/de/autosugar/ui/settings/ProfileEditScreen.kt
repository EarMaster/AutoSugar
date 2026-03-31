package de.autosugar.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.autosugar.R
import de.autosugar.data.model.GlucoseUnit
import de.autosugar.data.model.ProfileIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: String?,
    onNavigateUp: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(profileId) {
        profileId?.let { viewModel.loadProfile(it) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val apiToken by viewModel.apiToken.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val icon by viewModel.icon.collectAsState()
    val alertsEnabled by viewModel.alertsEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: we gracefully handle missing permission in GlucoseAlertManager */ }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ProfileEditUiState.Saved -> onNavigateUp()
            is ProfileEditUiState.Deleted -> onNavigateUp()
            is ProfileEditUiState.TestSuccess -> {
                snackbarHostState.showSnackbar("BG: ${state.message}")
                viewModel.clearState()
            }
            is ProfileEditUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
            }
            else -> Unit
        }
    }

    val isLoading = uiState is ProfileEditUiState.Loading
    val isValidUrl = runCatching {
        val uri = java.net.URI(baseUrl.trim())
        uri.scheme in listOf("http", "https") &&
            !uri.host.isNullOrEmpty() &&
            uri.host.contains('.')
    }.getOrDefault(false)
    val canSave = !isLoading && displayName.isNotBlank() && isValidUrl

    val title = if (profileId == null) {
        stringResource(R.string.label_add_source)
    } else {
        stringResource(R.string.label_edit_source)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.save() },
                            enabled = canSave,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.btn_save),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Connection fields ─────────────────────────────────────────
            OutlinedTextField(
                value = displayName,
                onValueChange = { viewModel.displayName.value = it },
                label = { Text(stringResource(R.string.hint_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { viewModel.baseUrl.value = it },
                label = { Text(stringResource(R.string.hint_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next,
                ),
                isError = baseUrl.isNotBlank() && !isValidUrl,
                supportingText = if (baseUrl.isNotBlank() && !isValidUrl) {
                    { Text(stringResource(R.string.error_invalid_url)) }
                } else null,
                enabled = !isLoading,
            )
            OutlinedTextField(
                value = apiToken,
                onValueChange = { viewModel.apiToken.value = it },
                label = { Text(stringResource(R.string.hint_api_token)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
            )
            OutlinedButton(
                onClick = { viewModel.testConnection() },
                enabled = !isLoading && baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_test_connection))
            }

            // ── Alerts ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.label_glucose_alerts))
                Switch(
                    checked = alertsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.alertsEnabled.value = enabled
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    enabled = !isLoading,
                )
            }

            // ── Tab icon ─────────────────────────────────────────────────
            Text(stringResource(R.string.label_tab_icon))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileIcon.entries.forEach { profileIcon ->
                    val selected = icon == profileIcon
                    Card(
                        onClick = { viewModel.icon.value = profileIcon },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.size(48.dp),
                        enabled = !isLoading,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(profileIcon.resId),
                                contentDescription = profileIcon.name,
                            )
                        }
                    }
                }
            }

            // ── Unit ─────────────────────────────────────────────────────
            Text(stringResource(R.string.label_unit))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlucoseUnit.entries.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { viewModel.unit.value = u },
                        label = {
                            Text(when (u) {
                                GlucoseUnit.MG_DL  -> "mg/dL"
                                GlucoseUnit.MMOL_L -> "mmol/L"
                            })
                        },
                        enabled = !isLoading,
                    )
                }
            }

            // ── Delete (edit mode only) ───────────────────────────────────
            if (profileId != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.delete() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.btn_delete))
                }
            }
        }
    }
}
