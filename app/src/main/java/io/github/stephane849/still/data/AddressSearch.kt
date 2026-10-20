package io.github.stephane849.still.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class AddressResult(val label: String, val lat: Double, val lng: Double)

/**
 * Looks up an address with OpenStreetMap's Nominatim, which needs no key.
 * Its usage policy allows about one request a second and no
 * search-as-you-type, so Still searches only when you press Search.
 */
object AddressSearch {
    private val client by lazy { HttpClient(Android) }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, nearLat: Double? = null, nearLng: Double? = null): List<AddressResult> {
        if (query.isBlank()) return emptyList()
        val body = client.get("https://nominatim.openstreetmap.org/search") {
            header("User-Agent", "Still/0.3 (https://github.com/stephane849/Still)")
            parameter("q", query.trim())
            parameter("format", "jsonv2")
            parameter("limit", 6)
            if (nearLat != null && nearLng != null) {
                // Prefer results within roughly 50 km, without excluding others.
                parameter("viewbox", "${nearLng - 0.6},${nearLat + 0.45},${nearLng + 0.6},${nearLat - 0.45}")
            }
        }.bodyAsText()
        return json.decodeFromString(ListSerializer(NominatimPlace.serializer()), body).mapNotNull {
            val lat = it.lat.toDoubleOrNull() ?: return@mapNotNull null
            val lng = it.lon.toDoubleOrNull() ?: return@mapNotNull null
            AddressResult(shorten(it.display_name), lat, lng)
        }
    }

    /** Keeps the first four parts of Nominatim's long address, dropping the country. */
    private fun shorten(full: String): String =
        full.split(", ").filterNot { it == "Canada" }.take(4).joinToString(", ")
}

@Suppress("PropertyName")
@Serializable
private data class NominatimPlace(val display_name: String, val lat: String, val lon: String)
