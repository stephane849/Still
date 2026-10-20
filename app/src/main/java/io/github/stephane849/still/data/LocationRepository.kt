package io.github.stephane849.still.data

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import io.github.stephane849.still.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationRepository(context: Context) : LocationListener {

    private val locationService = LocationService(context)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    /**
     * @param driving true for turn-by-turn (1 s, 3 m); false for the
     * calmer browsing rate (5 s, 10 m) that spares the e-ink screen.
     */
    fun startLocationUpdates(driving: Boolean = false) {
        if (driving) {
            locationService.startLocationUpdates(this, intervalMs = 1000L, minDistanceM = 3f)
        } else {
            locationService.startLocationUpdates(this)
        }
    }

    fun stopLocationUpdates() {
        locationService.stopLocationUpdates()
    }

    override fun onLocationChanged(location: Location) {
        _location.value = location
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
