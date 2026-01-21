package com.bitchat.android.music

import android.content.Context
import android.util.Log
import androidx.room.*
import com.bitchat.android.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

// Room Database Entities and DAOs
@Entity(tableName = "playback_records")
data class PlaybackRecordEntity(
    @PrimaryKey val recordId: String,
    val contentId: String,
    val deviceId: String,
    val timestamp: Long,
    val durationPlayed: Int,
    val trackDuration: Int,
    val playPercentage: Float,
    val skipCount: Int,
    val repeatFlag: Boolean,
    val sourceType: String,
    val deviceSignature: ByteArray?,
    val isSynced: Boolean = false,
    val syncedAt: Long? = null
) {
    fun toModel(): PlaybackRecord {
        return PlaybackRecord(
            recordId = recordId,
            contentId = contentId,
            deviceId = deviceId,
            timestamp = timestamp,
            durationPlayed = durationPlayed,
            trackDuration = trackDuration,
            playPercentage = playPercentage,
            skipCount = skipCount,
            repeatFlag = repeatFlag,
            sourceType = com.bitchat.android.music.model.SourceType.valueOf(sourceType),
            deviceSignature = deviceSignature
        )
    }
}

@Entity(tableName = "track_metadata")
data class TrackMetadataEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Int,
    val audioFingerprint: ByteArray,
    val firstSeen: Long,
    val isSynced: Boolean = false,
    val syncedAt: Long? = null
) {
    fun toModel(): TrackMetadata {
        return TrackMetadata(
            contentId = contentId,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            audioFingerprint = audioFingerprint,
            firstSeen = firstSeen
        )
    }
}

// Sharing Record Database Entity
@Entity(tableName = "sharing_records")
data class SharingRecordEntity(
    @PrimaryKey val recordId: String,
    val contentId: String,
    val sharerDeviceId: String,
    val recipientDeviceId: String?,
    val shareMethod: String,
    val timestamp: Long,
    val fileSize: Long,
    val transferDuration: Long?,
    val transferStatus: String,
    val shareContext: String,
    val deviceSignature: ByteArray?,
    val isSynced: Boolean = false,
    val syncedAt: Long? = null
) {
    fun toModel(): SharingRecord {
        return SharingRecord(
            recordId = recordId,
            contentId = contentId,
            sharerDeviceId = sharerDeviceId,
            recipientDeviceId = recipientDeviceId,
            shareMethod = ShareMethod.valueOf(shareMethod),
            timestamp = timestamp,
            fileSize = fileSize,
            transferDuration = transferDuration,
            transferStatus = TransferStatus.valueOf(transferStatus),
            shareContext = ShareContext.valueOf(shareContext),
            deviceSignature = deviceSignature
        )
    }
}

@Dao
interface PlaybackRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PlaybackRecordEntity)
    
    @Query("SELECT * FROM playback_records WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int): List<PlaybackRecordEntity>
    
    @Query("UPDATE playback_records SET isSynced = 1, syncedAt = :syncTime WHERE recordId IN (:recordIds)")
    suspend fun markAsSynced(recordIds: List<String>, syncTime: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM playback_records")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM playback_records WHERE isSynced = 0")
    suspend fun countPendingSync(): Int
    
    @Query("SELECT SUM(durationPlayed) FROM playback_records")
    suspend fun getTotalPlayTime(): Long
    
    @Query("SELECT COUNT(*) FROM playback_records WHERE durationPlayed >= 30 OR (durationPlayed * 2) >= trackDuration")
    suspend fun countQualifyingPlays(): Int
    
    @Query("DELETE FROM playback_records WHERE isSynced = 1 AND syncedAt < :cutoffTime")
    suspend fun deleteOldSyncedRecords(cutoffTime: Long): Int
}

@Dao
interface TrackMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: TrackMetadataEntity)
    
    @Query("SELECT * FROM track_metadata WHERE contentId = :contentId")
    suspend fun getByContentId(contentId: String): TrackMetadataEntity?
    
    @Query("SELECT * FROM track_metadata WHERE isSynced = 0")
    suspend fun getPendingSync(): List<TrackMetadataEntity>
    
    @Query("UPDATE track_metadata SET isSynced = 1, syncedAt = :syncTime WHERE contentId IN (:contentIds)")
    suspend fun markAsSynced(contentIds: List<String>, syncTime: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM track_metadata")
    suspend fun count(): Int
}

@Dao
interface SharingRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SharingRecordEntity)
    
    @Query("SELECT * FROM sharing_records WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int): List<SharingRecordEntity>
    
    @Query("UPDATE sharing_records SET isSynced = 1, syncedAt = :syncTime WHERE recordId IN (:recordIds)")
    suspend fun markAsSynced(recordIds: List<String>, syncTime: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM sharing_records")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM sharing_records WHERE isSynced = 0")
    suspend fun countPendingSync(): Int
    
    @Query("SELECT COUNT(*) FROM sharing_records WHERE transferStatus = 'COMPLETED'")
    suspend fun countSuccessfulShares(): Int
    
    @Query("SELECT SUM(fileSize) FROM sharing_records WHERE transferStatus = 'COMPLETED'")
    suspend fun getTotalBytesShared(): Long
    
    @Query("DELETE FROM sharing_records WHERE isSynced = 1 AND syncedAt < :cutoffTime")
    suspend fun deleteOldSyncedRecords(cutoffTime: Long): Int
}

@Database(
    entities = [PlaybackRecordEntity::class, TrackMetadataEntity::class, SharingRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MusicAnalyticsDatabase : RoomDatabase() {
    abstract fun playbackRecordDao(): PlaybackRecordDao
    abstract fun trackMetadataDao(): TrackMetadataDao
    abstract fun sharingRecordDao(): SharingRecordDao
}

// Extension functions for entity conversion
fun PlaybackRecord.toEntity(): PlaybackRecordEntity {
    return PlaybackRecordEntity(
        recordId = recordId,
        contentId = contentId,
        deviceId = deviceId,
        timestamp = timestamp,
        durationPlayed = durationPlayed,
        trackDuration = trackDuration,
        playPercentage = playPercentage,
        skipCount = skipCount,
        repeatFlag = repeatFlag,
        sourceType = sourceType.name,
        deviceSignature = deviceSignature
    )
}

fun TrackMetadata.toEntity(): TrackMetadataEntity {
    return TrackMetadataEntity(
        contentId = contentId,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        audioFingerprint = audioFingerprint,
        firstSeen = firstSeen
    )
}

// Extension function for entity conversion
fun SharingRecord.toEntity(): SharingRecordEntity {
    return SharingRecordEntity(
        recordId = recordId,
        contentId = contentId,
        sharerDeviceId = sharerDeviceId,
        recipientDeviceId = recipientDeviceId,
        shareMethod = shareMethod.name,
        timestamp = timestamp,
        fileSize = fileSize,
        transferDuration = transferDuration,
        transferStatus = transferStatus.name,
        shareContext = shareContext.name,
        deviceSignature = deviceSignature
    )
}

/**
 * Playback analytics tracker with local SQLite storage
 * Manages playback records and prepares them for BLE sync
 */
class PlaybackAnalyticsTracker(
    private val context: Context,
    private val meshSyncManager: MusicAnalyticsMeshSync
) {
    
    companion object {
        private const val TAG = "PlaybackAnalyticsTracker"
        private const val MAX_RECORDS_IN_MEMORY = 1000
        private const val BATCH_SIZE = 50
        private const val SYNC_INTERVAL_MS = 30000L // 30 seconds
    }
    
    private val database by lazy { 
        Room.databaseBuilder(
            context.applicationContext,
            MusicAnalyticsDatabase::class.java,
            "music_analytics.db"
        ).build()
    }
    
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    // Statistics
    private val _totalRecords = MutableStateFlow(0)
    val totalRecords: StateFlow<Int> = _totalRecords.asStateFlow()
    
    private val _pendingSyncRecords = MutableStateFlow(0)
    val pendingSyncRecords: StateFlow<Int> = _pendingSyncRecords.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()
    
    init {
        startPeriodicSync()
        updateStatistics()
    }
    
    /**
     * Record a playback event
     */
    suspend fun recordPlayback(record: PlaybackRecord) {
        withContext(Dispatchers.IO) {
            try {
                // Store in database
                database.playbackRecordDao().insert(record.toEntity())
                
                // Update statistics
                updateStatistics()
                
                Log.d(TAG, "Recorded playback: ${record.contentId} - ${record.durationPlayed}s")
                
                // Trigger immediate sync if we have enough records
                val pendingCount = database.playbackRecordDao().countPendingSync()
                if (pendingCount >= BATCH_SIZE) {
                    triggerSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording playback", e)
            }
        }
    }
    
    /**
     * Record track metadata
     */
    suspend fun recordTrackMetadata(metadata: TrackMetadata) {
        withContext(Dispatchers.IO) {
            try {
                // Check if we already have this track
                val existing = database.trackMetadataDao().getByContentId(metadata.contentId)
                if (existing == null) {
                    database.trackMetadataDao().insert(metadata.toEntity())
                    Log.d(TAG, "Recorded track metadata: ${metadata.title} by ${metadata.artist}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording track metadata", e)
            }
        }
    }
    
    /**
     * Record a sharing event
     */
    suspend fun recordSharing(record: SharingRecord) {
        withContext(Dispatchers.IO) {
            try {
                // Store in database
                database.sharingRecordDao().insert(record.toEntity())
                
                // Update statistics
                updateStatistics()
                
                Log.d(TAG, "Recorded sharing: ${record.contentId} - ${record.shareMethod}")
                
                // Trigger sync if we have enough records
                val pendingCount = database.sharingRecordDao().countPendingSync()
                if (pendingCount >= BATCH_SIZE) {
                    triggerSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording sharing", e)
            }
        }
    }
    
    /**
     * Get pending records for sync (batch)
     */
    suspend fun getPendingRecordsForSync(limit: Int = BATCH_SIZE): List<PlaybackRecord> {
        return withContext(Dispatchers.IO) {
            try {
                database.playbackRecordDao()
                    .getPendingSync(limit)
                    .map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pending records", e)
                emptyList()
            }
        }
    }
    
    /**
     * Mark records as synced
     */
    suspend fun markRecordsAsSynced(recordIds: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                database.playbackRecordDao().markAsSynced(recordIds)
                updateStatistics()
                Log.d(TAG, "Marked ${recordIds.size} records as synced")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking records as synced", e)
            }
        }
    }
    
    /**
     * Get track metadata that needs to be synced
     */
    suspend fun getPendingTrackMetadata(): List<TrackMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                database.trackMetadataDao()
                    .getPendingSync()
                    .map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pending track metadata", e)
                emptyList()
            }
        }
    }
    
    /**
     * Mark track metadata as synced
     */
    suspend fun markTrackMetadataAsSynced(contentIds: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                database.trackMetadataDao().markAsSynced(contentIds)
                Log.d(TAG, "Marked ${contentIds.size} track metadata as synced")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking track metadata as synced", e)
            }
        }
    }
    
    /**
     * Get pending sharing records for sync
     */
    suspend fun getPendingSharingRecordsForSync(limit: Int = BATCH_SIZE): List<SharingRecord> {
        return withContext(Dispatchers.IO) {
            try {
                database.sharingRecordDao()
                    .getPendingSync(limit)
                    .map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pending sharing records", e)
                emptyList()
            }
        }
    }
    
    /**
     * Mark sharing records as synced
     */
    suspend fun markSharingRecordsAsSynced(recordIds: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                database.sharingRecordDao().markAsSynced(recordIds)
                updateStatistics()
                Log.d(TAG, "Marked ${recordIds.size} sharing records as synced")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking sharing records as synced", e)
            }
        }
    }
    
    /**
     * Get analytics statistics
     */
    suspend fun getAnalyticsStats(): AnalyticsStats {
        return withContext(Dispatchers.IO) {
            try {
                val totalRecords = database.playbackRecordDao().count()
                val pendingSync = database.playbackRecordDao().countPendingSync()
                val uniqueTracks = database.trackMetadataDao().count()
                val totalPlayTime = database.playbackRecordDao().getTotalPlayTime()
                val qualifyingPlays = database.playbackRecordDao().countQualifyingPlays()
                
                AnalyticsStats(
                    totalRecords = totalRecords,
                    pendingSyncRecords = pendingSync,
                    uniqueTracks = uniqueTracks,
                    totalPlayTimeSeconds = totalPlayTime,
                    qualifyingPlays = qualifyingPlays
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting analytics stats", e)
                AnalyticsStats()
            }
        }
    }
    
    /**
     * Clean up old records (keep last 30 days)
     */
    suspend fun cleanupOldRecords() {
        withContext(Dispatchers.IO) {
            try {
                val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                val deletedCount = database.playbackRecordDao().deleteOldSyncedRecords(thirtyDaysAgo)
                Log.i(TAG, "Cleaned up $deletedCount old records")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old records", e)
            }
        }
    }
    
    /**
     * Trigger immediate sync
     */
    fun triggerSync() {
        syncJob?.cancel()
        syncJob = trackerScope.launch {
            performSync()
        }
    }
    
    private fun startPeriodicSync() {
        trackerScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                performSync()
            }
        }
    }
    
    private suspend fun performSync() {
        try {
            // Get pending records
            val pendingRecords = getPendingRecordsForSync()
            if (pendingRecords.isNotEmpty()) {
                meshSyncManager.syncPlaybackRecords(pendingRecords)
            }
            
            // Get pending track metadata
            val pendingMetadata = getPendingTrackMetadata()
            if (pendingMetadata.isNotEmpty()) {
                meshSyncManager.syncTrackMetadata(pendingMetadata)
            }
            
            // Get pending sharing records
            val pendingSharingRecords = getPendingSharingRecordsForSync()
            if (pendingSharingRecords.isNotEmpty()) {
                meshSyncManager.syncSharingRecords(pendingSharingRecords)
            }
            
            _lastSyncTime.value = System.currentTimeMillis()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
        }
    }
    
    private fun updateStatistics() {
        trackerScope.launch {
            try {
                val stats = getAnalyticsStats()
                _totalRecords.value = stats.totalRecords
                _pendingSyncRecords.value = stats.pendingSyncRecords
            } catch (e: Exception) {
                Log.e(TAG, "Error updating statistics", e)
            }
        }
    }
    
    fun release() {
        trackerScope.cancel()
        database.close()
    }
    
    data class AnalyticsStats(
        val totalRecords: Int = 0,
        val pendingSyncRecords: Int = 0,
        val uniqueTracks: Int = 0,
        val totalPlayTimeSeconds: Long = 0,
        val qualifyingPlays: Int = 0
    )
}