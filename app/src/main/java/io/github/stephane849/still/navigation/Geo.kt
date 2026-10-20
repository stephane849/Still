package io.github.stephane849.still.navigation

import io.github.stephane849.still.routing.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

fun distanceM(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
}

/** Cumulative distance along a polyline, starting at 0. */
fun cumulativeDistances(points: List<GeoPoint>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) out[i] = out[i - 1] + distanceM(points[i - 1], points[i])
    return out
}

data class RoutePosition(
    /** Distance travelled along the route to the snapped point, in metres. */
    val alongM: Double,
    /** How far the real position is from the route line, in metres. */
    val offRouteM: Double
)

/**
 * Snaps a position onto a polyline using a local flat projection,
 * which is accurate enough at street scale.
 */
fun snapToRoute(p: GeoPoint, points: List<GeoPoint>, cumulative: DoubleArray): RoutePosition {
    if (points.size < 2) return RoutePosition(0.0, if (points.isEmpty()) 0.0 else distanceM(p, points[0]))
    val mPerDegLat = 111_320.0
    val mPerDegLng = 111_320.0 * cos(Math.toRadians(p.lat))
    var best = RoutePosition(0.0, Double.MAX_VALUE)
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val ax = (a.lng - p.lng) * mPerDegLng
        val ay = (a.lat - p.lat) * mPerDegLat
        val bx = (b.lng - p.lng) * mPerDegLng
        val by = (b.lat - p.lat) * mPerDegLat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val d = sqrt(cx * cx + cy * cy)
        if (d < best.offRouteM) {
            best = RoutePosition(cumulative[i] + t * sqrt(len2), d)
        }
    }
    return best
}

/** Index of the polyline vertex closest to a point. */
fun nearestIndex(p: GeoPoint, points: List<GeoPoint>): Int {
    var bestI = 0
    var bestD = Double.MAX_VALUE
    points.forEachIndexed { i, q ->
        val d = distanceM(p, q)
        if (d < bestD) {
            bestD = d
            bestI = i
        }
    }
    return bestI
}

fun formatDistance(m: Double): String = when {
    m >= 10_000 -> "${(m / 1000).toInt()} km"
    m >= 1_000 -> "%.1f km".format(m / 1000)
    m >= 100 -> "${((m / 50).toInt() * 50)} m"
    else -> "${((m / 10).toInt() * 10).coerceAtLeast(10)} m"
}

fun formatDuration(s: Double): String {
    val minutes = (s / 60).toInt().coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
