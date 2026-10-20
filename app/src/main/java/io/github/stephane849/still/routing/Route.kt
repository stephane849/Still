package io.github.stephane849.still.routing

data class GeoPoint(val lat: Double, val lng: Double)

enum class TravelMode(val label: String) {
    CAR("Car"),
    BIKE("Bike"),
    WALK("Walk")
}

data class RouteStep(
    val type: String,
    val modifier: String?,
    val exit: Int?,
    val road: String,
    val location: GeoPoint,
    val distanceM: Double,
    val durationS: Double
) {
    /** Short spoken and written instruction, e.g. "Turn right". */
    val action: String
        get() {
            val side = modifier ?: ""
            return when (type) {
                "depart" -> "Head out"
                "arrive" -> "Arrive"
                "turn", "end of road" -> when (side) {
                    "left" -> "Turn left"
                    "right" -> "Turn right"
                    "slight left" -> "Bear left"
                    "slight right" -> "Bear right"
                    "sharp left" -> "Sharp left"
                    "sharp right" -> "Sharp right"
                    "uturn" -> "Make a U-turn"
                    else -> "Continue"
                }
                "merge" -> "Merge"
                "on ramp" -> "Take the ramp"
                "off ramp" -> "Take the exit"
                "fork" -> if (side.contains("left")) "Keep left" else "Keep right"
                "roundabout", "rotary" -> if (exit != null) "Take exit $exit at the roundabout" else "Enter the roundabout"
                "exit roundabout", "exit rotary" -> "Exit the roundabout"
                else -> when (side) {
                    "left", "sharp left", "slight left" -> "Keep left"
                    "right", "sharp right", "slight right" -> "Keep right"
                    "uturn" -> "Make a U-turn"
                    else -> "Continue"
                }
            }
        }

    /** "onto Bronson Ave", or empty when the road has no name. */
    val ontoRoad: String
        get() = if (road.isBlank()) "" else "onto $road"
}

/** A stretch of the route, by polyline point index, where traffic is slow. */
data class SlowSection(
    val startIndex: Int,
    val endIndex: Int,
    /** JAM, ROAD_WORK, ROAD_CLOSURE or OTHER (TomTom's simple categories). */
    val category: String,
    val delayS: Double
)

/** A reported road event (Ontario 511) close to the route. */
data class Incident(
    val point: GeoPoint,
    val type: String,
    val road: String,
    val description: String,
    val fullClosure: Boolean
) {
    val shortLabel: String
        get() = when {
            fullClosure -> "Road closed"
            type == "roadwork" -> "Roadwork"
            type == "closures" -> "Lane closure"
            type == "accidentsAndIncidents" -> "Incident"
            else -> "Road event"
        } + if (road.isNotBlank()) " on $road" else ""
}

data class Route(
    val points: List<GeoPoint>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
    val slowSections: List<SlowSection> = emptyList(),
    val trafficDelayS: Double = 0.0,
    /** True when the travel time accounts for live traffic. */
    val hasTraffic: Boolean = false
) {
    /** The name of the road that carries most of the trip, for "via …". */
    val mainRoad: String?
        get() = steps
            .filter { it.road.isNotBlank() }
            .groupBy { it.road }
            .maxByOrNull { (_, s) -> s.sumOf { it.distanceM } }
            ?.key
}
