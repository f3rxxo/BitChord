package com.music.bitchord.playback

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sets the lock screen wallpaper to the current cover while music plays.
 *
 * The wallpaper is the cover on a dark, blurred fill of itself, so it suits a
 * tall screen. It goes through [WallpaperManager] with the lock-screen flag, so
 * the home screen wallpaper is untouched.
 *
 * Before the first change the existing lock screen wallpaper is saved, when the
 * system lets the app read it, and put back once playback has been paused for a
 * while or the setting is turned off. Where the system refuses the read there
 * is nothing to put back, so the last cover stays until the wallpaper is
 * changed from the phone's own settings.
 */
object LockWallpaper {
    private const val TAG = "LockWallpaper"
    private const val BACKUP = "lock_wallpaper_backup.png"
    private const val RESTORE_AFTER_PAUSE_MS = 30_000L

    private var lastArt: String? = null
    private var applyJob: Job? = null
    private var restoreJob: Job? = null
    private var changed = false

    fun attach(context: Context, player: Player, scope: CoroutineScope) {
        val app = context.applicationContext

        fun evaluate() {
            if (!AppSettings.lockWallpaper.value) {
                if (changed) schedule(app, scope, 0L)
                return
            }
            if (player.isPlaying) {
                restoreJob?.cancel()
                val art = player.mediaMetadata.artworkUri?.toString()
                if (art != null && art != lastArt) {
                    lastArt = art
                    applyJob?.cancel()
                    applyJob = scope.launch(Dispatchers.IO) { apply(app, art) }
                }
            } else if (changed) {
                schedule(app, scope, RESTORE_AFTER_PAUSE_MS)
            }
        }

        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = evaluate()
            override fun onIsPlayingChanged(isPlaying: Boolean) = evaluate()
        })
        scope.launch { AppSettings.lockWallpaper.collect { evaluate() } }
    }

    private fun schedule(app: Context, scope: CoroutineScope, delayMs: Long) {
        restoreJob?.cancel()
        restoreJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            restore(app)
        }
    }

    private suspend fun apply(app: Context, art: String) {
        try {
            val wm = WallpaperManager.getInstance(app)
            if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) return
            val metrics = app.resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels

            val request = ImageRequest.Builder(app)
                .data(art.artworkAt(w) ?: art)
                .size(Size(w, w))
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(app).execute(request) as? SuccessResult ?: return
            val cover = result.image.toBitmap()

            backupOnce(app, wm)
            val wallpaper = compose(cover, w, h)
            withContext(Dispatchers.IO) {
                wm.setBitmap(wallpaper, null, false, WallpaperManager.FLAG_LOCK)
            }
            changed = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not set the lock screen wallpaper", e)
        }
    }

    /** The cover, centred in the upper part of the screen, over a darkened blur of itself. */
    private fun compose(cover: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // A 24-pixel copy scaled back up with filtering is a cheap, heavy blur.
        val tiny = Bitmap.createScaledBitmap(cover, 24, 24, true)
        canvas.drawBitmap(tiny, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        canvas.drawColor(Color.argb(120, 0, 0, 0))

        val side = (w * 0.9f)
        val left = (w - side) / 2f
        val top = h * 0.30f
        canvas.drawBitmap(cover, null, RectF(left, top, left + side, top + side), paint)
        return out
    }

    private fun backupOnce(app: Context, wm: WallpaperManager) {
        val file = File(app.filesDir, BACKUP)
        if (changed || file.exists()) return
        try {
            val pfd = wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
                ?: wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                ?: return
            pfd.use { fd ->
                val bmp = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor) ?: return
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Existing lock screen wallpaper could not be saved", e)
        }
    }

    private fun restore(app: Context) {
        if (!changed) return
        val file = File(app.filesDir, BACKUP)
        try {
            if (file.exists()) {
                val wm = WallpaperManager.getInstance(app)
                file.inputStream().use { wm.setStream(it, null, false, WallpaperManager.FLAG_LOCK) }
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore the lock screen wallpaper", e)
        }
        changed = false
        lastArt = null
    }
}
