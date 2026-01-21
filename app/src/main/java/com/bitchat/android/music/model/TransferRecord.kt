package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Transfer metadata record for tracking music distribution patterns
 * Captures when music files are transferred between devices via various methods
 */
@Parcelize
data class TransferRecord(
    val transferId: String = UUID.randomUUID().toString(),
    val contentId: String, // 32-char SHA256 hash of transferred track
    val sourceDeviceId: String, // Device initiating the transfer
    val targetDeviceId: String?, // Device receiving the transfer (null if unknown)
    val transferMethod: TransferMethod,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long, // Size of transferred file in bytes
    val transferStatus: TransferStatus = TransferStatus.INITIATED,
    val transferDuration: Long? = null, // Time taken for transfer (if completed)
    val deviceSignature: ByteArray? = null // Ed25519 signature for verification
) : Parcelable {

    /**
     * Convert transfer record to binary payload for BLE transmission
     */
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        // Flags byte for optional fields
        var flags: Int = 0
        if (targetDeviceId != null) flags = flags or 0x01
        if (transferDuration != null) flags = flags or 0x02
        if (deviceSignature != null) flags = flags or 0x04
        
        buffer.put(flags.toByte())
        
        // Core fields
        buffer.putLong(timestamp)
        buffer.putLong(fileSize)
        buffer.put(transferMethod.value.toByte())
        buffer.put(transferStatus.value.toByte())
        
        // Variable length fields
        writeStringWithLength(buffer, transferId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, sourceDeviceId)
        
        // Optional fields
        if (targetDeviceId != null) {
            writeStringWithLength(buffer, targetDeviceId)
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
    fun getSigningData(): ByteArray {
        return "$transferId$contentId$sourceDeviceId$timestamp$fileSize".toByteArray(Charsets.UTF_8)
    }
    
    companion object {
        /**
         * Parse transfer record from binary payload
         */
        fun fromBinaryPayload(data: ByteArray): TransferRecord? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val flags = buffer.get().toInt()
                val hasTargetDevice = (flags and 0x01) != 0
                val hasDuration = (flags and 0x02) != 0
                val hasSignature = (flags and 0x04) != 0
                
                val timestamp = buffer.getLong()
                val fileSize = buffer.getLong()
                val transferMethod = TransferMethod.fromValue(buffer.get().toInt())
                val transferStatus = TransferStatus.fromValue(buffer.get().toInt())
                
                val transferId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val sourceDeviceId = readStringWithLength(buffer)
                
                val targetDeviceId = if (hasTargetDevice) readStringWithLength(buffer) else null
                val transferDuration = if (hasDuration) buffer.getLong() else null
                
                val deviceSignature = if (hasSignature) {
                    val sigLength = buffer.getShort().toInt()
                    ByteArray(sigLength).also { buffer.get(it) }
                } else null
                
                TransferRecord(
                    transferId = transferId,
                    contentId = contentId,
                    sourceDeviceId = sourceDeviceId,
                    targetDeviceId = targetDeviceId,
                    transferMethod = transferMethod,
                    timestamp = timestamp,
                    fileSize = fileSize,
                    transferStatus = transferStatus,
                    transferDuration = transferDuration,
                    deviceSignature = deviceSignature
                )
            } catch (e: Exception) {
                null
            }
        }
        
        private fun readStringWithLength(buffer: ByteBuffer): String {
            val length = buffer.getShort().toInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

/**
 * Transfer method enumeration
 */
enum class TransferMethod(val value: Int) {
    USB_OTG(0),
    BLUETOOTH(1),
    WIFI_DIRECT(2),
    MANUAL_IMPORT(3),
    UNKNOWN(99);
    
    companion object {
        fun fromValue(value: Int): TransferMethod {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * Transfer status enumeration
 */
enum class TransferStatus(val value: Int) {
    INITIATED(0),
    IN_PROGRESS(1),
    COMPLETED(2),
    FAILED(3),
    CANCELLED(4);
    
    companion object {
        fun fromValue(value: Int): TransferStatus {
            return values().find { it.value == value } ?: FAILED
        }
    }
}