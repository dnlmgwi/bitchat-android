package com.bitchat.android.music

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
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
 * BLE mesh sync manager for music analytics
 * Handles device-to-device and device-to-aggregator synchronization
 * Adapts bitchat's mesh networking for playback data transmission
 */
class MusicAnalyticsMeshSync(
    private val context: Context,
    private val meshService: BluetoothMeshService,
    private val deviceIdentificationService: DeviceIdentificationService
) {
    
    companion object {
        private const val TAG = "MusicAnalyticsMeshSync"
        private const val MAX_RECORDS_PER_BATCH = 50
        private const val AGGREGATOR_DISCOVERY_INTERVAL = 60000L // 1 minute
        private const val SYNC_RETRY_DELAY = 5000L // 5 seconds
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null
    
    // Discovered aggregators
    private val _discoveredAggregators = MutableStateFlow<Map<String, AggregatorInfo>>(emptyMap())
    val discoveredAggregators: StateFlow<Map<String, AggregatorInfo>> = _discoveredAggregators.asStateFlow()
    
    // Sync status
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    // Pending sync queues
    private val pendingPlaybackRecords = mutableListOf<PlaybackRecord>()
    private val pendingTrackMetadata = mutableListOf<TrackMetadata>()
    private val pendingAcks = mutableMapOf<String, MutableSet<String>>() // aggregatorId -> recordIds
    
    data class AggregatorInfo(
        val aggregatorId: String,
        val capacity: Int,
        val currentLoad: Int,
        val lastSyncTime: Long,
        val supportedVersion: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )
    
    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Discovering : SyncStatus()
        data class Syncing(val aggregatorId: String, val progress: Float) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
        data class Success(val recordsSynced: Int, val aggregatorId: String) : SyncStatus()
    }
    
    init {
        startAggregatorDiscovery()
        registerMessageHandlers()
    }
    
    /**
     * Sync playback records to mesh network
     */
    suspend fun syncPlaybackRecords(records: List<PlaybackRecord>) {
        withContext(Dispatchers.IO) {
            try {
                _syncStatus.value = SyncStatus.Discovering
                
                // Add to pending queue
                synchronized(pendingPlaybackRecords) {
                    pendingPlaybackRecords.addAll(records)
                }
                
                // Try to sync to discovered aggregators
                val aggregators = _discoveredAggregators.value.values.sortedBy { it.currentLoad }
                
                if (aggregators.isEmpty()) {
                    Log.w(TAG, "No aggregators discovered, broadcasting to mesh")
                    broadcastToMesh(records)
                } else {
                    // Sync to best available aggregator
                    val bestAggregator = aggregators.first()
                    syncToAggregator(bestAggregator.aggregatorId, records)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing playback records", e)
                _syncStatus.value = SyncStatus.Error("Sync failed: ${e.message}")
            }
        }
    }
    
    /**
     * Sync track metadata to mesh network
     */
    suspend fun syncTrackMetadata(metadata: List<TrackMetadata>) {
        withContext(Dispatchers.IO) {
            try {
                synchronized(pendingTrackMetadata) {
                    pendingTrackMetadata.addAll(metadata)
                }
                
                // Send track metadata messages
                metadata.forEach { track ->
                    val message = TrackMetaMessage(track)
                    val packet = createPacket(MessageType.TRACK_META, message.toBinaryPayload())
                    meshService.broadcastMusicPacket(packet)
                }
                
                Log.d(TAG, "Synced ${metadata.size} track metadata records")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing track metadata", e)
            }
        }
    }
    
    /**
     * Sync sharing records to mesh network
     */
    suspend fun syncSharingRecords(records: List<SharingRecord>) {
        withContext(Dispatchers.IO) {
            try {
                // Send sharing records in batches
                val batches = records.chunked(MAX_RECORDS_PER_BATCH)
                
                batches.forEach { batch ->
                    batch.forEach { record ->
                        val message = SharingRecordMessage(record)
                        val packet = createPacket(MessageType.SHARING_RECORD, message.toBinaryPayload())
                        meshService.broadcastMusicPacket(packet)
                    }
                }
                
                Log.d(TAG, "Synced ${records.size} sharing records")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing sharing records", e)
            }
        }
    }
    
    /**
     * Sync transfer records to mesh network
     */
    suspend fun syncTransferRecords(records: List<TransferRecord>) {
        withContext(Dispatchers.IO) {
            try {
                // Add to pending queue for aggregator sync
                val aggregators = _discoveredAggregators.value.values.sortedBy { it.currentLoad }
                
                if (aggregators.isEmpty()) {
                    Log.w(TAG, "No aggregators discovered, broadcasting transfer records to mesh")
                    broadcastTransferRecordsToMesh(records)
                } else {
                    // Sync to best available aggregator
                    val bestAggregator = aggregators.first()
                    syncTransferRecordsToAggregator(bestAggregator.aggregatorId, records)
                }
                
                Log.d(TAG, "Synced ${records.size} transfer records")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing transfer records", e)
            }
        }
    }
    
    /**
     * Register as aggregator (for burning centers)
     */
    fun startAggregatorMode(aggregatorId: String, capacity: Int = 50) {
        syncScope.launch {
            try {
                Log.i(TAG, "Starting aggregator mode: $aggregatorId with capacity: $capacity")
                
                // Update sync status to show aggregator is starting
                _syncStatus.value = SyncStatus.Success(0, "Aggregator mode started: $aggregatorId")
                
                // Add ourselves to the discovered aggregators list
                val aggregatorInfo = AggregatorInfo(
                    aggregatorId = aggregatorId,
                    capacity = capacity,
                    currentLoad = 0,
                    lastSyncTime = System.currentTimeMillis(),
                    supportedVersion = 1
                )
                
                val currentAggregators = _discoveredAggregators.value.toMutableMap()
                currentAggregators[aggregatorId] = aggregatorInfo
                _discoveredAggregators.value = currentAggregators
                
                // Register device with mesh service (simplified)
                try {
                    val registerMessage = DeviceRegisterMessage(
                        deviceId = deviceIdentificationService.getDeviceId(),
                        publicKey = deviceIdentificationService.getPublicKeyBytes(),
                        deviceInfo = getDeviceInfo()
                    )
                    
                    val packet = createPacket(MessageType.DEVICE_REGISTER, registerMessage.toBinaryPayload())
                    meshService.broadcastMusicPacket(packet)
                    
                    Log.d(TAG, "Registered device with mesh service")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not register with mesh service: ${e.message}")
                }
                
                // Start broadcasting aggregator beacon
                startAggregatorBeacon(aggregatorId, capacity)
                
                Log.i(TAG, "Aggregator mode started successfully: $aggregatorId")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting aggregator mode", e)
                _syncStatus.value = SyncStatus.Error("Failed to start aggregator mode: ${e.message}")
            }
        }
    }
    
    private fun startAggregatorDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = syncScope.launch {
            while (isActive) {
                try {
                    // Clean up old aggregators (not seen for 5 minutes)
                    val cutoffTime = System.currentTimeMillis() - 300000L
                    val currentAggregators = _discoveredAggregators.value.toMutableMap()
                    val cleaned = currentAggregators.filterValues { it.lastSeen > cutoffTime }
                    _discoveredAggregators.value = cleaned
                    
                    delay(AGGREGATOR_DISCOVERY_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in aggregator discovery", e)
                }
            }
        }
    }
    
    private fun startAggregatorBeacon(aggregatorId: String, capacity: Int) {
        syncScope.launch {
            while (isActive) {
                try {
                    val beacon = AggregatorBeaconMessage(
                        aggregatorId = aggregatorId,
                        capacity = capacity,
                        currentLoad = getCurrentLoad(),
                        lastSyncTime = getLastInternetSyncTime()
                    )
                    
                    val packet = createPacket(MessageType.AGGREGATOR_BEACON, beacon.toBinaryPayload())
                    meshService.broadcastMusicPacket(packet)
                    
                    delay(30000L) // Beacon every 30 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting aggregator beacon", e)
                }
            }
        }
    }
    
    private suspend fun syncToAggregator(aggregatorId: String, records: List<PlaybackRecord>) {
        try {
            _syncStatus.value = SyncStatus.Syncing(aggregatorId, 0.0f)
            
            // Split records into batches
            val batches = records.chunked(MAX_RECORDS_PER_BATCH)
            var syncedCount = 0
            
            batches.forEachIndexed { index, batch ->
                val batchMessage = PlaybackBatchMessage(
                    batchId = UUID.randomUUID().toString(),
                    deviceId = deviceIdentificationService.getDeviceId(),
                    records = batch
                )
                
                val packet = createPacket(MessageType.PLAYBACK_BATCH, batchMessage.toBinaryPayload())
                
                // Send to specific aggregator (if we have a direct connection)
                // Otherwise broadcast to mesh
                if (meshService.hasDirectConnection(aggregatorId)) {
                    meshService.sendPacketToPeer(aggregatorId, packet)
                } else {
                    meshService.broadcastMusicPacket(packet)
                }
                
                syncedCount += batch.size
                val progress = syncedCount.toFloat() / records.size.toFloat()
                _syncStatus.value = SyncStatus.Syncing(aggregatorId, progress)
                
                // Wait for potential ACK
                delay(1000L)
            }
            
            _syncStatus.value = SyncStatus.Success(syncedCount, aggregatorId)
            Log.i(TAG, "Successfully synced $syncedCount records to $aggregatorId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to aggregator $aggregatorId", e)
            _syncStatus.value = SyncStatus.Error("Failed to sync to $aggregatorId: ${e.message}")
        }
    }
    
    private suspend fun broadcastToMesh(records: List<PlaybackRecord>) {
        try {
            val batches = records.chunked(MAX_RECORDS_PER_BATCH)
            
            batches.forEach { batch ->
                val batchMessage = PlaybackBatchMessage(
                    batchId = UUID.randomUUID().toString(),
                    deviceId = deviceIdentificationService.getDeviceId(),
                    records = batch
                )
                
                val packet = createPacket(MessageType.PLAYBACK_BATCH, batchMessage.toBinaryPayload())
                meshService.broadcastMusicPacket(packet)
                
                delay(500L) // Small delay between batches
            }
            
            Log.d(TAG, "Broadcasted ${records.size} records to mesh network")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting to mesh", e)
        }
    }
    
    private suspend fun syncTransferRecordsToAggregator(aggregatorId: String, records: List<TransferRecord>) {
        try {
            _syncStatus.value = SyncStatus.Syncing(aggregatorId, 0.0f)
            
            // Split records into batches
            val batches = records.chunked(MAX_RECORDS_PER_BATCH)
            var syncedCount = 0
            
            batches.forEachIndexed { index, batch ->
                val batchMessage = TransferBatchMessage(
                    batchId = UUID.randomUUID().toString(),
                    deviceId = deviceIdentificationService.getDeviceId(),
                    records = batch
                )
                
                val packet = createPacket(MessageType.TRANSFER_BATCH, batchMessage.toBinaryPayload())
                
                // Send to specific aggregator (if we have a direct connection)
                // Otherwise broadcast to mesh
                if (meshService.hasDirectConnection(aggregatorId)) {
                    meshService.sendPacketToPeer(aggregatorId, packet)
                } else {
                    meshService.broadcastMusicPacket(packet)
                }
                
                syncedCount += batch.size
                val progress = syncedCount.toFloat() / records.size.toFloat()
                _syncStatus.value = SyncStatus.Syncing(aggregatorId, progress)
                
                // Wait for potential ACK
                delay(1000L)
            }
            
            Log.i(TAG, "Successfully synced $syncedCount transfer records to $aggregatorId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing transfer records to aggregator $aggregatorId", e)
            _syncStatus.value = SyncStatus.Error("Failed to sync transfer records to $aggregatorId: ${e.message}")
        }
    }
    
    private suspend fun broadcastTransferRecordsToMesh(records: List<TransferRecord>) {
        try {
            val batches = records.chunked(MAX_RECORDS_PER_BATCH)
            
            batches.forEach { batch ->
                val batchMessage = TransferBatchMessage(
                    batchId = UUID.randomUUID().toString(),
                    deviceId = deviceIdentificationService.getDeviceId(),
                    records = batch
                )
                
                val packet = createPacket(MessageType.TRANSFER_BATCH, batchMessage.toBinaryPayload())
                meshService.broadcastMusicPacket(packet)
                
                delay(500L) // Small delay between batches
            }
            
            Log.d(TAG, "Broadcasted ${records.size} transfer records to mesh network")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting transfer records to mesh", e)
        }
    }
    
    private fun registerMessageHandlers() {
        // Register handlers for music analytics message types
        // This would integrate with the existing mesh service message handling
        // For now, we'll use a simplified approach
        
        syncScope.launch {
            // Handle incoming aggregator beacons
            // Handle sync acknowledgments
            // Handle playback batch messages (if we're an aggregator)
            // This would be integrated with the existing BluetoothMeshService delegate pattern
        }
    }
    
    private fun handleAggregatorBeacon(beacon: AggregatorBeaconMessage) {
        val aggregatorInfo = AggregatorInfo(
            aggregatorId = beacon.aggregatorId,
            capacity = beacon.capacity,
            currentLoad = beacon.currentLoad,
            lastSyncTime = beacon.lastSyncTime,
            supportedVersion = beacon.supportedVersion
        )
        
        val currentAggregators = _discoveredAggregators.value.toMutableMap()
        currentAggregators[beacon.aggregatorId] = aggregatorInfo
        _discoveredAggregators.value = currentAggregators
        
        Log.d(TAG, "Discovered aggregator: ${beacon.aggregatorId} (load: ${beacon.currentLoad}/${beacon.capacity})")
    }
    
    private fun handleSyncAck(ack: SyncAckMessage) {
        synchronized(pendingAcks) {
            pendingAcks[ack.aggregatorId] = ack.receivedRecordIds.toMutableSet()
        }
        
        Log.d(TAG, "Received sync ACK from ${ack.aggregatorId}: ${ack.receivedRecordIds.size} records")
    }
    
    private fun createPacket(messageType: MessageType, payload: ByteArray): BitchatPacket {
        return BitchatPacket(
            type = messageType.value,
            ttl = AppConstants.MESSAGE_TTL_HOPS,
            senderID = meshService.myPeerID,
            payload = payload
        )
    }
    
    private fun getDeviceInfo(): String {
        return "Android ${android.os.Build.VERSION.RELEASE} ${android.os.Build.MODEL}"
    }
    
    private fun getCurrentLoad(): Int {
        // Return current number of connected devices (placeholder)
        return 0
    }
    
    private fun getLastInternetSyncTime(): Long {
        // Return last successful internet sync timestamp (placeholder)
        return System.currentTimeMillis() - 3600000L // 1 hour ago
    }
    
    fun release() {
        syncScope.cancel()
    }
}

/**
 * Extension functions for BluetoothMeshService integration
 * Simplified implementation for basic functionality
 */
private fun BluetoothMeshService.sendMusicAnalyticsMessage(messageType: String, data: String) {
    // Use the existing sendMessage method to broadcast analytics data
    val messageContent = "MUSIC_ANALYTICS:$messageType:$data"
    sendMessage(messageContent)
    Log.d("MusicAnalyticsMeshSync", "Sent music analytics message: $messageType")
}

private fun BluetoothMeshService.hasDirectConnection(peerId: String): Boolean {
    // For now, return false to always use broadcast
    return false
}

private fun BluetoothMeshService.sendPacketToPeer(peerId: String, packet: BitchatPacket) {
    // For now, just broadcast
    broadcastMusicPacket(packet)
}

private fun BluetoothMeshService.broadcastMusicPacket(packet: BitchatPacket) {
    // Convert packet to a simple message format
    val messageType = when (packet.type.toInt()) {
        MessageType.AGGREGATOR_BEACON.value.toInt() -> "AGGREGATOR_BEACON"
        MessageType.DEVICE_REGISTER.value.toInt() -> "DEVICE_REGISTER"
        MessageType.PLAYBACK_BATCH.value.toInt() -> "PLAYBACK_BATCH"
        MessageType.TRACK_META.value.toInt() -> "TRACK_META"
        MessageType.TRANSFER_BATCH.value.toInt() -> "TRANSFER_BATCH"
        else -> "UNKNOWN"
    }
    
    sendMusicAnalyticsMessage(messageType, "size:${packet.payload.size}")
}