package com.siteblocker.app

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DailyStat(val date: String, var count: Int)

/**
 * Records every block event locally (SharedPreferences, JSON-encoded) and
 * derives the numbers shown on the Statistics screen and Dashboard.
 */
class StatisticsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /** Notified whenever a new block is recorded, for live Dashboard updates. */
    val todayCount = MutableLiveData(getTodayCount())

    @Synchronized
    fun recordBlock(site: String, browserPackage: String) {
        val today = dateFormat.format(java.util.Date())
        val daily = getDailyStats().toMutableMap()
        daily[today] = (daily[today] ?: 0) + 1
        persistDaily(daily)

        val siteCounts = getSiteCounts().toMutableMap()
        siteCounts[site] = (siteCounts[site] ?: 0) + 1
        persistSiteCounts(siteCounts)

        val browserCounts = getBrowserCounts().toMutableMap()
        browserCounts[browserPackage] = (browserCounts[browserPackage] ?: 0) + 1
        persistBrowserCounts(browserCounts)

        prefs.edit().putInt(KEY_TOTAL, getTotalCount() + 1).apply()
        todayCount.postValue(daily[today] ?: 0)
    }

    fun getTodayCount(): Int {
        val today = dateFormat.format(java.util.Date())
        return getDailyStats()[today] ?: 0
    }

    fun getYesterdayCount(): Int {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormat.format(cal.time)
        return getDailyStats()[yesterday] ?: 0
    }

    fun getLast7DaysCount(): Int {
        val daily = getDailyStats()
        val cal = java.util.Calendar.getInstance()
        var sum = 0
        for (i in 0 until 7) {
            val key = dateFormat.format(cal.time)
            sum += daily[key] ?: 0
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return sum
    }

    fun getTotalCount(): Int = prefs.getInt(KEY_TOTAL, 0)

    fun getMostBlockedSite(): String? =
        getSiteCounts().maxByOrNull { it.value }?.key

    fun getMostActiveBrowser(): String? =
        getBrowserCounts().maxByOrNull { it.value }?.key

    fun getAverageDailyBlocks(): Double {
        val daily = getDailyStats()
        if (daily.isEmpty()) return 0.0
        return daily.values.sum().toDouble() / daily.size
    }

    fun getTopSites(limit: Int = 5): List<Pair<String, Int>> =
        getSiteCounts().entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    fun resetAll() {
        prefs.edit().clear().apply()
        todayCount.postValue(0)
    }

    private fun getDailyStats(): Map<String, Int> {
        val json = prefs.getString(KEY_DAILY, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getSiteCounts(): Map<String, Int> {
        val json = prefs.getString(KEY_SITES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getBrowserCounts(): Map<String, Int> {
        val json = prefs.getString(KEY_BROWSERS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun persistDaily(map: Map<String, Int>) {
        prefs.edit().putString(KEY_DAILY, gson.toJson(map)).apply()
    }

    private fun persistSiteCounts(map: Map<String, Int>) {
        prefs.edit().putString(KEY_SITES, gson.toJson(map)).apply()
    }

    private fun persistBrowserCounts(map: Map<String, Int>) {
        prefs.edit().putString(KEY_BROWSERS, gson.toJson(map)).apply()
    }

    companion object {
        private const val PREFS_NAME = "site_blocker_stats"
        private const val KEY_DAILY = "daily_stats"
        private const val KEY_SITES = "site_counts"
        private const val KEY_BROWSERS = "browser_counts"
        private const val KEY_TOTAL = "total_count"
    }
}
