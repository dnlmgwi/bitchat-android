package com.bitchat.android.music.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.android.music.AggregatedDataSyncManager
import com.bitchat.android.music.AggregatorDataExporter
import com.bitchat.android.music.DeviceIdentificationService
import com.bitchat.android.music.PlaybackAnalyticsTracker
import com.bitchat.android.service.MeshServiceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class AggregatorModeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val analyticsTracker = PlaybackAnalyticsTracker.getInstance(application)
    private val dataExporter = AggregatorDataExporter(application)
    
    // Aggregated data sync manager for inter-aggregator communication
    private var aggregatedDataSyncManager: AggregatedDataSyncManager? = null
    
    private val _dataCounts = MutableStateFlow(com.bitchat.android.music.DataCounts())
    val dataCounts: StateFlow<com.bitchat.android.music.DataCounts> = _dataCounts.asStateFlow()
    
    // Sync status from AggregatedDataSyncManager
    private val _syncStatus = MutableStateFlow<AggregatedDataSyncManager.AggregatorSyncStatus?>(null)
    val syncStatus: StateFlow<AggregatedDataSyncManager.AggregatorSyncStatus?> = _syncStatus.asStateFlow()
    
    // Discovered peer aggregators
    private val _discoveredAggregators = MutableStateFlow<Map<String, com.bitchat.android.music.AggregatedDataAnnouncement>>(emptyMap())
    val discoveredAggregators: StateFlow<Map<String, com.bitchat.android.music.AggregatedDataAnnouncement>> = _discoveredAggregators.asStateFlow()
    
    init {
        refreshDataCounts()
        initializeAggregatedDataSync()
    }
    
    private fun initializeAggregatedDataSync() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val meshService = MeshServiceHolder.getOrCreate(context)
                val deviceIdService = DeviceIdentificationService(context)
                
                aggregatedDataSyncManager = AggregatedDataSyncManager(
                    context = context,
                    meshService = meshService,
                    deviceIdentificationService = deviceIdService,
                    analyticsTracker = analyticsTracker
                )
                
                // Collect sync status updates
                launch {
                    aggregatedDataSyncManager?.syncStatus?.collect { status ->
                        _syncStatus.value = status
                    }
                }
                
                // Collect discovered aggregators
                launch {
                    aggregatedDataSyncManager?.discoveredAggregators?.collect { aggregators ->
                        _discoveredAggregators.value = aggregators
                    }
                }
                
                // Start aggregator sync
                aggregatedDataSyncManager?.startAggregatorSync()
                
            } catch (e: Exception) {
                android.util.Log.e("AggregatorModeViewModel", "Error initializing aggregated data sync", e)
            }
        }
    }
    
    fun refreshDataCounts() {
        viewModelScope.launch {
            try {
                val playbackRecords = analyticsTracker.getAllPlaybackRecords()
                val sharingRecords = analyticsTracker.getAllSharingRecords()
                val trackMetadata = analyticsTracker.getAllTrackMetadata()
                val transferRecords = analyticsTracker.getAllTransferRecords()
                
                _dataCounts.value = com.bitchat.android.music.DataCounts(
                    playbackRecords = playbackRecords.size,
                    sharingRecords = sharingRecords.size,
                    trackMetadata = trackMetadata.size,
                    transferRecords = transferRecords.size
                )
            } catch (e: Exception) {
                // Handle error - could emit error state
                _dataCounts.value = com.bitchat.android.music.DataCounts()
            }
        }
    }
    
    /**
     * Request aggregated data from a peer aggregator
     */
    fun requestDataFromPeer(peerId: String) {
        viewModelScope.launch {
            try {
                aggregatedDataSyncManager?.requestAggregatedDataFromPeer(peerId)
            } catch (e: Exception) {
                android.util.Log.e("AggregatorModeViewModel", "Error requesting data from peer", e)
            }
        }
    }
    
    /**
     * Broadcast availability of our aggregated data to nearby aggregators
     */
    fun broadcastDataAvailability() {
        viewModelScope.launch {
            try {
                aggregatedDataSyncManager?.broadcastAggregatedDataAvailability()
            } catch (e: Exception) {
                android.util.Log.e("AggregatorModeViewModel", "Error broadcasting availability", e)
            }
        }
    }
    
    /**
     * Export all aggregated data including transfer records
     */
    suspend fun exportAllAggregatedData(format: AggregatorDataExporter.ExportFormat): AggregatorDataExporter.ExportResult {
        return try {
            aggregatedDataSyncManager?.exportAllAggregatedData(format)
                ?: AggregatorDataExporter.ExportResult(
                    success = false,
                    filePath = null,
                    recordCount = 0,
                    error = "Aggregated data sync manager not initialized"
                )
        } catch (e: Exception) {
            AggregatorDataExporter.ExportResult(
                success = false,
                filePath = null,
                recordCount = 0,
                error = e.message
            )
        }
    }
    
    suspend fun exportData(
        format: AggregatorDataExporter.ExportFormat,
        dataType: ExportDataType
    ): AggregatorDataExporter.ExportResult {
        return try {
            when (dataType) {
                ExportDataType.PLAYBACK_RECORDS -> {
                    val records = analyticsTracker.getAllPlaybackRecords()
                    dataExporter.exportPlaybackRecords(records, format)
                }
                ExportDataType.SHARING_RECORDS -> {
                    val records = analyticsTracker.getAllSharingRecords()
                    dataExporter.exportSharingRecords(records, format)
                }
                ExportDataType.TRACK_METADATA -> {
                    val tracks = analyticsTracker.getAllTrackMetadata()
                    dataExporter.exportTrackMetadata(tracks, format)
                }
                ExportDataType.TRANSFER_RECORDS -> {
                    val records = analyticsTracker.getAllTransferRecords()
                    dataExporter.exportTransferRecords(records, format)
                }
                ExportDataType.COMPREHENSIVE -> {
                    exportAllAggregatedData(format)
                }
            }
        } catch (e: Exception) {
            AggregatorDataExporter.ExportResult(
                success = false,
                filePath = null,
                recordCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Share the exported file using Android's share intent
     */
    fun shareExportedFile(filePath: String) {
        try {
            val file = File(filePath)
            android.util.Log.d("AggregatorModeViewModel", "Attempting to share file: $filePath")
            android.util.Log.d("AggregatorModeViewModel", "File exists: ${file.exists()}, size: ${if (file.exists()) file.length() else "N/A"}")
            
            if (!file.exists()) {
                android.util.Log.e("AggregatorModeViewModel", "File does not exist: $filePath")
                return
            }
            
            val context = getApplication<Application>()
            
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                android.util.Log.d("AggregatorModeViewModel", "Generated URI: $uri")
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when (file.extension.lowercase()) {
                        "json" -> "application/json"
                        "xml" -> "application/xml"
                        "csv" -> "text/csv"
                        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        else -> "text/plain"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Music Analytics Export - ${file.name}")
                    putExtra(Intent.EXTRA_TEXT, "Exported music analytics data from mabench")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val chooserIntent = Intent.createChooser(shareIntent, "Share Export File")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)
                
                android.util.Log.d("AggregatorModeViewModel", "Share intent launched successfully")
                
            } catch (e: Exception) {
                android.util.Log.e("AggregatorModeViewModel", "Error creating FileProvider URI for: $filePath", e)
                
                // Try alternative approach - copy to cache directory first
                try {
                    val cacheDir = File(context.cacheDir, "exports")
                    cacheDir.mkdirs()
                    val cachedFile = File(cacheDir, file.name)
                    
                    file.copyTo(cachedFile, overwrite = true)
                    android.util.Log.d("AggregatorModeViewModel", "Copied file to cache: ${cachedFile.absolutePath}")
                    
                    val cacheUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cachedFile
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = when (file.extension.lowercase()) {
                            "json" -> "application/json"
                            "xml" -> "application/xml"
                            "csv" -> "text/csv"
                            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            else -> "text/plain"
                        }
                        putExtra(Intent.EXTRA_STREAM, cacheUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Music Analytics Export - ${file.name}")
                        putExtra(Intent.EXTRA_TEXT, "Exported music analytics data from mabench")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    val chooserIntent = Intent.createChooser(shareIntent, "Share Export File")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)
                    
                    android.util.Log.d("AggregatorModeViewModel", "Share intent launched successfully via cache")
                    
                } catch (e2: Exception) {
                    android.util.Log.e("AggregatorModeViewModel", "Failed to share file via cache as well", e2)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AggregatorModeViewModel", "Error sharing file: $filePath", e)
        }
    }
    
    /**
     * Open the file location using a file manager or viewer
     */
    fun openFileLocation(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                android.util.Log.e("AggregatorModeViewModel", "File does not exist: $filePath")
                return
            }
            
            android.util.Log.d("AggregatorModeViewModel", "Attempting to open file: $filePath")
            
            val context = getApplication<Application>()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            android.util.Log.d("AggregatorModeViewModel", "Generated URI for open: $uri")
            
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, when (file.extension.lowercase()) {
                    "json" -> "application/json"
                    "xml" -> "application/xml"
                    "csv" -> "text/csv"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    else -> "text/plain"
                })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Try to open with a specific app, fallback to file manager
            try {
                context.startActivity(openIntent)
                android.util.Log.d("AggregatorModeViewModel", "File opened successfully")
            } catch (e: Exception) {
                android.util.Log.w("AggregatorModeViewModel", "Could not open file directly, trying file manager", e)
                // Fallback: try to open file manager to the exports directory
                val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file.parentFile ?: file
                        ),
                        "resource/folder"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(folderIntent)
                    android.util.Log.d("AggregatorModeViewModel", "File manager opened successfully")
                } catch (e2: Exception) {
                    android.util.Log.e("AggregatorModeViewModel", "Could not open file or file manager", e2)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AggregatorModeViewModel", "Error opening file: $filePath", e)
        }
    }
}

data class DataCounts(
    val playbackRecords: Int = 0,
    val sharingRecords: Int = 0,
    val trackMetadata: Int = 0,
    val transferRecords: Int = 0
)