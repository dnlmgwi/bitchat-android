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
    private val meshSync = MusicAnalyticsMeshSync(application, meshService, deviceIdentificationService)
    private val analyticsTracker = PlaybackAnalyticsTracker(application, meshSync)
    private val transferTrackingService = TransferTrackingService(
        application,
        deviceIdentificationService,
        contentIdGenerator,
        analyticsTracker
    )
    private val musicPlayerService = MusicPlayerService(
        application, 
        deviceIdentificationService, 
        contentIdGenerator, 
        analyticsTracker
    )
    private val musicSharingService = MusicMetadataService(
        application,
        deviceIdentificationService,
        contentIdGenerator
    )
    private val musicLibraryService = MusicLibraryService(application)
    
    // Expose services for UI
    val playerService: MusicPlayerService = musicPlayerService
    val tracker: PlaybackAnalyticsTracker = analyticsTracker
    val sync: MusicAnalyticsMeshSync = meshSync
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
        // Initialize music notification manager
        (application as? BitchatApplication)?.initializeMusicNotificationManager(musicPlayerService)
        
        // Combine various state flows for UI
        viewModelScope.launch {
            combine(
                musicPlayerService.isPlaying,
                musicPlayerService.currentTrackInfo,
                analyticsTracker.totalRecords,
                analyticsTracker.pendingSyncRecords,
                meshSync.syncStatus,
                meshSync.discoveredAggregators
            ) { flows ->
                val isPlaying = flows[0] as Boolean
                val currentTrack = flows[1] as MusicPlayerService.TrackInfo?
                val totalRecords = flows[2] as Int
                val pendingSync = flows[3] as Int
                val syncStatus = flows[4] as MusicAnalyticsMeshSync.SyncStatus
                val aggregators = flows[5] as Map<String, MusicAnalyticsMeshSync.AggregatorInfo>
                
                MusicAnalyticsUiState(
                    isPlaying = isPlaying,
                    currentTrack = currentTrack,
                    totalRecords = totalRecords,
                    pendingSyncRecords = pendingSync,
                    syncStatus = syncStatus,
                    discoveredAggregators = aggregators.size
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
     * Load and play a music file
     */
    fun loadAndPlayTrack(filePath: String, sourceType: SourceType = SourceType.LOCAL_FILE) {
        viewModelScope.launch {
            val success = musicPlayerService.loadTrack(filePath, sourceType)
            if (success) {
                musicPlayerService.play()
            }
        }
    }
    
    /**
     * Play/pause current track
     */
    fun togglePlayPause() {
        if (musicPlayerService.isPlaying.value) {
            musicPlayerService.pause()
        } else {
            musicPlayerService.play()
        }
    }
    
    /**
     * Stop playback
     */
    fun stopPlayback() {
        musicPlayerService.stop()
    }
    
    /**
     * Seek to position
     */
    fun seekTo(positionSeconds: Int) {
        musicPlayerService.seekTo(positionSeconds)
    }
    
    /**
     * Toggle repeat mode (cycles through OFF -> ONE -> ALL -> OFF)
     */
    fun toggleRepeatMode() {
        musicPlayerService.toggleRepeat()
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
        meshSync.startAggregatorMode(aggregatorId, capacity)
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
        musicPlayerService.release()
        analyticsTracker.release()
        meshSync.release()
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