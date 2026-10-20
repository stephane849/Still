package io.github.stephane849.still.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stephane849.still.routing.RouteStep

/** The heavy rule CalmDirectory draws under its top bar. */
@Composable
fun Rule(thickness: Dp = 3.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(thickness)
            .background(Color.Black)
    )
}

/** A top bar in CalmDirectory's style: back arrow, title, actions. */
@Composable
fun StillHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
    Rule()
}

fun maneuverIcon(step: RouteStep?): ImageVector {
    if (step == null) return Icons.Filled.Straight
    val m = step.modifier ?: ""
    return when (step.type) {
        "arrive" -> Icons.Filled.Flag
        "roundabout", "rotary", "exit roundabout", "exit rotary" -> Icons.Filled.RoundaboutRight
        "merge" -> Icons.Filled.Merge
        "on ramp", "off ramp" -> if (m.contains("left")) Icons.Filled.RampLeft else Icons.Filled.RampRight
        "fork" -> if (m.contains("left")) Icons.Filled.ForkLeft else Icons.Filled.ForkRight
        else -> when (m) {
            "left" -> Icons.Filled.TurnLeft
            "right" -> Icons.Filled.TurnRight
            "slight left" -> Icons.Filled.TurnSlightLeft
            "slight right" -> Icons.Filled.TurnSlightRight
            "sharp left" -> Icons.Filled.TurnSharpLeft
            "sharp right" -> Icons.Filled.TurnSharpRight
            "uturn" -> Icons.Filled.UTurnLeft
            else -> Icons.Filled.Straight
        }
    }
}
