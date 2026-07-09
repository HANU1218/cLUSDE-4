package com.siteblocker.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Continuously displays live accessibility internals: current package,
 * activity, URL, window title, node count, rootInActiveWindow availability,
 * last event type, and timestamp. Useful for debugging detection issues.
 */
class InspectorFragment : Fragment() {

    private val rows = mutableMapOf<String, TextView>()
    private var container: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_inspector, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.inspectorContainer)

        val fields = listOf(
            KEY_PACKAGE to R.string.inspector_current_package,
            KEY_ACTIVITY to R.string.inspector_current_activity,
            KEY_URL to R.string.inspector_current_url,
            KEY_TITLE to R.string.inspector_window_title,
            KEY_NODES to R.string.inspector_node_count,
            KEY_ROOT_NULL to R.string.inspector_root_null,
            KEY_EVENT_TYPE to R.string.inspector_event_type,
            KEY_TIMESTAMP to R.string.inspector_timestamp
        )
        fields.forEach { (key, labelRes) -> addRow(key, getString(labelRes)) }

        val owner = viewLifecycleOwner
        AppState.currentPackageName.observe(owner) { rows[KEY_PACKAGE]?.text = it.ifBlank { "-" } }
        AppState.currentActivityName.observe(owner) { rows[KEY_ACTIVITY]?.text = it.ifBlank { "-" } }
        AppState.currentUrl.observe(owner) { rows[KEY_URL]?.text = it.ifBlank { "-" } }
        AppState.currentWindowTitle.observe(owner) { rows[KEY_TITLE]?.text = it.ifBlank { "-" } }
        AppState.nodeCount.observe(owner) { rows[KEY_NODES]?.text = it.toString() }
        AppState.rootInActiveWindowNull.observe(owner) { rows[KEY_ROOT_NULL]?.text = if (it) "Yes" else "No" }
        AppState.lastEventType.observe(owner) { rows[KEY_EVENT_TYPE]?.text = it.ifBlank { "-" } }
        AppState.lastEventTime.observe(owner) {
            rows[KEY_TIMESTAMP]?.text = if (it == 0L) "-" else
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(java.util.Date(it))
        }
    }

    private fun addRow(key: String, label: String) {
        val c = container ?: return
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_stat_row, c, false)
        row.findViewById<TextView>(R.id.statLabel).text = label
        val value = row.findViewById<TextView>(R.id.statValue)
        value.text = "-"
        rows[key] = value
        c.addView(row)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        container = null
        rows.clear()
    }

    companion object {
        private const val KEY_PACKAGE = "package"
        private const val KEY_ACTIVITY = "activity"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_NODES = "nodes"
        private const val KEY_ROOT_NULL = "root_null"
        private const val KEY_EVENT_TYPE = "event_type"
        private const val KEY_TIMESTAMP = "timestamp"
    }
}
