package io.github.stephane849.still.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.stephane849.still.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One place for every API key: traffic (TomTom), road events (Ontario 511)
 * and place search (HERE or Google). Each key has a Paste button so long
 * keys don't have to be typed.
 */
@Composable
fun ApiKeysScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    var tomTom by remember { mutableStateOf("") }
    var ontario511 by remember { mutableStateOf("") }
    var here by remember { mutableStateOf("") }
    var google by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tomTom = prefs.tomTomKey.first()
        ontario511 = prefs.ontario511Key.first()
        here = prefs.hereApiKey.first()
        google = prefs.googleApiKey.first() ?: ""
    }

    fun save() {
        scope.launch {
            prefs.saveTomTomKey(tomTom)
            prefs.saveOntario511Key(ontario511)
            prefs.saveHereApiKey(here)
            prefs.saveGoogleApiKey(google.trim())
            saved = true
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        StillHeader(title = "API keys", onBack = { navController.popBackStack() })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            KeyField(
                title = "TomTom: live traffic",
                hint = "Free key from developer.tomtom.com. Routes avoid traffic and slow stretches show dashed.",
                value = tomTom,
                onChange = { tomTom = it; saved = false }
            )
            KeyField(
                title = "Ontario 511: road events",
                hint = "Free key from 511on.ca, under Developers. Shows roadwork, closures and collisions on your route.",
                value = ontario511,
                onChange = { ontario511 = it; saved = false }
            )
            KeyField(
                title = "HERE: place search",
                hint = "Free key from platform.here.com. Used when search is set to HERE.",
                value = here,
                onChange = { here = it; saved = false }
            )
            KeyField(
                title = "Google Places: place search",
                hint = "Used when search is set to Google Places.",
                value = google,
                onChange = { google = it; saved = false }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color.Black)
                    .clickable { save() },
                contentAlignment = Alignment.Center
            ) {
                Text(if (saved) "Saved" else "Save keys", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun KeyField(title: String, hint: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (value.isBlank()) "Not set" else "Set", fontSize = 15.sp)
        }
        Text(hint, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.trim()) },
                singleLine = true,
                placeholder = { Text("Paste or type the key") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black
                ),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .border(2.dp, Color.Black)
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!text.isNullOrBlank()) onChange(text.trim())
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Paste", fontSize = 17.sp) }
        }
    }
}
