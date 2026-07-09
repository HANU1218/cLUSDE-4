package com.siteblocker.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale

/** Live, persistent event log: every important thing the service does. */
class DebugLogFragment : Fragment() {

    private lateinit var repository: EventRepository
    private lateinit var adapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_debug_log, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = EventRepository.getInstance(requireContext())

        val recyclerView = view.findViewById<RecyclerView>(R.id.logRecyclerView)
        val emptyText = view.findViewById<TextView>(R.id.logEmptyText)
        adapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        repository.entries.observe(viewLifecycleOwner) { entries ->
            adapter.updateEntries(entries)
            emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        }

        view.findViewById<View>(R.id.clearLogButton).setOnClickListener { confirmClear() }
        view.findViewById<View>(R.id.copyLogButton).setOnClickListener { copyLog() }
        view.findViewById<View>(R.id.exportLogButton).setOnClickListener { exportLog() }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.btn_clear_log)
            .setMessage(R.string.settings_reset_confirm_message)
            .setPositiveButton(R.string.btn_clear_log) { d, _ ->
                repository.clear()
                Toast.makeText(requireContext(), R.string.log_cleared, Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun copyLog() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SiteBlocker Debug Log", repository.getAllAsText()))
            Toast.makeText(requireContext(), R.string.log_copied, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Logger.e(TAG, "Copy log failed", e)
        }
    }

    private fun exportLog() {
        val fileName = "siteblocker_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.txt"
        val file = ShareUtil.exportAndShare(requireContext(), fileName, repository.getAllAsText())
        if (file != null) {
            Toast.makeText(requireContext(), getString(R.string.log_exported, file.name), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.log_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "DebugLogFragment"
    }
}
