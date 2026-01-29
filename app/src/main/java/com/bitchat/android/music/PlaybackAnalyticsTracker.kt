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
    val syncedAt: Long? = null,
    
    // Listening Context Data
    val timeOfDayBucket: String = "UNKNOWN",
    val dayOfWeek: String = "UNKNOWN", 
    val sessionDuration: Int = 0,
    val playbackMode: String = "UNKNOWN",
    val volumeLevelAvg: Float = 0.0f,
    val audioOutputType: String = "UNKNOWN"
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
            deviceSignature = deviceSignature,
            timeOfDayBucket = com.bitchat.android.music.model.TimeOfDayBucket.valueOf(timeOfDayBucket),
            dayOfWeek = com.bitchat.android.music.model.DayOfWeek.valueOf(dayOfWeek),
            sessionDuration = sessionDuration,
            playbackMode = com.bitchat.android.music.model.PlaybackMode.valueOf(playbackMode),
            volumeLevelAvg = volumeLevelAvg,
            audioOutputType = com.bitchat.android.music.model.AudioOutputType.valueOf(audioOutputType)
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
    
    @Query("SELECT * FROM playback_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<PlaybackRecordEntity>
    
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
    
    @Query("SELECT * FROM track_metadata ORDER BY firstSeen DESC")
    suspend fun getAllRecords(): List<TrackMetadataEntity>
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
    
    @Query("SELECT * FROM sharing_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<SharingRecordEntity>
    
    @Query("DELETE FROM sharing_records WHERE isSynced = 1 AND syncedAt < :cutoffTime")
    suspend fun deleteOldSyncedRecords(cutoffTime: Long): Int
}

@Database(
    entities = [PlaybackRecordEntity::class, TrackMetadataEntity::class, SharingRecordEntity::class, TransferRecordEntity::class],
    version = 4,
    exportSchema = false
)
abstract class MusicAnalyticsDatabase : RoomDatabase() {
    abstract fun playbackRecordDao(): PlaybackRecordDao
    abstract fun trackMetadataDao(): TrackMetadataDao
    abstract fun sharingRecordDao(): SharingRecordDao
    abstract fun transferRecordDao(): TransferRecordDao
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
        deviceSignature = deviceSignature,
        timeOfDayBucket = timeOfDayBucket.name,
        dayOfWeek = dayOfWeek.name,
        sessionDuration = sessionDuration,
        playbackMode = playbackMode.name,
        volumeLevelAvg = volumeLevelAvg,
        audioOutputType = audioOutputType.name
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

// Transfer Record Database Entity
@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey val recordId: String,
    val contentId: String,
    val sourceDeviceId: String,
    val targetDeviceId: String?,
    val transferMethod: String,
    val timestamp: Long,
    val fileSize: Long,
    val transferDuration: Long?,
    val transferStatus: String,
    val deviceSignature: ByteArray?,
    val isSynced: Boolean = false,
    val syncedAt: Long? = null
) {
    fun toModel(): TransferRecord {
        return TransferRecord(
            transferId = recordId,
            contentId = contentId,
            sourceDeviceId = sourceDeviceId,
            targetDeviceId = targetDeviceId,
            transferMethod = com.bitchat.android.music.model.TransferMethod.valueOf(transferMethod),
            timestamp = timestamp,
            fileSize = fileSize,
            transferDuration = transferDuration,
            transferStatus = com.bitchat.android.music.model.TransferStatus.valueOf(transferStatus),
            deviceSignature = deviceSignature
        )
    }
}

@Dao
interface TransferRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecordEntity)
    
    @Query("SELECT * FROM transfer_records WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int): List<TransferRecordEntity>
    
    @Query("UPDATE transfer_records SET isSynced = 1, syncedAt = :syncTime WHERE recordId IN (:recordIds)")
    suspend fun markAsSynced(recordIds: List<String>, syncTime: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM transfer_records")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM transfer_records WHERE isSynced = 0")
    suspend fun countPendingSync(): Int
    
    @Query("SELECT COUNT(*) FROM transfer_records WHERE transferStatus = 'COMPLETED'")
    suspend fun countSuccessfulTransfers(): Int
    
    @Query("SELECT SUM(fileSize) FROM transfer_records WHERE transferStatus = 'COMPLETED'")
    suspend fun getTotalBytesTransferred(): Long
    
    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<TransferRecordEntity>
    
    @Query("DELETE FROM transfer_records WHERE isSynced = 1 AND syncedAt < :cutoffTime")
    suspend fun deleteOldSyncedRecords(cutoffTime: Long): Int
}

// Extension function for entity conversion
fun TransferRecord.toEntity(): TransferRecordEntity {
    return TransferRecordEntity(
        recordId = this.transferId,
        contentId = this.contentId,
        sourceDeviceId = this.sourceDeviceId,
        targetDeviceId = this.targetDeviceId,
        transferMethod = this.transferMethod.name,
        timestamp = this.timestamp,
        fileSize = this.fileSize,
        transferDuration = this.transferDuration,
        transferStatus = this.transferStatus.name,
        deviceSignature = this.deviceSignature
    )
}

/**
 * Playback analytics tracker with local SQLite storage
 * Manages playback records and prepares them for BLE sync
 */
class PlaybackAnalyticsTracker private constructor(
    private val context: Context,
    private val meshSyncManager: MusicAnalyticsMeshSyncInterface
) {
    
    companion object {
        private const val TAG = "PlaybackAnalyticsTracker"
        private const val MAX_RECORDS_IN_MEMORY = 1000
        private const val BATCH_SIZE = 50
        private const val SYNC_INTERVAL_MS = 30000L // 30 seconds
        
        @Volatile
        private var INSTANCE: PlaybackAnalyticsTracker? = null
        
        fun getInstance(context: Context): PlaybackAnalyticsTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // Create a placeholder mesh sync manager for now
                    // In production, this would be injected or retrieved from a service locator
                    val meshSync = createMeshSyncManager(context)
                    PlaybackAnalyticsTracker(context.applicationContext, meshSync).also { 
                        INSTANCE = it 
                        // Connect tracker to sync manager for callbacks
                        meshSync.setTracker(it)
                    }
                }
            }
        }
        
        private fun createMeshSyncManager(context: Context): MusicAnalyticsMeshSyncInterface {
            return try {
                Log.d(TAG, "Creating real mesh sync manager")
                val meshService = com.bitchat.android.service.MeshServiceHolder.getOrCreate(context)
                val deviceIdService = DeviceIdentificationService(context)
                MusicAnalyticsMeshSync(context, meshService, deviceIdService)
            } catch (e: Exception) {
                Log.w(TAG, "Could not create mesh sync manager, using stub", e)
                StubMusicAnalyticsMeshSync(context)
            }
        }
    }
    
    // Database migration from version 3 to 4 (added listening context fields)
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            Log.d(TAG, "Migrating database from version 3 to 4")
            
            // Add new columns to playback_records table
            database.execSQL("ALTER TABLE playback_records ADD COLUMN timeOfDayBucket TEXT NOT NULL DEFAULT 'UNKNOWN'")
            database.execSQL("ALTER TABLE playback_records ADD COLUMN dayOfWeek TEXT NOT NULL DEFAULT 'UNKNOWN'")
            database.execSQL("ALTER TABLE playback_records ADD COLUMN sessionDuration INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE playback_records ADD COLUMN playbackMode TEXT NOT NULL DEFAULT 'SEQUENTIAL'")
            database.execSQL("ALTER TABLE playback_records ADD COLUMN volumeLevelAvg REAL NOT NULL DEFAULT 0.5")
            database.execSQL("ALTER TABLE playback_records ADD COLUMN audioOutputType TEXT NOT NULL DEFAULT 'UNKNOWN'")
            
            Log.d(TAG, "Database migration from version 3 to 4 completed successfully")
        }
    }

    private val database by lazy { 
        Log.d(TAG, "Initializing Room database...")
        val db = Room.databaseBuilder(
            context.applicationContext,
            MusicAnalyticsDatabase::class.java,
            "music_analytics.db"
        )
        .addMigrations(MIGRATION_3_4)
        .build()
        Log.d(TAG, "Room database initialized successfully")
        db
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
                throw e // Re-throw to let caller handle
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
                throw e
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
     * Get pending transfer records for sync
     */
    suspend fun getPendingTransferRecordsForSync(limit: Int = BATCH_SIZE): List<TransferRecord> {
        return withContext(Dispatchers.IO) {
            try {
                database.transferRecordDao()
                    .getPendingSync(limit)
                    .map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pending transfer records", e)
                emptyList()
            }
        }
    }
    
    /**
     * Mark transfer records as synced
     */
    suspend fun markTransferRecordsAsSynced(recordIds: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                database.transferRecordDao().markAsSynced(recordIds)
                updateStatistics()
                Log.d(TAG, "Marked ${recordIds.size} transfer records as synced")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking transfer records as synced", e)
            }
        }
    }
    
    /**
     * Record a transfer event
     */
    suspend fun recordTransfer(record: TransferRecord) {
        withContext(Dispatchers.IO) {
            try {
                // Store in database
                database.transferRecordDao().insert(record.toEntity())
                
                // Update statistics
                updateStatistics()
                
                Log.d(TAG, "Recorded transfer: ${record.contentId} - ${record.transferMethod}")
                
                // Trigger sync if we have enough records
                val pendingCount = database.transferRecordDao().countPendingSync()
                if (pendingCount >= BATCH_SIZE) {
                    triggerSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording transfer", e)
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
            
            // Get pending transfer records
            val pendingTransferRecords = getPendingTransferRecordsForSync()
            if (pendingTransferRecords.isNotEmpty()) {
                meshSyncManager.syncTransferRecords(pendingTransferRecords)
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
    
    /**
     * Get all playback records for export
     */
    suspend fun getAllPlaybackRecords(): List<PlaybackRecord> {
        return withContext(Dispatchers.IO) {
            try {
                database.playbackRecordDao().getAllRecords().map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all playback records", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get all sharing records for export
     */
    suspend fun getAllSharingRecords(): List<SharingRecord> {
        return withContext(Dispatchers.IO) {
            try {
                database.sharingRecordDao().getAllRecords().map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all sharing records", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get all track metadata for export
     */
    suspend fun getAllTrackMetadata(): List<TrackMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                database.trackMetadataDao().getAllRecords().map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all track metadata", e)
                emptyList()
            }
        }
    }
    
    /**
     * Get all transfer records for export
     */
    suspend fun getAllTransferRecords(): List<TransferRecord> {
        return withContext(Dispatchers.IO) {
            try {
                database.transferRecordDao().getAllRecords().map { it.toModel() }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all transfer records", e)
                emptyList()
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

/**
 * Stub implementation of MusicAnalyticsMeshSync for development/testing
 * This allows the app to compile and run without requiring full mesh service setup
 */
private class StubMusicAnalyticsMeshSync(private val context: Context) : MusicAnalyticsMeshSyncInterface {
    
    companion object {
        private const val TAG = "StubMusicAnalyticsMeshSync"
    }
    
    override suspend fun syncPlaybackRecords(records: List<PlaybackRecord>) {
        Log.d(TAG, "Stub: Would sync ${records.size} playback records")
        // In stub mode, just mark as synced immediately
        delay(100) // Simulate network delay
    }
    
    override suspend fun syncTrackMetadata(metadata: List<TrackMetadata>) {
        Log.d(TAG, "Stub: Would sync ${metadata.size} track metadata records")
        delay(100)
    }
    
    override suspend fun syncSharingRecords(records: List<SharingRecord>) {
        Log.d(TAG, "Stub: Would sync ${records.size} sharing records")
        delay(100)
    }
    
    override suspend fun syncTransferRecords(records: List<TransferRecord>) {
        Log.d(TAG, "Stub: Would sync ${records.size} transfer records")
        delay(100)
    }
    
    override fun startAggregatorDiscovery() {
        Log.d(TAG, "Stub: Starting aggregator discovery")
    }
    
    override fun stopAggregatorDiscovery() {
        Log.d(TAG, "Stub: Stopping aggregator discovery")
    }
    
    override fun release() {
        Log.d(TAG, "Stub: Released")
    }

    override fun setTracker(tracker: PlaybackAnalyticsTracker) {
        Log.d(TAG, "Stub: setTracker called")
    }
}