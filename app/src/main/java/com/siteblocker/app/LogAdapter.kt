package com.siteblocker.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(private var entries: List<LogEntry> = emptyList()) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.logTime)
        val level: TextView = view.findViewById(R.id.logLevel)
        val message: TextView = view.findViewById(R.id.logMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = entries[position]
        holder.time.text = entry.formattedTime()
        holder.level.text = entry.level.name
        holder.message.text = "${entry.tag}: ${entry.message}"

        val colorRes = when (entry.level) {
            LogLevel.DEBUG -> R.color.log_debug
            LogLevel.INFO -> R.color.log_info
            LogLevel.WARNING -> R.color.log_warning
            LogLevel.ERROR -> R.color.log_error
        }
        holder.level.setTextColor(holder.itemView.context.getColor(colorRes))
    }

    override fun getItemCount(): Int = entries.size

    /** Shows newest entries first. */
    fun updateEntries(newEntries: List<LogEntry>) {
        entries = newEntries.asReversed()
        notifyDataSetChanged()
    }
}
