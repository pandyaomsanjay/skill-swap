package com.example.sgp

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent

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
        OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(this, "68ee31c3-5a74-48c8-bf07-7751b4306618")

        // Tap handler: opens the appeals screen when the user taps an appeal push.
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData ?: return
                val type = data.optString("type")
                if (type == "appeal_approved" || type == "appeal_rejected") {
                    startActivity(
                        Intent(applicationContext, UserAppealsActivity::class.java)
                            .putExtra("appealId", data.optString("appeal_id"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                }
            }
        })

        // Foreground notification display listener
        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                event.notification.display()
            }
        })
    }
}