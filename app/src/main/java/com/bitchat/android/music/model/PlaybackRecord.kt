package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Playback record data model based on the Offline Music Analytics specification
 * Represents a single music playback event with verification data
 */
@Parcelize
data class PlaybackRecord(
    val recordId: String = UUID.randomUUID().toString(),
    val contentId: String, // 32-char SHA256 hash of track fingerprint + metadata
    val deviceId: String, // 64-char anonymized device fingerprint
    val timestamp: Long, // Unix epoch when playback started
    val durationPlayed: Int, // Seconds actually listened
    val trackDuration: Int, // Total track length in seconds
    val playPercentage: Float, // Percentage of track listened (0.0-1.0)
    val skipCount: Int = 0, // Number of times user skipped within track
    val repeatFlag: Boolean = false, // Was track played on repeat
    val sourceType: SourceType = SourceType.LOCAL_FILE,
    val deviceSignature: ByteArray? = null // Ed25519 signature for verification
) : Parcelable {

    /**
     * Convert playback record to binary payload for BLE transmission
     * Format optimized for minimal size while maintaining all required data
     */
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        // Flags byte for optional fields
        var flags: UByte = 0u
        if (skipCount > 0) flags = flags or 0x01u
        if (repeatFlag) flags = flags or 0x02u
        if (deviceSignature != null) flags = flags or 0x04u
        
        buffer.put(flags.toByte())
        
        // Core fields (fixed size)
        buffer.putLong(timestamp)
        buffer.putInt(durationPlayed)
        buffer.putInt(trackDuration)
        buffer.putFloat(playPercentage)
        buffer.put(sourceType.value.toByte())
        
        // Variable length fields with length prefixes
        writeStringWithLength(buffer, recordId)
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, deviceId)
        
        // Optional fields based on flags
        if (skipCount > 0) {
            buffer.putInt(skipCount)
        }
        
        if (deviceSignature != null) {
            buffer.putShort(deviceSignature.size.toShort())
            buffer.put(deviceSignature)
        }
        
        // Return only the used portion of the buffer
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }
    
    private fun writeStringWithLength(buffer: ByteBuffer, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        buffer.putShort(bytes.size.toShort())
        buffer.put(bytes)
    }
    
    /**
     * Create data for signing (excludes signature field)
     */
    fun getDataForSigning(): ByteArray {
        return "$recordId$contentId$deviceId$timestamp$durationPlayed".toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Check if this playback qualifies for royalty calculation
     * Following industry standards: 30 seconds OR 50% of track (whichever is shorter)
     */
    fun qualifiesForRoyalty(): Boolean {
        val minSeconds = minOf(30, trackDuration / 2)
        return durationPlayed >= minSeconds
    }
    
    companion object {
        /**
         * Parse playback record from binary payload
         */
        fun fromBinaryPayload(data: ByteArray): PlaybackRecord? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val flags = buffer.get().toUByte()
                val hasSkipCount = (flags and 0x01u) != 0u.toUByte()
                val hasRepeatFlag = (flags and 0x02u) != 0u.toUByte()
                val hasSignature = (flags and 0x04u) != 0u.toUByte()
                
                // Core fields
                val timestamp = buffer.long
                val durationPlayed = buffer.int
                val trackDuration = buffer.int
                val playPercentage = buffer.float
                val sourceType = SourceType.fromValue(buffer.get().toUByte()) ?: SourceType.LOCAL_FILE
                
                // Variable length fields
                val recordId = readStringWithLength(buffer)
                val contentId = readStringWithLength(buffer)
                val deviceId = readStringWithLength(buffer)
                
                // Optional fields
                val skipCount = if (hasSkipCount) buffer.int else 0
                val repeatFlag = hasRepeatFlag
                
                val signature = if (hasSignature) {
                    val sigLength = buffer.short.toInt()
                    ByteArray(sigLength).also { buffer.get(it) }
                } else null
                
                PlaybackRecord(
                    recordId = recordId,
                    contentId = contentId,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    durationPlayed = durationPlayed,
                    trackDuration = trackDuration,
                    playPercentage = playPercentage,
                    skipCount = skipCount,
                    repeatFlag = repeatFlag,
                    sourceType = sourceType,
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
 * Source type for playback records
 */
enum class SourceType(val value: UByte) {
    LOCAL_FILE(0x01u),
    USB_TRANSFER(0x02u),
    BLUETOOTH_TRANSFER(0x03u),
    WIFI_TRANSFER(0x04u),
    MANUAL_IMPORT(0x05u);
    
    companion object {
        fun fromValue(value: UByte): SourceType? {
            return values().find { it.value == value }
        }
    }
}