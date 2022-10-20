package com.zionhuang.music.playback

import android.app.Notification
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.lifecycleScope
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.cache.Cache

class MusicService : LifecycleMediaBrowserService() {
    private val binder = MusicBinder()
    private lateinit var songPlayer: SongPlayer

    override fun onCreate() {
        super.onCreate()
        songPlayer = SongPlayer(this, lifecycleScope, object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }

            override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                if (ongoing) {
                    startForegroundService(this@MusicService, Intent(this@MusicService, MusicService::class.java))
                    startForeground(notificationId, notification)
                } else {
                    stopForeground(0)
                }
            }
        })
        sessionToken = songPlayer.mediaSession.sessionToken
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        MediaButtonReceiver.handleIntent(songPlayer.mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        songPlayer.onDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        val superBinder = super.onBind(intent)
        return when (intent.action) {
            SERVICE_INTERFACE -> superBinder
            else -> binder
        }
    }

    inner class MusicBinder : Binder() {
        val sessionToken: MediaSessionCompat.Token
            get() = songPlayer.mediaSession.sessionToken

        val songPlayer: SongPlayer
            get() = this@MusicService.songPlayer

        val cache: Cache
            get() = this@MusicService.songPlayer.cache
    }

    // TODO: Support Android Auto
    private val ROOT_ID = "root"

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId == ROOT_ID) {
            result.sendResult(mutableListOf(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("id_all")
                    .setTitle("All")
                    .build(),
                FLAG_BROWSABLE)))
        } else {
            result.sendResult(mutableListOf())
        }
    }
}