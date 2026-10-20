package io.github.stephane849.still.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.stephane849.still.map.StillMap
import io.github.stephane849.still.navigation.formatDistance
import io.github.stephane849.still.navigation.formatDuration
import io.github.stephane849.still.navigationSession
import java.text.DateFormat
import java.util.Date

/**
 * Turn-by-turn on the phone: the map follows you with the route drawn,
 * the next turn sits in a card on top, and arrival time sits below.
 */
@Composable
fun DriveScreen(navController: NavController) {
    val context = LocalContext.current
    val view = LocalView.current
    val session = context.navigationSession
    val destination by session.destination.collectAsState()
    val drive by session.drive.collectAsState()
    val route by session.route.collectAsState()
    val incidents by session.incidents.collectAsState()
    val location by session.location.collectAsState()
    val loading by session.loading.collectAsState()

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Leaving via the car screen's End button also brings the phone back home.
    LaunchedEffect(drive) {
        if (drive == null) navController.popBackStack("main", inclusive = false)
    }

    fun end() {
        session.endDrive()
        navController.popBackStack("main", inclusive = false)
    }

    val d = drive
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StillMap(
                modifier = Modifier.fillMaxSize(),
                location = location,
                route = route,
                incidents = incidents,
                follow = true
            )

            // Next-turn card.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(3.dp, Color.Black)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    d == null -> Text("Starting…", fontSize = 26.sp)
                    d.arrived -> Text("You've arrived", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(maneuverIcon(d.next), contentDescription = null, modifier = Modifier.size(64.dp))
                            Text(formatDistance(d.distanceToNextM), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${d.next?.action ?: "Continue"} ${d.next?.ontoRoad ?: ""}".trim(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        d.afterNext?.let { then ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Then", fontSize = 16.sp)
                                Icon(maneuverIcon(then), contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    "${then.action} ${then.ontoRoad}".trim(),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (loading) Text("Rerouting…", fontSize = 16.sp)
                    }
                }
            }

            // Road event ahead.
            if (d?.incidentAhead != null && d.incidentAheadM < 3000) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Color.White)
                        .border(2.dp, Color.Black)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        "${d.incidentAhead.shortLabel} · ${formatDistance(d.incidentAheadM)}",
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Rule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(destination?.name ?: "", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d != null && !d.arrived) {
                    val arrive = DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(System.currentTimeMillis() + (d.remainingS * 1000).toLong()))
                    Text(arrive, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${formatDuration(d.remainingS)} · ${formatDistance(d.remainingM)}", fontSize = 16.sp)
                }
            }
            Box(
                modifier = Modifier
                    .border(2.dp, Color.Black)
                    .clickable { end() }
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text("End", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
