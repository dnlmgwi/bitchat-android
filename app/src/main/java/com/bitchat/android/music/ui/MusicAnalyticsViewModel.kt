package com.bitchat.android.music.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.android.BitchatApplication
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.music.*
import com.bitchat.android.music.model.SourceType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for music analytics integration
 * Follows bitchat's MVVM architecture patterns with StateFlow
 */
class MusicAnalyticsViewModel(
    application: Application,
    private val meshService: BluetoothMeshService
) : AndroidViewModel(application) {
    
    // Core services
    private val deviceIdentificationService = DeviceIdentificationService(application)
    private val contentIdGenerator = ContentIdGenerator(application)
    private val analyticsTracker = PlaybackAnalyticsTracker.getInstance(application)
    private val transferTrackingService = TransferTrackingService(
        application,
        deviceIdentificationService,
        contentIdGenerator,
        analyticsTracker
    )
    
    // Music player service will be obtained from foreground service
    private var musicPlayerService: MusicPlayerService? = null
    private var musicForegroundService: MusicForegroundService? = null
    
    private val musicSharingService = MusicMetadataService(
        application,
        deviceIdentificationService,
        contentIdGenerator
    )
    private val musicLibraryService = MusicLibraryService(application)
    
    // Expose services for UI (will be initialized when foreground service connects)
    val playerService: MusicPlayerService? get() = musicPlayerService
    val tracker: PlaybackAnalyticsTracker = analyticsTracker
    val metadataService: MusicMetadataService = musicSharingService
    val sharingService: MusicSharingService = MusicSharingService(
        application,
        meshService,
        deviceIdentificationService,
        contentIdGenerator,
        analyticsTracker
    )
    val libraryService: MusicLibraryService = musicLibraryService
    val transferTracker: TransferTrackingService = transferTrackingService
    
    // Combined state for UI
    private val _uiState = MutableStateFlow(MusicAnalyticsUiState())
    val uiState: StateFlow<MusicAnalyticsUiState> = _uiState.asStateFlow()
    
    // Analytics statistics
    private val _analyticsStats = MutableStateFlow(PlaybackAnalyticsTracker.AnalyticsStats())
    val analyticsStats: StateFlow<PlaybackAnalyticsTracker.AnalyticsStats> = _analyticsStats.asStateFlow()
    
    init {
        // Start the music foreground service and bind to it
        startMusicForegroundService()
        
        // Initialize music notification manager when player service is available
        initializeNotificationManager()
        
        // Combine various state flows for UI
        viewModelScope.launch {
            // Wait for music player service to be available
            while (musicPlayerService == null) {
                delay(100)
            }
            
            combine(
                musicPlayerService!!.isPlaying,
                musicPlayerService!!.currentTrackInfo,
                analyticsTracker.totalRecords,
                analyticsTracker.pendingSyncRecords
            ) { flows ->
                val isPlaying = flows[0] as Boolean
                val currentTrack = flows[1] as MusicPlayerService.TrackInfo?
                val totalRecords = flows[2] as Int
                val pendingSync = flows[3] as Int
                
                MusicAnalyticsUiState(
                    isPlaying = isPlaying,
                    currentTrack = currentTrack,
                    totalRecords = totalRecords,
                    pendingSyncRecords = pendingSync,
                    syncStatus = MusicAnalyticsMeshSync.SyncStatus.Idle, // Simplified for now
                    discoveredAggregators = 0 // Simplified for now
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
        
        // Update analytics stats periodically
        viewModelScope.launch {
            while (true) {
                _analyticsStats.value = analyticsTracker.getAnalyticsStats()
                kotlinx.coroutines.delay(5000) // Update every 5 seconds
            }
        }
    }
    
    /**
     * Start the music foreground service
     */
    private fun startMusicForegroundService() {
        val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
        intent.action = MusicForegroundService.ACTION_START_PLAYBACK
        getApplication<Application>().startService(intent)
        
        // Bind to the service to get access to the music player
        bindToMusicForegroundService()
    }
    
    /**
     * Bind to the music foreground service to get the player instance
     */
    private fun bindToMusicForegroundService() {
        val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                val binder = service as? MusicForegroundService.MusicServiceBinder
                musicForegroundService = binder?.getService()
                musicPlayerService = musicForegroundService?.getMusicPlayerService()
                
                Log.d("MusicAnalyticsViewModel", "Connected to music foreground service")
                
                // Initialize notification manager now that we have the player service
                musicPlayerService?.let { player ->
                    (getApplication() as? BitchatApplication)?.initializeMusicNotificationManager(player)
                }
            }
            
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                musicForegroundService = null
                musicPlayerService = null
                Log.d("MusicAnalyticsViewModel", "Disconnected from music foreground service")
            }
        }
        
        getApplication<Application>().bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Initialize notification manager when player service is available
     */
    private fun initializeNotificationManager() {
        viewModelScope.launch {
            // Wait for music player service to be available
            while (musicPlayerService == null) {
                delay(100)
            }
            
            musicPlayerService?.let { player ->
                (getApplication() as? BitchatApplication)?.initializeMusicNotificationManager(player)
            }
        }
    }
    
    /**
     * Load and play a music file
     */
    fun loadAndPlayTrack(filePath: String, sourceType: SourceType = SourceType.LOCAL_FILE) {
        viewModelScope.launch {
            // Ensure foreground service is started for background playback
            val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
            intent.action = MusicForegroundService.ACTION_START_PLAYBACK
            getApplication<Application>().startService(intent)
            
            musicPlayerService?.let { player ->
                val success = player.loadTrack(filePath, sourceType)
                if (success) {
                    player.play()
                }
            }
        }
    }
    
    /**
     * Play/pause current track
     */
    fun togglePlayPause() {
        musicPlayerService?.let { player ->
            if (player.isPlaying.value) {
                // Send pause action to foreground service
                val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
                intent.action = MusicForegroundService.ACTION_PAUSE_PLAYBACK
                getApplication<Application>().startService(intent)
            } else {
                // Send play action to foreground service
                val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
                intent.action = MusicForegroundService.ACTION_START_PLAYBACK
                getApplication<Application>().startService(intent)
                player.play()
            }
        }
    }
    
    /**
     * Stop playback
     */
    fun stopPlayback() {
        musicPlayerService?.stop()
        
        // Stop the foreground service when playback stops
        val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
        intent.action = MusicForegroundService.ACTION_STOP_PLAYBACK
        getApplication<Application>().startService(intent)
    }
    
    /**
     * Seek to position
     */
    fun seekTo(positionSeconds: Int) {
        musicPlayerService?.seekTo(positionSeconds)
    }
    
    /**
     * Toggle repeat mode (cycles through OFF -> ONE -> ALL -> OFF)
     */
    fun toggleRepeatMode() {
        musicPlayerService?.toggleRepeat()
    }
    
    /**
     * Trigger manual sync
     */
    fun triggerSync() {
        analyticsTracker.triggerSync()
    }
    
    /**
     * Start aggregator mode (for burning centers)
     */
    fun startAggregatorMode(aggregatorId: String, capacity: Int = 50) {
        // This would be handled by the aggregator mode manager
        Log.d("MusicAnalyticsViewModel", "Aggregator mode start requested: $aggregatorId")
    }
    
    /**
     * Get detailed analytics statistics
     */
    fun getDetailedStats(): StateFlow<PlaybackAnalyticsTracker.AnalyticsStats> = analyticsStats
    
    /**
     * Clean up old records
     */
    fun cleanupOldRecords() {
        viewModelScope.launch {
            analyticsTracker.cleanupOldRecords()
        }
    }
    
    /**
     * Get device information for debugging
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = deviceIdentificationService.getDeviceId(),
            publicKeyHex = deviceIdentificationService.getPublicKeyHex(),
            meshPeerId = meshService.myPeerID
        )
    }
    
    /**
     * Share a music file via broadcast
     */
    fun shareFileBroadcast(filePath: String) {
        viewModelScope.launch {
            // Generate content ID and announce metadata only
            val contentId = contentIdGenerator.generateContentId(filePath)
            if (contentId != null) {
                // Extract metadata from file (simplified)
                val file = java.io.File(filePath)
                musicSharingService.announceTrackMetadata(
                    contentId = contentId,
                    title = file.nameWithoutExtension,
                    artist = "Unknown Artist", // Would extract from metadata in real implementation
                    duration = 0, // Would extract from metadata
                    fileSize = file.length()
                )
            }
        }
    }
    
    /**
     * Share a music file directly with a specific device
     */
    fun shareFileDirect(filePath: String, recipientDeviceId: String) {
        // For metadata-only system, this is the same as broadcast
        shareFileBroadcast(filePath)
    }
    
    /**
     * Cancel an active transfer
     */
    fun cancelTransfer(recordId: String) {
        // No actual transfers to cancel in metadata-only system
        Log.d("MusicAnalyticsViewModel", "Transfer cancellation not applicable in metadata-only mode")
    }
    
    /**
     * Request shared music from another device
     */
    fun requestSharedMusic(contentId: String, sharerDeviceId: String) {
        // In metadata-only system, this would trigger a manual transfer log
        viewModelScope.launch {
            val metadata = musicSharingService.getTrackMetadata(contentId)
            if (metadata != null) {
                transferTrackingService.logManualTransfer(
                    contentId = contentId,
                    method = com.bitchat.android.music.model.TransferMethod.BLUETOOTH,
                    fileSize = metadata.fileSize,
                    targetDeviceId = deviceIdentificationService.getDeviceId()
                )
            }
        }
    }
    
    /**
     * Get sharing statistics
     */
    fun getSharingStats() = musicSharingService.metadataStats
    
    /**
     * Get discovered music from nearby devices
     */
    fun getDiscoveredMusic() = musicSharingService.discoveredMusic
    
    /**
     * Log a manual transfer
     */
    fun logManualTransfer(
        contentId: String,
        method: com.bitchat.android.music.model.TransferMethod,
        fileSize: Long,
        targetDeviceId: String? = null
    ) {
        transferTrackingService.logManualTransfer(contentId, method, fileSize, targetDeviceId)
    }
    
    /**
     * Get transfer statistics
     */
    fun getTransferStats() = transferTrackingService.getTransferStats()
    
    /**
     * Get all transfer records
     */
    fun getTransferRecords() = transferTrackingService.transferRecords
    
    /**
     * Get active transfers
     */
    fun getActiveTransfers() = transferTrackingService.activeTransfers
    
    override fun onCleared() {
        super.onCleared()
        
        // Stop the music foreground service
        val intent = android.content.Intent(getApplication(), MusicForegroundService::class.java)
        intent.action = MusicForegroundService.ACTION_STOP_PLAYBACK
        getApplication<Application>().startService(intent)
        
        // Clean up other services
        analyticsTracker.release()
        musicSharingService.release()
        musicLibraryService.release()
        transferTrackingService.release()
    }
    
    data class MusicAnalyticsUiState(
        val isPlaying: Boolean = false,
        val currentTrack: MusicPlayerService.TrackInfo? = null,
        val totalRecords: Int = 0,
        val pendingSyncRecords: Int = 0,
        val syncStatus: MusicAnalyticsMeshSync.SyncStatus = MusicAnalyticsMeshSync.SyncStatus.Idle,
        val discoveredAggregators: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    data class DeviceInfo(
        val deviceId: String,
        val publicKeyHex: String,
        val meshPeerId: String
    )
}

/**
 * Factory for creating MusicAnalyticsViewModel with dependencies
 */
class MusicAnalyticsViewModelFactory(
    private val application: Application,
    private val meshService: BluetoothMeshService
) : androidx.lifecycle.ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicAnalyticsViewModel::class.java)) {
            return MusicAnalyticsViewModel(application, meshService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}