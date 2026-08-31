package com.example.sgp

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class MyApp : Application() {

    override fun attachBaseContext(base: Context) {
        val langCode = LocaleHelper.getLanguageCode(base)
        val contextWithLocale = LocaleHelper.setLocale(base, langCode)
        super.attachBaseContext(contextWithLocale)
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // OneSignal initialization
        OneSignal.Debug.logLevel = LogLevel.VERBOSE // remove/lower for release builds
        OneSignal.initWithContext(this, "68ee31c3-5a74-48c8-bf07-7751b4306618")

        // Notification permission is now requested from the Login screen,
        // not here, so the OS prompt doesn't appear over the splash screen.
    }
}