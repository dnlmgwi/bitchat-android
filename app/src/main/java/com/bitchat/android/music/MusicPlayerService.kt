package com.bitchat.android.music

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.bitchat.android.music.model.PlaybackRecord
import com.bitchat.android.music.model.SourceType
import com.bitchat.android.music.model.TrackMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Music player service with integrated playback analytics tracking
 * Tracks detailed listening behavior for royalty calculations
 */
class MusicPlayerService(
    private val context: Context,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val contentIdGenerator: ContentIdGenerator,
    private val analyticsTracker: PlaybackAnalyticsTracker
) {
    
    companion object {
        private const val TAG = "MusicPlayerService"
        private const val PROGRESS_UPDATE_INTERVAL = 1000L // 1 second
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressUpdateJob: Job? = null
    
    // Current track state
    private var currentTrack: TrackInfo? = null
    private var playbackStartTime: Long = 0
    private var totalPlayedTime: Int = 0
    private var skipCount: Int = 0
    
    // Playlist management
    private var playlist: List<String> = emptyList()
    private var currentTrackIndex: Int = -1
    private var shuffledIndices: List<Int> = emptyList()
    
    // Player state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    private val _currentTrackInfo = MutableStateFlow<TrackInfo?>(null)
    val currentTrackInfo: StateFlow<TrackInfo?> = _currentTrackInfo.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    
    private val _isShuffleMode = MutableStateFlow(false)
    val isShuffleMode: StateFlow<Boolean> = _isShuffleMode.asStateFlow()
    
    enum class RepeatMode {
        OFF,        // No repeat
        ONE,        // Repeat current track
        ALL         // Repeat entire playlist
    }
    
    data class TrackInfo(
        val filePath: String,
        val contentId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val duration: Int,
        val sourceType: SourceType = SourceType.LOCAL_FILE
    )
    
    /**
     * Load and prepare a track for playback
     */
    suspend fun loadTrack(filePath: String, sourceType: SourceType = SourceType.LOCAL_FILE): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Stop current playback
                stop()
                
                // Validate file exists
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "File does not exist: $filePath")
                    return@withContext false
                }
                
                // Generate content ID
                val contentId = contentIdGenerator.generateContentId(filePath)
                if (contentId == null) {
                    Log.e(TAG, "Failed to generate content ID for: $filePath")
                    return@withContext false
                }
                
                // Extract metadata
                val metadata = contentIdGenerator.extractTrackMetadata(filePath, contentId)
                if (metadata == null) {
                    Log.e(TAG, "Failed to extract metadata for: $filePath")
                    return@withContext false
                }
                
                // Create MediaPlayer and wait for preparation
                val preparationResult = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, Uri.fromFile(file))
                            
                            setOnPreparedListener { player ->
                                try {
                                    val trackDuration = player.duration / 1000 // Convert to seconds
                                    
                                    currentTrack = TrackInfo(
                                        filePath = filePath,
                                        contentId = contentId,
                                        title = metadata.title,
                                        artist = metadata.artist,
                                        album = metadata.album,
                                        duration = trackDuration,
                                        sourceType = sourceType
                                    )
                                    
                                    _duration.value = trackDuration
                                    _currentTrackInfo.value = currentTrack
                                    
                                    Log.i(TAG, "Track loaded: ${metadata.title} by ${metadata.artist} (${trackDuration}s)")
                                    continuation.resume(true) {}
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in onPrepared", e)
                                    continuation.resume(false) {}
                                }
                            }
                            
                            setOnErrorListener { _, what, extra ->
                                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                continuation.resume(false) {}
                                false
                            }
                            
                            setOnCompletionListener {
                                handleTrackCompletion()
                            }
                            
                            try {
                                prepareAsync()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error calling prepareAsync", e)
                                continuation.resume(false) {}
                            }
                        }
                        
                        // Set timeout for preparation
                        continuation.invokeOnCancellation {
                            mediaPlayer?.release()
                            mediaPlayer = null
                        }
                    }
                }
                
                preparationResult
            } catch (e: Exception) {
                Log.e(TAG, "Error loading track: $filePath", e)
                false
            }
        }
    }
    
    /**
     * Start playback
     */
    fun play() {
        try {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    _isPlaying.value = true
                    playbackStartTime = System.currentTimeMillis()
                    startProgressUpdates()
                    
                    Log.d(TAG, "Playback started")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
        }
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _isPlaying.value = false
                    updatePlayedTime()
                    stopProgressUpdates()
                    
                    Log.d(TAG, "Playback paused")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }
    
    /**
     * Stop playback and record analytics
     */
    fun stop() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
                player.seekTo(0)
                _isPlaying.value = false
                _currentPosition.value = 0
                updatePlayedTime()
                stopProgressUpdates()
                
                // Record playback analytics if track was played
                recordPlaybackAnalytics()
                
                // Reset tracking state
                resetTrackingState()
                
                Log.d(TAG, "Playback stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }
    
    /**
     * Seek to position (in seconds)
     */
    fun seekTo(positionSeconds: Int) {
        try {
            mediaPlayer?.let { player ->
                val wasPlaying = player.isPlaying
                if (wasPlaying) {
                    updatePlayedTime()
                }
                
                player.seekTo(positionSeconds * 1000)
                _currentPosition.value = positionSeconds
                
                // Count as skip if significant jump
                val currentPos = player.currentPosition / 1000
                if (kotlin.math.abs(currentPos - positionSeconds) > 5) {
                    skipCount++
                }
                
                if (wasPlaying) {
                    playbackStartTime = System.currentTimeMillis()
                }
                
                Log.d(TAG, "Seeked to: ${positionSeconds}s")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }
    
    /**
     * Set playlist and start playing from specified index
     */
    suspend fun setPlaylist(trackPaths: List<String>, startIndex: Int = 0) {
        playlist = trackPaths
        currentTrackIndex = startIndex.coerceIn(0, trackPaths.size - 1)
        generateShuffledIndices()
        
        if (trackPaths.isNotEmpty() && currentTrackIndex >= 0) {
            loadTrack(trackPaths[currentTrackIndex])
        }
    }
    
    /**
     * Play next track in playlist
     */
    fun playNext() {
        serviceScope.launch {
            playNextSuspend()
        }
    }
    
    /**
     * Play previous track in playlist
     */
    fun playPrevious() {
        serviceScope.launch {
            playPreviousSuspend()
        }
    }
    
    private suspend fun playNextSuspend() {
        if (playlist.isEmpty()) return
        
        val nextIndex = getNextTrackIndex()
        if (nextIndex >= 0) {
            currentTrackIndex = nextIndex
            loadTrack(playlist[currentTrackIndex])
            play()
        }
    }
    
    private suspend fun playPreviousSuspend() {
        if (playlist.isEmpty()) return
        
        val prevIndex = getPreviousTrackIndex()
        if (prevIndex >= 0) {
            currentTrackIndex = prevIndex
            loadTrack(playlist[currentTrackIndex])
            play()
        }
    }
    
    /**
     * Toggle shuffle mode
     */
    fun toggleShuffle() {
        _isShuffleMode.value = !_isShuffleMode.value
        generateShuffledIndices()
        Log.d(TAG, "Shuffle mode: ${_isShuffleMode.value}")
    }
    
    /**
     * Cycle through repeat modes: OFF -> ONE -> ALL -> OFF
     */
    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        
        // Update MediaPlayer looping for repeat one mode
        mediaPlayer?.isLooping = (_repeatMode.value == RepeatMode.ONE)
        
        Log.d(TAG, "Repeat mode: ${_repeatMode.value}")
    }
    
    /**
     * Skip to next track (for notification controls)
     */
    fun next() {
        playNext()
    }
    
    /**
     * Skip to previous track (for notification controls)
     */
    fun previous() {
        playPrevious()
    }
    
    /**
     * Release resources
     */
    fun release() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
        serviceScope.cancel()
        Log.d(TAG, "MusicPlayerService released")
    }
    
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = serviceScope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition / 1000
                    }
                }
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }
    
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    
    private fun updatePlayedTime() {
        if (playbackStartTime > 0) {
            val sessionTime = ((System.currentTimeMillis() - playbackStartTime) / 1000).toInt()
            totalPlayedTime += sessionTime
            playbackStartTime = 0
        }
    }
    
    private fun handleTrackCompletion() {
        updatePlayedTime()
        _isPlaying.value = false
        stopProgressUpdates()
        
        // Record analytics for completed track
        recordPlaybackAnalytics()
        
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Repeat current track - MediaPlayer looping handles this automatically
                // Reset tracking state for new play
                playbackStartTime = System.currentTimeMillis()
                totalPlayedTime = 0
                skipCount = 0
            }
            RepeatMode.ALL -> {
                // Repeat playlist - go to next track, loop back to start if at end
                resetTrackingState()
                playNext()
            }
            RepeatMode.OFF -> {
                // No repeat - go to next track, stop if at end
                resetTrackingState()
                val nextIndex = getNextTrackIndex()
                if (nextIndex >= 0 && nextIndex != 0) {
                    // Continue to next track (but not if we'd loop back to start)
                    playNext()
                }
                // If nextIndex is 0, we're at the end of playlist, so stop
            }
        }
        
        Log.d(TAG, "Track completed with repeat mode: ${_repeatMode.value}")
    }
    
    private fun getNextTrackIndex(): Int {
        if (playlist.isEmpty()) return -1
        
        return if (_isShuffleMode.value) {
            val currentShuffledIndex = shuffledIndices.indexOf(currentTrackIndex)
            if (currentShuffledIndex >= 0 && currentShuffledIndex < shuffledIndices.size - 1) {
                shuffledIndices[currentShuffledIndex + 1]
            } else {
                shuffledIndices.firstOrNull() ?: -1
            }
        } else {
            if (currentTrackIndex < playlist.size - 1) {
                currentTrackIndex + 1
            } else {
                0 // Loop back to beginning
            }
        }
    }
    
    private fun getPreviousTrackIndex(): Int {
        if (playlist.isEmpty()) return -1
        
        return if (_isShuffleMode.value) {
            val currentShuffledIndex = shuffledIndices.indexOf(currentTrackIndex)
            if (currentShuffledIndex > 0) {
                shuffledIndices[currentShuffledIndex - 1]
            } else {
                shuffledIndices.lastOrNull() ?: -1
            }
        } else {
            if (currentTrackIndex > 0) {
                currentTrackIndex - 1
            } else {
                playlist.size - 1 // Loop to end
            }
        }
    }
    
    private fun generateShuffledIndices() {
        if (playlist.isEmpty()) {
            shuffledIndices = emptyList()
            return
        }
        
        shuffledIndices = playlist.indices.shuffled()
    }
    
    private fun recordPlaybackAnalytics() {
        currentTrack?.let { track ->
            if (totalPlayedTime > 0) {
                val playPercentage = (totalPlayedTime.toFloat() / track.duration.toFloat()).coerceAtMost(1.0f)
                
                val record = PlaybackRecord(
                    contentId = track.contentId,
                    deviceId = deviceIdentificationService.getDeviceId(),
                    timestamp = System.currentTimeMillis(),
                    durationPlayed = totalPlayedTime,
                    trackDuration = track.duration,
                    playPercentage = playPercentage,
                    skipCount = skipCount,
                    repeatFlag = (_repeatMode.value != RepeatMode.OFF),
                    sourceType = track.sourceType
                )
                
                // Sign the record
                val signature = deviceIdentificationService.signPlaybackRecord(record)
                val signedRecord = record.copy(deviceSignature = signature)
                
                // Submit to analytics tracker
                serviceScope.launch {
                    analyticsTracker.recordPlayback(signedRecord)
                    
                    // Also record track metadata for unique track counting
                    val trackMetadata = TrackMetadata(
                        contentId = track.contentId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album ?: "Unknown Album",
                        duration = track.duration,
                        audioFingerprint = ByteArray(0), // Empty for now
                        firstSeen = System.currentTimeMillis()
                    )
                    analyticsTracker.recordTrackMetadata(trackMetadata)
                }
                
                Log.i(TAG, "Recorded playback: ${track.title} - ${totalPlayedTime}s/${track.duration}s (${(playPercentage * 100).toInt()}%)")
            }
        }
    }
    
    private fun resetTrackingState() {
        totalPlayedTime = 0
        skipCount = 0
        playbackStartTime = 0
    }
}