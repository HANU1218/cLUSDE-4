package com.siteblocker.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Core accessibility service. Intentionally thin: all matching logic lives in
 * [KeywordManager]/[UrlParser], browser identification in [BrowserDetector],
 * and overlay rendering in [OverlayManager]. This class wires events to those
 * collaborators and keeps [AppState] updated so the Dashboard/Inspector/
 * Developer Console reflect what's happening live.
 */
class AccessibilityBlockerService : AccessibilityService() {

    private lateinit var keywordManager: KeywordManager
    private lateinit var browserDetector: BrowserDetector
    private lateinit var overlayManager: OverlayManager
    private lateinit var statisticsManager: StatisticsManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var notificationHelper: NotificationHelper

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null

    private var lastProcessedText: String = ""
    private var currentPackageName: String = ""

    private var packageChangeReceiver: BroadcastReceiver? = null
    private var screenStateReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.init(applicationContext)
        Logger.i(TAG, "Accessibility Service Connected")

        try {
            keywordManager = KeywordManager(applicationContext)
            browserDetector = BrowserDetector(applicationContext)
            overlayManager = OverlayManager(applicationContext)
            statisticsManager = StatisticsManager(applicationContext)
            settingsManager = SettingsManager(applicationContext)
            notificationHelper = NotificationHelper(applicationContext)

            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 100
            }
            serviceInfo = info

            registerPackageChangeReceiver()
            registerScreenStateReceiver()

            AppState.accessibilityRunning.postValue(true)
            AppState.serviceStartTime.postValue(System.currentTimeMillis())
            AppState.totalKeywords.postValue(keywordManager.getKeywords().size)

            if (settingsManager.areNotificationsEnabled()) {
                notificationHelper.show("", "")
            }

            instance = this
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize service", e)
            AppState.lastError.postValue("Init failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            AppState.incrementDropped()
            return
        }

        val startTime = System.currentTimeMillis()
        AppState.recordEvent(startTime)
        AppState.lastEventType.postValue(eventTypeName(event.eventType))

        try {
            val packageName = event.packageName?.toString()
            if (packageName == null) {
                AppState.incrementDropped()
                return
            }
            event.className?.toString()?.let { AppState.currentActivityName.postValue(it) }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    handlePossibleBrowserEvent(packageName)
                }
                else -> {
                    // Ignore other event types; nothing to do for blocking logic.
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "onAccessibilityEvent threw", e)
            AppState.lastError.postValue("Event handling error: ${e.message}")
            AppState.incrementDropped()
        } finally {
            AppState.recordProcessingTime(System.currentTimeMillis() - startTime)
        }
    }

    private fun handlePossibleBrowserEvent(packageName: String) {
        currentPackageName = packageName
        AppState.currentPackageName.postValue(packageName)

        if (!safeIsBrowser(packageName)) {
            // A non-browser app came to the foreground: any existing overlay
            // belonged to a browser session that's no longer active.
            if (overlayManager.isShowing()) {
                overlayManager.removeOverlay(animate = settingsManager.isOverlayAnimationEnabled())
            }
            AppState.currentBrowserPackage.postValue("")
            return
        }

        AppState.currentBrowserPackage.postValue(packageName)
        Logger.d(TAG, "Browser event: $packageName")

        // Debounce rapid-fire events (page loads, redirects, tab switches all
        // fire many events in quick succession).
        pendingCheck?.let { mainHandler.removeCallbacks(it) }
        val check = Runnable { safeEvaluateCurrentPage() }
        pendingCheck = check
        mainHandler.postDelayed(check, DEBOUNCE_MS)
    }

    private fun safeEvaluateCurrentPage() {
        try {
            evaluateCurrentPage()
        } catch (e: Exception) {
            Logger.e(TAG, "evaluateCurrentPage threw", e)
            AppState.lastError.postValue("Page evaluation error: ${e.message}")
        }
    }

    private fun evaluateCurrentPage() {
        AppState.incrementUrlChecks()

        val root: AccessibilityNodeInfo? = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Logger.w(TAG, "rootInActiveWindow threw: ${e.message}")
            null
        }

        AppState.rootInActiveWindowNull.postValue(root == null)

        if (root == null) {
            Logger.d(TAG, "Null root window, skipping check")
            return
        }

        try {
            AppState.nodeCount.postValue(countNodesSafely(root))

            val candidateText = extractAddressBarText(root)

            if (candidateText.isEmpty()) {
                return
            }

            AppState.currentUrl.postValue(candidateText)

            if (candidateText == lastProcessedText && overlayManager.isShowing()) {
                // Same page, overlay already up. Nothing new to do.
                return
            }
            lastProcessedText = candidateText

            Logger.d(TAG, "URL detected: $candidateText")

            val matchedKeyword = keywordManager.findMatchingKeyword(candidateText)
            if (matchedKeyword != null) {
                Logger.i(TAG, "Keyword matched: $matchedKeyword for $candidateText")
                showBlockOverlay(candidateText, matchedKeyword)
            } else if (overlayManager.isShowing()) {
                // Navigated away from a blocked page to a safe one.
                overlayManager.removeOverlay(animate = settingsManager.isOverlayAnimationEnabled())
            }
        } finally {
            try {
                root.recycle()
            } catch (e: Exception) {
                // Already recycled elsewhere; safe to ignore.
            }
        }
    }

    private fun countNodesSafely(root: AccessibilityNodeInfo, depth: Int = 0, budget: Int = 500): Int {
        if (depth > MAX_SCAN_DEPTH || budget <= 0) return 0
        var count = 1
        var remaining = budget - 1
        for (i in 0 until root.childCount) {
            if (remaining <= 0) break
            val child = try { root.getChild(i) } catch (e: Exception) { null } ?: continue
            val childCount = countNodesSafely(child, depth + 1, remaining)
            count += childCount
            remaining -= childCount
            child.recycle()
        }
        return count
    }

    /**
     * Walks the active window's node tree looking for the most likely
     * address-bar / page-identifying text. Different browsers expose this
     * differently, so this checks a few common signal sources:
     * the window title (often reflects the page title/URL) and any
     * editable/URL-bar-like node text near the top of the tree.
     */
    private fun extractAddressBarText(root: AccessibilityNodeInfo): String {
        val windowTitle = try {
            windows?.firstOrNull { it.root == root }?.title?.toString()
        } catch (e: Exception) {
            null
        }

        AppState.currentWindowTitle.postValue(windowTitle.orEmpty())

        if (!windowTitle.isNullOrBlank()) {
            return UrlParser.extractCandidate(windowTitle)
        }

        // Fallback: scan for a short, single-line, non-empty text node near
        // the top of the tree (best-effort heuristic, safe against nulls).
        return findFirstMeaningfulText(root, depth = 0)
    }

    private fun findFirstMeaningfulText(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null || depth > MAX_SCAN_DEPTH) return ""

        val text = node.text?.toString()
        if (!text.isNullOrBlank() && text.length in 4..256) {
            return UrlParser.extractCandidate(text)
        }

        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            } ?: continue

            val result = findFirstMeaningfulText(child, depth + 1)
            child.recycle()
            if (result.isNotEmpty()) {
                return result
            }
        }
        return ""
    }

    private fun showBlockOverlay(url: String, matchedKeyword: String) {
        overlayManager.showOverlay(
            blockedUrl = url,
            matchedKeyword = matchedKeyword,
            animate = safeSettingsAnimationEnabled(),
            onGoBack = {
                try {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } catch (e: Exception) {
                    Logger.e(TAG, "Back action failed", e)
                }
                overlayManager.removeOverlay(animate = safeSettingsAnimationEnabled())
                Logger.i(TAG, "Back button executed")
            },
            onCloseApp = {
                try {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } catch (e: Exception) {
                    Logger.e(TAG, "Close app action failed", e)
                }
                overlayManager.removeOverlay(animate = safeSettingsAnimationEnabled())
                Logger.i(TAG, "Close app executed")
            }
        )

        AppState.lastBlockedWebsite.postValue(url)
        AppState.lastBlockedKeyword.postValue(matchedKeyword)
        statisticsManager.recordBlock(url, currentPackageName)

        if (settingsManager.areNotificationsEnabled()) {
            notificationHelper.show(currentPackageName, url)
        }
    }

    private fun safeSettingsAnimationEnabled(): Boolean = try {
        settingsManager.isOverlayAnimationEnabled()
    } catch (e: Exception) {
        true
    }

    private fun safeIsBrowser(packageName: String): Boolean {
        return try {
            browserDetector.isBrowser(packageName)
        } catch (e: Exception) {
            Logger.e(TAG, "Browser check failed", e)
            false
        }
    }

    private fun eventTypeName(type: Int): String = try {
        AccessibilityEvent.eventTypeToString(type)
    } catch (e: Exception) {
        type.toString()
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Logger.i(TAG, "Package change detected, refreshing browser list")
                try {
                    browserDetector.refresh()
                } catch (e: Exception) {
                    Logger.e(TAG, "Browser refresh after package change failed", e)
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else 0

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, flags)
            } else {
                registerReceiver(receiver, filter)
            }
            packageChangeReceiver = receiver
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register package receiver", e)
        }
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> "Screen On"
                    Intent.ACTION_SCREEN_OFF -> "Screen Off"
                    Intent.ACTION_USER_PRESENT -> "Unlocked"
                    else -> "Unknown"
                }
                AppState.screenState.postValue(state)
                Logger.d(TAG, "Screen state: $state")
            }
        }
        try {
            registerReceiver(receiver, filter)
            screenStateReceiver = receiver
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register screen state receiver", e)
        }
    }

    override fun onInterrupt() {
        Logger.w(TAG, "Service interrupted")
        try {
            overlayManager.removeOverlay()
        } catch (e: Exception) {
            Logger.e(TAG, "removeOverlay on interrupt failed", e)
        }
    }

    override fun onDestroy() {
        Logger.w(TAG, "Service destroyed")
        cleanup()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.w(TAG, "Service unbound")
        cleanup()
        return super.onUnbind(intent)
    }

    private fun cleanup() {
        pendingCheck?.let { mainHandler.removeCallbacks(it) }
        pendingCheck = null

        if (::overlayManager.isInitialized) {
            try {
                overlayManager.removeOverlay()
            } catch (e: Exception) {
                Logger.e(TAG, "removeOverlay on cleanup failed", e)
            }
        }

        packageChangeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Logger.d(TAG, "Package receiver already unregistered: ${e.message}")
            }
        }
        packageChangeReceiver = null

        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Logger.d(TAG, "Screen state receiver already unregistered: ${e.message}")
            }
        }
        screenStateReceiver = null

        if (::notificationHelper.isInitialized) {
            notificationHelper.cancel()
        }

        AppState.accessibilityRunning.postValue(false)
        instance = null
    }

    companion object {
        private const val TAG = "AccessibilityBlockerService"
        private const val DEBOUNCE_MS = 350L
        private const val MAX_SCAN_DEPTH = 12

        @Volatile
        private var instance: AccessibilityBlockerService? = null

        /** Used by the Developer Console to force-show a test overlay. */
        fun forceShowOverlay() {
            val svc = instance ?: return
            svc.showBlockOverlay("dev-console-test.local", "test")
        }

        /** Used by the Developer Console to force-hide any active overlay. */
        fun forceHideOverlay() {
            instance?.overlayManager?.removeOverlay()
        }

        /** Used by the Developer Console to test keyword matching against arbitrary text. */
        fun testUrlMatch(url: String): String? {
            return instance?.keywordManager?.findMatchingKeyword(url)
        }

        /** Used by the Developer Console to force a browser-list refresh. */
        fun refreshBrowsers() {
            instance?.browserDetector?.refresh()
        }

        fun isRunning(): Boolean = instance != null
    }
}
