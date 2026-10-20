package io.github.stephane849.still.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import io.github.stephane849.still.data.SavedPlace
import io.github.stephane849.still.data.SavedPlacesRepository
import io.github.stephane849.still.getIconForCategory
import io.github.stephane849.still.poiCategories

/**
 * What search shows before you type, like Google Maps: a row of
 * category shortcuts, then your saved places.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestions(
    hasSearchKey: Boolean,
    onCategory: (String) -> Unit,
    onSavedPlace: (SavedPlace) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SavedPlacesRepository(context) }
    val saved by repo.places.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxWidth()) {
        if (!hasSearchKey) {
            Text(
                text = "Add a HERE or Google Places key in Settings to search for places.",
                fontSize = 17.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            poiCategories.forEach { category ->
                Row(
                    modifier = Modifier
                        .border(2.dp, Color.Black)
                        .clickable { onCategory(category) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(getIconForCategory(category), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(category, fontSize = 16.sp)
                }
            }
        }
        if (saved.isNotEmpty()) {
            Text(
                "SAVED",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            saved.forEach { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSavedPlace(place) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Outlined.Place, contentDescription = null)
                    Text(place.name, fontSize = 20.sp)
                }
                HorizontalDividerMMD(modifier = Modifier.padding(start = 16.dp), thickness = 1.dp)
            }
        }
    }
}
