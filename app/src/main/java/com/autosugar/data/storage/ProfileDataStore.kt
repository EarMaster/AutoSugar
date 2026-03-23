package com.autosugar.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autosugar.data.model.NightscoutProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profiles")

private val PROFILES_KEY = stringPreferencesKey("profiles_json")

@Singleton
class ProfileDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serializer: ProfileSerializer,
) {
    val profilesFlow: Flow<List<NightscoutProfile>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PROFILES_KEY] ?: return@map emptyList()
            serializer.fromJson(json)
        }

    suspend fun save(profiles: List<NightscoutProfile>) {
        context.dataStore.edit { prefs ->
            prefs[PROFILES_KEY] = serializer.toJson(profiles)
        }
    }
}
