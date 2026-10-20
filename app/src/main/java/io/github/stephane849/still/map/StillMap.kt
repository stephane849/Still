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
import io.github.stephane849.still.routing.GeoPoint
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** Lets screens zoom and recenter the map from their own buttons. */
@Stable
class StillMapState {
    internal var map: MapLibreMap? = null
    internal var lastLocation: Location? = null

    fun zoomIn() {
        map?.moveCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        map?.moveCamera(CameraUpdateFactory.zoomOut())
    }

    fun recenter() {
        val loc = lastLocation ?: return
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0))
    }
}

@Composable
fun rememberStillMapState(): StillMapState = remember { StillMapState() }

/**
 * Black roads on white. Camera moves are instant (no animation) to keep
 * e-ink ghosting down; rotation and tilt are off so the map stays north-up.
 *
 * @param route when set, the route is drawn and the camera fits it.
 * @param follow when true, the camera stays centred on the current position.
 */
@Composable
fun StillMap(
    modifier: Modifier = Modifier,
    state: StillMapState = rememberStillMapState(),
    location: Location?,
    route: List<GeoPoint>? = null,
    follow: Boolean = false,
    night: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var style by remember { mutableStateOf<Style?>(null) }
    var centredOnce by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                state.map = map
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isFlingVelocityAnimationEnabled = false
                }
                map.setStyle(Style.Builder().fromJson(MapStyle.json(context, night))) { loaded ->
                    addOverlayLayers(loaded, night)
                    style = loaded
                }
            }
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
            state.map = null
        }
    }

    // Position marker and camera.
    LaunchedEffect(style, location, follow) {
        val s = style ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        state.lastLocation = loc
        s.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(pointJson(loc.latitude, loc.longitude))
        val map = state.map ?: return@LaunchedEffect
        if (follow) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.0))
        } else if (!centredOnce && route == null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0))
            centredOnce = true
        }
    }

    // Route line and fitting the camera to it.
    LaunchedEffect(style, route) {
        val s = style ?: return@LaunchedEffect
        val source = s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return@LaunchedEffect
        if (route.isNullOrEmpty()) {
            source.setGeoJson(EMPTY)
            return@LaunchedEffect
        }
        source.setGeoJson(lineJson(route))
        if (!follow && route.size >= 2) {
            val bounds = LatLngBounds.Builder()
                .includes(route.map { LatLng(it.lat, it.lng) })
                .build()
            state.map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60))
        }
    }

    AndroidView(modifier = modifier, factory = { mapView })
}

private const val ME_SOURCE = "still-me"
private const val ROUTE_SOURCE = "still-route"
private const val LABEL_LAYER = "road-label"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

private fun addOverlayLayers(style: Style, night: Boolean) {
    val bg = if (night) MapStyle.BLACK else MapStyle.WHITE
    val fg = if (night) MapStyle.WHITE else MapStyle.BLACK

    style.addSource(GeoJsonSource(ROUTE_SOURCE, EMPTY))
    style.addLayerBelow(
        LineLayer("still-route-casing", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor(bg),
            PropertyFactory.lineWidth(16f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        ),
        LABEL_LAYER
    )
    style.addLayerBelow(
        LineLayer("still-route-line", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor(fg),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        ),
        LABEL_LAYER
    )

    style.addSource(GeoJsonSource(ME_SOURCE, EMPTY))
    style.addLayer(
        CircleLayer("still-me-ring", ME_SOURCE).withProperties(
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleColor(bg),
            PropertyFactory.circleStrokeColor(fg),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
    style.addLayer(
        CircleLayer("still-me-dot", ME_SOURCE).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(fg)
        )
    )
}

private fun pointJson(lat: Double, lng: Double): String =
    """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[$lng,$lat]}}"""

private fun lineJson(points: List<GeoPoint>): String =
    points.joinToString(
        prefix = """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""",
        postfix = "]}}",
        separator = ","
    ) { "[${it.lng},${it.lat}]" }
