package io.github.stephane849.still.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.CarAppService
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.validation.HostValidator
import io.github.stephane849.still.navigationSession

/** Entry point Android Auto binds to. */
class StillCarAppService : CarAppService() {
    // Still is sideloaded and open source, so any Android Auto host may connect.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = StillCarSession()
}

class StillCarSession : Session() {

    private lateinit var renderer: CarMapRenderer

    override fun onCreateScreen(intent: Intent): Screen {
        renderer = CarMapRenderer(carContext)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        carContext.getCarService(NavigationManager::class.java)
            .setNavigationManagerCallback(object : NavigationManagerCallback {
                override fun onStopNavigation() {
                    carContext.navigationSession.endDrive()
                }
            })

        val places = PlacesCarScreen(carContext)
        // If a drive is already running from the phone, go straight to it.
        if (carContext.navigationSession.isDriving) {
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(places)
            return NavigationCarScreen(carContext)
        }
        return places
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        if (::renderer.isInitialized) renderer.setNight(carContext.isDarkMode)
    }
}
