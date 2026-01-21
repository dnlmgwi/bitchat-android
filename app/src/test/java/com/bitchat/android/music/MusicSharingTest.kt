package com.bitchat.android.music

import com.bitchat.android.music.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for music sharing functionality
 */
class MusicSharingTest {
    
    @Test
    fun testSharingRecordSerialization() {
        val record = SharingRecord(
            recordId = "share-123",
            contentId = "content-456",
            sharerDeviceId = "device-789",
            recipientDeviceId = "recipient-abc",
            shareMethod = ShareMethod.MESH_DIRECT,
            timestamp = System.currentTimeMillis(),
            fileSize = 5242880L, // 5MB
            transferDuration = 30000L, // 30 seconds
            transferStatus = TransferStatus.COMPLETED,
            shareContext = ShareContext.MANUAL
        )
        
        // Test binary serialization
        val binaryData = record.toBinaryPayload()
        assertNotNull(binaryData)
        assertTrue(binaryData.isNotEmpty())
        
        // Test deserialization
        val deserializedRecord = SharingRecord.fromBinaryPayload(binaryData)
        assertNotNull(deserializedRecord)
        assertEquals(record.recordId, deserializedRecord!!.recordId)
        assertEquals(record.contentId, deserializedRecord.contentId)
        assertEquals(record.sharerDeviceId, deserializedRecord.sharerDeviceId)
        assertEquals(record.recipientDeviceId, deserializedRecord.recipientDeviceId)
        assertEquals(record.shareMethod, deserializedRecord.shareMethod)
        assertEquals(record.fileSize, deserializedRecord.fileSize)
        assertEquals(record.transferDuration, deserializedRecord.transferDuration)
        assertEquals(record.transferStatus, deserializedRecord.transferStatus)
        assertEquals(record.shareContext, deserializedRecord.shareContext)
    }
    
    @Test
    fun testMusicShareAnnouncementSerialization() {
        val announcement = MusicShareAnnouncement(
            contentId = "content-123",
            sharerDeviceId = "device-456",
            fileName = "test-song.mp3",
            fileSize = 3145728L, // 3MB
            shareMethod = ShareMethod.MESH_BROADCAST
        )
        
        val binaryData = announcement.toBinaryPayload()
        assertNotNull(binaryData)
        assertTrue(binaryData.isNotEmpty())
        
        val deserialized = MusicShareAnnouncement.fromBinaryPayload(binaryData)
        assertNotNull(deserialized)
        assertEquals(announcement.contentId, deserialized!!.contentId)
        assertEquals(announcement.sharerDeviceId, deserialized.sharerDeviceId)
        assertEquals(announcement.fileName, deserialized.fileName)
        assertEquals(announcement.fileSize, deserialized.fileSize)
        assertEquals(announcement.shareMethod, deserialized.shareMethod)
    }
    
    @Test
    fun testMusicShareOfferSerialization() {
        val offer = MusicShareOffer(
            offerId = "offer-123",
            contentId = "content-456",
            sharerDeviceId = "sharer-789",
            recipientDeviceId = "recipient-abc",
            fileName = "shared-track.mp3",
            fileSize = 4194304L // 4MB
        )
        
        val binaryData = offer.toBinaryPayload()
        assertNotNull(binaryData)
        
        val deserialized = MusicShareOffer.fromBinaryPayload(binaryData)
        assertNotNull(deserialized)
        assertEquals(offer.offerId, deserialized!!.offerId)
        assertEquals(offer.contentId, deserialized.contentId)
        assertEquals(offer.sharerDeviceId, deserialized.sharerDeviceId)
        assertEquals(offer.recipientDeviceId, deserialized.recipientDeviceId)
        assertEquals(offer.fileName, deserialized.fileName)
        assertEquals(offer.fileSize, deserialized.fileSize)
    }
    
    @Test
    fun testMusicShareRequestSerialization() {
        val request = MusicShareRequest(
            requestId = "request-123",
            contentId = "content-456",
            requesterDeviceId = "requester-789",
            sharerDeviceId = "sharer-abc"
        )
        
        val binaryData = request.toBinaryPayload()
        assertNotNull(binaryData)
        
        val deserialized = MusicShareRequest.fromBinaryPayload(binaryData)
        assertNotNull(deserialized)
        assertEquals(request.requestId, deserialized!!.requestId)
        assertEquals(request.contentId, deserialized.contentId)
        assertEquals(request.requesterDeviceId, deserialized.requesterDeviceId)
        assertEquals(request.sharerDeviceId, deserialized.sharerDeviceId)
    }
    
    @Test
    fun testMusicShareResponseSerialization() {
        val response = MusicShareResponse(
            requestId = "request-123",
            contentId = "content-456",
            sharerDeviceId = "sharer-789",
            requesterDeviceId = "requester-abc",
            accepted = true,
            reason = "File available"
        )
        
        val binaryData = response.toBinaryPayload()
        assertNotNull(binaryData)
        
        val deserialized = MusicShareResponse.fromBinaryPayload(binaryData)
        assertNotNull(deserialized)
        assertEquals(response.requestId, deserialized!!.requestId)
        assertEquals(response.contentId, deserialized.contentId)
        assertEquals(response.sharerDeviceId, deserialized.sharerDeviceId)
        assertEquals(response.requesterDeviceId, deserialized.requesterDeviceId)
        assertEquals(response.accepted, deserialized.accepted)
        assertEquals(response.reason, deserialized.reason)
    }
    
    @Test
    fun testMusicFileChunkSerialization() {
        val chunkData = "This is test chunk data for file transfer".toByteArray()
        val chunk = MusicFileChunk(
            transferId = "transfer-123",
            contentId = "content-456",
            chunkIndex = 5,
            totalChunks = 20,
            chunkData = chunkData,
            checksum = "abc123def456"
        )
        
        val binaryData = chunk.toBinaryPayload()
        assertNotNull(binaryData)
        
        val deserialized = MusicFileChunk.fromBinaryPayload(binaryData)
        assertNotNull(deserialized)
        assertEquals(chunk.transferId, deserialized!!.transferId)
        assertEquals(chunk.contentId, deserialized.contentId)
        assertEquals(chunk.chunkIndex, deserialized.chunkIndex)
        assertEquals(chunk.totalChunks, deserialized.totalChunks)
        assertArrayEquals(chunk.chunkData, deserialized.chunkData)
        assertEquals(chunk.checksum, deserialized.checksum)
    }
    
    @Test
    fun testMusicTransferStatusSerialization() {
        val status = MusicTransferStatus(
            transferId = "transfer-123",
            contentId = "content-456",
            senderDeviceId = "sender-789",
            receiverDeviceId = "receiver-abc",
            status = TransferStatus.IN_PROGRESS,
            progress = 0.65f,
            errorMessage = null
        )
        
        val binaryData = status.toBinaryPayload()
        assertNotNull(binaryData)
        
        val deserialized = MusicTransferStatus.fromBinaryPayload(binaryData)
        assertNotNull(deserialized)
        assertEquals(status.transferId, deserialized!!.transferId)
        assertEquals(status.contentId, deserialized.contentId)
        assertEquals(status.senderDeviceId, deserialized.senderDeviceId)
        assertEquals(status.receiverDeviceId, deserialized.receiverDeviceId)
        assertEquals(status.status, deserialized.status)
        assertEquals(status.progress, deserialized.progress, 0.01f)
        assertEquals(status.errorMessage, deserialized.errorMessage)
    }
    
    @Test
    fun testShareMethodEnumValues() {
        // Test all share method enum values
        assertEquals(ShareMethod.MESH_BROADCAST, ShareMethod.fromValue(0x01u))
        assertEquals(ShareMethod.MESH_DIRECT, ShareMethod.fromValue(0x02u))
        assertEquals(ShareMethod.MESH_RELAY, ShareMethod.fromValue(0x03u))
        assertEquals(ShareMethod.QR_CODE, ShareMethod.fromValue(0x04u))
        assertEquals(ShareMethod.NFC, ShareMethod.fromValue(0x05u))
        assertNull(ShareMethod.fromValue(0x99u)) // Invalid value
    }
    
    @Test
    fun testTransferStatusEnumValues() {
        // Test all transfer status enum values
        assertEquals(TransferStatus.INITIATED, TransferStatus.fromValue(0x01u))
        assertEquals(TransferStatus.IN_PROGRESS, TransferStatus.fromValue(0x02u))
        assertEquals(TransferStatus.COMPLETED, TransferStatus.fromValue(0x03u))
        assertEquals(TransferStatus.FAILED, TransferStatus.fromValue(0x04u))
        assertEquals(TransferStatus.CANCELLED, TransferStatus.fromValue(0x05u))
        assertEquals(TransferStatus.REJECTED, TransferStatus.fromValue(0x06u))
        assertNull(TransferStatus.fromValue(0x99u)) // Invalid value
    }
    
    @Test
    fun testShareContextEnumValues() {
        // Test all share context enum values
        assertEquals(ShareContext.MANUAL, ShareContext.fromValue(0x01u))
        assertEquals(ShareContext.AUTO_DISCOVERY, ShareContext.fromValue(0x02u))
        assertEquals(ShareContext.RECOMMENDATION, ShareContext.fromValue(0x03u))
        assertEquals(ShareContext.PLAYLIST_SYNC, ShareContext.fromValue(0x04u))
        assertEquals(ShareContext.BACKUP, ShareContext.fromValue(0x05u))
        assertNull(ShareContext.fromValue(0x99u)) // Invalid value
    }
    
    @Test
    fun testSharingRecordDataForSigning() {
        val record = SharingRecord(
            recordId = "test-record",
            contentId = "test-content",
            sharerDeviceId = "test-device",
            recipientDeviceId = null,
            shareMethod = ShareMethod.MESH_BROADCAST,
            timestamp = 1234567890L,
            fileSize = 1024L,
            transferStatus = TransferStatus.COMPLETED,
            shareContext = ShareContext.MANUAL
        )
        
        val signingData = record.getDataForSigning()
        assertNotNull(signingData)
        assertTrue(signingData.isNotEmpty())
        
        // Should be deterministic
        val signingData2 = record.getDataForSigning()
        assertArrayEquals(signingData, signingData2)
    }
}