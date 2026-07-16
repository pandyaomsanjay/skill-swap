package com.example.sgp

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val langCode = LocaleHelper.getLanguageCode(newBase)
        val contextWithLocale = LocaleHelper.setLocale(newBase, langCode)
        super.attachBaseContext(contextWithLocale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
    }

    fun capitalizeName(name: String): String = name.split(" ")
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }
}