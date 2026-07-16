package com.example.sgp

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    fun setLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }

        return context.createConfigurationContext(config)
    }

    fun getLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val langName = prefs.getString(SettingsActivity.KEY_LANGUAGE, "English") ?: "English"
        return when (langName) {
            "Spanish" -> "es"
            "French"  -> "fr"
            "German"  -> "de"
            "Hindi"   -> "hi"
            else      -> "en"
        }
    }
}