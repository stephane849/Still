package io.github.stephane849.still

import android.app.Application
import android.content.Context
import io.github.stephane849.still.navigation.NavigationSession
import org.maplibre.android.MapLibre

class MyApplication : Application() {

    /** The single navigation session shared by the phone UI and Android Auto. */
    val navigation: NavigationSession by lazy { NavigationSession(this) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}

val Context.navigationSession: NavigationSession
    get() = (applicationContext as MyApplication).navigation
