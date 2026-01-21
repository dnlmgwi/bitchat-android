package com.bitchat.android.music.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Music analytics message types for BLE mesh networking
 * Based on the Offline Music Analytics specification section 5.2
 */

/**
 * Batch of playback records for efficient transmission
 * Up to 100 records per message to stay within BLE size limits
 */
@Parcelize
data class PlaybackBatchMessage(
    val batchId: String,
    val deviceId: String,
    val records: List<PlaybackRecord>,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(8192).apply { order(ByteOrder.BIG_ENDIAN) }
        
        // Header
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, batchId)
        writeStringWithLength(buffer, deviceId)
        buffer.putShort(records.size.toShort())
        
        // Records
        records.forEach { record ->
            val recordBytes = record.toBinaryPayload()
            buffer.putShort(recordBytes.size.toShort())
            buffer.put(recordBytes)
        }
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): PlaybackBatchMessage? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val batchId = readStringWithLength(buffer)
                val deviceId = readStringWithLength(buffer)
                val recordCount = buffer.short.toInt()
                
                val records = mutableListOf<PlaybackRecord>()
                repeat(recordCount) {
                    val recordSize = buffer.short.toInt()
                    val recordBytes = ByteArray(recordSize)
                    buffer.get(recordBytes)
                    PlaybackRecord.fromBinaryPayload(recordBytes)?.let { record ->
                        records.add(record)
                    }
                }
                
                PlaybackBatchMessage(batchId, deviceId, records, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Track metadata message for new content IDs
 */
@Parcelize
data class TrackMetaMessage(
    val metadata: TrackMetadata,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(2048).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        val metadataBytes = metadata.toBinaryPayload()
        buffer.putShort(metadataBytes.size.toShort())
        buffer.put(metadataBytes)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): TrackMetaMessage? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val metadataSize = buffer.short.toInt()
                val metadataBytes = ByteArray(metadataSize)
                buffer.get(metadataBytes)
                
                TrackMetadata.fromBinaryPayload(metadataBytes)?.let { metadata ->
                    TrackMetaMessage(metadata, timestamp)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Device registration message for establishing trust with aggregators
 */
@Parcelize
data class DeviceRegisterMessage(
    val deviceId: String,
    val publicKey: ByteArray,
    val deviceInfo: String, // Device model, OS version, etc.
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(1024).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, deviceId)
        writeStringWithLength(buffer, deviceInfo)
        buffer.putShort(publicKey.size.toShort())
        buffer.put(publicKey)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): DeviceRegisterMessage? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val deviceId = readStringWithLength(buffer)
                val deviceInfo = readStringWithLength(buffer)
                val keySize = buffer.short.toInt()
                val publicKey = ByteArray(keySize)
                buffer.get(publicKey)
                
                DeviceRegisterMessage(deviceId, publicKey, deviceInfo, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Sync acknowledgment message with list of received record IDs
 */
@Parcelize
data class SyncAckMessage(
    val receivedRecordIds: List<String>,
    val aggregatorId: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(4096).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, aggregatorId)
        buffer.putShort(receivedRecordIds.size.toShort())
        
        receivedRecordIds.forEach { recordId ->
            writeStringWithLength(buffer, recordId)
        }
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): SyncAckMessage? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val aggregatorId = readStringWithLength(buffer)
                val recordCount = buffer.short.toInt()
                
                val recordIds = mutableListOf<String>()
                repeat(recordCount) {
                    recordIds.add(readStringWithLength(buffer))
                }
                
                SyncAckMessage(recordIds, aggregatorId, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Aggregator beacon message advertising presence and capacity
 */
@Parcelize
data class AggregatorBeaconMessage(
    val aggregatorId: String,
    val capacity: Int, // Number of devices that can sync simultaneously
    val currentLoad: Int, // Current number of connected devices
    val lastSyncTime: Long, // Last successful internet sync timestamp
    val supportedVersion: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun toBinaryPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }
        
        buffer.putLong(timestamp)
        writeStringWithLength(buffer, aggregatorId)
        buffer.putInt(capacity)
        buffer.putInt(currentLoad)
        buffer.putLong(lastSyncTime)
        buffer.putInt(supportedVersion)
        
        return getUsedBytes(buffer)
    }
    
    companion object {
        fun fromBinaryPayload(data: ByteArray): AggregatorBeaconMessage? {
            return try {
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                val timestamp = buffer.long
                val aggregatorId = readStringWithLength(buffer)
                val capacity = buffer.int
                val currentLoad = buffer.int
                val lastSyncTime = buffer.long
                val supportedVersion = buffer.int
                
                AggregatorBeaconMessage(
                    aggregatorId, capacity, currentLoad, lastSyncTime, supportedVersion, timestamp
                )
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