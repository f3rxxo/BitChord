package com.music.bitchord.playback

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings

/**
 * Puts the current cover on the lock screen while music plays.
 *
 * Android has no lock-screen wallpaper API a player can drive, so this does
 * what an alarm clock does: when the screen comes on over the keyguard and
 * something is playing, it raises [LockArtActivity] above the lock screen. The
 * activity is dismissed by swiping up, which leaves the ordinary lock screen
 * (and its unlock) underneath it.
 *
 * An app in the background may not simply start an activity, so the launch is
 * carried by a full-screen-intent notification, the one route the system keeps
 * open for showing something over the keyguard. On Android 14+ that needs a
 * permission the user grants once, which [ensureAccess] sends them to.
 */
object LockScreenArt {
    private const val TAG = "LockScreenArt"
    private const val CHANNEL_ID = "lock_screen_art"
    const val NOTIFICATION_ID = 0x4C53

    /**
     * Registers the screen-on listener for as long as the service lives.
     * [isPlaying] is asked at the moment the screen turns on.
     */
    fun register(context: Context, isPlaying: () -> Boolean): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON && isPlaying()) show(c)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    private fun show(context: Context) {
        if (!AppSettings.lockScreenArt.value) return
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return
        if (!keyguard.isKeyguardLocked) return

        val intent = Intent(context, LockArtActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        // Works outright where the app is allowed to draw over others; otherwise the
        // system quietly refuses and the notification below does the launching.
        runCatching { context.startActivity(intent) }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lock screen art", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                },
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Now playing")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Lock screen art notification refused", e)
        }
    }

    /**
     * Android 14 turned the full-screen-intent permission off by default for
     * most apps. Opens its settings page when it is not granted.
     */
    fun ensureAccess(context: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.canUseFullScreenIntent()) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
