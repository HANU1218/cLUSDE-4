package com.siteblocker.app

import android.content.Context
import android.util.Log

/**
 * Central logging facade for the whole app. Every class should log through
 * here instead of calling [android.util.Log] directly:
 *  - Still writes to logcat (useful when attached to a debugger).
 *  - Additionally records the entry into [EventRepository] so it shows up on
 *    the in-app Debug Log screen and survives process death.
 *
 * Must be initialized once (see [SiteBlockerApplication]) before use;
 * calls made before init are safely dropped after also going to logcat.
 */
object Logger {

    @Volatile
    private var repository: EventRepository? = null

    fun init(context: Context) {
        if (repository == null) {
            repository = EventRepository.getInstance(context)
        }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message (${throwable.javaClass.simpleName}: ${throwable.message})" else message
        log(LogLevel.ERROR, tag, full)
        if (throwable != null) {
            Log.e(tag, message, throwable)
        }
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        repository?.add(LogEntry(System.currentTimeMillis(), level, tag, message))
    }
}
