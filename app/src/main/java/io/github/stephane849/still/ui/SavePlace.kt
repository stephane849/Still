package io.github.stephane849.still.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text_field.TextFieldMMD
import io.github.stephane849.still.data.SavedPlace
import io.github.stephane849.still.data.SavedPlacesRepository
import io.github.stephane849.still.navigation.distanceM
import io.github.stephane849.still.routing.GeoPoint
import kotlinx.coroutines.launch

/** The saved place at (about) this spot, if any. */
fun List<SavedPlace>.savedAt(lat: Double, lng: Double): SavedPlace? =
    firstOrNull { distanceM(GeoPoint(it.lat, it.lng), GeoPoint(lat, lng)) < 30 }

/**
 * A full-width Save / Saved button. Saving asks for a name (with Home and
 * Work as one-tap choices); tapping a saved place offers to remove it.
 */
@Composable
fun SavePlaceButton(
    defaultName: String,
    lat: Double,
    lng: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { SavedPlacesRepository(context) }
    val saved by repo.places.collectAsState(initial = emptyList())
    val existing = saved.savedAt(lat, lng)
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(2.dp, Color.Black)
            .clickable { dialog = true },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (existing != null) "Saved as ${existing.name}" else "Save place",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (dialog) {
        if (existing != null) {
            AlertDialog(
                onDismissRequest = { dialog = false },
                title = { Text("Remove ${existing.name}?") },
                text = { Text("It will no longer show on the map screen or in the car.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { repo.remove(existing.name) }
                        dialog = false
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { dialog = false }) { Text("Keep") } }
            )
        } else {
            NamePlaceDialog(
                defaultName = defaultName,
                full = saved.size >= SavedPlacesRepository.MAX_PLACES,
                oldest = saved.firstOrNull()?.name,
                onDismiss = { dialog = false },
                onSave = { name ->
                    scope.launch { repo.save(SavedPlace(name, lat, lng)) }
                    dialog = false
                }
            )
        }
    }
}

@Composable
private fun NamePlaceDialog(
    defaultName: String,
    full: Boolean,
    oldest: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work").forEach { label ->
                        Box(
                            modifier = Modifier
                                .border(2.dp, Color.Black)
                                .clickable { name = label }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text(label, fontSize = 18.sp) }
                    }
                }
                TextFieldMMD(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") }
                )
                if (full && oldest != null) {
                    Text("You can keep four places. This replaces $oldest.", fontSize = 15.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
