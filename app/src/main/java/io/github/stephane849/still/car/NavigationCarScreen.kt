package io.github.stephane849.still.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import io.github.stephane849.still.R
import io.github.stephane849.still.navigation.formatDistance
import io.github.stephane849.still.navigationSession
import io.github.stephane849.still.routing.RouteStep
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Turn-by-turn on the car screen: Android Auto's navigation template
 * over Still's own map. Shares one session with the phone, so ending
 * the drive in either place ends it in both.
 */
class NavigationCarScreen(carContext: CarContext) : Screen(carContext) {

    private val session = carContext.navigationSession
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private var started = false

    init {
        lifecycleScope.launch {
            combine(session.drive, session.loading, session.error, session.incidents) { d, _, _, _ -> d }
                .collect { d ->
                    if (d != null && !started) {
                        navigationManager.navigationStarted()
                        started = true
                    }
                    if (d == null && started && !session.loading.value) {
                        // The drive ended (here, on the phone, or from another app).
                        navigationManager.navigationEnded()
                        started = false
                        screenManager.pop()
                        return@collect
                    }
                    invalidate()
                }
        }
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("End")
                    .setOnClickListener {
                        if (session.isDriving) session.endDrive() else screenManager.pop()
                    }
                    .build()
            )
            .build()

        val builder = NavigationTemplate.Builder().setActionStrip(actionStrip)
        val d = session.drive.value
        val error = session.error.value

        when {
            d == null && error != null -> builder.setNavigationInfo(MessageInfo.Builder(error).build())
            d == null -> builder.setNavigationInfo(MessageInfo.Builder("Finding a route…").build())
            d.arrived -> builder.setNavigationInfo(
                MessageInfo.Builder("You've arrived")
                    .setImage(icon(R.drawable.ic_maneuver_flag))
                    .build()
            )
            else -> {
                val routing = RoutingInfo.Builder()
                    .setCurrentStep(step(d.next), distance(d.distanceToNextM))
                d.afterNext?.let { routing.setNextStep(step(it)) }
                builder.setNavigationInfo(routing.build())

                val arrival = DateTimeWithZone.create(
                    System.currentTimeMillis() + (d.remainingS * 1000).toLong(),
                    TimeZone.getDefault()
                )
                builder.setDestinationTravelEstimate(
                    TravelEstimate.Builder(distance(d.remainingM), arrival)
                        .setRemainingTimeSeconds(d.remainingS.toLong())
                        .build()
                )
            }
        }
        return builder.build()
    }

    private fun step(s: RouteStep?): Step {
        val cue = if (s == null) "Continue" else "${s.action} ${s.ontoRoad}".trim()
        val b = Step.Builder(cue).setManeuver(maneuver(s))
        s?.road?.takeIf { it.isNotBlank() }?.let { b.setRoad(it) }
        return b.build()
    }

    private fun maneuver(s: RouteStep?): Maneuver {
        val m = s?.modifier ?: ""
        val left = m.contains("left")
        val (type, iconRes) = when (s?.type) {
            null -> Maneuver.TYPE_STRAIGHT to R.drawable.ic_maneuver_straight
            "depart" -> Maneuver.TYPE_DEPART to R.drawable.ic_maneuver_straight
            "arrive" -> Maneuver.TYPE_DESTINATION to R.drawable.ic_maneuver_flag
            "roundabout", "rotary", "exit roundabout", "exit rotary" ->
                (if (s.exit != null) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW) to
                    R.drawable.ic_maneuver_roundabout
            "merge" -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED to
                (if (left) R.drawable.ic_maneuver_slight_left else R.drawable.ic_maneuver_slight_right)
            "on ramp" -> (if (left) Maneuver.TYPE_ON_RAMP_NORMAL_LEFT else Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT) to
                (if (left) R.drawable.ic_maneuver_slight_left else R.drawable.ic_maneuver_slight_right)
            "off ramp" -> (if (left) Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT else Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT) to
                (if (left) R.drawable.ic_maneuver_slight_left else R.drawable.ic_maneuver_slight_right)
            "fork" -> (if (left) Maneuver.TYPE_FORK_LEFT else Maneuver.TYPE_FORK_RIGHT) to
                (if (left) R.drawable.ic_maneuver_slight_left else R.drawable.ic_maneuver_slight_right)
            else -> when (m) {
                "left" -> Maneuver.TYPE_TURN_NORMAL_LEFT to R.drawable.ic_maneuver_turn_left
                "right" -> Maneuver.TYPE_TURN_NORMAL_RIGHT to R.drawable.ic_maneuver_turn_right
                "slight left" -> Maneuver.TYPE_TURN_SLIGHT_LEFT to R.drawable.ic_maneuver_slight_left
                "slight right" -> Maneuver.TYPE_TURN_SLIGHT_RIGHT to R.drawable.ic_maneuver_slight_right
                "sharp left" -> Maneuver.TYPE_TURN_SHARP_LEFT to R.drawable.ic_maneuver_turn_left
                "sharp right" -> Maneuver.TYPE_TURN_SHARP_RIGHT to R.drawable.ic_maneuver_turn_right
                "uturn" -> Maneuver.TYPE_U_TURN_LEFT to R.drawable.ic_maneuver_uturn
                else -> Maneuver.TYPE_STRAIGHT to R.drawable.ic_maneuver_straight
            }
        }
        val b = Maneuver.Builder(type).setIcon(icon(iconRes))
        if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW) {
            b.setRoundaboutExitNumber(s?.exit ?: 1)
        }
        return b.build()
    }

    private fun icon(res: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    private fun distance(m: Double): Distance =
        if (m >= 1000) Distance.create(Math.round(m / 100) / 10.0, Distance.UNIT_KILOMETERS)
        else Distance.create(formatDistance(m).substringBefore(" ").toDoubleOrNull() ?: m, Distance.UNIT_METERS)
}
