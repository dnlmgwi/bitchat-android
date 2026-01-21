package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Music sharing message types for BLE mesh networking
 * Handles file sharing announcements, offers, requests, and transfers
 */

/**
 * Announcement of available music for sharing (broadcast)
 */
@Parcelize
data class MusicShareAnnouncement(
    val contentId: String,
    val sharerDeviceId: String,
    val fileName: String,
    val fileSize: Long,
    val shareMethod: ShareMethod,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(1024).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        buffer.putLong(fileSize)
        buffer.put(shareMethod.value.toByte())
        
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, sharerDeviceId)
        writeStringWithLength(buffer, fileName)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): MusicShareAnnouncement? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val fileSize = buffer.long
                val shareMethod = ShareMethod.fromValue(buffer.get().toUByte()) ?: ShareMethod.MESH_BROADCAST
                
                val contentId = readStringWithLength(buffer)
                val sharerDeviceId = readStringWithLength(buffer)
                val fileName = readStringWithLength(buffer)
                
                MusicShareAnnouncement(contentId, sharerDeviceId, fileName, fileSize, shareMethod, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Direct sharing offer to specific device
 */
@Parcelize
data class MusicShareOffer(
    val offerId: String,
    val contentId: String,
    val sharerDeviceId: String,
    val recipientDeviceId: String,
    val fileName: String,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(1024).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        buffer.putLong(fileSize)
        
        writeStringWithLength(buffer, offerId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, sharerDeviceId)
        writeStringWithLength(buffer, recipientDeviceId)
        writeStringWithLength(buffer, fileName)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): MusicShareOffer? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val fileSize = buffer.long
                
                val offerId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val sharerDeviceId = readStringWithLength(buffer)
                val recipientDeviceId = readStringWithLength(buffer)
                val fileName = readStringWithLength(buffer)
                
                MusicShareOffer(offerId, contentId, sharerDeviceId, recipientDeviceId, fileName, fileSize, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Request for shared music file
 */
@Parcelize
data class MusicShareRequest(
    val requestId: String,
    val contentId: String,
    val requesterDeviceId: String,
    val sharerDeviceId: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        
        writeStringWithLength(buffer, requestId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, requesterDeviceId)
        writeStringWithLength(buffer, sharerDeviceId)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): MusicShareRequest? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                
                val requestId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val requesterDeviceId = readStringWithLength(buffer)
                val sharerDeviceId = readStringWithLength(buffer)
                
                MusicShareRequest(requestId, contentId, requesterDeviceId, sharerDeviceId, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Response to sharing request (accept/reject)
 */
@Parcelize
data class MusicShareResponse(
    val requestId: String,
    val contentId: String,
    val sharerDeviceId: String,
    val requesterDeviceId: String,
    val accepted: Boolean,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        var flags: UByte = 0u
        if (reason != null) flags = flags or 0x01u
        
        buffer.put(flags.toByte())
        buffer.putLong(timestamp)
        buffer.put(if (accepted) 1.toByte() else 0.toByte())
        
        writeStringWithLength(buffer, requestId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, sharerDeviceId)
        writeStringWithLength(buffer, requesterDeviceId)
        
        if (reason != null) {
            writeStringWithLength(buffer, reason)
        }
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): MusicShareResponse? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val flags = buffer.get().toUByte()
                val hasReason = (flags and 0x01u) != 0u.toUByte()
                
                val timestamp = buffer.long
                val accepted = buffer.get() != 0.toByte()
                
                val requestId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val sharerDeviceId = readStringWithLength(buffer)
                val requesterDeviceId = readStringWithLength(buffer)
                
                val reason = if (hasReason) readStringWithLength(buffer) else null
                
                MusicShareResponse(requestId, contentId, sharerDeviceId, requesterDeviceId, accepted, reason, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * File transfer chunk for actual file data transmission
 */
@Parcelize
data class MusicFileChunk(
    val transferId: String,
    val contentId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkData: ByteArray,
    val checksum: String, // MD5 checksum of chunk data
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(2048).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        buffer.putInt(chunkIndex)
        buffer.putInt(totalChunks)
        buffer.putShort(chunkData.size.toShort())
        
        writeStringWithLength(buffer, transferId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, checksum)
        
        buffer.put(chunkData)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): MusicFileChunk? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val chunkIndex = buffer.int
                val totalChunks = buffer.int
                val chunkSize = buffer.short.toInt()
                
                val transferId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val checksum = readStringWithLength(buffer)
                
                val chunkData = ByteArray(chunkSize)
                buffer.get(chunkData)
                
                MusicFileChunk(transferId, contentId, chunkIndex, totalChunks, chunkData, checksum, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Transfer status update message
 */
@Parcelize
data class MusicTransferStatus(
    val transferId: String,
    val contentId: String,
    val senderDeviceId: String,
    val receiverDeviceId: String,
    val status: TransferStatus,
    val progress: Float, // 0.0 to 1.0
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        var flags: UByte = 0u
        if (errorMessage != null) flags = flags or 0x01u
        
        buffer.put(flags.toByte())
        buffer.putLong(timestamp)
        buffer.put(status.value.toByte())
        buffer.putFloat(progress)
        
        writeStringWithLength(buffer, transferId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, senderDeviceId)
        writeStringWithLength(buffer, receiverDeviceId)
        
        if (errorMessage != null) {
            writeStringWithLength(buffer, errorMessage)
        }
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): MusicTransferStatus? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val flags = buffer.get().toUByte()
                val hasError = (flags and 0x01u) != 0u.toUByte()
                
                val timestamp = buffer.long
                val status = TransferStatus.fromValue(buffer.get().toInt()) ?: TransferStatus.INITIATED
                val progress = buffer.float
                
                val transferId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val senderDeviceId = readStringWithLength(buffer)
                val receiverDeviceId = readStringWithLength(buffer)
                
                val errorMessage = if (hasError) readStringWithLength(buffer) else null
                
                MusicTransferStatus(transferId, contentId, senderDeviceId, receiverDeviceId, status, progress, errorMessage, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

// Helper functions for binary serialization
private fun writeStringWithLength(buffer: ByteBuffer, str: String) {
    val bytes = str.toByteArray(Charsets.UTF_8)
    buffer.putShort(bytes.size.toShort())
    buffer.put(bytes)
}

private fun readStringWithLength(buffer: ByteBuffer): String {
    val length = buffer.short.toInt()
    val bytes = ByteArray(length)
    buffer.get(bytes)
    return String(bytes, Charsets.UTF_8)
}

private fun getUsedBytes(buffer: ByteBuffer): ByteArray {
    val result = ByteArray(buffer.position())
    buffer.rewind()
    buffer.get(result)
    return result
}

/**
 * Individual sharing record message for BLE transmission
 */
data class SharingRecordMessage(
    val record: SharingRecord
) {
    fun toBinaryPayload(): ByteArray {
        return record.toBinaryPayload()
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): SharingRecordMessage? {
            return SharingRecord.fromBinaryPayload(data)?.let { 
                SharingRecordMessage(it) 
            }
        }
    }
}