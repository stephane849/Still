package io.github.stephane849.still

import android.app.Application
import org.maplibre.android.MapLibre

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
