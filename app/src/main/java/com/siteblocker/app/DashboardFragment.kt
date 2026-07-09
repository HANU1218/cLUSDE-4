package com.siteblocker.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Main dashboard: live-updating overview of everything happening in the
 * accessibility service. All values refresh automatically via [AppState]
 * LiveData plus a lightweight polling timer for values that aren't
 * event-driven (uptime, memory usage).
 */
class DashboardFragment : Fragment() {

    private var statsContainer: LinearLayout? = null
    private lateinit var keywordManager: KeywordManager
    private lateinit var statisticsManager: StatisticsManager

    private val rows = mutableMapOf<String, TextView>()
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        keywordManager = KeywordManager(ctx)
        statisticsManager = StatisticsManager(ctx)

        statsContainer = view.findViewById(R.id.statsContainer)
        buildStatRows()

        view.findViewById<View>(R.id.enableAccessibilityButton).setOnClickListener {
            openAccessibilitySettings()
        }
        view.findViewById<View>(R.id.openBrowsersButton).setOnClickListener {
            (activity as? MainActivity)?.openScreen(BrowsersFragment(), getString(R.string.nav_browsers))
        }
        view.findViewById<View>(R.id.openInspectorButton).setOnClickListener {
            (activity as? MainActivity)?.openScreen(InspectorFragment(), getString(R.string.nav_inspector))
        }

        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            refreshStaticValues()
            swipeRefresh.isRefreshing = false
        }

        observeState()
    }

    private fun buildStatRows() {
        val labels = listOf(
            KEY_OVERLAY to R.string.dashboard_overlay_status,
            KEY_BLOCKED_TODAY to R.string.dashboard_blocked_today,
            KEY_TOTAL_KEYWORDS to R.string.dashboard_total_keywords,
            KEY_PACKAGE to R.string.dashboard_current_package,
            KEY_ACTIVITY to R.string.dashboard_current_activity,
            KEY_EPS to R.string.dashboard_events_per_sec,
            KEY_MEMORY to R.string.dashboard_memory_usage,
            KEY_UPTIME to R.string.dashboard_uptime,
            KEY_LAST_EVENT to R.string.dashboard_last_event_time,
            KEY_LAST_OVERLAY to R.string.dashboard_last_overlay_time,
            KEY_SCREEN to R.string.dashboard_screen_state,
            KEY_ROOT_NULL to R.string.dashboard_root_null,
            KEY_LAST_ERROR to R.string.dashboard_last_error
        )
        // Browser and URL and last-blocked get their own rows first, inserted above.
        val prelude = listOf(
            KEY_BROWSER to R.string.dashboard_current_browser,
            KEY_URL to R.string.dashboard_current_url,
            KEY_LAST_BLOCKED to R.string.dashboard_last_blocked
        )
        (prelude + labels).forEach { (key, labelRes) ->
            addRow(key, getString(labelRes))
        }
    }

    private fun addRow(key: String, label: String) {
        val container = statsContainer ?: return
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_stat_row, container, false)
        row.findViewById<TextView>(R.id.statLabel).text = label
        val valueView = row.findViewById<TextView>(R.id.statValue)
        valueView.text = getString(R.string.dashboard_none)
        rows[key] = valueView
        container.addView(row)
    }

    private fun observeState() {
        val owner = viewLifecycleOwner

        AppState.accessibilityRunning.observe(owner) { running ->
            val view = view ?: return@observe
            val statusText = view.findViewById<TextView>(R.id.statusText)
            statusText.text = if (running) getString(R.string.status_enabled) else getString(R.string.status_disabled)
            statusText.setTextColor(
                resources.getColor(if (running) R.color.status_running else R.color.status_disabled, null)
            )
        }
        AppState.currentBrowserPackage.observe(owner) { setOrNone(KEY_BROWSER, it) }
        AppState.currentUrl.observe(owner) { setOrNone(KEY_URL, it) }
        AppState.lastBlockedWebsite.observe(owner) { setOrNone(KEY_LAST_BLOCKED, it) }
        AppState.overlayShowing.observe(owner) { rows[KEY_OVERLAY]?.text = if (it) "Showing" else "Hidden" }
        AppState.currentPackageName.observe(owner) { setOrNone(KEY_PACKAGE, it) }
        AppState.currentActivityName.observe(owner) { setOrNone(KEY_ACTIVITY, it) }
        AppState.eventsPerSecond.observe(owner) { rows[KEY_EPS]?.text = String.format(Locale.US, "%.1f", it) }
        AppState.lastEventTime.observe(owner) { rows[KEY_LAST_EVENT]?.text = formatTime(it) }
        AppState.lastOverlayTime.observe(owner) { rows[KEY_LAST_OVERLAY]?.text = formatTime(it) }
        AppState.screenState.observe(owner) { rows[KEY_SCREEN]?.text = it }
        AppState.rootInActiveWindowNull.observe(owner) { rows[KEY_ROOT_NULL]?.text = if (it) "Yes" else "No" }
        AppState.lastError.observe(owner) { setOrNone(KEY_LAST_ERROR, it) }
        AppState.totalKeywords.observe(owner) { rows[KEY_TOTAL_KEYWORDS]?.text = it.toString() }
        statisticsManager.todayCount.observe(owner) { rows[KEY_BLOCKED_TODAY]?.text = it.toString() }

        startPolling()
    }

    private fun startPolling() {
        val runnable = object : Runnable {
            override fun run() {
                refreshStaticValues()
                pollHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        pollHandler.post(runnable)
    }

    private fun refreshStaticValues() {
        rows[KEY_TOTAL_KEYWORDS]?.text = keywordManager.getKeywords().size.toString()
        AppState.totalKeywords.postValue(keywordManager.getKeywords().size)

        val uptime = AppState.uptimeMs()
        rows[KEY_UPTIME]?.text = formatDuration(uptime)

        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        rows[KEY_MEMORY]?.text = "$usedMb MB"
        AppState.memoryUsageMb.postValue(usedMb)
    }

    private fun setOrNone(key: String, value: String) {
        rows[key]?.text = if (TextUtils.isEmpty(value)) getString(R.string.dashboard_none) else value
    }

    private fun formatTime(ts: Long): String {
        if (ts == 0L) return getString(R.string.dashboard_none)
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ts))
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return getString(R.string.dashboard_not_running)
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open Accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStaticValues()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        statsContainer = null
        rows.clear()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
        private const val KEY_BROWSER = "browser"
        private const val KEY_URL = "url"
        private const val KEY_LAST_BLOCKED = "last_blocked"
        private const val KEY_OVERLAY = "overlay"
        private const val KEY_BLOCKED_TODAY = "blocked_today"
        private const val KEY_TOTAL_KEYWORDS = "total_keywords"
        private const val KEY_PACKAGE = "package"
        private const val KEY_ACTIVITY = "activity"
        private const val KEY_EPS = "eps"
        private const val KEY_MEMORY = "memory"
        private const val KEY_UPTIME = "uptime"
        private const val KEY_LAST_EVENT = "last_event"
        private const val KEY_LAST_OVERLAY = "last_overlay"
        private const val KEY_SCREEN = "screen"
        private const val KEY_ROOT_NULL = "root_null"
        private const val KEY_LAST_ERROR = "last_error"
    }
}
