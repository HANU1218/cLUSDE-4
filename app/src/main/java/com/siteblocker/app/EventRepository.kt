package com.siteblocker.app

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * Persists the Debug Log's event list to disk (as JSON) so it survives app
 * restarts, and exposes it as [LiveData] so any screen can show live updates.
 *
 * All file IO happens on a single background thread; the in-memory list is
 * the source of truth for the UI and is updated synchronously so callers
 * never see stale data.
 */
class EventRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val logFile = File(appContext.filesDir, "debug_log.json")
    private val gson = Gson()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val _entries = MutableLiveData<List<LogEntry>>(emptyList())
    val entries: LiveData<List<LogEntry>> get() = _entries

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()

    init {
        loadFromDisk()
    }

    fun add(entry: LogEntry) {
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
            _entries.postValue(buffer.toList())
        }
        persistAsync()
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.postValue(emptyList())
        }
        persistAsync()
    }

    fun getAllAsText(): String {
        synchronized(lock) {
            return buffer.joinToString(separator = "\n") { it.formattedFull() }
        }
    }

    fun getSnapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    private fun loadFromDisk() {
        ioExecutor.execute {
            try {
                if (logFile.exists()) {
                    val json = logFile.readText()
                    val type = object : TypeToken<List<LogEntry>>() {}.type
                    val loaded: List<LogEntry>? = gson.fromJson(json, type)
                    if (loaded != null) {
                        synchronized(lock) {
                            buffer.clear()
                            buffer.addAll(loaded.takeLast(MAX_ENTRIES))
                            _entries.postValue(buffer.toList())
                        }
                    }
                }
            } catch (e: Exception) {
                // Corrupt or unreadable log file; start fresh rather than crash.
                android.util.Log.w("EventRepository", "Failed to load persisted log: ${e.message}")
            }
        }
    }

    private fun persistAsync() {
        val snapshot = getSnapshot()
        ioExecutor.execute {
            try {
                logFile.writeText(gson.toJson(snapshot))
            } catch (e: Exception) {
                android.util.Log.w("EventRepository", "Failed to persist log: ${e.message}")
            }
        }
    }

    companion object {
        private const val MAX_ENTRIES = 500

        @Volatile
        private var instance: EventRepository? = null

        fun getInstance(context: Context): EventRepository {
            return instance ?: synchronized(this) {
                instance ?: EventRepository(context).also { instance = it }
            }
        }
    }
}
