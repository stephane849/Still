package io.github.stephane849.still.routing

import io.github.stephane849.still.navigation.distanceM
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Road events (roadwork, closures, collisions) from Ontario 511's open API.
 * The API allows ten calls a minute, so Still asks once per route.
 */
class Ontario511(
    private val apiKey: String,
    private val client: HttpClient = defaultClient
) {
    suspend fun eventsNear(route: List<GeoPoint>, withinM: Double = 150.0): List<Incident> {
        if (route.isEmpty()) return emptyList()
        val url = "https://511on.ca/api/v2/get/event?format=json&lang=en&key=${apiKey.trim()}"
        val body = client.get(url).bodyAsText()
        val events = json.decodeFromString(ListSerializer(Event511.serializer()), body)

        // Cheap bounding-box test first, then distance to sampled route points.
        val minLat = route.minOf { it.lat } - 0.01
        val maxLat = route.maxOf { it.lat } + 0.01
        val minLng = route.minOf { it.lng } - 0.01
        val maxLng = route.maxOf { it.lng } + 0.01
        val sampled = route.filterIndexed { i, _ -> i % 3 == 0 } + route.last()

        return events
            .filter { it.Latitude in minLat..maxLat && it.Longitude in minLng..maxLng }
            .filter { e ->
                val p = GeoPoint(e.Latitude, e.Longitude)
                sampled.any { distanceM(it, p) <= withinM }
            }
            .map {
                Incident(
                    point = GeoPoint(it.Latitude, it.Longitude),
                    type = it.EventType ?: "",
                    road = it.RoadwayName ?: "",
                    description = it.Description ?: "",
                    fullClosure = it.IsFullClosure ?: false
                )
            }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val defaultClient by lazy { HttpClient(Android) }
    }
}

@Suppress("PropertyName")
@Serializable
private data class Event511(
    val Latitude: Double = 0.0,
    val Longitude: Double = 0.0,
    val EventType: String? = null,
    val RoadwayName: String? = null,
    val Description: String? = null,
    val IsFullClosure: Boolean? = null
)
