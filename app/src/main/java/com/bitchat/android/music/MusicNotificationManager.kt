package com.bitchat.android.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bitchat.android.MainActivity
import com.bitchat.android.R
import com.bitchat.android.BitchatApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Music notification manager that provides media controls in the notification drawer
 * Integrates with Android's MediaSession API for system-wide media control
 */
class MusicNotificationManager(
    private val context: Context,
    private val musicPlayerService: MusicPlayerService
) {
    
    companion object {
        private const val TAG = "MusicNotificationManager"
        private const val MEDIA_NOTIFICATION_CHANNEL_ID = "bitchat_media_playback"
        private const val MEDIA_NOTIFICATION_ID = 1001
        
        // Notification actions
        const val ACTION_PLAY = "com.bitchat.android.music.PLAY"
        const val ACTION_PAUSE = "com.bitchat.android.music.PAUSE"
        const val ACTION_NEXT = "com.bitchat.android.music.NEXT"
        const val ACTION_PREVIOUS = "com.bitchat.android.music.PREVIOUS"
        const val ACTION_STOP = "com.bitchat.android.music.STOP"
        
        private const val REQUEST_CODE_PLAY = 100
        private const val REQUEST_CODE_PAUSE = 101
        private const val REQUEST_CODE_NEXT = 102
        private const val REQUEST_CODE_PREVIOUS = 103
        private const val REQUEST_CODE_STOP = 104
        private const val REQUEST_CODE_OPEN_PLAYER = 105
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val notificationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // MediaSession for system integration
    private var mediaSession: MediaSessionCompat? = null
    private var isNotificationActive = false
    
    init {
        createNotificationChannel()
        setupMediaSession()
        observePlayerState()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Music Playback"
            val descriptionText = "Controls for music playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(MEDIA_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
            }
            
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(context, TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    musicPlayerService.play()
                }
                
                override fun onPause() {
                    musicPlayerService.pause()
                }
                
                override fun onStop() {
                    musicPlayerService.stop()
                }
                
                override fun onSkipToNext() {
                    musicPlayerService.next()
                }
                
                override fun onSkipToPrevious() {
                    musicPlayerService.previous()
                }
                
                override fun onSeekTo(pos: Long) {
                    musicPlayerService.seekTo((pos / 1000).toInt())
                }
            })
            
            isActive = true
        }
    }
    
    private fun observePlayerState() {
        notificationScope.launch {
            // Observe playing state
            launch {
                musicPlayerService.isPlaying.collect { isPlaying ->
                    updatePlaybackState(isPlaying)
                    if (musicPlayerService.currentTrackInfo.value != null) {
                        updateNotification()
                    }
                }
            }
            
            // Observe current track
            launch {
                musicPlayerService.currentTrackInfo.collect { trackInfo ->
                    if (trackInfo != null) {
                        updateMediaMetadata(trackInfo)
                        showNotification()
                    } else {
                        hideNotification()
                    }
                }
            }
            
            // Observe position for progress updates
            launch {
                musicPlayerService.currentPosition.collect { position ->
                    updatePlaybackState(musicPlayerService.isPlaying.value, position)
                }
            }
        }
    }
    
    private fun updatePlaybackState(isPlaying: Boolean, position: Int = 0) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
        
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, position * 1000L, 1.0f)
            .setActions(actions)
            .build()
        
        mediaSession?.setPlaybackState(playbackState)
    }
    
    private fun updateMediaMetadata(trackInfo: MusicPlayerService.TrackInfo) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackInfo.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, trackInfo.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, trackInfo.duration * 1000L)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, trackInfo.contentId)
        
        // Try to load album art if available
        loadAlbumArt(trackInfo.filePath)?.let { bitmap ->
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        }
        
        mediaSession?.setMetadata(metadata.build())
    }
    
    private fun loadAlbumArt(filePath: String): Bitmap? {
        return try {
            // For now, use a default music icon
            // In a full implementation, you'd extract embedded album art from the audio file
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load album art for: $filePath", e)
            null
        }
    }
    
    private fun showNotification() {
        if (isNotificationActive) {
            updateNotification()
            return
        }
        
        val trackInfo = musicPlayerService.currentTrackInfo.value ?: return
        val isPlaying = musicPlayerService.isPlaying.value
        
        val notification = buildNotification(trackInfo, isPlaying)
        
        try {
            notificationManager.notify(MEDIA_NOTIFICATION_ID, notification)
            isNotificationActive = true
            Log.d(TAG, "Media notification shown")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show notification - missing permission", e)
        }
    }
    
    private fun updateNotification() {
        if (!isNotificationActive) return
        
        val trackInfo = musicPlayerService.currentTrackInfo.value ?: return
        val isPlaying = musicPlayerService.isPlaying.value
        
        val notification = buildNotification(trackInfo, isPlaying)
        
        try {
            notificationManager.notify(MEDIA_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
    
    private fun buildNotification(trackInfo: MusicPlayerService.TrackInfo, isPlaying: Boolean): android.app.Notification {
        // Create pending intent to open the music player
        val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_music_player", true)
        }
        val openPlayerPendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE_OPEN_PLAYER, openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create action pending intents
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                "Pause",
                createActionPendingIntent(ACTION_PAUSE, REQUEST_CODE_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                "Play",
                createActionPendingIntent(ACTION_PLAY, REQUEST_CODE_PLAY)
            ).build()
        }
        
        val previousAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Previous",
            createActionPendingIntent(ACTION_PREVIOUS, REQUEST_CODE_PREVIOUS)
        ).build()
        
        val nextAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Next",
            createActionPendingIntent(ACTION_NEXT, REQUEST_CODE_NEXT)
        ).build()
        
        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Stop",
            createActionPendingIntent(ACTION_STOP, REQUEST_CODE_STOP)
        ).build()
        
        return NotificationCompat.Builder(context, MEDIA_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(trackInfo.title)
            .setContentText(trackInfo.artist)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPlayerPendingIntent)
            .setDeleteIntent(createActionPendingIntent(ACTION_STOP, REQUEST_CODE_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Show previous, play/pause, next in compact view
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(createActionPendingIntent(ACTION_STOP, REQUEST_CODE_STOP))
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }
    
    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun hideNotification() {
        if (isNotificationActive) {
            notificationManager.cancel(MEDIA_NOTIFICATION_ID)
            isNotificationActive = false
            Log.d(TAG, "Media notification hidden")
        }
    }
    
    /**
     * Handle notification actions from broadcast receiver
     */
    fun handleNotificationAction(action: String) {
        Log.d(TAG, "Handling notification action: $action")
        
        when (action) {
            ACTION_PLAY -> {
                musicPlayerService.play()
                // Ensure foreground service is running
                val intent = Intent(context, MusicForegroundService::class.java)
                intent.action = MusicForegroundService.ACTION_START_PLAYBACK
                context.startService(intent)
            }
            ACTION_PAUSE -> {
                musicPlayerService.pause()
                // Keep foreground service running but update notification
                val intent = Intent(context, MusicForegroundService::class.java)
                intent.action = MusicForegroundService.ACTION_PAUSE_PLAYBACK
                context.startService(intent)
            }
            ACTION_NEXT -> musicPlayerService.next()
            ACTION_PREVIOUS -> musicPlayerService.previous()
            ACTION_STOP -> {
                musicPlayerService.stop()
                hideNotification()
                // Stop the foreground service
                val intent = Intent(context, MusicForegroundService::class.java)
                intent.action = MusicForegroundService.ACTION_STOP_PLAYBACK
                context.startService(intent)
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        hideNotification()
        mediaSession?.release()
        mediaSession = null
        notificationScope.cancel()
        Log.d(TAG, "Music notification manager cleaned up")
    }
}