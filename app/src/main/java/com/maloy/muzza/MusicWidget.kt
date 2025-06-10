package com.maloy.muzza

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.ImageLoader
import coil.request.ImageRequest
import com.maloy.muzza.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MusicWidget : AppWidgetProvider() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        startProgressUpdater(context)
    }

    override fun onEnabled(context: Context) {
        startProgressUpdater(context)
    }

    override fun onDisabled(context: Context) {
        stopProgressUpdater()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                PlayerConnection.instance?.togglePlayPause()
                abortBroadcast()
                updateAllWidgets(context)
            }
            ACTION_PREV -> {
                PlayerConnection.instance?.seekToPrevious()
                abortBroadcast()
                updateAllWidgets(context)
            }
            ACTION_NEXT -> {
                PlayerConnection.instance?.seekToNext()
                abortBroadcast()
                updateAllWidgets(context)
            }
            ACTION_SHUFFLE -> {
                PlayerConnection.instance?.toggleShuffle()
                abortBroadcast()
                updateAllWidgets(context)
            }
            ACTION_LIKE -> {
                PlayerConnection.instance?.toggleLike()
                abortBroadcast()
                updateAllWidgets(context)
            }
            ACTION_REPLAY -> {
                PlayerConnection.instance?.toggleReplayMode()
                abortBroadcast()
                updateAllWidgets(context)
            }
            ACTION_STATE_CHANGED, ACTION_UPDATE_PROGRESS -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun startProgressUpdater(context: Context) {
        runnable = Runnable {
            updateAllWidgets(context)
            handler.postDelayed(runnable, 1000)
        }
        handler.post(runnable)
    }

    private fun stopProgressUpdater() {
        handler.removeCallbacks(runnable)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.maloy.muzza.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.maloy.muzza.ACTION_PREV"
        const val ACTION_NEXT = "com.maloy.muzza.ACTION_NEXT"
        const val ACTION_SHUFFLE = "com.maloy.muzza.ACTION_SHUFFLE"
        const val ACTION_LIKE = "com.maloy.muzza.ACTION_LIKE"
        const val ACTION_REPLAY = "com.maloy.muzza.ACTION_REPLAY"
        const val ACTION_STATE_CHANGED = "com.maloy.muzza.ACTION_STATE_CHANGED"
        const val ACTION_UPDATE_PROGRESS = "com.maloy.muzza.ACTION_UPDATE_PROGRESS"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MusicWidget::class.java)
            )
            widgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)
            val playerConnection = PlayerConnection.instance
            val player = playerConnection?.player

            player?.let { it ->
                views.setTextViewText(R.id.widget_track_title, it.mediaMetadata.title)
                views.setTextViewText(R.id.widget_artist, it.mediaMetadata.artist)
                val playPauseIcon = if (it.playWhenReady) R.drawable.pause else R.drawable.play
                views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)
                val shuffleIcon = if (it.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle
                views.setImageViewResource(R.id.widget_shuffle, shuffleIcon)
                val likeIcon = R.drawable.favorite
                views.setImageViewResource(R.id.widget_like, likeIcon)
                if (it.repeatMode == Player.REPEAT_MODE_ONE) {
                    views.setInt(R.id.widget_play_pause, "setColorFilter", context.getColor(R.color.light_blue_50))
                } else {
                    views.setInt(R.id.widget_play_pause, "setColorFilter", context.getColor(android.R.color.transparent))
                }
                val currentPos = formatTime(it.currentPosition)
                val duration = formatTime(it.duration)
                views.setTextViewText(R.id.widget_current_time, currentPos)
                views.setTextViewText(R.id.widget_total_time, duration)
                val progress = if (it.duration > 0) {
                    (it.currentPosition * 100 / it.duration).toInt()
                } else 0
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
                val thumbnailUrl = it.mediaMetadata.artworkUri?.toString()
                if (!thumbnailUrl.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .build()
                            val drawable = ImageLoader(context).execute(request).drawable
                            drawable?.let {
                                views.setImageViewBitmap(R.id.widget_album_art, it.toBitmap())
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                            }
                        } catch (e: Exception) {
                            views.setImageViewResource(R.id.widget_album_art, R.drawable.album)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.album)
                }
            }

            views.setOnClickPendingIntent(R.id.widget_play_pause, getBroadcastPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_prev, getBroadcastPendingIntent(context, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_next, getBroadcastPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_shuffle, getBroadcastPendingIntent(context, ACTION_SHUFFLE))
            views.setOnClickPendingIntent(R.id.widget_like, getBroadcastPendingIntent(context, ACTION_LIKE))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        private fun getBroadcastPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidget::class.java).apply {
                this.action = action
                flags = Intent.FLAG_RECEIVER_FOREGROUND
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        @SuppressLint("DefaultLocale")
        private fun formatTime(millis: Long): String {
            return if (millis < 0) "0:00" else String.format(
                "%d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            )
        }
    }
}