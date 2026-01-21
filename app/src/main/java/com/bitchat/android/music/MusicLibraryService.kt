package com.bitchat.android.music

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Service for managing the music library with folder filtering
 * Automatically scans and maintains a collection of audio files
 */
class MusicLibraryService(
    private val context: Context
) {
    companion object {
        private const val TAG = "MusicLibraryService"
        private const val PREFS_NAME = "music_library_prefs"
        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Library state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _audioFiles = MutableStateFlow<List<AudioTrack>>(emptyList())
    val audioFiles: StateFlow<List<AudioTrack>> = _audioFiles.asStateFlow()
    
    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()
    
    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()
    
    data class AudioTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val path: String,
        val size: Long,
        val folderPath: String,
        val uri: Uri
    )
    
    data class MusicFolder(
        val path: String,
        val name: String,
        val trackCount: Int,
        val isExcluded: Boolean
    )
    
    init {
        loadExcludedFolders()
        // Auto-scan on initialization
        scanLibrary()
    }
    
    /**
     * Scan the device for audio files and organize by folders
     */
    fun scanLibrary() {
        serviceScope.launch {
            _isScanning.value = true
            
            try {
                val allTracks = scanAudioFiles()
                val folderMap = groupTracksByFolder(allTracks)
                val excludedSet = _excludedFolders.value
                
                // Filter out excluded folders
                val filteredTracks = allTracks.filter { track ->
                    !excludedSet.contains(track.folderPath)
                }
                
                // Update folders list
                val folders = folderMap.map { (path, tracks) ->
                    MusicFolder(
                        path = path,
                        name = File(path).name,
                        trackCount = tracks.size,
                        isExcluded = excludedSet.contains(path)
                    )
                }.sortedBy { it.name }
                
                _audioFiles.value = filteredTracks.sortedWith(
                    compareBy<AudioTrack> { it.artist }.thenBy { it.album }.thenBy { it.title }
                )
                _folders.value = folders
                
                Log.i(TAG, "Library scan complete: ${filteredTracks.size} tracks in ${folders.size} folders")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning library", e)
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    /**
     * Toggle folder inclusion/exclusion
     */
    fun toggleFolderExclusion(folderPath: String) {
        val currentExcluded = _excludedFolders.value.toMutableSet()
        
        if (currentExcluded.contains(folderPath)) {
            currentExcluded.remove(folderPath)
        } else {
            currentExcluded.add(folderPath)
        }
        
        _excludedFolders.value = currentExcluded
        saveExcludedFolders(currentExcluded)
        
        // Refresh library with new exclusions
        scanLibrary()
    }
    
    /**
     * Get tracks for a specific folder
     */
    fun getTracksInFolder(folderPath: String): List<AudioTrack> {
        return _audioFiles.value.filter { it.folderPath == folderPath }
    }
    
    /**
     * Search tracks by query
     */
    fun searchTracks(query: String): List<AudioTrack> {
        if (query.isBlank()) return _audioFiles.value
        
        val lowerQuery = query.lowercase()
        return _audioFiles.value.filter { track ->
            track.title.lowercase().contains(lowerQuery) ||
            track.artist.lowercase().contains(lowerQuery) ||
            track.album.lowercase().contains(lowerQuery)
        }
    }
    
    private suspend fun scanAudioFiles(): List<AudioTrack> {
        return withContext(Dispatchers.IO) {
            val tracks = mutableListOf<AudioTrack>()
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            
            try {
                val cursor: Cursor? = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )
                
                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val title = it.getString(titleColumn) ?: "Unknown Title"
                        val artist = it.getString(artistColumn) ?: "Unknown Artist"
                        val album = it.getString(albumColumn) ?: "Unknown Album"
                        val duration = it.getLong(durationColumn)
                        val path = it.getString(dataColumn) ?: ""
                        val size = it.getLong(sizeColumn)
                        
                        // Verify file exists and get folder path
                        if (path.isNotEmpty() && File(path).exists()) {
                            val folderPath = File(path).parent ?: ""
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            
                            tracks.add(
                                AudioTrack(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = duration,
                                    path = path,
                                    size = size,
                                    folderPath = folderPath,
                                    uri = uri
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning audio files", e)
            }
            
            tracks
        }
    }
    
    private fun groupTracksByFolder(tracks: List<AudioTrack>): Map<String, List<AudioTrack>> {
        return tracks.groupBy { it.folderPath }
    }
    
    private fun loadExcludedFolders() {
        val excludedSet = prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet()
        _excludedFolders.value = excludedSet
    }
    
    private fun saveExcludedFolders(excludedFolders: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_EXCLUDED_FOLDERS, excludedFolders)
            .apply()
    }
    
    fun release() {
        serviceScope.cancel()
    }
}