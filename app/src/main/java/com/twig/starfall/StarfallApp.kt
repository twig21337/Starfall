package com.twig.starfall

import android.app.Application
import com.starfall.core.save.SaveManager

class StarfallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SaveManager.initialize(applicationContext)
    }
}
