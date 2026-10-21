package com.music.bitchord.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Loads the artwork the media session hands to the lock screen, the
 * notification and every other system media surface.
 *
 * Media3's stock loader opens the artwork URI with its own plain HTTP data
 * source, which shares nothing with the rest of the app. This one goes through
 * the app's Coil loader instead, so the lock screen gets exactly the cover the
 * player already has: the same cache, the same network stack, and `content://`
 * and `file://` covers of local and downloaded tracks as well as `https://`.
 *
 * What it returns is always the caller's own software copy, never the bitmap
 * Coil holds in its memory cache. Media3 hands these bitmaps on to the legacy
 * session and the notification, which can recycle them; recycling one that is
 * still in Coil's cache would blank that cover everywhere else in the app.
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = futureOf {
        BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: throw IllegalArgumentException("Artwork bytes could not be decoded")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = futureOf { load(uri) }

    private suspend fun load(uri: Uri): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(uri.toString())
            .size(Size(NOTIFICATION_ART_PX, NOTIFICATION_ART_PX))
            // The lock screen and the notification draw into a software canvas.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: throw IllegalStateException("Artwork could not be loaded: $uri")
        val source = result.image.toBitmap()
        return source.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalStateException("Artwork could not be copied: $uri")
    }

    private fun futureOf(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Throwable) {
                future.setException(e)
            }
        }
        future.addListener(
            { if (future.isCancelled) job.cancel() },
            MoreExecutors.directExecutor(),
        )
        return future
    }
}
