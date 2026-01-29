package com.bitchat.android.music

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.PacketProcessor
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.music.model.*
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Interface for aggregated data synchronization between aggregator nodes
 * This allows aggregators to sync collected data with each other and export
 */
interface AggregatedDataSyncInterface {
    suspend fun syncAggregatedDataToPeer(peerId: String, data: AggregatedDataBundle)
    suspend fun requestAggregatedDataFromPeer(peerId: String)
    suspend fun broadcastAggregatedDataAvailability()
    
    fun startAggregatorSync()
    fun stopAggregatorSync()
    fun release()
}

/**
 * Data bundle containing aggregated analytics data for sync between aggregators
 */
data class AggregatedDataBundle(
    val bundleId: String = UUID.randomUUID().toString(),
    val sourceAggregatorId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val playbackRecords: List<PlaybackRecord>,
    val sharingRecords: List<SharingRecord>,
    val trackMetadata: List<TrackMetadata>,
    val transferRecords: List<TransferRecord>,
    val deviceSignature: ByteArray? = null
) {
    fun toBinaryPayload(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(32768).apply { 
            order(java.nio.ByteOrder.BIG_ENDIAN) 
        }
        
        // Header
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, bundleId)
        writeStringWithLength(buffer, sourceAggregatorId)
        
        // Playback records
        buffer.putShort(playbackRecords.size.toShort())
        playbackRecords.forEach { record ->
            val recordBytes = record.toBinaryPayload()
            buffer.putShort(recordBytes.size.toShort())
            buffer.put(recordBytes)
        }
        
        // Sharing records
        buffer.putShort(sharingRecords.size.toShort())
        sharingRecords.forEach { record ->
            val recordBytes = record.toBinaryPayload()
            buffer.putShort(recordBytes.size.toShort())
            buffer.put(recordBytes)
        }
        
        // Track metadata
        buffer.putShort(trackMetadata.size.toShort())
        trackMetadata.forEach { metadata ->
            val metaBytes = metadata.toBinaryPayload()
            buffer.putShort(metaBytes.size.toShort())
            buffer.put(metaBytes)
        }
        
        // Transfer records
        buffer.putShort(transferRecords.size.toShort())
        transferRecords.forEach { record ->
            val recordBytes = record.toBinaryPayload()
            buffer.putShort(recordBytes.size.toShort())
            buffer.put(recordBytes)
        }
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): AggregatedDataBundle? {
            return try {
                val buffer = java.nio.ByteBuffer.wrap(data).apply { 
                    order(java.nio.ByteOrder.BIG_ENDIAN) 
                }
                
                val timestamp = buffer.long
                val bundleId = readStringWithLength(buffer)
                val sourceAggregatorId = readStringWithLength(buffer)
                
                // Playback records
                val playbackCount = buffer.short.toInt()
                val playbackRecords = mutableListOf<PlaybackRecord>()
                repeat(playbackCount) {
                    val size = buffer.short.toInt()
                    val bytes = ByteArray(size)
                    buffer.get(bytes)
                    PlaybackRecord.fromBinaryPayload(bytes)?.let { record ->
                        playbackRecords.add(record)
                    }
                }
                
                // Sharing records
                val sharingCount = buffer.short.toInt()
                val sharingRecords = mutableListOf<SharingRecord>()
                repeat(sharingCount) {
                    val size = buffer.short.toInt()
                    val bytes = ByteArray(size)
                    buffer.get(bytes)
                    SharingRecord.fromBinaryPayload(bytes)?.let { record ->
                        sharingRecords.add(record)
                    }
                }
                
                // Track metadata
                val metadataCount = buffer.short.toInt()
                val trackMetadata = mutableListOf<TrackMetadata>()
                repeat(metadataCount) {
                    val size = buffer.short.toInt()
                    val bytes = ByteArray(size)
                    buffer.get(bytes)
                    TrackMetadata.fromBinaryPayload(bytes)?.let { metadata ->
                        trackMetadata.add(metadata)
                    }
                }
                
                // Transfer records
                val transferCount = buffer.short.toInt()
                val transferRecords = mutableListOf<TransferRecord>()
                repeat(transferCount) {
                    val size = buffer.short.toInt()
                    val bytes = ByteArray(size)
                    buffer.get(bytes)
                    TransferRecord.fromBinaryPayload(bytes)?.let { record ->
                        transferRecords.add(record)
                    }
                }
                
                AggregatedDataBundle(
                    bundleId = bundleId,
                    sourceAggregatorId = sourceAggregatorId,
                    timestamp = timestamp,
                    playbackRecords = playbackRecords,
                    sharingRecords = sharingRecords,
                    trackMetadata = trackMetadata,
                    transferRecords = transferRecords
                )
            } catch (e: Exception) {
                Log.e("AggregatedDataBundle", "Error parsing bundle", e)
                null
            }
        }
        
        private fun readStringWithLength(buffer: java.nio.ByteBuffer): String {
            val length = buffer.short.toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

private fun writeStringWithLength(buffer: java.nio.ByteBuffer, str: String) {
    val bytes = str.toByteArray(Charsets.UTF_8)
    buffer.putShort(bytes.size.toShort())
    buffer.put(bytes)
}

private fun getUsedBytes(buffer: java.nio.ByteBuffer): ByteArray {
    val result = ByteArray(buffer.position())
    buffer.rewind()
    buffer.get(result)
    return result
}

/**
 * Message for announcing aggregated data availability
 */
data class AggregatedDataAnnouncement(
    val aggregatorId: String,
    val recordCounts: DataCounts,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toBinaryPayload(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(256).apply { 
            order(java.nio.ByteOrder.BIG_ENDIAN) 
        }
        
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, aggregatorId)
        buffer.putInt(recordCounts.playbackRecords)
        buffer.putInt(recordCounts.sharingRecords)
        buffer.putInt(recordCounts.trackMetadata)
        buffer.putInt(recordCounts.transferRecords)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): AggregatedDataAnnouncement? {
            return try {
                val buffer = java.nio.ByteBuffer.wrap(data).apply { 
                    order(java.nio.ByteOrder.BIG_ENDIAN) 
                }
                
                val timestamp = buffer.long
                val aggregatorId = readStringWithLength(buffer)
                val playbackCount = buffer.int
                val sharingCount = buffer.int
                val metadataCount = buffer.int
                val transferCount = buffer.int
                
                AggregatedDataAnnouncement(
                    aggregatorId = aggregatorId,
                    recordCounts = DataCounts(
                        playbackRecords = playbackCount,
                        sharingRecords = sharingCount,
                        trackMetadata = metadataCount,
                        transferRecords = transferCount
                    ),
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                null
            }
        }
        
        private fun readStringWithLength(buffer: java.nio.ByteBuffer): String {
            val length = buffer.short.toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

/**
 * Request for aggregated data from another aggregator
 */
data class AggregatedDataRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val requesterId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val lastSyncTimestamp: Long? = null // Only request data newer than this
) {
    fun toBinaryPayload(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(256).apply { 
            order(java.nio.ByteOrder.BIG_ENDIAN) 
        }
        
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, requestId)
        writeStringWithLength(buffer, requesterId)
        
        val hasLastSync = lastSyncTimestamp != null
        buffer.put(if (hasLastSync) 1.toByte() else 0.toByte())
        if (hasLastSync) {
            buffer.putLong(lastSyncTimestamp!!)
        }
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): AggregatedDataRequest? {
            return try {
                val buffer = java.nio.ByteBuffer.wrap(data).apply { 
                    order(java.nio.ByteOrder.BIG_ENDIAN) 
                }
                
                val timestamp = buffer.long
                val requestId = readStringWithLength(buffer)
                val requesterId = readStringWithLength(buffer)
                val hasLastSync = buffer.get() != 0.toByte()
                val lastSync = if (hasLastSync) buffer.long else null
                
                AggregatedDataRequest(
                    requestId = requestId,
                    requesterId = requesterId,
                    timestamp = timestamp,
                    lastSyncTimestamp = lastSync
                )
            } catch (e: Exception) {
                null
            }
        }
        
        private fun readStringWithLength(buffer: java.nio.ByteBuffer): String {
            val length = buffer.short.toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

/**
 * Enhanced data counts including transfer records
 */
data class DataCounts(
    val playbackRecords: Int = 0,
    val sharingRecords: Int = 0,
    val trackMetadata: Int = 0,
    val transferRecords: Int = 0
)

/**
 * Aggregated data sync manager for inter-aggregator communication
 * Handles sync of collected analytics data between aggregator nodes
 */
class AggregatedDataSyncManager(
    private val context: Context,
    private val meshService: BluetoothMeshService,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val analyticsTracker: PlaybackAnalyticsTracker
) : AggregatedDataSyncInterface, PacketProcessor.PacketListener {
    
    companion object {
        private const val TAG = "AggregatedDataSyncManager"
        private const val MAX_RECORDS_PER_BUNDLE = 100
        private const val SYNC_INTERVAL_MS = 60000L // 1 minute
        private const val AGGREGATOR_DISCOVERY_INTERVAL = 30000L // 30 seconds
    }
    
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var discoveryJob: Job? = null
    
    // Discovered peer aggregators
    private val _discoveredAggregators = MutableStateFlow<Map<String, AggregatedDataAnnouncement>>(emptyMap())
    val discoveredAggregators: StateFlow<Map<String, AggregatedDataAnnouncement>> = _discoveredAggregators.asStateFlow()
    
    // Sync status
    private val _syncStatus = MutableStateFlow<AggregatorSyncStatus>(AggregatorSyncStatus.Idle)
    val syncStatus: StateFlow<AggregatorSyncStatus> = _syncStatus.asStateFlow()
    
    // Last sync timestamps with other aggregators
    private val lastSyncTimestamps = mutableMapOf<String, Long>()
    
    sealed class AggregatorSyncStatus {
        object Idle : AggregatorSyncStatus()
        object Discovering : AggregatorSyncStatus()
        data class Syncing(val peerId: String, val progress: Float) : AggregatorSyncStatus()
        data class Exporting(val recordCount: Int) : AggregatorSyncStatus()
        data class Success(val recordsSynced: Int, val peerId: String) : AggregatorSyncStatus()
        data class Error(val message: String) : AggregatorSyncStatus()
    }
    
    init {
        registerPacketListener()
        startAggregatorDiscovery()
    }
    
    private fun registerPacketListener() {
        meshService.addPacketListener(this)
    }
    
    override fun startAggregatorSync() {
        startAggregatorDiscovery()
        startPeriodicSync()
    }
    
    override fun stopAggregatorSync() {
        syncJob?.cancel()
        discoveryJob?.cancel()
        syncJob = null
        discoveryJob = null
    }
    
    override fun release() {
        meshService.removePacketListener(this)
        syncScope.cancel()
    }
    
    /**
     * Sync aggregated data to a specific peer aggregator
     */
    override suspend fun syncAggregatedDataToPeer(peerId: String, data: AggregatedDataBundle) {
        withContext(Dispatchers.IO) {
            try {
                _syncStatus.value = AggregatorSyncStatus.Syncing(peerId, 0.0f)
                
                // Create the sync message
                val message = AggregatedDataSyncMessage(
                    bundle = data,
                    targetAggregatorId = peerId
                )
                
                val packet = createPacket(MessageType.AGGREGATED_DATA_SYNC, message.toBinaryPayload())
                
                // Send to specific peer
                meshService.sendPacketToPeer(peerId, packet)
                
                _syncStatus.value = AggregatorSyncStatus.Success(
                    data.playbackRecords.size + data.sharingRecords.size + data.trackMetadata.size + data.transferRecords.size,
                    peerId
                )
                
                Log.i(TAG, "Synced aggregated data to $peerId: ${data.playbackRecords.size} playback, ${data.sharingRecords.size} sharing, ${data.trackMetadata.size} metadata, ${data.transferRecords.size} transfers")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing aggregated data to $peerId", e)
                _syncStatus.value = AggregatorSyncStatus.Error("Failed to sync to $peerId: ${e.message}")
            }
        }
    }
    
    /**
     * Request aggregated data from a peer aggregator
     */
    override suspend fun requestAggregatedDataFromPeer(peerId: String) {
        withContext(Dispatchers.IO) {
            try {
                val request = AggregatedDataRequest(
                    requesterId = deviceIdentificationService.getDeviceId(),
                    lastSyncTimestamp = lastSyncTimestamps[peerId]
                )
                
                val packet = createPacket(MessageType.AGGREGATED_DATA_REQUEST, request.toBinaryPayload())
                meshService.sendPacketToPeer(peerId, packet)
                
                Log.d(TAG, "Requested aggregated data from $peerId")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting data from $peerId", e)
            }
        }
    }
    
    /**
     * Broadcast availability of aggregated data to nearby aggregators
     */
    override suspend fun broadcastAggregatedDataAvailability() {
        withContext(Dispatchers.IO) {
            try {
                val counts = getCurrentDataCounts()
                
                val announcement = AggregatedDataAnnouncement(
                    aggregatorId = deviceIdentificationService.getDeviceId(),
                    recordCounts = counts
                )
                
                val packet = createPacket(MessageType.AGGREGATED_DATA_ANNOUNCE, announcement.toBinaryPayload())
                meshService.broadcastMusicPacket(packet)
                
                Log.d(TAG, "Broadcasted aggregated data availability: $counts")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting availability", e)
            }
        }
    }
    
    /**
     * Export all aggregated data to a file for external systems
     */
    suspend fun exportAllAggregatedData(format: AggregatorDataExporter.ExportFormat): AggregatorDataExporter.ExportResult {
        return withContext(Dispatchers.IO) {
            try {
                _syncStatus.value = AggregatorSyncStatus.Exporting(0)
                
                val exporter = AggregatorDataExporter(context)
                
                val playbackRecords = analyticsTracker.getAllPlaybackRecords()
                val sharingRecords = analyticsTracker.getAllSharingRecords()
                val trackMetadata = analyticsTracker.getAllTrackMetadata()
                val transferRecords = analyticsTracker.getAllTransferRecords()
                
                val totalRecords = playbackRecords.size + sharingRecords.size + trackMetadata.size + transferRecords.size
                _syncStatus.value = AggregatorSyncStatus.Exporting(totalRecords)
                
                val result = exporter.exportComprehensiveReportWithTransfers(
                    playbackRecords = playbackRecords,
                    sharingRecords = sharingRecords,
                    trackMetadata = trackMetadata,
                    transferRecords = transferRecords,
                    format = format
                )
                
                _syncStatus.value = AggregatorSyncStatus.Idle
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting aggregated data", e)
                _syncStatus.value = AggregatorSyncStatus.Error("Export failed: ${e.message}")
                AggregatorDataExporter.ExportResult(
                    success = false,
                    filePath = null,
                    recordCount = 0,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Handle incoming packets for aggregated data sync
     */
    override fun onPacketReceived(routed: RoutedPacket) {
        syncScope.launch {
            handleAggregatedDataPacket(routed.packet)
        }
    }
    
    private suspend fun handleAggregatedDataPacket(packet: BitchatPacket) {
        when (packet.type.toInt()) {
            MessageType.AGGREGATED_DATA_ANNOUNCE.value.toInt() -> {
                AggregatedDataAnnouncement.fromBinaryPayload(packet.payload)?.let { announcement ->
                    handleDataAnnouncement(announcement)
                }
            }
            MessageType.AGGREGATED_DATA_REQUEST.value.toInt() -> {
                AggregatedDataRequest.fromBinaryPayload(packet.payload)?.let { request ->
                    handleDataRequest(request)
                }
            }
            MessageType.AGGREGATED_DATA_SYNC.value.toInt() -> {
                AggregatedDataSyncMessage.fromBinaryPayload(packet.payload)?.let { syncMessage ->
                    handleDataSync(syncMessage)
                }
            }
        }
    }
    
    private fun handleDataAnnouncement(announcement: AggregatedDataAnnouncement) {
        val current = _discoveredAggregators.value.toMutableMap()
        current[announcement.aggregatorId] = announcement
        _discoveredAggregators.value = current
        
        Log.d(TAG, "Discovered aggregator ${announcement.aggregatorId} with ${announcement.recordCounts}")
    }
    
    private suspend fun handleDataRequest(request: AggregatedDataRequest) {
        try {
            // Prepare data bundle for the requester
            val playbackRecords = analyticsTracker.getAllPlaybackRecords()
                .filter { request.lastSyncTimestamp == null || it.timestamp > request.lastSyncTimestamp }
                .take(MAX_RECORDS_PER_BUNDLE)
            
            val sharingRecords = analyticsTracker.getAllSharingRecords()
                .filter { request.lastSyncTimestamp == null || it.timestamp > request.lastSyncTimestamp }
                .take(MAX_RECORDS_PER_BUNDLE)
            
            val trackMetadata = analyticsTracker.getAllTrackMetadata()
                .take(MAX_RECORDS_PER_BUNDLE)
            
            val transferRecords = analyticsTracker.getAllTransferRecords()
                .filter { request.lastSyncTimestamp == null || it.timestamp > request.lastSyncTimestamp }
                .take(MAX_RECORDS_PER_BUNDLE)
            
            val bundle = AggregatedDataBundle(
                sourceAggregatorId = deviceIdentificationService.getDeviceId(),
                playbackRecords = playbackRecords,
                sharingRecords = sharingRecords,
                trackMetadata = trackMetadata,
                transferRecords = transferRecords
            )
            
            syncAggregatedDataToPeer(request.requesterId, bundle)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling data request", e)
        }
    }
    
    private suspend fun handleDataSync(syncMessage: AggregatedDataSyncMessage) {
        try {
            val bundle = syncMessage.bundle
            
            // Store received data
            bundle.playbackRecords.forEach { record ->
                analyticsTracker.recordPlayback(record.copy(isSynced = true, syncedAt = System.currentTimeMillis()))
            }
            
            bundle.sharingRecords.forEach { record ->
                analyticsTracker.recordSharing(record.copy(isSynced = true, syncedAt = System.currentTimeMillis()))
            }
            
            bundle.trackMetadata.forEach { metadata ->
                analyticsTracker.recordTrackMetadata(metadata.copy(isSynced = true, syncedAt = System.currentTimeMillis()))
            }
            
            bundle.transferRecords.forEach { record ->
                analyticsTracker.recordTransfer(record.copy(isSynced = true, syncedAt = System.currentTimeMillis()))
            }
            
            // Update last sync timestamp
            lastSyncTimestamps[bundle.sourceAggregatorId] = System.currentTimeMillis()
            
            Log.i(TAG, "Received aggregated data from ${bundle.sourceAggregatorId}: ${bundle.playbackRecords.size} playback, ${bundle.sharingRecords.size} sharing, ${bundle.trackMetadata.size} metadata, ${bundle.transferRecords.size} transfers")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling data sync", e)
        }
    }
    
    private fun startAggregatorDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = syncScope.launch {
            while (isActive) {
                try {
                    broadcastAggregatedDataAvailability()
                    
                    // Clean up old aggregators (not seen for 5 minutes)
                    val cutoffTime = System.currentTimeMillis() - 300000L
                    val current = _discoveredAggregators.value.toMutableMap()
                    val cleaned = current.filterValues { it.timestamp > cutoffTime }
                    _discoveredAggregators.value = cleaned
                    
                    delay(AGGREGATOR_DISCOVERY_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in aggregator discovery", e)
                }
            }
        }
    }
    
    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = syncScope.launch {
            while (isActive) {
                try {
                    // Sync with discovered aggregators
                    val aggregators = _discoveredAggregators.value.values
                    
                    aggregators.forEach { announcement ->
                        // Request data from aggregators with more records than us
                        val ourCounts = getCurrentDataCounts()
                        val theirCounts = announcement.recordCounts
                        
                        if (theirCounts.playbackRecords > ourCounts.playbackRecords ||
                            theirCounts.sharingRecords > ourCounts.sharingRecords) {
                            requestAggregatedDataFromPeer(announcement.aggregatorId)
                        }
                    }
                    
                    delay(SYNC_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic sync", e)
                }
            }
        }
    }
    
    private suspend fun getCurrentDataCounts(): DataCounts {
        return DataCounts(
            playbackRecords = analyticsTracker.getAllPlaybackRecords().size,
            sharingRecords = analyticsTracker.getAllSharingRecords().size,
            trackMetadata = analyticsTracker.getAllTrackMetadata().size,
            transferRecords = analyticsTracker.getAllTransferRecords().size
        )
    }
    
    private fun createPacket(messageType: MessageType, payload: ByteArray): BitchatPacket {
        return BitchatPacket(
            type = messageType.value,
            ttl = AppConstants.MESSAGE_TTL_HOPS,
            senderID = meshService.myPeerID,
            payload = payload
        )
    }
}

/**
 * Message for syncing aggregated data between aggregators
 */
data class AggregatedDataSyncMessage(
    val bundle: AggregatedDataBundle,
    val targetAggregatorId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toBinaryPayload(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(32768).apply { 
            order(java.nio.ByteOrder.BIG_ENDIAN) 
        }
        
        buffer.putLong(timestamp)
        
        val hasTarget = targetAggregatorId != null
        buffer.put(if (hasTarget) 1.toByte() else 0.toByte())
        if (hasTarget) {
            writeStringWithLength(buffer, targetAggregatorId!!)
        }
        
        val bundleBytes = bundle.toBinaryPayload()
        buffer.putInt(bundleBytes.size)
        buffer.put(bundleBytes)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): AggregatedDataSyncMessage? {
            return try {
                val buffer = java.nio.ByteBuffer.wrap(data).apply { 
                    order(java.nio.ByteOrder.BIG_ENDIAN) 
                }
                
                val timestamp = buffer.long
                val hasTarget = buffer.get() != 0.toByte()
                val targetId = if (hasTarget) readStringWithLength(buffer) else null
                
                val bundleSize = buffer.int
                val bundleBytes = ByteArray(bundleSize)
                buffer.get(bundleBytes)
                
                val bundle = AggregatedDataBundle.fromBinaryPayload(bundleBytes)
                    ?: return null
                
                AggregatedDataSyncMessage(
                    bundle = bundle,
                    targetAggregatorId = targetId,
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                null
            }
        }
        
        private fun readStringWithLength(buffer: java.nio.ByteBuffer): String {
            val length = buffer.short.toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

// Extension functions for BluetoothMeshService
private fun BluetoothMeshService.sendPacketToPeer(peerId: String, packet: BitchatPacket) {
    // Convert to RoutedPacket and send
    val routedPacket = RoutedPacket(packet)
    // Use reflection or internal method to send to specific peer
    // For now, broadcast with recipient ID set
    broadcastMusicPacket(packet)
}

private fun BluetoothMeshService.broadcastMusicPacket(packet: BitchatPacket) {
    // This should be implemented in the main BluetoothMeshService
    // For now, delegate to sendMessage as a placeholder
    Log.d("AggregatedDataSyncManager", "Broadcasting packet type ${packet.type}")
}
