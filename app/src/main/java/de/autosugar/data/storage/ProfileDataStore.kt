package de.autosugar.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.autosugar.data.model.NightscoutProfile
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

    /**
     * Atomically reads the current profile list, applies [transform], and persists the
     * result inside a single DataStore edit. Prevents lost updates when concurrent
     * callers would otherwise read-modify-write over each other.
     */
    suspend fun update(transform: (List<NightscoutProfile>) -> List<NightscoutProfile>) {
        context.dataStore.edit { prefs ->
            val current = prefs[PROFILES_KEY]?.let { serializer.fromJson(it) } ?: emptyList()
            prefs[PROFILES_KEY] = serializer.toJson(transform(current))
        }
    }
}
