package com.fishaudio.tts

import android.app.Application
import com.google.android.material.color.DynamicColors

class FishTtsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
