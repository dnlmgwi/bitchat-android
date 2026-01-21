package com.bitchat.android.music

import android.content.Context
import android.util.Log
import com.bitchat.android.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Music metadata service that tracks music distribution patterns
 * Focuses on metadata collection rather than file sharing
 */
class MusicMetadataService(
    private val context: Context,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val contentIdGenerator: ContentIdGenerator
) {
    
    companion object {
        private const val TAG = "MusicMetadataService"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Discovered music metadata from other devices
    private val _discoveredMusic = MutableStateFlow<Map<String, SharedMusicInfo>>(emptyMap())
    val discoveredMusic: StateFlow<Map<String, SharedMusicInfo>> = _discoveredMusic.asStateFlow()
    
    // Metadata sharing statistics
    private val _metadataStats = MutableStateFlow(MetadataStats())
    val metadataStats: StateFlow<MetadataStats> = _metadataStats.asStateFlow()
    
    data class SharedMusicInfo(
        val contentId: String,
        val title: String,
        val artist: String,
        val duration: Int,
        val fileSize: Long,
        val sharerDeviceId: String,
        val shareTime: Long,
        val isAvailable: Boolean = true
    )
    
    data class MetadataStats(
        val totalDiscovered: Int = 0,
        val uniqueTracks: Int = 0,
        val totalDevices: Int = 0,
        val lastSyncTime: Long = 0
    )
    
    /**
     * Announce music metadata to nearby devices
     * This shares track information but not the actual files
     */
    fun announceTrackMetadata(
        contentId: String,
        title: String,
        artist: String,
        duration: Int,
        fileSize: Long
    ) {
        serviceScope.launch {
            try {
                val musicInfo = SharedMusicInfo(
                    contentId = contentId,
                    title = title,
                    artist = artist,
                    duration = duration,
                    fileSize = fileSize,
                    sharerDeviceId = deviceIdentificationService.getDeviceId(),
                    shareTime = System.currentTimeMillis()
                )
                
                // Add to local discovered music (for testing/demo purposes)
                val currentDiscovered = _discoveredMusic.value.toMutableMap()
                currentDiscovered[contentId] = musicInfo
                _discoveredMusic.value = currentDiscovered
                
                updateMetadataStats()
                
                Log.i(TAG, "Announced track metadata: $title by $artist")
            } catch (e: Exception) {
                Log.e(TAG, "Error announcing track metadata", e)
            }
        }
    }
    
    /**
     * Process discovered music metadata from other devices
     */
    fun processDiscoveredMetadata(musicInfo: SharedMusicInfo) {
        serviceScope.launch {
            val currentDiscovered = _discoveredMusic.value.toMutableMap()
            currentDiscovered[musicInfo.contentId] = musicInfo
            _discoveredMusic.value = currentDiscovered
            
            updateMetadataStats()
            
            Log.i(TAG, "Discovered music: ${musicInfo.title} from device ${musicInfo.sharerDeviceId.take(8)}")
        }
    }
    
    /**
     * Remove unavailable music metadata
     */
    fun removeUnavailableMusic(contentId: String) {
        val currentDiscovered = _discoveredMusic.value.toMutableMap()
        currentDiscovered[contentId]?.let { info ->
            currentDiscovered[contentId] = info.copy(isAvailable = false)
            _discoveredMusic.value = currentDiscovered
            Log.d(TAG, "Marked music as unavailable: $contentId")
        }
    }
    
    /**
     * Clear old metadata entries
     */
    fun clearOldMetadata(olderThanHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000L)
        val currentDiscovered = _discoveredMusic.value.toMutableMap()
        
        val removed = currentDiscovered.values.removeAll { it.shareTime < cutoffTime }
        if (removed) {
            _discoveredMusic.value = currentDiscovered
            updateMetadataStats()
            Log.d(TAG, "Cleared old metadata entries")
        }
    }
    
    /**
     * Update metadata statistics
     */
    private fun updateMetadataStats() {
        val discovered = _discoveredMusic.value
        val stats = MetadataStats(
            totalDiscovered = discovered.size,
            uniqueTracks = discovered.keys.size,
            totalDevices = discovered.values.map { it.sharerDeviceId }.distinct().size,
            lastSyncTime = System.currentTimeMillis()
        )
        _metadataStats.value = stats
    }
    
    /**
     * Get metadata for a specific track
     */
    fun getTrackMetadata(contentId: String): SharedMusicInfo? {
        return _discoveredMusic.value[contentId]
    }
    
    /**
     * Get all available tracks from a specific device
     */
    fun getTracksFromDevice(deviceId: String): List<SharedMusicInfo> {
        return _discoveredMusic.value.values.filter { 
            it.sharerDeviceId == deviceId && it.isAvailable 
        }
    }
    
    /**
     * Search discovered music by title or artist
     */
    fun searchDiscoveredMusic(query: String): List<SharedMusicInfo> {
        val lowerQuery = query.lowercase()
        return _discoveredMusic.value.values.filter { info ->
            info.isAvailable && (
                info.title.lowercase().contains(lowerQuery) ||
                info.artist.lowercase().contains(lowerQuery)
            )
        }
    }
    
    /**
     * Get statistics about discovered music
     */
    fun getMetadataStatistics(): MetadataStats = _metadataStats.value
    
    /**
     * Release resources
     */
    fun release() {
        serviceScope.cancel()
        Log.d(TAG, "MusicMetadataService released")
    }
}