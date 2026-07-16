package com.example.sgp

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

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
    }
}