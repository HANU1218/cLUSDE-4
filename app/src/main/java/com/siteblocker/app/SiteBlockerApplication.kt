package com.siteblocker.app

import android.app.Application

/**
 * App entry point. Initializes [Logger] before anything else touches it, and
 * installs a global uncaught-exception hook so a crash is recorded to the
 * persistent Debug Log before the process dies (per "Crash Protection").
 * The default system handler still runs afterwards; this only ensures the
 * failure is visible next time the app is opened.
 */
class SiteBlockerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init(applicationContext)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Logger.e("UncaughtException", "Fatal crash on ${thread.name}", throwable)
            } catch (ignored: Exception) {
                // Never let logging itself crash the crash handler.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Logger.i("SiteBlockerApplication", "App started")
    }
}
