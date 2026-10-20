package io.github.stephane849.still.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import io.github.stephane849.still.data.SavedPlace
import io.github.stephane849.still.data.SavedPlacesRepository
import io.github.stephane849.still.map.StillMap
import io.github.stephane849.still.navigation.NavigationViewModel
import io.github.stephane849.still.navigation.formatDistance
import io.github.stephane849.still.navigation.formatDuration
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun RoutePreviewScreen(
    navController: NavController,
    navigationViewModel: NavigationViewModel
) {
    val context = LocalContext.current
    val destination by navigationViewModel.destination.collectAsState()
    val route by navigationViewModel.route.collectAsState()
    val loading by navigationViewModel.loading.collectAsState()
    val error by navigationViewModel.error.collectAsState()
    val savedRepo = remember { SavedPlacesRepository(context) }
    val saved by savedRepo.places.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(destination) {
        if (destination != null && navigationViewModel.route.value == null) navigationViewModel.planRoute()
    }

    val dest = destination
    val isSaved = dest != null && saved.any { it.name == dest.name }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        StillHeader(
            title = dest?.name ?: "Route",
            onBack = { navController.popBackStack() },
            actions = {
                if (dest != null) {
                    IconButton(onClick = {
                        scope.launch {
                            if (isSaved) savedRepo.remove(dest.name)
                            else savedRepo.save(SavedPlace(dest.name, dest.point.lat, dest.point.lng))
                        }
                    }) {
                        Icon(
                            if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save place"
                        )
                    }
                }
            }
        )

        val r = route
        when {
            loading && r == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicatorMMD()
            }
            r == null -> Column(Modifier.weight(1f).padding(16.dp)) {
                Text(error ?: "Finding a route…", fontSize = 20.sp)
                if (error != null) {
                    Spacer(Modifier.height(16.dp))
                    ButtonMMD(onClick = { navigationViewModel.planRoute() }) { Text("Try again") }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                StillMap(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(1.dp, Color.Black),
                    location = null,
                    route = r.points
                )
                ButtonMMD(
                    onClick = {
                        navigationViewModel.startDrive()
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
