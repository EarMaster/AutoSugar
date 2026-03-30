package com.autosugar.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

private val REFRESH_INTERVAL_KEY = intPreferencesKey("refresh_interval_seconds")

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val refreshIntervalSeconds: Flow<Int> = context.appPrefsDataStore.data
        .map { prefs -> prefs[REFRESH_INTERVAL_KEY] ?: 60 }

    suspend fun setRefreshInterval(seconds: Int) {
        context.appPrefsDataStore.edit { prefs ->
            prefs[REFRESH_INTERVAL_KEY] = seconds
        }
    }
}
