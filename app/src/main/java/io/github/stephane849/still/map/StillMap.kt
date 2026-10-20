package io.github.stephane849.still.map

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.stephane849.still.routing.Incident
import io.github.stephane849.still.routing.Route
import org.maplibre.android.maps.MapView

/** Lets screens zoom and recenter the map from their own buttons. */
@Stable
class StillMapState {
    internal var controller: MapController? by mutableStateOf(null)

    fun zoomIn() {
        controller?.zoomIn()
    }

    fun zoomOut() {
        controller?.zoomOut()
    }

    fun recenter() {
        controller?.recenter()
    }
}

@Composable
fun rememberStillMapState(): StillMapState = remember { StillMapState() }

/**
 * Black roads on white. Camera moves are instant (no animation) to keep
 * e-ink ghosting down; rotation and tilt are off so the map stays north-up.
 *
 * @param route when set, the route is drawn; without [follow] the camera fits it.
 * @param follow when true, the camera stays centred on the current position.
 */
@Composable
fun StillMap(
    modifier: Modifier = Modifier,
    state: StillMapState = rememberStillMapState(),
    location: Location?,
    route: Route? = null,
    incidents: List<Incident> = emptyList(),
    follow: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var centredOnce by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map -> state.controller = MapController(context, map) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            state.controller = null
        }
    }

    val controller = state.controller

    LaunchedEffect(controller, location, follow) {
        val c = controller ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        val firstFix = !centredOnce && route == null
        c.setLocation(
            loc.latitude,
            loc.longitude,
            follow = follow || firstFix,
            zoom = if (follow) 16.0 else 15.0,
            bearing = if (loc.hasBearing()) loc.bearing else null
        )
        centredOnce = true
    }

    LaunchedEffect(controller, route) {
        val c = controller ?: return@LaunchedEffect
        c.setRoute(route)
        if (route != null && !follow) c.fitRoute()
    }

    LaunchedEffect(controller, incidents) {
        controller?.setIncidents(incidents)
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
