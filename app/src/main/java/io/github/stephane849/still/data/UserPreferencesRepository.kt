package io.github.stephane849.still.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(
    private val context: Context
) {
    val googleApiKey: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY]
        }

    val useDeviceLocation: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            // Default to NOT using device location so onboarding can prompt for a manual location.
            preferences[USE_DEVICE_LOCATION] ?: false
        }

    val defaultLocation: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_LOCATION]
        }

    val mapApp: Flow<MapApp> = context.dataStore.data
        .map { preferences ->
            MapApp.valueOf(preferences[MAP_APP] ?: MapApp.DEFAULT.name)
        }

    val searchProvider: Flow<SearchProvider> = context.dataStore.data
        .map { preferences ->
            val stored = preferences[SEARCH_PROVIDER]
            // Default to HERE so the app works out of the box, and gracefully
            // fall back if an unknown value (e.g. a removed provider) is stored.
            SearchProvider.values().firstOrNull { it.name == stored } ?: SearchProvider.HERE
        }

    val searchRadius: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SEARCH_RADIUS] ?: 10 // default 10 miles
        }

    /** Base URL of an OSRM-compatible routing server. */
    val routingServer: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[ROUTING_SERVER]?.takeIf { it.isNotBlank() }
                ?: io.github.stephane849.still.routing.OsrmRouter.DEFAULT_SERVER
        }

    /** TomTom API key for traffic-aware routing; blank means use OSRM. */
    val tomTomKey: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[TOMTOM_KEY] ?: "" }

    /** Ontario 511 developer key for road events; blank means none. */
    val ontario511Key: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[ONTARIO_511_KEY] ?: "" }

    /** Spoken turn-by-turn guidance on or off. */
    val voiceEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[VOICE_ENABLED] ?: true }

    suspend fun saveVoiceEnabled(on: Boolean) {
        context.dataStore.edit { settings -> settings[VOICE_ENABLED] = on }
    }

    suspend fun saveTomTomKey(key: String) {
        context.dataStore.edit { settings -> settings[TOMTOM_KEY] = key.trim() }
    }

    suspend fun saveOntario511Key(key: String) {
        context.dataStore.edit { settings -> settings[ONTARIO_511_KEY] = key.trim() }
    }

    suspend fun saveRoutingServer(url: String) {
        context.dataStore.edit { settings ->
            settings[ROUTING_SERVER] = url.trim()
        }
    }

    suspend fun saveGoogleApiKey(apiKey: String) {
        context.dataStore.edit { settings ->
            settings[API_KEY] = apiKey
        }
    }

    suspend fun saveUseDeviceLocation(useDeviceLocation: Boolean) {
        context.dataStore.edit { settings ->
            settings[USE_DEVICE_LOCATION] = useDeviceLocation
        }
    }

    suspend fun saveDefaultLocation(defaultLocation: String) {
        context.dataStore.edit { settings ->
            settings[DEFAULT_LOCATION] = defaultLocation
        }
    }

    suspend fun saveMapApp(mapApp: MapApp) {
        context.dataStore.edit { settings ->
            settings[MAP_APP] = mapApp.name
        }
    }

    suspend fun saveSearchRadius(radius: Int) {
        context.dataStore.edit { settings ->
            settings[SEARCH_RADIUS] = radius
        }
    }

    suspend fun saveSearchProvider(provider: SearchProvider) {
        context.dataStore.edit { settings ->
            settings[SEARCH_PROVIDER] = provider.name
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val MAP_APP = stringPreferencesKey("map_app")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val SEARCH_RADIUS = intPreferencesKey("search_radius") // in miles
        val ROUTING_SERVER = stringPreferencesKey("routing_server")
        val TOMTOM_KEY = stringPreferencesKey("tomtom_key")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val ONTARIO_511_KEY = stringPreferencesKey("ontario_511_key")

    }
}
