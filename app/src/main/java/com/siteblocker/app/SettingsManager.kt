package com.siteblocker.app

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Persists user-configurable settings (theme, notifications, debug mode,
 * overlay animation, auto-start check). Exposes [debugModeEnabled] as
 * LiveData since several screens need to react live to it being toggled.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val debugModeEnabled = MutableLiveData(isDebugModeEnabled())

    fun getThemeMode(): ThemeMode =
        ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun isAutoStartCheckEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_START_CHECK, true)
    fun setAutoStartCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_CHECK, enabled).apply()
    }

    fun areNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, true)
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun isOverlayAnimationEnabled(): Boolean = prefs.getBoolean(KEY_OVERLAY_ANIM, true)
    fun setOverlayAnimationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ANIM, enabled).apply()
    }

    fun isDebugModeEnabled(): Boolean = prefs.getBoolean(KEY_DEBUG_MODE, false)
    fun setDebugModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        debugModeEnabled.postValue(enabled)
    }

    companion object {
        private const val PREFS_NAME = "site_blocker_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AUTO_START_CHECK = "auto_start_check"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_OVERLAY_ANIM = "overlay_animation"
        private const val KEY_DEBUG_MODE = "debug_mode"
    }
}
