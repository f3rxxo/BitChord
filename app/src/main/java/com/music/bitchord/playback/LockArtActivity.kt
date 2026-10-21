package com.music.bitchord.playback

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * The current cover, full screen, above the lock screen. Swipe up to slide it
 * away and reach the normal lock screen beneath. See [LockScreenArt].
 */
class LockArtActivity : ComponentActivity() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        getSystemService(NotificationManager::class.java)?.cancel(LockScreenArt.NOTIFICATION_ID)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent { LockArtScreen(onDismiss = { finish() }) }
    }

    override fun onStop() {
        super.onStop()
        // Gone with the screen: the next wake raises a fresh one.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }
}

@Composable
private fun LockArtScreen(onDismiss: () -> Unit) {
    val controller = rememberMediaController()
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var art by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            fun refresh() {
                val m = c.mediaMetadata
                title = m.title?.toString().orEmpty()
                artist = m.artist?.toString().orEmpty()
                art = m.artworkUri?.toString()
                playing = c.isPlaying
            }
            refresh()
            val listener = object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
                override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
            }
            c.addListener(listener)
            onDispose { c.removeListener(listener) }
        }
    }

    var dragY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            // Follows the finger upward only; a swipe far enough lets go of the screen.
            .offset { IntOffset(0, dragY.roundToInt().coerceAtMost(0)) }
            .alpha(1f - (-dragY / height).coerceIn(0f, 0.6f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        dragY += delta
                    },
                    onDragEnd = {
                        if (-dragY > height * 0.22f) onDismiss() else dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                )
            },
    ) {
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Darkens the bottom so the text and controls read over any cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                artist,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(
                    onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Text(
                "Swipe up for lock screen",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
