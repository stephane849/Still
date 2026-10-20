package io.github.stephane849.still.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import io.github.stephane849.still.data.SavedPlace
import io.github.stephane849.still.data.SavedPlacesRepository
import io.github.stephane849.still.navigationSession
import kotlinx.coroutines.launch

/** The car's first screen: your saved places, one tap to start driving. */
class PlacesCarScreen(carContext: CarContext) : Screen(carContext) {

    private var places: List<SavedPlace> = emptyList()

    init {
        lifecycleScope.launch {
            SavedPlacesRepository(carContext).places.collect {
                places = it
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        list.setNoItemsMessage("Save places on your phone to see them here.")
        places.forEach { place ->
            list.addItem(
                Row.Builder()
                    .setTitle(place.name)
                    .setOnClickListener { go(place) }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("Still")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list.build())
            .build()
    }

    private fun go(place: SavedPlace) {
        val session = carContext.navigationSession
        session.setMode(io.github.stephane849.still.routing.TravelMode.CAR)
        session.setDestination(place.name, place.lat, place.lng)
        session.planRoute(onReady = { session.startDrive() })
        screenManager.push(NavigationCarScreen(carContext))
    }
}
