package io.github.stephane849.still.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import io.github.stephane849.still.routing.TravelMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import io.github.stephane849.still.data.SavedPlace
import io.github.stephane849.still.data.SavedPlacesRepository
import io.github.stephane849.still.map.StillMap
import io.github.stephane849.still.navigation.formatDistance
import io.github.stephane849.still.navigation.formatDuration
import io.github.stephane849.still.navigationSession
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun RoutePreviewScreen(navController: NavController) {
    val context = LocalContext.current
    val session = context.navigationSession
    val destination by session.destination.collectAsState()
    val route by session.route.collectAsState()
    val incidents by session.incidents.collectAsState()
    val loading by session.loading.collectAsState()
    val error by session.error.collectAsState()

    LaunchedEffect(destination) {
        if (destination != null && session.route.value == null) session.planRoute()
    }

    val dest = destination

    Column(Modifier.fillMaxSize().background(Color.White)) {
        StillHeader(
            title = dest?.name ?: "Route",
            onBack = { navController.popBackStack() }
        )
        val mode by session.mode.collectAsState()
        ModeSelector(selected = mode, onSelect = { session.setMode(it) })
        Rule(1.dp)

        val r = route
        when {
            loading && r == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicatorMMD()
            }
            r == null -> Column(Modifier.weight(1f).padding(16.dp)) {
                Text(error ?: "Finding a route…", fontSize = 20.sp)
                if (error != null) {
                    Spacer(Modifier.height(16.dp))
                    ButtonMMD(onClick = { session.planRoute() }) { Text("Try again") }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(formatDuration(r.durationS), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(formatDistance(r.distanceM), fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                val arrive = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(Date(System.currentTimeMillis() + (r.durationS * 1000).toLong()))
                val via = r.mainRoad?.let { "via $it · " } ?: ""
                Text(
                    text = "${via}arrive $arrive",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (r.hasTraffic) {
                    val traffic = if (r.trafficDelayS >= 60) {
                        "${formatDuration(r.trafficDelayS)} slower than usual · dashed line = slow traffic"
                    } else {
                        "Traffic is light"
                    }
                    Text(traffic, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }

                StillMap(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(1.dp, Color.Black),
                    location = null,
                    route = r,
                    incidents = incidents
                )

                incidents.take(2).forEach { inc ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(inc.shortLabel, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                ButtonMMD(
                    onClick = {
                        session.startDrive()
                        navController.navigate("drive")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(64.dp)
                ) {
                    Text("Go", fontSize = 24.sp)
                }
            }
        }
    }
}

/** Car, bike or walk, as three equal tabs; the selected one is solid black. */
@Composable
private fun ModeSelector(selected: TravelMode, onSelect: (TravelMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TravelMode.values().forEach { m ->
            val on = m == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(if (on) Color.Black else Color.White)
                    .border(2.dp, Color.Black)
                    .clickable { onSelect(m) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when (m) {
                        TravelMode.CAR -> Icons.Outlined.DirectionsCar
                        TravelMode.BIKE -> Icons.Outlined.DirectionsBike
                        TravelMode.WALK -> Icons.Outlined.DirectionsWalk
                    },
                    contentDescription = null,
                    tint = if (on) Color.White else Color.Black,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    m.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (on) Color.White else Color.Black,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}
