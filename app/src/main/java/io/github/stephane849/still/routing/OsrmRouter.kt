package io.github.stephane849.still.routing

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Routes through any server that speaks the OSRM HTTP API: the public
 * OSRM demo, or a self-hosted OSRM (for example on a Raspberry Pi).
 */
class OsrmRouter(
    private val baseUrl: String,
    private val profile: String = "driving",
    private val client: HttpClient = defaultClient
) {
    suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val base = baseUrl.trim().trimEnd('/')
        val url = "$base/route/v1/$profile/${from.lng},${from.lat};${to.lng},${to.lat}" +
            "?overview=full&geometries=geojson&steps=true"
        val body = client.get(url).bodyAsText()
        val response = json.decodeFromString(OsrmResponse.serializer(), body)
        if (response.code != "Ok" || response.routes.isEmpty()) {
            throw RoutingException(response.message ?: "No route found")
        }
        val r = response.routes.first()
        return Route(
            points = r.geometry.coordinates.map { GeoPoint(lat = it[1], lng = it[0]) },
            distanceM = r.distance,
            durationS = r.duration,
            steps = r.legs.flatMap { leg ->
                leg.steps.map { s ->
                    RouteStep(
                        type = s.maneuver.type,
                        modifier = s.maneuver.modifier,
                        exit = s.maneuver.exit,
                        road = s.name.ifBlank { s.ref ?: "" },
                        location = GeoPoint(lat = s.maneuver.location[1], lng = s.maneuver.location[0]),
                        distanceM = s.distance,
                        durationS = s.duration
                    )
                }
            }
        )
    }

    companion object {
        const val DEFAULT_SERVER = "https://router.project-osrm.org"

        /** FOSSGIS's public OSRM servers for cycling and walking (fair use). */
        const val BIKE_SERVER = "https://routing.openstreetmap.de/routed-bike"
        const val WALK_SERVER = "https://routing.openstreetmap.de/routed-foot"
        private val json = Json { ignoreUnknownKeys = true }
        private val defaultClient by lazy { HttpClient(Android) }
    }
}

class RoutingException(message: String) : Exception(message)

@Serializable
private data class OsrmResponse(
    val code: String,
    val message: String? = null,
    val routes: List<OsrmRoute> = emptyList()
)

@Serializable
private data class OsrmRoute(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometry,
    val legs: List<OsrmLeg> = emptyList()
)

@Serializable
private data class OsrmGeometry(val coordinates: List<List<Double>>)

@Serializable
private data class OsrmLeg(val steps: List<OsrmStep> = emptyList())

@Serializable
private data class OsrmStep(
    val distance: Double,
    val duration: Double,
    val name: String = "",
    val ref: String? = null,
    val maneuver: OsrmManeuver
)

@Serializable
private data class OsrmManeuver(
    val type: String,
    val modifier: String? = null,
    val location: List<Double>,
    val exit: Int? = null
)
