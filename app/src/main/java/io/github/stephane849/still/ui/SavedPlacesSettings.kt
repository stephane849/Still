package io.github.stephane849.still.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import com.mudita.mmd.components.switcher.SwitchMMD
import io.github.stephane849.still.navigationSession
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import io.github.stephane849.still.LocationService
import io.github.stephane849.still.data.AddressResult
import io.github.stephane849.still.data.AddressSearch
import io.github.stephane849.still.data.SavedPlace
import io.github.stephane849.still.data.SavedPlacesRepository
import kotlinx.coroutines.launch

fun slotIcon(slot: Int): ImageVector = when (slot) {
    SavedPlacesRepository.HOME -> Icons.Outlined.Home
    SavedPlacesRepository.WORK -> Icons.Outlined.Work
    else -> Icons.Outlined.Place
}

/** The "Saved places" block at the top of Settings: four slots. */
@Composable
fun SavedPlacesSection(onEdit: (Int) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SavedPlacesRepository(context) }
    val places by repo.places.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Saved places", fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Shown on the map screen and in the car.", fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        for (slot in 0 until SavedPlacesRepository.SLOTS) {
            val place = places.firstOrNull { it.slot == slot }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(slot) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(slotIcon(slot), contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        place?.name ?: SavedPlacesRepository.defaultName(slot),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        place?.address?.ifBlank { null } ?: if (place != null) "Set" else "Not set",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            if (slot < SavedPlacesRepository.SLOTS - 1) HorizontalDividerMMD(thickness = 1.dp)
        }
    }
}

/** Sets one slot: a name (for the two custom slots) and an address found by search. */
@Composable
fun EditPlaceScreen(navController: NavController, slot: Int) {
    val context = LocalContext.current
    val repo = remember { SavedPlacesRepository(context) }
    val places by repo.places.collectAsState(initial = emptyList())
    val current = places.firstOrNull { it.slot == slot }
    val scope = rememberCoroutineScope()

    val fixedName = SavedPlacesRepository.isFixedName(slot)
    var name by remember(current) { mutableStateOf(current?.name ?: if (fixedName) SavedPlacesRepository.defaultName(slot) else "") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AddressResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            searching = true
            message = null
            val here = runCatching { LocationService(context).getBestLocationOrNull() }.getOrNull()
            results = runCatching { AddressSearch.search(query, here?.latitude, here?.longitude) }
                .getOrElse {
                    message = "Search failed. Check your connection."
                    emptyList()
                }
            if (results.isEmpty() && message == null) message = "No matches. Try adding the city."
            searching = false
        }
    }

    fun choose(r: AddressResult) {
        val finalName = name.trim().ifBlank {
            if (fixedName) SavedPlacesRepository.defaultName(slot) else r.label.substringBefore(",")
        }
        scope.launch {
            repo.setSlot(SavedPlace(finalName, r.lat, r.lng, slot, r.label))
            navController.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        StillHeader(
            title = if (fixedName) "Set ${SavedPlacesRepository.defaultName(slot)}" else "Saved place",
            onBack = { navController.popBackStack() }
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (current != null) {
                Text("Now: ${current.address.ifBlank { current.name }}", fontSize = 16.sp)
            }
            if (!fixedName) {
                TextFieldMMD(value = name, onValueChange = { name = it }, label = { Text("Name, e.g. Gym") })
            }
            TextFieldMMD(value = query, onValueChange = { query = it }, label = { Text("Address or place") })
            ButtonMMD(onClick = { runSearch() }, modifier = Modifier.fillMaxWidth()) { Text("Search") }

            if (searching) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicatorMMD() }
            }
            message?.let { Text(it, fontSize = 16.sp) }
            results.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                        .clickable { choose(r) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(r.label, fontSize = 18.sp, modifier = Modifier.weight(1f))
                }
            }

            if (current != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(2.dp, Color.Black)
                        .clickable {
                            scope.launch {
                                repo.clearSlot(slot)
                                navController.popBackStack()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Remove", fontSize = 18.sp) }
            }
        }
    }
}

/** Voice on/off, a test sentence, and a shortcut to Android's voice engine settings. */
@Composable
fun VoiceSettings() {
    val context = LocalContext.current
    val session = context.navigationSession
    val on by session.voiceEnabled.collectAsState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Voice guidance", fontSize = 16.sp, modifier = Modifier.weight(1f))
            SwitchMMD(checked = on, onCheckedChange = { session.setVoiceEnabled(it) })
        }
        Text(
            "Still speaks with your phone's text-to-speech engine. For a more natural voice, " +
                "install SherpaTTS from F-Droid and choose it as the preferred engine.",
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonMMD(onClick = { session.testVoice() }) { Text("Test voice") }
            ButtonMMD(onClick = {
                val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Choose voice") }
        }
    }
}
