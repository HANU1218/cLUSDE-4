package com.siteblocker.app

/** Severity of a [LogEntry], mirrored in the UI as a colored tag. */
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

/**
 * A single entry in the persistent Debug Log.
 * Kept intentionally small/serializable so it can be written to disk as JSON.
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun formattedTime(): String {
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(timestamp))
    }

    fun formattedFull(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return "[${fmt.format(java.util.Date(timestamp))}] ${level.name} $tag: $message"
    }
}
