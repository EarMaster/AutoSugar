package com.autosugar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autosugar.R
import com.autosugar.data.model.GlucoseUnit
import com.autosugar.data.model.ProfileIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource

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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ProfileEditUiState.Saved -> onNavigateUp()
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
                        modifier = androidx.compose.ui.Modifier.size(48.dp),
                        enabled = !isLoading,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(profileIcon.resId),
                                contentDescription = profileIcon.name,
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.label_unit))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlucoseUnit.entries.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { viewModel.unit.value = u },
                        label = { Text(when (u) {
                            GlucoseUnit.MG_DL -> "mg/dL"
                            GlucoseUnit.MMOL_L -> "mmol/L"
                        }) },
                        enabled = !isLoading,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = !isLoading && baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.btn_test_connection))
                }

                Button(
                    onClick = { viewModel.save() },
                    enabled = !isLoading && displayName.isNotBlank() && baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text(stringResource(R.string.btn_save))
                    }
                }
            }
        }
    }
}
