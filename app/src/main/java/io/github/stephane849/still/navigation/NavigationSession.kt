package io.github.stephane849.still.navigation

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import io.github.stephane849.still.LocationService
import io.github.stephane849.still.data.LocationRepository
import io.github.stephane849.still.data.UserPreferencesRepository
import io.github.stephane849.still.routing.GeoPoint
import io.github.stephane849.still.routing.Incident
import io.github.stephane849.still.routing.Ontario511
import io.github.stephane849.still.routing.OsrmRouter
import io.github.stephane849.still.routing.Route
import io.github.stephane849.still.routing.RouteStep
import io.github.stephane849.still.routing.TomTomRouter
import io.github.stephane849.still.routing.TravelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

data class Destination(val name: String, val point: GeoPoint)

data class DriveState(
    val next: RouteStep?,
    val afterNext: RouteStep?,
    val distanceToNextM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val arrived: Boolean,
    /** The nearest reported road event still ahead, if any. */
    val incidentAhead: Incident? = null,
    val incidentAheadM: Double = 0.0
)

/**
 * One navigation session for the whole app, shared by the phone screens
 * and the Android Auto screens: destination, route, live drive state,
 * location updates while driving, and voice prompts.
 */
class NavigationSession(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = UserPreferencesRepository(appContext)
    private val locationService = LocationService(appContext)
    private val locationRepository = LocationRepository(appContext)

    /** Live position while a drive is running. */
    val location: StateFlow<Location?> = locationRepository.location

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route

    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    val incidents: StateFlow<List<Incident>> = _incidents

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _drive = MutableStateFlow<DriveState?>(null)
    val drive: StateFlow<DriveState?> = _drive

    private val _mode = MutableStateFlow(TravelMode.CAR)
    val mode: StateFlow<TravelMode> = _mode

    /** Switches between car, bike and walking, replanning any current route. */
    fun setMode(m: TravelMode) {
        if (m == _mode.value) return
        _mode.value = m
        if (_destination.value != null && !isDriving) {
            _route.value = null
            _incidents.value = emptyList()
            planRoute()
        }
    }

    private var cumulative = DoubleArray(0)
    private var stepAlong = DoubleArray(0)
    private var incidentAlong = DoubleArray(0)
    private var offRouteCount = 0
    private val announced = mutableSetOf<String>()
    private var routeJob: Job? = null
    private var locationJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    val isDriving: Boolean get() = _drive.value != null

    fun setDestination(name: String, lat: Double, lng: Double) {
        if (isDriving) endDrive()
        _destination.value = Destination(name, GeoPoint(lat, lng))
        _route.value = null
        _incidents.value = emptyList()
        _error.value = null
    }

    /**
     * Plans a route to the destination: traffic-aware through TomTom when
     * a key is set, otherwise through the OSRM server in Settings.
     */
    fun planRoute(from: Location? = null, onReady: (() -> Unit)? = null) {
        val dest = _destination.value ?: return
        routeJob?.cancel()
        routeJob = scope.launch {
            _loading.value = true
            _error.value = null
            try {
                val origin = from ?: location.value ?: locationService.getBestLocationOrNull()
                if (origin == null) {
                    _error.value = "Can't find your location. Check that location is on."
                    return@launch
                }
                val start = GeoPoint(origin.latitude, origin.longitude)
                val r = when (_mode.value) {
                    TravelMode.BIKE -> OsrmRouter(OsrmRouter.BIKE_SERVER).route(start, dest.point)
                    TravelMode.WALK -> OsrmRouter(OsrmRouter.WALK_SERVER).route(start, dest.point)
                    TravelMode.CAR -> {
                        val tomTomKey = prefs.tomTomKey.first()
                        val osrm = OsrmRouter(prefs.routingServer.first())
                        if (tomTomKey.isNotBlank()) {
                            runCatching { TomTomRouter(tomTomKey).route(start, dest.point) }
                                .getOrElse { osrm.route(start, dest.point) }
                        } else {
                            osrm.route(start, dest.point)
                        }
                    }
                }
                setRoute(r)
                loadIncidents(r)
                onReady?.invoke()
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
        // Step numbers change with a new route, so earlier prompts no longer apply.
        announced.removeAll { it.startsWith("near-") || it.startsWith("far-") }
    }

    private suspend fun loadIncidents(r: Route) {
        val key = prefs.ontario511Key.first()
        if (key.isBlank() || _mode.value != TravelMode.CAR) {
            _incidents.value = emptyList()
            incidentAlong = DoubleArray(0)
            return
        }
        val found = runCatching { Ontario511(key).eventsNear(r.points) }.getOrDefault(emptyList())
        _incidents.value = found
        incidentAlong = DoubleArray(found.size) { i -> cumulative[nearestIndex(found[i].point, r.points)] }
    }

    fun startDrive() {
        val r = _route.value ?: return
        announced.clear()
        _drive.value = DriveState(
            next = r.steps.getOrNull(1),
            afterNext = r.steps.getOrNull(2),
            distanceToNextM = r.steps.firstOrNull()?.distanceM ?: 0.0,
            remainingM = r.distanceM,
            remainingS = r.durationS,
            arrived = false
        )
        ensureTts()
        locationRepository.startLocationUpdates(driving = true)
        locationJob?.cancel()
        locationJob = scope.launch {
            location.collect { loc -> if (loc != null) onLocation(loc) }
        }
        r.steps.getOrNull(1)?.let { speak("${it.action} ${it.ontoRoad}".trim(), "start") }
    }

    fun endDrive() {
        locationJob?.cancel()
        locationJob = null
        locationRepository.stopLocationUpdates()
        _drive.value = null
        tts?.stop()
    }

    private fun onLocation(loc: Location) {
        val r = _route.value ?: return
        if (_drive.value == null || r.points.size < 2) return
        val pos = snapToRoute(GeoPoint(loc.latitude, loc.longitude), r.points, cumulative)

        // Off route: reroute after three bad fixes in a row.
        if (pos.offRouteM > OFF_ROUTE_M) {
            offRouteCount++
            if (offRouteCount >= 3 && !_loading.value) {
                offRouteCount = 0
                speak("Rerouting", "reroute-${System.currentTimeMillis() / 30_000}")
                planRoute(loc)
            }
            return
        }
        offRouteCount = 0

        val total = cumulative.last().coerceAtLeast(1.0)
        val remaining = (total - pos.alongM).coerceAtLeast(0.0)
        val nextIndex = stepAlong.indexOfFirst { it > pos.alongM + 5 }.takeIf { it >= 0 }
        val next = nextIndex?.let { r.steps[it] }
        val toNext = nextIndex?.let { stepAlong[it] - pos.alongM } ?: remaining
        val arrived = remaining < ARRIVED_M

        val incidentIndex = incidentAlong.indices
            .filter { incidentAlong[it] > pos.alongM }
            .minByOrNull { incidentAlong[it] }
        val incident = incidentIndex?.let { _incidents.value.getOrNull(it) }
        val incidentM = incidentIndex?.let { incidentAlong[it] - pos.alongM } ?: 0.0

        _drive.value = DriveState(
            next = next,
            afterNext = nextIndex?.let { r.steps.getOrNull(it + 1) },
            distanceToNextM = toNext,
            remainingM = remaining,
            remainingS = r.durationS * (remaining / total),
            arrived = arrived,
            incidentAhead = incident,
            incidentAheadM = incidentM
        )

        if (arrived) {
            speak("You have arrived", "arrived")
            return
        }
        if (incident != null && incidentM <= INCIDENT_WARN_M) {
            speak("${incident.shortLabel} ahead", "incident-$incidentIndex")
        }
        if (next != null && nextIndex != null) {
            val text = "${next.action} ${next.ontoRoad}".trim()
            val stepLength = stepAlong[nextIndex] - (stepAlong.getOrNull(nextIndex - 1) ?: 0.0)
            if (toNext <= NEAR_M) {
                speak(text, "near-$nextIndex")
            } else if (toNext <= FAR_M && stepLength > FAR_M * 1.5) {
                speak("In ${formatDistance(toNext)}, $text", "far-$nextIndex")
            }
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    private fun speak(text: String, key: String) {
        if (!announced.add(key)) return
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
    }

    private companion object {
        const val OFF_ROUTE_M = 50.0
        const val ARRIVED_M = 25.0
        const val NEAR_M = 150.0
        const val FAR_M = 800.0
        const val INCIDENT_WARN_M = 1000.0
    }
}
