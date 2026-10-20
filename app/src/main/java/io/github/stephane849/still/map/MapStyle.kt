package io.github.stephane849.still.map

import android.content.Context
import java.io.File

/**
 * Builds Still's black-and-white map style.
 *
 * Tiles come from an offline PMTiles file when one is present
 * (OpenMapTiles schema, e.g. built with Planetiler), otherwise from
 * OpenFreeMap's free online vector tiles.
 */
object MapStyle {
    const val ONLINE_SOURCE = "https://tiles.openfreemap.org/planet"
    const val OFFLINE_FILE_NAME = "map.pmtiles"

    const val WHITE = "#FFFFFF"
    const val BLACK = "#000000"

    fun offlineFile(context: Context): File? =
        context.getExternalFilesDir(null)
            ?.let { File(it, OFFLINE_FILE_NAME) }
            ?.takeIf { it.exists() && it.length() > 0 }

    fun sourceUrl(context: Context): String =
        offlineFile(context)?.let { "pmtiles://file://${it.absolutePath}" } ?: ONLINE_SOURCE

    fun json(context: Context, night: Boolean = false): String {
        val template = context.assets.open("still_style.json").bufferedReader().use { it.readText() }
        val background = if (night) BLACK else WHITE
        val foreground = if (night) WHITE else BLACK
        return template
            .replace("__SOURCE__", sourceUrl(context))
            .replace("__BG__", background)
            .replace("__FG__", foreground)
    }
}
