package com.siteblocker.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Owns the persistent "SiteBlocker Running" notification shown while the
 * accessibility service is active. Tapping it opens the dashboard.
 */
class NotificationHelper(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SiteBlocker Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status while the blocker is active"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun show(currentBrowser: String, lastBlocked: String) {
        try {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, flags)

            val browserLabel = if (currentBrowser.isNotBlank()) currentBrowser else "None"
            val blockedLabel = if (lastBlocked.isNotBlank()) lastBlocked else "None yet"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("SiteBlocker Running")
                .setContentText("Browser: $browserLabel  •  Last blocked: $blockedLabel")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Current browser: $browserLabel\nLast blocked website: $blockedLabel")
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to show notification", e)
        }
    }

    fun cancel() {
        try {
            manager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel notification", e)
        }
    }

    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_ID = "site_blocker_status"
        private const val NOTIFICATION_ID = 1001
    }
}
