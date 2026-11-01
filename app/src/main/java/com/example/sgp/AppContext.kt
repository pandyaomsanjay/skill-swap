package com.example.sgp

import android.app.Application
import android.content.Context

/**
 * Tiny singleton that holds the Application context so TradeManager (an
 * object with no Context) can cancel WorkManager reminders without
 * threading a Context through every call-site.
 *
 * Initialized once from MyApp.onCreate(). If it's never initialized, any
 * consumer that needs a Context sees `null` and safely no-ops.
 */
object AppContext {

    @Volatile
    private var app: Application? = null

    fun init(application: Application) {
        app = application
    }

    fun get(): Context? = app
}