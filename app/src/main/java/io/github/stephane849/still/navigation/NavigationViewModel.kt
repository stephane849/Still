package io.github.stephane849.still.navigation

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.stephane849.still.LocationService
import io.github.stephane849.still.data.UserPreferencesRepository
import io.github.stephane849.still.routing.GeoPoint
import io.github.stephane849.still.routing.OsrmRouter
import io.github.stephane849.still.routing.Route
import io.github.stephane849.still.routing.RouteStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class Destination(val name: String, val point: GeoPoint)

data class DriveState(
    val next: RouteStep?,
    val afterNext: RouteStep?,
    val distanceToNextM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val arrived: Boolean
)

/**
 * Holds the current destination, its route and the live drive state.
 * Activity-scoped so the preview and drive screens share one session.
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferencesRepository(application)
    private val locationService = LocationService(application)

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _drive = MutableStateFlow<DriveState?>(null)
    val drive: StateFlow<DriveState?> = _drive

    private val _speech = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val speech: SharedFlow<String> = _speech

    private var cumulative = DoubleArray(0)
    private var stepAlong = DoubleArray(0)
    private var offRouteCount = 0
    private var announced = mutableSetOf<String>()
    private var routeJob: Job? = null

    fun setDestination(name: String, lat: Double, lng: Double) {
        _destination.value = Destination(name, GeoPoint(lat, lng))
        _route.value = null
        _error.value = null
        _drive.value = null
    }

    /** Plans a route from the best known position to the destination. */
    fun planRoute(from: Location? = null) {
        val dest = _destination.value ?: return
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val origin = from ?: locationService.getBestLocationOrNull()
                if (origin == null) {
                    _error.value = "Can't find your location. Check that location is on."
                    return@launch
                }
                val server = prefs.routingServer.first()
                val r = OsrmRouter(server).route(GeoPoint(origin.latitude, origin.longitude), dest.point)
                setRoute(r)
            } catch (e: Exception) {
                _error.value = "No route: ${e.message ?: "routing server unreachable"}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun setRoute(r: Route) {
        _route.value = r
        cumulative = cumulativeDistances(r.points)
        stepAlong = DoubleArray(r.steps.size) { i ->
            cumulative[nearestIndex(r.steps[i].location, r.points)]
        }
        offRouteCount = 0
        // Step numbers change on a new route, so earlier announcements no longer apply.
        announced.removeAll { it.startsWith("near-") || it.startsWith("far-") }
    }

    fun startDrive() {
        announced.clear()
        val r = _route.value ?: return
        _drive.value = DriveState(
            next = r.steps.getOrNull(1),
            afterNext = r.steps.getOrNull(2),
            distanceToNextM = r.steps.firstOrNull()?.distanceM ?: 0.0,
            remainingM = r.distanceM,
            remainingS = r.durationS,
            arrived = false
        )
        r.steps.getOrNull(1)?.let { speak("${it.action} ${it.ontoRoad}".trim(), "start") }
    }

    fun onLocation(loc: Location) {
        val r = _route.value ?: return
        if (_drive.value == null || r.points.size < 2) return
        val p = GeoPoint(loc.latitude, loc.longitude)
        val pos = snapToRoute(p, r.points, cumulative)

        // Off route: reroute after three bad fixes in a row.
        if (pos.offRouteM > OFF_ROUTE_M) {
            offRouteCount++
            if (offRouteCount >= 3 && _loading.value.not()) {
                offRouteCount = 0
                speak("Rerouting", "reroute-${System.currentTimeMillis() / 30_000}")
                planRoute(loc)
            }
            return
        }
        offRouteCount = 0

        val total = r.distanceM.coerceAtLeast(1.0)
        val remaining = (cumulative.last() - pos.alongM).coerceAtLeast(0.0)
        val nextIndex = stepAlong.indexOfFirst { it > pos.alongM + 5 }.takeIf { it >= 0 }
        val next = nextIndex?.let { r.steps[it] }
        val toNext = nextIndex?.let { stepAlong[it] - pos.alongM } ?: remaining
        val arrived = remaining < ARRIVED_M

        _drive.value = DriveState(
            next = next,
            afterNext = nextIndex?.let { r.steps.getOrNull(it + 1) },
            distanceToNextM = toNext,
            remainingM = remaining,
            remainingS = r.durationS * (remaining / total),
            arrived = arrived
        )

        if (arrived) {
            speak("You have arrived", "arrived")
            return
        }
        if (next != null && nextIndex != null) {
            val text = "${next.action} ${next.ontoRoad}".trim()
            if (toNext <= NEAR_M) {
                speak(text, "near-$nextIndex")
            } else if (toNext <= FAR_M && (stepAlong[nextIndex] - (stepAlong.getOrNull(nextIndex - 1) ?: 0.0)) > FAR_M * 1.5) {
                speak("In ${formatDistance(toNext)}, $text", "far-$nextIndex")
            }
        }
    }

    fun endDrive() {
        _drive.value = null
    }

    private fun speak(text: String, key: String) {
        if (announced.add(key)) _speech.tryEmit(text)
    }

    private companion object {
        const val OFF_ROUTE_M = 50.0
        const val ARRIVED_M = 25.0
        const val NEAR_M = 150.0
        const val FAR_M = 800.0
    }
}
