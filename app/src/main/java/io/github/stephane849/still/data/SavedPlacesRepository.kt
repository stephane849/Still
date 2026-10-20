package io.github.stephane849.still.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SavedPlace(val name: String, val lat: Double, val lng: Double)

/** Saved places shown along the bottom of the map. */
class SavedPlacesRepository(private val context: Context) {

    val places: Flow<List<SavedPlace>> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun save(place: SavedPlace) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { decode(it) } ?: emptyList()
            val updated = current.filterNot { it.name == place.name } + place
            prefs[KEY] = encode(updated.takeLast(MAX_PLACES))
        }
    }

    suspend fun remove(name: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { decode(it) } ?: emptyList()
            prefs[KEY] = encode(current.filterNot { it.name == name })
        }
    }

    private fun decode(s: String): List<SavedPlace> =
        runCatching { json.decodeFromString(serializer, s) }.getOrDefault(emptyList())

    private fun encode(list: List<SavedPlace>): String = json.encodeToString(serializer, list)

    companion object {
        const val MAX_PLACES = 4
        private val KEY = stringPreferencesKey("saved_places")
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(SavedPlace.serializer())
    }
}
