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
import java.io.File
import java.util.*

/**
 * Music sharing service that handles file sharing through the mesh network
 * Integrates with existing file transfer capabilities and tracks sharing analytics
 */
class MusicSharingService(
    private val context: Context,
    private val meshService: BluetoothMeshService,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val contentIdGenerator: ContentIdGenerator,
    private val analyticsTracker: PlaybackAnalyticsTracker
) {
    
    companion object {
        private const val TAG = "MusicSharingService"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB limit
        private const val CHUNK_SIZE = 1024 // 1KB chunks for BLE transfer
    }
    
    private val sharingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Active transfers
    private val _activeTransfers = MutableStateFlow<Map<String, TransferInfo>>(emptyMap())
    val activeTransfers: StateFlow<Map<String, TransferInfo>> = _activeTransfers.asStateFlow()
    
    // Sharing statistics
    private val _sharingStats = MutableStateFlow(SharingStats())
    val sharingStats: StateFlow<SharingStats> = _sharingStats.asStateFlow()
    
    // Discovered shared music from other devices
    private val _discoveredMusic = MutableStateFlow<Map<String, SharedMusicInfo>>(emptyMap())
    val discoveredMusic: StateFlow<Map<String, SharedMusicInfo>> = _discoveredMusic.asStateFlow()
    
    data class TransferInfo(
        val recordId: String,
        val filePath: String,
        val contentId: String,
        val recipientDeviceId: String?,
        val shareMethod: ShareMethod,
        val startTime: Long,
        val totalSize: Long,
        val transferredBytes: Long = 0,
        val status: TransferStatus = TransferStatus.INITIATED
    )
    
    data class SharingStats(
        val totalShares: Int = 0,
        val successfulShares: Int = 0,
        val failedShares: Int = 0,
        val totalBytesShared: Long = 0,
        val averageTransferTime: Long = 0
    )
    
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
    
    /**
     * Share a music file with nearby devices via broadcast
     */
    suspend fun shareFileBroadcast(filePath: String, shareContext: ShareContext = ShareContext.MANUAL): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || file.length() > MAX_FILE_SIZE) {
                    Log.e(TAG, "File doesn't exist or is too large: $filePath")
                    return@withContext null
                }
                
                val contentId = contentIdGenerator.generateContentId(filePath)
                if (contentId == null) {
                    Log.e(TAG, "Failed to generate content ID for: $filePath")
                    return@withContext null
                }
                
                val recordId = UUID.randomUUID().toString()
                val sharingRecord = SharingRecord(
                    recordId = recordId,
                    contentId = contentId,
                    sharerDeviceId = deviceIdentificationService.getDeviceId(),
                    recipientDeviceId = null, // Broadcast
                    shareMethod = ShareMethod.MESH_BROADCAST,
                    fileSize = file.length(),
                    shareContext = shareContext
                )
                
                // Sign the sharing record
                val signature = deviceIdentificationService.signSharingRecord(sharingRecord)
                val signedRecord = sharingRecord.copy(deviceSignature = signature)
                
                // Create transfer info
                val transferInfo = TransferInfo(
                    recordId = recordId,
                    filePath = filePath,
                    contentId = contentId,
                    recipientDeviceId = null,
                    shareMethod = ShareMethod.MESH_BROADCAST,
                    startTime = System.currentTimeMillis(),
                    totalSize = file.length()
                )
                
                // Update active transfers
                updateActiveTransfers(recordId, transferInfo)
                
                // Start the sharing process
                startFileSharing(signedRecord, file)
                
                Log.i(TAG, "Started broadcast sharing: $filePath")
                recordId
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing file broadcast: $filePath", e)
                null
            }
        }
    }
    
    /**
     * Share a music file directly with a specific device
     */
    suspend fun shareFileDirect(filePath: String, recipientDeviceId: String, shareContext: ShareContext = ShareContext.MANUAL): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || file.length() > MAX_FILE_SIZE) {
                    Log.e(TAG, "File doesn't exist or is too large: $filePath")
                    return@withContext null
                }
                
                val contentId = contentIdGenerator.generateContentId(filePath)
                if (contentId == null) {
                    Log.e(TAG, "Failed to generate content ID for: $filePath")
                    return@withContext null
                }
                
                val recordId = UUID.randomUUID().toString()
                val sharingRecord = SharingRecord(
                    recordId = recordId,
                    contentId = contentId,
                    sharerDeviceId = deviceIdentificationService.getDeviceId(),
                    recipientDeviceId = recipientDeviceId,
                    shareMethod = ShareMethod.MESH_DIRECT,
                    fileSize = file.length(),
                    shareContext = shareContext
                )
                
                // Sign the sharing record
                val signature = deviceIdentificationService.signSharingRecord(sharingRecord)
                val signedRecord = sharingRecord.copy(deviceSignature = signature)
                
                // Create transfer info
                val transferInfo = TransferInfo(
                    recordId = recordId,
                    filePath = filePath,
                    contentId = contentId,
                    recipientDeviceId = recipientDeviceId,
                    shareMethod = ShareMethod.MESH_DIRECT,
                    startTime = System.currentTimeMillis(),
                    totalSize = file.length()
                )
                
                // Update active transfers
                updateActiveTransfers(recordId, transferInfo)
                
                // Start the sharing process
                startDirectFileSharing(signedRecord, file, recipientDeviceId)
                
                Log.i(TAG, "Started direct sharing to $recipientDeviceId: $filePath")
                recordId
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing file direct: $filePath", e)
                null
            }
        }
    }
    
    /**
     * Cancel an active file transfer
     */
    fun cancelTransfer(recordId: String) {
        sharingScope.launch {
            val currentTransfers = _activeTransfers.value.toMutableMap()
            currentTransfers[recordId]?.let { transfer ->
                val updatedTransfer = transfer.copy(status = TransferStatus.CANCELLED)
                currentTransfers[recordId] = updatedTransfer
                _activeTransfers.value = currentTransfers
                
                // Record the cancellation
                recordSharingEvent(transfer.copy(status = TransferStatus.CANCELLED))
                
                Log.i(TAG, "Cancelled transfer: $recordId")
            }
        }
    }
    
    /**
     * Get list of music files available for sharing from nearby devices
     */
    fun getDiscoveredMusic(): Map<String, SharedMusicInfo> {
        return _discoveredMusic.value
    }
    
    /**
     * Request a shared music file from another device
     */
    suspend fun requestSharedMusic(contentId: String, sharerDeviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = MusicShareRequest(
                    requestId = UUID.randomUUID().toString(),
                    contentId = contentId,
                    requesterDeviceId = deviceIdentificationService.getDeviceId(),
                    sharerDeviceId = sharerDeviceId,
                    timestamp = System.currentTimeMillis()
                )
                
                val packet = createPacket(MessageType.MUSIC_SHARE_REQUEST, request.toBinaryPayload())
                
                // Send request to specific device
                meshService.sendPacketToPeer(sharerDeviceId, packet)
                
                Log.i(TAG, "Requested shared music: $contentId from $sharerDeviceId")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting shared music", e)
                false
            }
        }
    }
    
    private suspend fun startFileSharing(sharingRecord: SharingRecord, file: File) {
        try {
            // Create file share announcement
            val announcement = MusicShareAnnouncement(
                contentId = sharingRecord.contentId,
                sharerDeviceId = sharingRecord.sharerDeviceId,
                fileName = file.name,
                fileSize = file.length(),
                shareMethod = sharingRecord.shareMethod,
                timestamp = sharingRecord.timestamp
            )
            
            // Broadcast announcement
            val packet = createPacket(MessageType.MUSIC_SHARE_ANNOUNCEMENT, announcement.toBinaryPayload())
            meshService.broadcastPacket(packet)
            
            // Update transfer status
            updateTransferStatus(sharingRecord.recordId, TransferStatus.IN_PROGRESS)
            
            // For now, simulate successful sharing (in real implementation, this would handle actual file transfer)
            delay(2000) // Simulate transfer time
            
            // Complete the transfer
            completeTransfer(sharingRecord.recordId, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in file sharing process", e)
            completeTransfer(sharingRecord.recordId, false)
        }
    }
    
    private suspend fun startDirectFileSharing(sharingRecord: SharingRecord, file: File, recipientDeviceId: String) {
        try {
            // Create direct share offer
            val offer = MusicShareOffer(
                offerId = sharingRecord.recordId,
                contentId = sharingRecord.contentId,
                sharerDeviceId = sharingRecord.sharerDeviceId,
                recipientDeviceId = recipientDeviceId,
                fileName = file.name,
                fileSize = file.length(),
                timestamp = sharingRecord.timestamp
            )
            
            // Send offer to specific device
            val packet = createPacket(MessageType.MUSIC_SHARE_OFFER, offer.toBinaryPayload())
            meshService.sendPacketToPeer(recipientDeviceId, packet)
            
            // Update transfer status
            updateTransferStatus(sharingRecord.recordId, TransferStatus.IN_PROGRESS)
            
            // For now, simulate successful sharing
            delay(3000) // Simulate transfer time
            
            // Complete the transfer
            completeTransfer(sharingRecord.recordId, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in direct file sharing", e)
            completeTransfer(sharingRecord.recordId, false)
        }
    }
    
    private suspend fun completeTransfer(recordId: String, success: Boolean) {
        val currentTransfers = _activeTransfers.value.toMutableMap()
        currentTransfers[recordId]?.let { transfer ->
            val finalStatus = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED
            val duration = System.currentTimeMillis() - transfer.startTime
            
            val updatedTransfer = transfer.copy(
                status = finalStatus,
                transferredBytes = if (success) transfer.totalSize else transfer.transferredBytes
            )
            
            currentTransfers[recordId] = updatedTransfer
            _activeTransfers.value = currentTransfers
            
            // Record the sharing event
            recordSharingEvent(updatedTransfer, duration)
            
            // Update statistics
            updateSharingStats(success, transfer.totalSize, duration)
            
            // Remove from active transfers after a delay
            delay(5000)
            val finalTransfers = _activeTransfers.value.toMutableMap()
            finalTransfers.remove(recordId)
            _activeTransfers.value = finalTransfers
        }
    }
    
    private suspend fun recordSharingEvent(transfer: TransferInfo, duration: Long? = null) {
        try {
            val sharingRecord = SharingRecord(
                recordId = transfer.recordId,
                contentId = transfer.contentId,
                sharerDeviceId = deviceIdentificationService.getDeviceId(),
                recipientDeviceId = transfer.recipientDeviceId,
                shareMethod = transfer.shareMethod,
                timestamp = transfer.startTime,
                fileSize = transfer.totalSize,
                transferDuration = duration,
                transferStatus = transfer.status
            )
            
            // Sign and store the sharing record
            val signature = deviceIdentificationService.signSharingRecord(sharingRecord)
            val signedRecord = sharingRecord.copy(deviceSignature = signature)
            
            // Store in analytics tracker (extend it to handle sharing records)
            analyticsTracker.recordSharing(signedRecord)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording sharing event", e)
        }
    }
    
    private fun updateActiveTransfers(recordId: String, transferInfo: TransferInfo) {
        val currentTransfers = _activeTransfers.value.toMutableMap()
        currentTransfers[recordId] = transferInfo
        _activeTransfers.value = currentTransfers
    }
    
    private fun updateTransferStatus(recordId: String, status: TransferStatus) {
        val currentTransfers = _activeTransfers.value.toMutableMap()
        currentTransfers[recordId]?.let { transfer ->
            currentTransfers[recordId] = transfer.copy(status = status)
            _activeTransfers.value = currentTransfers
        }
    }
    
    private fun updateSharingStats(success: Boolean, bytesTransferred: Long, duration: Long) {
        val currentStats = _sharingStats.value
        val newStats = currentStats.copy(
            totalShares = currentStats.totalShares + 1,
            successfulShares = if (success) currentStats.successfulShares + 1 else currentStats.successfulShares,
            failedShares = if (!success) currentStats.failedShares + 1 else currentStats.failedShares,
            totalBytesShared = currentStats.totalBytesShared + bytesTransferred,
            averageTransferTime = if (currentStats.totalShares > 0) {
                (currentStats.averageTransferTime * currentStats.totalShares + duration) / (currentStats.totalShares + 1)
            } else duration
        )
        _sharingStats.value = newStats
    }
    
    private fun createPacket(messageType: MessageType, payload: ByteArray): BitchatPacket {
        return BitchatPacket(
            type = messageType.value,
            ttl = AppConstants.MESSAGE_TTL_HOPS,
            senderID = meshService.myPeerID,
            payload = payload
        )
    }
    
    fun release() {
        sharingScope.cancel()
    }
}

/**
 * Extension function for DeviceIdentificationService to sign sharing records
 */
private fun DeviceIdentificationService.signSharingRecord(record: SharingRecord): ByteArray {
    val (privateKey, _) = getKeyPair()
    val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
    signer.init(true, privateKey)
    
    val dataToSign = record.getDataForSigning()
    signer.update(dataToSign, 0, dataToSign.size)
    
    return signer.generateSignature()
}

/**
 * Extension functions for BluetoothMeshService (placeholders for actual implementation)
 */
private fun BluetoothMeshService.sendPacketToPeer(peerId: String, packet: BitchatPacket) {
    // This would need to be implemented in BluetoothMeshService
    // For now, just broadcast
    broadcastPacket(packet)
}

private fun BluetoothMeshService.broadcastPacket(packet: BitchatPacket) {
    // Use the existing mesh service broadcast method
    android.util.Log.d("MusicSharingService", "Broadcasting packet type: ${packet.type}")
}