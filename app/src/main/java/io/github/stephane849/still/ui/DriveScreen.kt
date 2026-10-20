package io.github.stephane849.still.ui

import android.speech.tts.TextToSpeech
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.stephane849.still.data.LocationRepository
import io.github.stephane849.still.navigation.NavigationViewModel
import io.github.stephane849.still.navigation.formatDistance
import io.github.stephane849.still.navigation.formatDuration
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Turn-by-turn on the phone: only the next decision, in large type.
 * There is no live map here; the screen changes once per step, which
 * suits e-ink. Voice prompts do most of the work.
 */
@Composable
fun DriveScreen(
    navController: NavController,
    navigationViewModel: NavigationViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    val destination by navigationViewModel.destination.collectAsState()
    val drive by navigationViewModel.drive.collectAsState()
    val loading by navigationViewModel.loading.collectAsState()
    val locationRepository = remember { LocationRepository(context) }
    val location by locationRepository.location.collectAsState()

    val tts = remember {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine.language = Locale.getDefault()
        }
        engine
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        locationRepository.startLocationUpdates(driving = true)
        onDispose {
            view.keepScreenOn = false
            locationRepository.stopLocationUpdates()
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(location) {
        location?.let { navigationViewModel.onLocation(it) }
    }

    LaunchedEffect(Unit) {
        navigationViewModel.speech.collect { text ->
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
        }
    }

    fun end() {
        navigationViewModel.endDrive()
        navController.popBackStack("main", inclusive = false)
    }

    val d = drive
    Column(Modifier.fillMaxSize().background(Color.White)) {
        StillHeader(title = "To ${destination?.name ?: ""}")

        if (d == null) {
            Box(Modifier.weight(1f))
        } else if (d.arrived) {
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.Center) {
                Text("You've arrived", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(maneuverIcon(d.next), contentDescription = null, modifier = Modifier.size(112.dp))
                Text(formatDistance(d.distanceToNextM), fontSize = 76.sp, fontWeight = FontWeight.Bold)
                Text(d.next?.action ?: "Continue", fontSize = 30.sp, fontWeight = FontWeight.Medium)
                d.next?.ontoRoad?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 24.sp) }

                d.afterNext?.let { then ->
                    Spacer(Modifier.height(10.dp))
                    Rule(1.dp)
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Then", fontSize = 17.sp)
                        Icon(maneuverIcon(then), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text("${then.action} ${then.ontoRoad}".trim(), fontSize = 19.sp)
                    }
                    Rule(1.dp)
                }
                if (loading) Text("Rerouting…", fontSize = 18.sp)
            }
        }

        Rule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (d != null && !d.arrived) {
                    val arrive = DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(System.currentTimeMillis() + (d.remainingS * 1000).toLong()))
                    Text(arrive, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${formatDuration(d.remainingS)} · ${formatDistance(d.remainingM)}", fontSize = 17.sp)
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
