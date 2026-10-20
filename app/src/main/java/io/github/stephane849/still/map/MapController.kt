package io.github.stephane849.still.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import io.github.stephane849.still.routing.GeoPoint
import io.github.stephane849.still.routing.Incident
import io.github.stephane849.still.routing.Route
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Drives one MapLibre map in Still's style. Used by the phone screens
 * and by the Android Auto car screen, so both draw the same map.
 *
 * Slow traffic is drawn as a hatched (dashed) route line; road events
 * are drawn as a ringed "!" — no colour is needed for either.
 */
class MapController(
    private val context: Context,
    val map: MapLibreMap,
    night: Boolean = false
) {
    private var style: Style? = null
    var night: Boolean = night
        private set

    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastBearing: Float = 0f
    private var route: Route? = null
    private var incidents: List<Incident> = emptyList()

    init {
        map.uiSettings.apply {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isLogoEnabled = false
            isFlingVelocityAnimationEnabled = false
        }
        loadStyle()
    }

    fun setNight(value: Boolean) {
        if (value == night) return
        night = value
        loadStyle()
    }

    private fun loadStyle() {
        style = null
        map.setStyle(Style.Builder().fromJson(MapStyle.json(context, night))) { loaded ->
            addOverlayLayers(loaded)
            style = loaded
            // Redraw whatever was set before the style finished loading.
            route?.let { drawRoute(it) }
            drawIncidents()
            val lat = lastLat
            val lng = lastLng
            if (lat != null && lng != null) drawLocation(lat, lng)
        }
    }

    /**
     * @param bearing direction of travel in degrees from north, when the
     * GPS knows it; the arrow keeps its last heading otherwise.
     */
    fun setLocation(lat: Double, lng: Double, follow: Boolean, zoom: Double = 16.0, bearing: Float? = null) {
        lastLat = lat
        lastLng = lng
        if (bearing != null) lastBearing = bearing
        drawLocation(lat, lng)
        if (follow) map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
    }

    fun centerOn(lat: Double, lng: Double, zoom: Double = 15.0) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
    }

    fun recenter(zoom: Double = 15.0) {
        val lat = lastLat ?: return
        val lng = lastLng ?: return
        centerOn(lat, lng, zoom)
    }

    fun zoomIn() = map.moveCamera(CameraUpdateFactory.zoomIn())
    fun zoomOut() = map.moveCamera(CameraUpdateFactory.zoomOut())

    fun setRoute(r: Route?) {
        route = r
        if (r == null) {
            style?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(EMPTY)
            style?.getSourceAs<GeoJsonSource>(SLOW_SOURCE)?.setGeoJson(EMPTY)
        } else {
            drawRoute(r)
        }
    }

    fun setIncidents(list: List<Incident>) {
        incidents = list
        drawIncidents()
    }

    fun fitRoute(paddingPx: Int = 60) {
        val pts = route?.points ?: return
        if (pts.size < 2) return
        val bounds = LatLngBounds.Builder().includes(pts.map { LatLng(it.lat, it.lng) }).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
    }

    private fun drawLocation(lat: Double, lng: Double) {
        style?.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(
            """{"type":"Feature","properties":{"bearing":$lastBearing},"geometry":{"type":"Point","coordinates":[$lng,$lat]}}"""
        )
    }

    private fun drawRoute(r: Route) {
        val s = style ?: return
        s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(lineJson(r.points))
        val slowLines = r.slowSections.mapNotNull { sec ->
            val from = sec.startIndex.coerceIn(0, r.points.lastIndex)
            val to = sec.endIndex.coerceIn(0, r.points.lastIndex)
            if (to > from) r.points.subList(from, to + 1) else null
        }
        s.getSourceAs<GeoJsonSource>(SLOW_SOURCE)?.setGeoJson(multiLineJson(slowLines))
    }

    private fun drawIncidents() {
        val s = style ?: return
        val features = incidents.joinToString(",") { pointFeature(it.point.lat, it.point.lng) }
        s.getSourceAs<GeoJsonSource>(INCIDENT_SOURCE)
            ?.setGeoJson("""{"type":"FeatureCollection","features":[$features]}""")
    }

    private fun addOverlayLayers(style: Style) {
        val bg = if (night) MapStyle.BLACK else MapStyle.WHITE
        val fg = if (night) MapStyle.WHITE else MapStyle.BLACK

        style.addSource(GeoJsonSource(ROUTE_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(SLOW_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(INCIDENT_SOURCE, EMPTY))
        style.addSource(GeoJsonSource(ME_SOURCE, EMPTY))

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
        // Slow traffic: background-coloured dashes cut into the route line.
        style.addLayerBelow(
            LineLayer("still-route-slow", SLOW_SOURCE).withProperties(
                PropertyFactory.lineColor(bg),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f))
            ),
            LABEL_LAYER
        )

        style.addLayer(
            CircleLayer("still-incident-ring", INCIDENT_SOURCE).withProperties(
                PropertyFactory.circleRadius(13f),
                PropertyFactory.circleColor(bg),
                PropertyFactory.circleStrokeColor(fg),
                PropertyFactory.circleStrokeWidth(3f)
            )
        )
        style.addLayer(
            SymbolLayer("still-incident-mark", INCIDENT_SOURCE).withProperties(
                PropertyFactory.textField("!"),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(16f),
                PropertyFactory.textColor(fg),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        )

        // Your position: a ringed navigation arrow that turns with your heading.
        style.addImage(ARROW_IMAGE, arrowBitmap(bg, fg))
        style.addLayer(
            SymbolLayer("still-me", ME_SOURCE).withProperties(
                PropertyFactory.iconImage(ARROW_IMAGE),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
    }

    /** Draws the position arrow: a pointed chevron inside a ring, in two tones only. */
    private fun arrowBitmap(bgHex: String, fgHex: String): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (40 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = android.graphics.Color.parseColor(bgHex)
        val fg = android.graphics.Color.parseColor(fgHex)
        val cx = size / 2f
        val r = size / 2f - 2 * d

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg; style = Paint.Style.STROKE; strokeWidth = 3 * d
        }
        c.drawCircle(cx, cx, r, fill)
        c.drawCircle(cx, cx, r, ring)

        // Arrow pointing up (north); MapLibre rotates it by the bearing.
        val arrow = Path().apply {
            moveTo(cx, cx - r * 0.62f)
            lineTo(cx + r * 0.46f, cx + r * 0.48f)
            lineTo(cx, cx + r * 0.22f)
            lineTo(cx - r * 0.46f, cx + r * 0.48f)
            close()
        }
        c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; style = Paint.Style.FILL })
        return bmp
    }

    private companion object {
        const val ME_SOURCE = "still-me"
        const val ARROW_IMAGE = "still-arrow"
        const val ROUTE_SOURCE = "still-route"
        const val SLOW_SOURCE = "still-slow"
        const val INCIDENT_SOURCE = "still-incidents"
        const val LABEL_LAYER = "road-label"
        const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

        fun pointFeature(lat: Double, lng: Double): String =
            """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[$lng,$lat]}}"""

        fun pointJson(lat: Double, lng: Double): String = pointFeature(lat, lng)

        fun coords(points: List<GeoPoint>): String =
            points.joinToString(prefix = "[", postfix = "]", separator = ",") { "[${it.lng},${it.lat}]" }

        fun lineJson(points: List<GeoPoint>): String =
            """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":${coords(points)}}}"""

        fun multiLineJson(lines: List<List<GeoPoint>>): String =
            if (lines.isEmpty()) EMPTY
            else """{"type":"Feature","properties":{},"geometry":{"type":"MultiLineString","coordinates":[${lines.joinToString(",") { coords(it) }}]}}"""
    }
}
