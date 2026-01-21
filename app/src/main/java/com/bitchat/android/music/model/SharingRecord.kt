package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Sharing record data model for tracking music file sharing events
 * Captures when users share music files through the mesh network
 */
@Parcelize
data class SharingRecord(
    val recordId: String = UUID.randomUUID().toString(),
    val contentId: String, // 32-char SHA256 hash of shared track
    val sharerDeviceId: String, // Device that initiated the share
    val recipientDeviceId: String?, // Target device (null for broadcast)
    val shareMethod: ShareMethod,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long, // Size of shared file in bytes
    val transferDuration: Long? = null, // Time taken for transfer (if completed)
    val transferStatus: com.bitchat.android.music.model.TransferStatus = com.bitchat.android.music.model.TransferStatus.INITIATED,
    val shareContext: ShareContext = ShareContext.MANUAL,
    val deviceSignature: ByteArray? = null // Ed25519 signature for verification
) : Parcelable {

    /**
     * Convert sharing record to binary payload for BLE transmission
     */
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        // Flags byte for optional fields
        var flags: UByte = 0u
        if (recipientDeviceId != null) flags = flags or 0x01u
        if (transferDuration != null) flags = flags or 0x02u
        if (deviceSignature != null) flags = flags or 0x04u
        
        buffer.put(flags.toByte())
        
        // Core fields
        buffer.putLong(timestamp)
        buffer.putLong(fileSize)
        buffer.put(shareMethod.value.toByte())
        buffer.put(transferStatus.value.toByte())
        buffer.put(shareContext.value.toByte())
        
        // Variable length fields
        writeStringWithLength(buffer, recordId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, sharerDeviceId)
        
        // Optional fields
        if (recipientDeviceId != null) {
            writeStringWithLength(buffer, recipientDeviceId)
        }
        
        if (transferDuration != null) {
            buffer.putLong(transferDuration)
        }
        
        if (deviceSignature != null) {
            buffer.putShort(deviceSignature.size.toShort())
            buffer.put(deviceSignature)
        }
        
        return getUsedBytes(buffer)
    }
    
    private fun writeStringWithLength(buffer: ByteBuffer, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        buffer.putShort(bytes.size.toShort())
        buffer.put(bytes)
    }
    
    private fun getUsedBytes(buffer: ByteBuffer): ByteArray {
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }
    
    /**
     * Create data for signing (excludes signature field)
     */
    fun getDataForSigning(): ByteArray {
        return "$recordId$contentId$sharerDeviceId$timestamp$fileSize".toByteArray(Charsets.UTF_8)
    }
    
    companion object {
        /**
         * Parse sharing record from binary payload
         */
        fun fromBinaryPayload(data: ByteArray): SharingRecord? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val flags = buffer.get().toUByte()
                val hasRecipient = (flags and 0x01u) != 0u.toUByte()
                val hasDuration = (flags and 0x02u) != 0u.toUByte()
                val hasSignature = (flags and 0x04u) != 0u.toUByte()
                
                // Core fields
                val timestamp = buffer.long
                val fileSize = buffer.long
                val shareMethod = ShareMethod.fromValue(buffer.get().toUByte()) ?: ShareMethod.MESH_BROADCAST
                val transferStatus = TransferStatus.fromValue(buffer.get().toInt()) ?: TransferStatus.INITIATED
                val shareContext = ShareContext.fromValue(buffer.get().toUByte()) ?: ShareContext.MANUAL
                
                // Variable length fields
                val recordId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val sharerDeviceId = readStringWithLength(buffer)
                
                // Optional fields
                val recipientDeviceId = if (hasRecipient) readStringWithLength(buffer) else null
                val transferDuration = if (hasDuration) buffer.long else null
                
                val signature = if (hasSignature) {
                    val sigLength = buffer.short.toInt()
                    ByteArray(sigLength).also { buffer.get(it) }
                } else null
                
                SharingRecord(
                    recordId = recordId,
                    contentId = contentId,
                    sharerDeviceId = sharerDeviceId,
                    recipientDeviceId = recipientDeviceId,
                    shareMethod = shareMethod,
                    timestamp = timestamp,
                    fileSize = fileSize,
                    transferDuration = transferDuration,
                    transferStatus = transferStatus,
                    shareContext = shareContext,
                    deviceSignature = signature
                )
            } catch (e: Exception) {
                null
            }
        }
        
        private fun readStringWithLength(buffer: ByteBuffer): String {
            val length = buffer.short.toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

/**
 * Method used for sharing music files
 */
enum class ShareMethod(val value: UByte) {
    MESH_BROADCAST(0x01u), // Broadcast to all nearby devices
    MESH_DIRECT(0x02u), // Direct transfer to specific device
    MESH_RELAY(0x03u), // Multi-hop relay through mesh
    QR_CODE(0x04u), // Shared via QR code with file reference
    NFC(0x05u); // Near Field Communication transfer
    
    companion object {
        fun fromValue(value: UByte): ShareMethod? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Context in which the sharing occurred
 */
enum class ShareContext(val value: UByte) {
    MANUAL(0x01u), // User manually shared the file
    AUTO_DISCOVERY(0x02u), // Automatic sharing based on preferences
    RECOMMENDATION(0x03u), // Shared as a recommendation
    PLAYLIST_SYNC(0x04u), // Part of playlist synchronization
    BACKUP(0x05u); // Backup/sync operation
    
    companion object {
        fun fromValue(value: UByte): ShareContext? {
            return values().find { it.value == value }
        }
    }
}