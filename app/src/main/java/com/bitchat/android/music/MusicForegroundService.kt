package com.bitchat.android.music

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

/**
 * Foreground service for music playback to ensure continuous playback in background
 * Integrates with MusicPlayerService and MusicNotificationManager
 */
class MusicForegroundService : Service() {
    
    companion object {
        private const val TAG = "MusicForegroundService"
        private const val MUSIC_NOTIFICATION_ID = 1001
        
        // Service actions
        const val ACTION_START_PLAYBACK = "com.bitchat.android.music.START_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.bitchat.android.music.STOP_PLAYBACK"
        const val ACTION_PAUSE_PLAYBACK = "com.bitchat.android.music.PAUSE_PLAYBACK"
    }
    
    private val binder = MusicServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Music components
    private var musicPlayerService: MusicPlayerService? = null
    private var musicNotificationManager: MusicNotificationManager? = null
    private var isServiceStarted = false
    
    inner class MusicServiceBinder : Binder() {
        fun getService(): MusicForegroundService = this@MusicForegroundService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Music foreground service created")
        
        // Initialize music components
        initializeMusicComponents()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Music foreground service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_PLAYBACK -> {
                startForegroundService()
            }
            ACTION_PAUSE_PLAYBACK -> {
                // Keep service running but update notification
                musicPlayerService?.pause()
            }
            ACTION_STOP_PLAYBACK -> {
                stopForegroundService()
            }
        }
        
        return START_STICKY // Restart service if killed by system
    }
    
    private fun initializeMusicComponents() {
        try {
            // Get or create music player service
            val deviceIdService = DeviceIdentificationService(this)
            val contentIdGenerator = ContentIdGenerator(this)
            val analyticsTracker = PlaybackAnalyticsTracker.getInstance(this)
            
            musicPlayerService = MusicPlayerService(
                context = this,
                deviceIdentificationService = deviceIdService,
                contentIdGenerator = contentIdGenerator,
                analyticsTracker = analyticsTracker
            )
            
            // Create notification manager
            musicNotificationManager = MusicNotificationManager(this, musicPlayerService!!)
            
            // Initialize the notification manager in the application
            (application as? com.bitchat.android.BitchatApplication)?.initializeMusicNotificationManager(musicPlayerService!!)
            
            Log.d(TAG, "Music components initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize music components", e)
        }
    }
    
    private fun startForegroundService() {
        if (isServiceStarted) return
        
        try {
            // Always start with a basic notification first
            val basicNotification = createBasicMusicNotification()
            startForeground(MUSIC_NOTIFICATION_ID, basicNotification)
            isServiceStarted = true
            
            Log.d(TAG, "Music foreground service started")
            
            // The MusicNotificationManager will update the notification with proper media controls
            // when a track is loaded and playing
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }
    
    private fun createBasicMusicNotification(): android.app.Notification {
        return androidx.core.app.NotificationCompat.Builder(this, "bitchat_media_playback")
            .setContentTitle("Music Player")
            .setContentText("Ready for playback")
            .setSmallIcon(com.bitchat.android.R.drawable.ic_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }
    
    private fun stopForegroundService() {
        try {
            musicPlayerService?.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isServiceStarted = false
            stopSelf()
            
            Log.d(TAG, "Music foreground service stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
    }
    
    /**
     * Get the music player service instance
     */
    fun getMusicPlayerService(): MusicPlayerService? = musicPlayerService
    
    /**
     * Get the notification manager instance
     */
    fun getMusicNotificationManager(): MusicNotificationManager? = musicNotificationManager
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        musicNotificationManager?.cleanup()
        musicPlayerService?.release()
        serviceScope.cancel()
        
        Log.d(TAG, "Music foreground service destroyed")
    }
}