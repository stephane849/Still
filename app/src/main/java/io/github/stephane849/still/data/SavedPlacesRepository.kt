package io.github.stephane849.still.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A place in one of the four slots: 0 is Home, 1 is Work, 2 and 3 are
 * named by you.
 */
@Serializable
data class SavedPlace(
    val name: String,
    val lat: Double,
    val lng: Double,
    val slot: Int = 0,
    val address: String = ""
)

/** Saved places, set from Settings and shown on the map screen and in the car. */
class SavedPlacesRepository(private val context: Context) {

    /** The places that are set, in slot order. */
    val places: Flow<List<SavedPlace>> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) }.orEmpty().sortedBy { it.slot }
    }

    suspend fun setSlot(place: SavedPlace) {
        require(place.slot in 0 until SLOTS)
        context.dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { decode(it) }.orEmpty()
            prefs[KEY] = encode(current.filterNot { it.slot == place.slot } + place)
        }
    }

    suspend fun clearSlot(slot: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { decode(it) }.orEmpty()
            prefs[KEY] = encode(current.filterNot { it.slot == slot })
        }
    }

    private fun decode(s: String): List<SavedPlace> =
        runCatching { json.decodeFromString(serializer, s) }.getOrDefault(emptyList())

    private fun encode(list: List<SavedPlace>): String = json.encodeToString(serializer, list)

    companion object {
        const val SLOTS = 4
        const val HOME = 0
        const val WORK = 1

        fun defaultName(slot: Int): String = when (slot) {
            HOME -> "Home"
            WORK -> "Work"
            else -> "Place ${slot + 1}"
        }

        fun isFixedName(slot: Int): Boolean = slot == HOME || slot == WORK

        // New key: places saved by earlier test builds had no slots.
        private val KEY = stringPreferencesKey("saved_places_v2")
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(SavedPlace.serializer())
    }
}
