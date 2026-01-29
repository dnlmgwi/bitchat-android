package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Playback record data model based on the Offline Music Analytics specification
 * Represents a single music playback event with verification data and listening context
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
    val deviceSignature: ByteArray? = null, // Ed25519 signature for verification
    
    // Listening Context Data for Business Intelligence
    val timeOfDayBucket: TimeOfDayBucket = TimeOfDayBucket.UNKNOWN, // Morning/Afternoon/Evening/Night
    val dayOfWeek: DayOfWeek = DayOfWeek.UNKNOWN, // Weekday vs weekend patterns
    val sessionDuration: Int = 0, // How long user listened in one sitting (seconds)
    val playbackMode: PlaybackMode = PlaybackMode.UNKNOWN, // Shuffle vs sequential vs repeat
    val volumeLevelAvg: Float = 0.0f, // Average volume during playback (0.0-1.0)
    val audioOutputType: AudioOutputType = AudioOutputType.UNKNOWN, // Speaker vs headphones
    
    // Local sync status (not transmitted)
    val isSynced: Boolean = false,
    val syncedAt: Long? = null
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

/**
 * Time of day buckets for listening pattern analysis
 * 4-hour windows prevent precise activity tracking while preserving patterns
 */
enum class TimeOfDayBucket(val displayName: String) {
    MORNING("Morning (6AM-12PM)"),      // 06:00-11:59
    AFTERNOON("Afternoon (12PM-6PM)"),  // 12:00-17:59
    EVENING("Evening (6PM-12AM)"),      // 18:00-23:59
    NIGHT("Night (12AM-6AM)"),          // 00:00-05:59
    UNKNOWN("Unknown");
    
    companion object {
        fun fromHour(hour: Int): TimeOfDayBucket {
            return when (hour) {
                in 6..11 -> MORNING
                in 12..17 -> AFTERNOON
                in 18..23 -> EVENING
                in 0..5 -> NIGHT
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Day of week patterns for understanding work vs leisure listening
 */
enum class DayOfWeek(val displayName: String) {
    WEEKDAY("Weekday"),
    WEEKEND("Weekend"),
    UNKNOWN("Unknown");
    
    companion object {
        fun fromCalendarDay(dayOfWeek: Int): DayOfWeek {
            return when (dayOfWeek) {
                java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY -> WEEKEND
                java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, 
                java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY, 
                java.util.Calendar.FRIDAY -> WEEKDAY
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Playback mode for understanding consumption patterns
 */
enum class PlaybackMode(val displayName: String) {
    SEQUENTIAL("Sequential"),    // Playing tracks in order
    SHUFFLE("Shuffle"),         // Random track order
    REPEAT_ONE("Repeat One"),   // Repeating single track
    REPEAT_ALL("Repeat All"),   // Repeating playlist/album
    UNKNOWN("Unknown");
}

/**
 * Audio output type for inferring listening context
 */
enum class AudioOutputType(val displayName: String) {
    SPEAKER("Speaker"),         // Device speakers (social listening)
    WIRED_HEADPHONES("Wired Headphones"),    // Wired headphones (personal)
    BLUETOOTH_HEADPHONES("Bluetooth Headphones"), // Bluetooth headphones (personal)
    BLUETOOTH_SPEAKER("Bluetooth Speaker"),  // External Bluetooth speaker (social)
    UNKNOWN("Unknown");
}