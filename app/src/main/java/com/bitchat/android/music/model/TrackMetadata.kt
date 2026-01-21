package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Track metadata record for music analytics
 * Stored once per unique track, synchronized separately from playback records
 */
@Parcelize
data class TrackMetadata(
    val contentId: String, // Primary key - 32-char SHA256 hash
    val title: String, // Track title from file metadata
    val artist: String, // Artist name from file metadata
    val album: String? = null, // Album name (if available)
    val duration: Int, // Track duration in seconds
    val audioFingerprint: ByteArray, // Compact audio fingerprint for matching
    val firstSeen: Long = System.currentTimeMillis() // When track was first encountered
) : Parcelable {

    /**
     * Convert track metadata to binary payload for BLE transmission
     */
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(1024).apply { order(ByteOrder.BIG_ENDIAN) }
        
        // Flags for optional fields
        var flags: UByte = 0u
        if (album != null) flags = flags or 0x01u
        
        buffer.put(flags.toByte())
        
        // Core fields
        buffer.putInt(duration)
        buffer.putLong(firstSeen)
        
        // Variable length fields with length prefixes
        writeStringWithLength(buffer, contentId)
        writeStringWithLength(buffer, title)
        writeStringWithLength(buffer, artist)
        
        if (album != null) {
            writeStringWithLength(buffer, album)
        }
        
        // Audio fingerprint
        buffer.putShort(audioFingerprint.size.toShort())
        buffer.put(audioFingerprint)
        
        // Return only used portion
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
    
    companion object {
        /**
         * Parse track metadata from binary payload
         */
        fun fromBinaryPayload(data: ByteArray): TrackMetadata? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val flags = buffer.get().toUByte()
                val hasAlbum = (flags and 0x01u) != 0u.toUByte()
                
                // Core fields
                val duration = buffer.int
                val firstSeen = buffer.long
                
                // Variable length fields
                val contentId = readStringWithLength(buffer)
                val title = readStringWithLength(buffer)
                val artist = readStringWithLength(buffer)
                val album = if (hasAlbum) readStringWithLength(buffer) else null
                
                // Audio fingerprint
                val fingerprintLength = buffer.short.toInt()
                val audioFingerprint = ByteArray(fingerprintLength)
                buffer.get(audioFingerprint)
                
                TrackMetadata(
                    contentId = contentId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    audioFingerprint = audioFingerprint,
                    firstSeen = firstSeen
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