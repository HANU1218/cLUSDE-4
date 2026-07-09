package com.siteblocker.app

import android.content.Context
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Owns the single blocking overlay's lifecycle. Nothing outside this class
 * touches WindowManager directly, which keeps overlay creation/removal
 * centralized and prevents duplicate or leaked windows.
 */
class OverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var timeUpdateHandler: android.os.Handler? = null
    private var timeUpdateRunnable: Runnable? = null

    private val motivationalQuotes = listOf(
        R.string.overlay_quote_1,
        R.string.overlay_quote_2,
        R.string.overlay_quote_3,
        R.string.overlay_quote_4,
        R.string.overlay_quote_5
    )

    /** Returns true if an overlay is currently showing. */
    fun isShowing(): Boolean = overlayView != null

    /**
     * Shows the blocking overlay. Safe to call repeatedly:
     * if an overlay already exists, this is a no-op (never creates duplicates).
     *
     * @param blockedUrl the URL/page that triggered the block, shown to the user.
     * @param matchedKeyword the keyword that matched, shown to the user.
     * @param animate whether to fade the overlay in (Settings > Overlay Animation).
     * @param onGoBack invoked when the user taps "Go Back".
     * @param onCloseApp invoked when the user taps "Close App".
     */
    fun showOverlay(
        blockedUrl: String,
        matchedKeyword: String,
        animate: Boolean,
        onGoBack: () -> Unit,
        onCloseApp: () -> Unit
    ) {
        if (overlayView != null) {
            Logger.d(TAG, "Overlay already showing, skipping duplicate show")
            return
        }

        try {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.overlay_blocker, null)

            view.findViewById<TextView>(R.id.overlayBlockedSite).text =
                blockedUrl.ifBlank { context.getString(R.string.overlay_title) }
            view.findViewById<TextView>(R.id.overlayBlockedKeyword).text =
                context.getString(R.string.overlay_keyword_prefix, matchedKeyword)

            val quoteView = view.findViewById<TextView>(R.id.overlayQuote)
            quoteView.setText(motivationalQuotes.random())

            val timeView = view.findViewById<TextView>(R.id.overlayTime)
            updateTime(timeView)

            view.findViewById<View>(R.id.overlayGoBackButton).setOnClickListener {
                onGoBack()
            }
            view.findViewById<View>(R.id.overlayCloseAppButton).setOnClickListener {
                onCloseApp()
            }

            val layoutFlag = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }

            if (animate) {
                view.alpha = 0f
            }

            windowManager.addView(view, params)
            overlayView = view

            if (animate) {
                val fadeIn = AlphaAnimation(0f, 1f).apply { duration = FADE_DURATION_MS }
                view.startAnimation(fadeIn)
                view.alpha = 1f
            }

            startClock(timeView)

            AppState.overlayShowing.postValue(true)
            AppState.lastOverlayTime.postValue(System.currentTimeMillis())
            AppState.incrementOverlayCount()
            Logger.i(TAG, "Overlay displayed for: $blockedUrl (keyword: $matchedKeyword)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to show overlay", e)
            AppState.lastError.postValue("Overlay failed: ${e.message}")
            overlayView = null
        }
    }

    /**
     * Removes the overlay if one exists. Safe to call even when no overlay
     * is showing (no-op) and safe to call multiple times.
     */
    fun removeOverlay(animate: Boolean = false) {
        val view = overlayView ?: return
        stopClock()

        val doRemove = {
            try {
                windowManager.removeView(view)
                Logger.i(TAG, "Overlay removed")
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to remove overlay (already detached?): ${e.message}")
            } finally {
                overlayView = null
                AppState.overlayShowing.postValue(false)
            }
        }

        if (animate) {
            try {
                val fadeOut = AlphaAnimation(1f, 0f).apply {
                    duration = FADE_DURATION_MS
                    setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation?) {}
                        override fun onAnimationRepeat(animation: Animation?) {}
                        override fun onAnimationEnd(animation: Animation?) {
                            doRemove()
                        }
                    })
                }
                view.startAnimation(fadeOut)
            } catch (e: Exception) {
                doRemove()
            }
        } else {
            doRemove()
        }
    }

    private fun startClock(timeView: TextView) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                updateTime(timeView)
                handler.postDelayed(this, 1000L)
            }
        }
        timeUpdateHandler = handler
        timeUpdateRunnable = runnable
        handler.post(runnable)
    }

    private fun stopClock() {
        timeUpdateRunnable?.let { timeUpdateHandler?.removeCallbacks(it) }
        timeUpdateHandler = null
        timeUpdateRunnable = null
    }

    private fun updateTime(timeView: TextView) {
        try {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            timeView.text = fmt.format(java.util.Date())
        } catch (e: Exception) {
            // Non-fatal: clock just won't update this tick.
        }
    }

    companion object {
        private const val TAG = "OverlayManager"
        private const val FADE_DURATION_MS = 250L
    }
}
