package com.siteblocker.app

import androidx.lifecycle.MutableLiveData

/**
 * Single in-process source of truth for "live" values shown on the Dashboard,
 * Accessibility Inspector, and Developer Console. The service writes to this;
 * every screen observes it via [MutableLiveData], so the UI updates
 * automatically without the user reopening the app.
 *
 * This is intentionally a plain singleton (not persisted) since it only
 * reflects the current moment-to-moment state of a running process.
 */
object AppState {

    // --- Accessibility service ---
    val accessibilityRunning = MutableLiveData(false)
    val serviceStartTime = MutableLiveData(0L)
    val lastError = MutableLiveData("")
    val lastEventTime = MutableLiveData(0L)
    val lastEventType = MutableLiveData("")
    val rootInActiveWindowNull = MutableLiveData(true)
    val screenState = MutableLiveData("Unknown")

    // --- Browser / page ---
    val currentBrowserPackage = MutableLiveData("")
    val currentUrl = MutableLiveData("")
    val currentWindowTitle = MutableLiveData("")
    val currentPackageName = MutableLiveData("")
    val currentActivityName = MutableLiveData("")
    val nodeCount = MutableLiveData(0)
    val lastBlockedWebsite = MutableLiveData("")
    val lastBlockedKeyword = MutableLiveData("")
    val totalKeywords = MutableLiveData(0)

    // --- Overlay ---
    val overlayShowing = MutableLiveData(false)
    val lastOverlayTime = MutableLiveData(0L)

    // --- Performance ---
    val eventsPerSecond = MutableLiveData(0.0)
    val avgProcessingTimeMs = MutableLiveData(0.0)
    val maxProcessingTimeMs = MutableLiveData(0L)
    val droppedEvents = MutableLiveData(0)
    val overlayCount = MutableLiveData(0)
    val urlCheckCount = MutableLiveData(0)
    val memoryUsageMb = MutableLiveData(0L)

    /** Rolling window used to compute events/sec. Access only from the service's own thread. */
    private val eventTimestamps = ArrayDeque<Long>()
    private val processingTimes = ArrayDeque<Long>()

    fun recordEvent(timestampMs: Long) {
        eventTimestamps.addLast(timestampMs)
        while (eventTimestamps.isNotEmpty() && timestampMs - eventTimestamps.first() > 1000L) {
            eventTimestamps.removeFirst()
        }
        eventsPerSecond.postValue(eventTimestamps.size.toDouble())
        lastEventTime.postValue(timestampMs)
    }

    fun recordProcessingTime(durationMs: Long) {
        processingTimes.addLast(durationMs)
        while (processingTimes.size > 100) {
            processingTimes.removeFirst()
        }
        val avg = if (processingTimes.isEmpty()) 0.0 else processingTimes.average()
        avgProcessingTimeMs.postValue(avg)
        val max = processingTimes.maxOrNull() ?: 0L
        if (max > (maxProcessingTimeMs.value ?: 0L)) {
            maxProcessingTimeMs.postValue(max)
        }
    }

    fun incrementDropped() {
        droppedEvents.postValue((droppedEvents.value ?: 0) + 1)
    }

    fun incrementOverlayCount() {
        overlayCount.postValue((overlayCount.value ?: 0) + 1)
    }

    fun incrementUrlChecks() {
        urlCheckCount.postValue((urlCheckCount.value ?: 0) + 1)
    }

    fun uptimeMs(): Long {
        val start = serviceStartTime.value ?: 0L
        if (start == 0L) return 0L
        return System.currentTimeMillis() - start
    }

    fun reset() {
        accessibilityRunning.postValue(false)
        serviceStartTime.postValue(0L)
        currentBrowserPackage.postValue("")
        currentUrl.postValue("")
        overlayShowing.postValue(false)
        eventTimestamps.clear()
        processingTimes.clear()
    }
}
