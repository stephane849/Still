package io.github.stephane849.still.car

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import io.github.stephane849.still.LocationService
import io.github.stephane849.still.map.MapController
import io.github.stephane849.still.navigationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView

/**
 * Draws Still's map on the car screen. Android Auto hands us a Surface;
 * we put a virtual display on it and show an ordinary MapLibre MapView
 * there, driven by the same MapController the phone uses.
 *
 * The car screen follows the car's own day/night signal: black roads on
 * white by day, white roads on black at night.
 */
class CarMapRenderer(private val carContext: CarContext) : SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var controller: MapController? = null
    private var scope: CoroutineScope? = null
    private var fittedRoute: Any? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        tearDown()

        val displayManager = carContext.getSystemService(DisplayManager::class.java)
        val display = displayManager.createVirtualDisplay(
            "StillCarMap",
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )
        virtualDisplay = display

        val p = Presentation(carContext, display.display)
        val view = MapView(p.context)
        p.setContentView(view)
        view.onCreate(null)
        view.onStart()
        view.onResume()
        p.show()
        presentation = p
        mapView = view

        view.getMapAsync { map ->
            controller = MapController(p.context, map, night = carContext.isDarkMode)
            observeSession()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        tearDown()
    }

    fun setNight(night: Boolean) {
        controller?.setNight(night)
    }

    private fun observeSession() {
        val c = controller ?: return
        val session = carContext.navigationSession
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s

        // Before a drive starts, centre on the last known position once.
        s.launch {
            LocationService(carContext).getBestLocationOrNull()?.let {
                if (!session.isDriving) c.setLocation(it.latitude, it.longitude, follow = true, zoom = 15.0)
            }
        }

        s.launch {
            combine(session.route, session.incidents) { r, i -> r to i }.collect { (r, i) ->
                c.setRoute(r)
                c.setIncidents(i)
                if (r != null && !session.isDriving && fittedRoute !== r) {
                    c.fitRoute(paddingPx = 80)
                    fittedRoute = r
                }
            }
        }

        s.launch {
            session.location.collect { loc ->
                if (loc != null) c.setLocation(
                    loc.latitude,
                    loc.longitude,
                    follow = session.isDriving,
                    bearing = if (loc.hasBearing()) loc.bearing else null
                )
            }
        }
    }

    private fun tearDown() {
        scope?.cancel()
        scope = null
        controller = null
        mapView?.let {
            it.onPause()
            it.onStop()
            it.onDestroy()
        }
        mapView = null
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        fittedRoute = null
    }
}
