package io.github.stephane849.still.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import io.github.stephane849.still.data.LocationRepository
import io.github.stephane849.still.data.SavedPlacesRepository
import io.github.stephane849.still.map.StillMap
import io.github.stephane849.still.map.rememberStillMapState
import io.github.stephane849.still.navigationSession

/** Still's home: the map, with search on top and saved places below. */
@Composable
fun MapHomeScreen(
    navController: NavController,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    val navigationViewModel = context.navigationSession
    val locationRepository = remember { LocationRepository(context) }
    val location by locationRepository.location.collectAsState()
    val savedPlacesRepository = remember { SavedPlacesRepository(context) }
    val saved by savedPlacesRepository.places.collectAsState(initial = emptyList())
    val mapState = rememberStillMapState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) locationRepository.startLocationUpdates()
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            locationRepository.startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { locationRepository.stopLocationUpdates() }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // Search bar and settings.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .border(2.dp, Color.Black)
                    .clickable { onSearch() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Where to?", fontSize = 20.sp)
            }
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
        Rule()

        // A drive started from the car shows here so the phone can join it.
        val drive by navigationViewModel.drive.collectAsState()
        val destination by navigationViewModel.destination.collectAsState()
        if (drive != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("drive") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Navigating to ${destination?.name ?: ""}",
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Open", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Rule(1.dp)
        }

        // The map.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StillMap(
                modifier = Modifier.fillMaxSize(),
                state = mapState,
                location = location
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.White)
                    .border(2.dp, Color.Black)
            ) {
                IconButton(onClick = { mapState.zoomIn() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Zoom in")
                }
                Box(Modifier.width(48.dp).height(1.dp).background(Color.Black))
                IconButton(onClick = { mapState.zoomOut() }) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Zoom out")
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color.White)
                    .border(2.dp, Color.Black)
            ) {
                IconButton(onClick = { mapState.recenter() }) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "Center on my location")
                }
            }
        }
        Rule()

        // Saved places.
        if (saved.isEmpty()) {
            Text(
                text = "Save places from search to see them here.",
                fontSize = 17.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                saved.forEach { place ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .border(2.dp, Color.Black)
                            .clickable {
                                navigationViewModel.setDestination(place.name, place.lat, place.lng)
                                navController.navigate("preview")
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(26.dp))
                        Text(
                            text = place.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
