package com.github.vermilion10.milea

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class MileaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this))
    }
}
