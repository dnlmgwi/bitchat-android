package com.bitchat.android.music

import com.bitchat.android.music.model.PlaybackRecord
import com.bitchat.android.music.model.SourceType
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for music analytics components
 */
class MusicAnalyticsTest {
    
    @Test
    fun testPlaybackRecordSerialization() {
        val record = PlaybackRecord(
            recordId = "test-record-123",
            contentId = "test-content-456",
            deviceId = "test-device-789",
            timestamp = System.currentTimeMillis(),
            durationPlayed = 120,
            trackDuration = 180,
            playPercentage = 0.67f,
            skipCount = 2,
            repeatFlag = false,
            sourceType = SourceType.LOCAL_FILE
        )
        
        // Test binary serialization
        val binaryData = record.toBinaryPayload()
        assertNotNull(binaryData)
        assertTrue(binaryData.isNotEmpty())
        
        // Test deserialization
        val deserializedRecord = PlaybackRecord.fromBinaryPayload(binaryData)
        assertNotNull(deserializedRecord)
        assertEquals(record.recordId, deserializedRecord!!.recordId)
        assertEquals(record.contentId, deserializedRecord.contentId)
        assertEquals(record.deviceId, deserializedRecord.deviceId)
        assertEquals(record.durationPlayed, deserializedRecord.durationPlayed)
        assertEquals(record.trackDuration, deserializedRecord.trackDuration)
        assertEquals(record.playPercentage, deserializedRecord.playPercentage, 0.01f)
        assertEquals(record.skipCount, deserializedRecord.skipCount)
        assertEquals(record.repeatFlag, deserializedRecord.repeatFlag)
        assertEquals(record.sourceType, deserializedRecord.sourceType)
    }
    
    @Test
    fun testPlaybackRecordQualifiesForRoyalty() {
        // Test 30-second rule
        val shortTrack = PlaybackRecord(
            recordId = "test1",
            contentId = "content1",
            deviceId = "device1",
            timestamp = System.currentTimeMillis(),
            durationPlayed = 30,
            trackDuration = 60,
            playPercentage = 0.5f,
            sourceType = SourceType.LOCAL_FILE
        )
        assertTrue(shortTrack.qualifiesForRoyalty())
        
        // Test 50% rule for longer tracks
        val longTrack = PlaybackRecord(
            recordId = "test2",
            contentId = "content2",
            deviceId = "device2",
            timestamp = System.currentTimeMillis(),
            durationPlayed = 90,
            trackDuration = 200,
            playPercentage = 0.45f,
            sourceType = SourceType.LOCAL_FILE
        )
        assertTrue(longTrack.qualifiesForRoyalty())
        
        // Test insufficient play time
        val insufficientPlay = PlaybackRecord(
            recordId = "test3",
            contentId = "content3",
            deviceId = "device3",
            timestamp = System.currentTimeMillis(),
            durationPlayed = 15,
            trackDuration = 200,
            playPercentage = 0.075f,
            sourceType = SourceType.LOCAL_FILE
        )
        assertFalse(insufficientPlay.qualifiesForRoyalty())
    }
    
    @Test
    fun testContentIdGeneration() {
        // Test that content ID generation is deterministic
        val generator = ContentIdGenerator(null) // Mock context
        
        // This would need proper mocking in a real test
        // For now, just test that the method exists and handles errors gracefully
        val contentId = generator.generateContentId("/nonexistent/file.mp3")
        // Should return null for non-existent file
        assertNull(contentId)
    }
    
    @Test
    fun testPlaybackRecordDataForSigning() {
        val record = PlaybackRecord(
            recordId = "test-record",
            contentId = "test-content",
            deviceId = "test-device",
            timestamp = 1234567890L,
            durationPlayed = 120,
            trackDuration = 180,
            playPercentage = 0.67f,
            sourceType = SourceType.LOCAL_FILE
        )
        
        val signingData = record.getDataForSigning()
        assertNotNull(signingData)
        assertTrue(signingData.isNotEmpty())
        
        // Should be deterministic
        val signingData2 = record.getDataForSigning()
        assertArrayEquals(signingData, signingData2)
    }
}