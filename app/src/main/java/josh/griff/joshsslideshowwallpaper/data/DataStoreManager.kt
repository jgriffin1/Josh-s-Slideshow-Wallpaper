package josh.griff.joshsslideshowwallpaper.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        private val IMAGE_URIS_STRING = stringPreferencesKey("image_uris_string")
        private val CURRENT_INDEX = intPreferencesKey("current_index")
        private val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        private val IS_RANDOM = booleanPreferencesKey("is_random")
        private val IS_SLIDESHOW_ENABLED = booleanPreferencesKey("is_slideshow_enabled")
    }

    val imageUris: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val urisString = preferences[IMAGE_URIS_STRING] ?: ""
        if (urisString.isEmpty()) emptyList() else urisString.split("|")
    }

    val currentIndex: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_INDEX] ?: 0
    }

    val intervalMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[INTERVAL_MINUTES] ?: 60
    }

    val isRandom: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_RANDOM] ?: false
    }

    val isSlideshowEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_SLIDESHOW_ENABLED] ?: false
    }

    suspend fun saveImageUris(uris: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_URIS_STRING] = uris.joinToString("|")
        }
    }

    suspend fun updateIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_INDEX] = index
        }
    }

    suspend fun saveInterval(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun saveIsRandom(random: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_RANDOM] = random
        }
    }

    suspend fun saveSlideshowEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SLIDESHOW_ENABLED] = enabled
        }
    }
}
