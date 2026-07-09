package com.siteblocker.app

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Hidden power-user screen (unlocked by tapping the version number 7 times
 * in Settings). Shows live internals and gives force-action buttons for
 * quick manual testing without needing a debugger attached.
 */
class DeveloperConsoleActivity : AppCompatActivity() {

    private val rows = mutableMapOf<String, TextView>()
    private lateinit var keywordManager: KeywordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_console)
        keywordManager = KeywordManager(applicationContext)

        setSupportActionBar(findViewById(R.id.devToolbar))

        buildStateRows()
        observeState()
        setupActions()
        renderExceptions()
    }

    private fun buildStateRows() {
        val container = findViewById<android.widget.LinearLayout>(R.id.devStateContainer)
        val fields = listOf(
            "running" to "Accessibility Service State",
            "browser" to "Current Browser Package",
            "url" to "Current URL",
            "overlay" to "Overlay Status",
            "nodes" to "Window Node Count",
            "root_null" to "rootInActiveWindow Available",
            "avg_time" to "Avg Processing Time",
            "max_time" to "Max Processing Time",
            "dropped" to "Dropped Events"
        )
        fields.forEach { (key, label) ->
            val row = layoutInflater.inflate(R.layout.item_stat_row, container, false)
            row.findViewById<TextView>(R.id.statLabel).text = label
            val value = row.findViewById<TextView>(R.id.statValue)
            value.text = "-"
            rows[key] = value
            container.addView(row)
        }
    }

    private fun observeState() {
        AppState.accessibilityRunning.observe(this, Observer {
            rows["running"]?.text = if (it) "Connected" else "Disconnected"
        })
        AppState.currentBrowserPackage.observe(this, Observer { rows["browser"]?.text = it.ifBlank { "-" } })
        AppState.currentUrl.observe(this, Observer { rows["url"]?.text = it.ifBlank { "-" } })
        AppState.overlayShowing.observe(this, Observer { rows["overlay"]?.text = if (it) "Showing" else "Hidden" })
        AppState.nodeCount.observe(this, Observer { rows["nodes"]?.text = it.toString() })
        AppState.rootInActiveWindowNull.observe(this, Observer { rows["root_null"]?.text = if (it) "No (null)" else "Yes" })
        AppState.avgProcessingTimeMs.observe(this, Observer {
            rows["avg_time"]?.text = String.format(Locale.US, "%.1f ms", it)
        })
        AppState.maxProcessingTimeMs.observe(this, Observer { rows["max_time"]?.text = "$it ms" })
        AppState.droppedEvents.observe(this, Observer { rows["dropped"]?.text = it.toString() })
    }

    private fun setupActions() {
        findViewById<android.view.View>(R.id.forceShowOverlayButton).setOnClickListener {
            if (AccessibilityBlockerService.isRunning()) {
                AccessibilityBlockerService.forceShowOverlay()
            } else {
                toast("Accessibility Service is not running")
            }
        }
        findViewById<android.view.View>(R.id.forceHideOverlayButton).setOnClickListener {
            AccessibilityBlockerService.forceHideOverlay()
        }
        findViewById<android.view.View>(R.id.refreshBrowsersDevButton).setOnClickListener {
            AccessibilityBlockerService.refreshBrowsers()
            toast("Browser list refresh requested")
        }
        findViewById<android.view.View>(R.id.exportLogsDevButton).setOnClickListener {
            val repo = EventRepository.getInstance(applicationContext)
            val fileName = "siteblocker_devlog_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.txt"
            val file = ShareUtil.exportAndShare(applicationContext, fileName, repo.getAllAsText())
            if (file == null) toast("Export failed")
        }
        findViewById<android.view.View>(R.id.testUrlButton).setOnClickListener {
            val input = findViewById<TextInputEditText>(R.id.testUrlInput).text?.toString().orEmpty()
            val result = findViewById<TextView>(R.id.testUrlResult)
            if (input.isBlank()) {
                result.text = "Enter a URL first"
                return@setOnClickListener
            }
            val normalized = keywordManager.normalize(input)
            val matched = keywordManager.findMatchingKeyword(input)
            result.text = if (matched != null) {
                "Normalized: $normalized\nMatched keyword: $matched → would BLOCK"
            } else {
                "Normalized: $normalized\nNo match → would ALLOW"
            }
        }
    }

    private fun renderExceptions() {
        val container = findViewById<android.widget.LinearLayout>(R.id.exceptionsContainer)
        val repo = EventRepository.getInstance(applicationContext)
        val errors = repo.getSnapshot().filter { it.level == LogLevel.ERROR }.takeLast(20).asReversed()

        if (errors.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No recent exceptions"
            tv.setTextColor(getColor(R.color.text_secondary))
            container.addView(tv)
            return
        }

        errors.forEach { entry ->
            val tv = TextView(this)
            tv.text = entry.formattedFull()
            tv.setTextColor(getColor(R.color.log_error))
            tv.textSize = 12f
            tv.setPadding(0, 8, 0, 8)
            container.addView(tv)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
