package io.github.stephane849.still.routing

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Traffic-aware routing through TomTom's Calculate Route API.
 * Returns the fastest route given live traffic, its turn-by-turn steps,
 * and the slow sections along it.
 */
class TomTomRouter(
    private val apiKey: String,
    private val client: HttpClient = defaultClient
) {
    suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val url = "https://api.tomtom.com/routing/1/calculateRoute/" +
            "${from.lat},${from.lng}:${to.lat},${to.lng}/json" +
            "?key=${apiKey.trim()}&traffic=true&travelMode=car&routeType=fastest" +
            "&instructionsType=coded&sectionType=traffic"
        val body = client.get(url).bodyAsText()
        val response = runCatching { json.decodeFromString(TtResponse.serializer(), body) }
            .getOrElse { throw RoutingException("TomTom: unexpected response") }
        response.error?.let { throw RoutingException("TomTom: ${it.description ?: "error"}") }
        val r = response.routes.firstOrNull() ?: throw RoutingException("TomTom: no route found")

        val points = r.legs.flatMap { leg -> leg.points.map { GeoPoint(it.latitude, it.longitude) } }
        val instructions = r.guidance?.instructions ?: emptyList()
        val steps = instructions.mapIndexed { i, ins ->
            val nextOffset = instructions.getOrNull(i + 1)?.routeOffsetInMeters ?: ins.routeOffsetInMeters
            val nextTime = instructions.getOrNull(i + 1)?.travelTimeInSeconds ?: ins.travelTimeInSeconds
            val (type, modifier) = mapManeuver(ins.maneuver, ins.turnAngleInDecimalDegrees)
            RouteStep(
                type = type,
                modifier = modifier,
                exit = ins.roundaboutExitNumber,
                road = ins.street?.takeIf { it.isNotBlank() } ?: ins.roadNumbers.firstOrNull() ?: "",
                location = GeoPoint(ins.point.latitude, ins.point.longitude),
                distanceM = (nextOffset - ins.routeOffsetInMeters).toDouble(),
                durationS = (nextTime - ins.travelTimeInSeconds).toDouble()
            )
        }
        val slow = r.sections
            .filter { it.sectionType == "TRAFFIC" && (it.magnitudeOfDelay ?: 1) >= 1 }
            .map {
                SlowSection(
                    startIndex = it.startPointIndex,
                    endIndex = it.endPointIndex,
                    category = it.simpleCategory ?: "OTHER",
                    delayS = (it.delayInSeconds ?: 0).toDouble()
                )
            }
        return Route(
            points = points,
            distanceM = r.summary.lengthInMeters.toDouble(),
            durationS = r.summary.travelTimeInSeconds.toDouble(),
            steps = steps,
            slowSections = slow,
            trafficDelayS = (r.summary.trafficDelayInSeconds ?: 0).toDouble(),
            hasTraffic = true
        )
    }

    /** Maps a TomTom maneuver code onto Still's OSRM-style type and modifier. */
    private fun mapManeuver(code: String?, angle: Int?): Pair<String, String?> {
        val side = if ((angle ?: 0) < 0) "left" else "right"
        return when (code) {
            "DEPART" -> "depart" to null
            "ARRIVE", "ARRIVE_LEFT", "ARRIVE_RIGHT", "WAYPOINT_REACHED" -> "arrive" to null
            "TURN_LEFT" -> "turn" to "left"
            "TURN_RIGHT" -> "turn" to "right"
            "BEAR_LEFT" -> "turn" to "slight left"
            "BEAR_RIGHT" -> "turn" to "slight right"
            "SHARP_LEFT" -> "turn" to "sharp left"
            "SHARP_RIGHT" -> "turn" to "sharp right"
            "MAKE_UTURN", "TRY_MAKE_UTURN", "ROUNDABOUT_BACK" -> "turn" to "uturn"
            "KEEP_LEFT" -> "fork" to "left"
            "KEEP_RIGHT" -> "fork" to "right"
            "SWITCH_PARALLEL_ROAD", "SWITCH_MAIN_ROAD" -> "fork" to side
            "ENTER_MOTORWAY", "ENTER_FREEWAY", "ENTER_HIGHWAY", "ENTRANCE_RAMP" -> "on ramp" to side
            "TAKE_EXIT", "MOTORWAY_EXIT_RIGHT" -> "off ramp" to "right"
            "MOTORWAY_EXIT_LEFT" -> "off ramp" to "left"
            "ROUNDABOUT_CROSS", "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> "roundabout" to null
            else -> "continue" to null
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val defaultClient by lazy { HttpClient(Android) }
    }
}

@Serializable
private data class TtResponse(
    val routes: List<TtRoute> = emptyList(),
    val error: TtError? = null
)

@Serializable
private data class TtError(val description: String? = null)

@Serializable
private data class TtRoute(
    val summary: TtSummary,
    val legs: List<TtLeg> = emptyList(),
    val sections: List<TtSection> = emptyList(),
    val guidance: TtGuidance? = null
)

@Serializable
private data class TtSummary(
    val lengthInMeters: Int,
    val travelTimeInSeconds: Int,
    val trafficDelayInSeconds: Int? = null
)

@Serializable
private data class TtLeg(val points: List<TtPoint> = emptyList())

@Serializable
private data class TtPoint(val latitude: Double, val longitude: Double)

@Serializable
private data class TtSection(
    val startPointIndex: Int,
    val endPointIndex: Int,
    val sectionType: String,
    val simpleCategory: String? = null,
    val magnitudeOfDelay: Int? = null,
    val delayInSeconds: Int? = null
)

@Serializable
private data class TtGuidance(val instructions: List<TtInstruction> = emptyList())

@Serializable
private data class TtInstruction(
    val routeOffsetInMeters: Int = 0,
    val travelTimeInSeconds: Int = 0,
    val point: TtPoint,
    val maneuver: String? = null,
    val street: String? = null,
    val roadNumbers: List<String> = emptyList(),
    val roundaboutExitNumber: Int? = null,
    val turnAngleInDecimalDegrees: Int? = null
)
