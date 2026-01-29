package com.bitchat.android.music

import android.content.Context
import android.util.Log
import com.bitchat.android.music.model.PlaybackRecord
import com.bitchat.android.music.model.SharingRecord
import com.bitchat.android.music.model.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream

/**
 * Exports aggregated music analytics data to various formats
 * Supports CSV, JSON, and XML formats for integration with different systems
 */
class AggregatorDataExporter(private val context: Context) {
    
    companion object {
        private const val TAG = "AggregatorDataExporter"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
    
    enum class ExportFormat {
        CSV, JSON, XML, XLSX
    }
    
    data class ExportResult(
        val success: Boolean,
        val filePath: String?,
        val recordCount: Int,
        val error: String? = null
    )
    
    /**
     * Export playback records to specified format
     */
    suspend fun exportPlaybackRecords(
        records: List<PlaybackRecord>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportFilename = filename ?: generateFilename("playback_records", format)
            val file = getExportFile(exportFilename)
            
            when (format) {
                ExportFormat.CSV -> exportPlaybackRecordsToCSV(records, file)
                ExportFormat.JSON -> exportPlaybackRecordsToJSON(records, file)
                ExportFormat.XML -> exportPlaybackRecordsToXML(records, file)
                ExportFormat.XLSX -> {
                   /* For individual exports, we can just use the comprehensive export logic but only populate one sheet
                      or implementing specific single-sheet exports. 
                      For simplicity and consistency, let's treat single exports as a subset.
                      However, to match existing patterns, we'll implement specific XLSX exporters or throw unsupported for now 
                      if we want to be strict, but better to support it. 
                      Let's implement a generic single-sheet excel export helper. */
                      exportRecordsToExcel(records, "Playback_Records", file)
                }
            }
            
            // Verify file was created and has content
            if (file.exists() && file.length() > 0) {
                Log.i(TAG, "Successfully exported ${records.size} playback records to ${file.absolutePath}")
                ExportResult(true, file.absolutePath, records.size)
            } else {
                Log.e(TAG, "Export file was not created or is empty: ${file.absolutePath}")
                ExportResult(false, null, 0, "Export file was not created or is empty")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export playback records", e)
            ExportResult(false, null, 0, e.message)
        }
    }
    
    /**
     * Export sharing records to specified format
     */
    suspend fun exportSharingRecords(
        records: List<SharingRecord>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportFilename = filename ?: generateFilename("sharing_records", format)
            val file = getExportFile(exportFilename)
            
            when (format) {
                ExportFormat.CSV -> exportSharingRecordsToCSV(records, file)
                ExportFormat.JSON -> exportSharingRecordsToJSON(records, file)
                ExportFormat.XML -> exportSharingRecordsToXML(records, file)
                ExportFormat.XLSX -> exportRecordsToExcel(records, "Sharing_Records", file)
            }
            
            Log.i(TAG, "Exported ${records.size} sharing records to ${file.absolutePath}")
            ExportResult(true, file.absolutePath, records.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export sharing records", e)
            ExportResult(false, null, 0, e.message)
        }
    }
    
    /**
     * Export transfer records to specified format
     */
    suspend fun exportTransferRecords(
        records: List<com.bitchat.android.music.model.TransferRecord>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportFilename = filename ?: generateFilename("transfer_records", format)
            val file = getExportFile(exportFilename)
            
            when (format) {
                ExportFormat.CSV -> exportTransferRecordsToCSV(records, file)
                ExportFormat.JSON -> exportTransferRecordsToJSON(records, file)
                ExportFormat.XML -> exportTransferRecordsToXML(records, file)
                ExportFormat.XLSX -> exportTransferRecordsToExcel(records, file)
            }
            
            Log.i(TAG, "Exported ${records.size} transfer records to ${file.absolutePath}")
            ExportResult(true, file.absolutePath, records.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export transfer records", e)
            ExportResult(false, null, 0, e.message)
        }
    }
    
    /**
     * Export track metadata to specified format
     */
    suspend fun exportTrackMetadata(
        tracks: List<TrackMetadata>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportFilename = filename ?: generateFilename("track_metadata", format)
            val file = getExportFile(exportFilename)
            
            when (format) {
                ExportFormat.CSV -> exportTrackMetadataToCSV(tracks, file)
                ExportFormat.JSON -> exportTrackMetadataToJSON(tracks, file)
                ExportFormat.XML -> exportTrackMetadataToXML(tracks, file)
                ExportFormat.XLSX -> exportRecordsToExcel(tracks, "Track_Metadata", file)
            }
            
            Log.i(TAG, "Exported ${tracks.size} track metadata records to ${file.absolutePath}")
            ExportResult(true, file.absolutePath, tracks.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export track metadata", e)
            ExportResult(false, null, 0, e.message)
        }
    }
    
    /**
     * Export comprehensive analytics report (all data types in one file with multiple sheets/sections)
     */
    suspend fun exportComprehensiveReport(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        exportComprehensiveReportWithTransfers(
            playbackRecords = playbackRecords,
            sharingRecords = sharingRecords,
            trackMetadata = trackMetadata,
            transferRecords = emptyList(),
            format = format,
            filename = filename
        )
    }
    
    /**
     * Export comprehensive analytics report including transfer records
     */
    suspend fun exportComprehensiveReportWithTransfers(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        transferRecords: List<com.bitchat.android.music.model.TransferRecord>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportFilename = filename ?: generateFilename("comprehensive_report", format)
            val file = getExportFile(exportFilename)
            
            val totalRecords = playbackRecords.size + sharingRecords.size + trackMetadata.size + transferRecords.size
            Log.d(TAG, "Starting comprehensive export of $totalRecords records to ${file.absolutePath}")
            
            when (format) {
                ExportFormat.JSON -> exportComprehensiveReportToJSONWithTransfers(
                    playbackRecords, sharingRecords, trackMetadata, transferRecords, file
                )
                ExportFormat.XML -> exportComprehensiveReportToXMLWithTransfers(
                    playbackRecords, sharingRecords, trackMetadata, transferRecords, file
                )
                ExportFormat.CSV -> {
                    // For CSV comprehensive, create a single file with multiple data sections (sheets)
                    exportComprehensiveReportToCSVWithTransfers(playbackRecords, sharingRecords, trackMetadata, transferRecords, file)
                }
                ExportFormat.XLSX -> exportComprehensiveReportToExcelWithTransfers(
                    playbackRecords, sharingRecords, trackMetadata, transferRecords, file
                )
            }
            
            // Verify file was created and has content
            if (file.exists() && file.length() > 0) {
                Log.i(TAG, "Successfully exported comprehensive report with $totalRecords total records to ${file.absolutePath} (${file.length()} bytes)")
                ExportResult(true, file.absolutePath, totalRecords)
            } else {
                Log.e(TAG, "Comprehensive export file was not created or is empty: ${file.absolutePath}")
                ExportResult(false, null, 0, "Export file was not created or is empty")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export comprehensive report", e)
            ExportResult(false, null, 0, e.message)
        }
    }
    
    // CSV Export Functions
    private fun exportPlaybackRecordsToCSV(records: List<PlaybackRecord>, file: File) {
        try {
            Log.d(TAG, "Exporting ${records.size} playback records to CSV: ${file.absolutePath}")
            
            FileWriter(file).use { writer ->
                // Header with all fields including listening context data
                writer.append("record_id,content_id,device_id,timestamp,duration_played,track_duration,play_percentage,skip_count,repeat_flag,source_type,time_of_day_bucket,day_of_week,session_duration,playback_mode,volume_level_avg,audio_output_type\n")
                
                // Data rows
                records.forEach { record ->
                    writer.append("${record.recordId},")
                    writer.append("${record.contentId},")
                    writer.append("${record.deviceId},")
                    writer.append("${dateFormat.format(Date(record.timestamp))},")
                    writer.append("${record.durationPlayed},")
                    writer.append("${record.trackDuration},")
                    writer.append("${record.playPercentage},")
                    writer.append("${record.skipCount},")
                    writer.append("${record.repeatFlag},")
                    writer.append("${record.sourceType},")
                    writer.append("${record.timeOfDayBucket},")
                    writer.append("${record.dayOfWeek},")
                    writer.append("${record.sessionDuration},")
                    writer.append("${record.playbackMode},")
                    writer.append("${record.volumeLevelAvg},")
                    writer.append("${record.audioOutputType}\n")
                }
            }
            
            Log.d(TAG, "Successfully exported playback records CSV: ${file.absolutePath} (${file.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export playback records to CSV: ${file.absolutePath}", e)
            throw e
        }
    }
    
    private fun exportSharingRecordsToCSV(records: List<SharingRecord>, file: File) {
        try {
            Log.d(TAG, "Exporting ${records.size} sharing records to CSV: ${file.absolutePath}")
            
            FileWriter(file).use { writer ->
                // Header
                writer.append("record_id,content_id,sharer_device_id,recipient_device_id,timestamp,file_size,share_method\n")
                
                // Data rows
                records.forEach { record ->
                    writer.append("${record.recordId},")
                    writer.append("${record.contentId},")
                    writer.append("${record.sharerDeviceId},")
                    writer.append("${record.recipientDeviceId ?: ""},")
                    writer.append("${dateFormat.format(Date(record.timestamp))},")
                    writer.append("${record.fileSize},")
                    writer.append("${record.shareMethod}\n")
                }
            }
            
            Log.d(TAG, "Successfully exported sharing records CSV: ${file.absolutePath} (${file.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export sharing records to CSV: ${file.absolutePath}", e)
            throw e
        }
    }
    
    private fun exportTrackMetadataToCSV(tracks: List<TrackMetadata>, file: File) {
        try {
            Log.d(TAG, "Exporting ${tracks.size} track metadata records to CSV: ${file.absolutePath}")
            
            FileWriter(file).use { writer ->
                // Header
                writer.append("content_id,title,artist,album,duration,first_seen\n")
                
                // Data rows
                tracks.forEach { track ->
                    writer.append("${track.contentId},")
                    writer.append("\"${track.title.replace("\"", "\"\"")}\",") // Escape quotes
                    writer.append("\"${track.artist.replace("\"", "\"\"")}\",")
                    writer.append("\"${track.album?.replace("\"", "\"\"") ?: ""}\",")
                    writer.append("${track.duration},")
                    writer.append("${dateFormat.format(Date(track.firstSeen))}\n")
                }
            }
            
            Log.d(TAG, "Successfully exported track metadata CSV: ${file.absolutePath} (${file.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export track metadata to CSV: ${file.absolutePath}", e)
            throw e
        }
    }
    
    private fun exportComprehensiveReportToCSV(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        file: File
    ) {
        try {
            Log.d(TAG, "Exporting comprehensive report to CSV with multiple sheets: ${file.absolutePath}")
            
            FileWriter(file).use { writer ->
                // File header with export information
                writer.append("# Comprehensive Music Analytics Export\n")
                writer.append("# Generated: ${dateFormat.format(Date())}\n")
                writer.append("# Total Records: ${playbackRecords.size + sharingRecords.size + trackMetadata.size}\n")
                writer.append("# \n")
                writer.append("# This file contains multiple data sheets separated by sheet markers\n")
                writer.append("# Import instructions: Use sheet markers to split data when importing\n")
                writer.append("# \n")
                writer.append("\n")
                
                // Sheet 1: Playback Records
                writer.append("### SHEET: Playback_Records ###\n")
                writer.append("# Records: ${playbackRecords.size}\n")
                writer.append("# Description: Detailed playback analytics with listening context data\n")
                writer.append("record_id,content_id,device_id,timestamp,duration_played,track_duration,play_percentage,skip_count,repeat_flag,source_type,time_of_day_bucket,day_of_week,session_duration,playback_mode,volume_level_avg,audio_output_type\n")
                
                playbackRecords.forEach { record ->
                    writer.append("${record.recordId},")
                    writer.append("${record.contentId},")
                    writer.append("${record.deviceId},")
                    writer.append("${dateFormat.format(Date(record.timestamp))},")
                    writer.append("${record.durationPlayed},")
                    writer.append("${record.trackDuration},")
                    writer.append("${record.playPercentage},")
                    writer.append("${record.skipCount},")
                    writer.append("${record.repeatFlag},")
                    writer.append("${record.sourceType},")
                    writer.append("${record.timeOfDayBucket},")
                    writer.append("${record.dayOfWeek},")
                    writer.append("${record.sessionDuration},")
                    writer.append("${record.playbackMode},")
                    writer.append("${record.volumeLevelAvg},")
                    writer.append("${record.audioOutputType}\n")
                }
                
                writer.append("\n\n")
                
                // Sheet 2: Sharing Records
                writer.append("### SHEET: Sharing_Records ###\n")
                writer.append("# Records: ${sharingRecords.size}\n")
                writer.append("# Description: Music sharing and transfer activity\n")
                writer.append("record_id,content_id,sharer_device_id,recipient_device_id,timestamp,file_size,share_method\n")
                
                sharingRecords.forEach { record ->
                    writer.append("${record.recordId},")
                    writer.append("${record.contentId},")
                    writer.append("${record.sharerDeviceId},")
                    writer.append("${record.recipientDeviceId ?: ""},")
                    writer.append("${dateFormat.format(Date(record.timestamp))},")
                    writer.append("${record.fileSize},")
                    writer.append("${record.shareMethod}\n")
                }
                
                writer.append("\n\n")
                
                // Sheet 3: Track Metadata
                writer.append("### SHEET: Track_Metadata ###\n")
                writer.append("# Records: ${trackMetadata.size}\n")
                writer.append("# Description: Track information and metadata\n")
                writer.append("content_id,title,artist,album,duration,first_seen\n")
                
                trackMetadata.forEach { track ->
                    writer.append("${track.contentId},")
                    writer.append("\"${track.title.replace("\"", "\"\"")}\",")
                    writer.append("\"${track.artist.replace("\"", "\"\"")}\",")
                    writer.append("\"${track.album?.replace("\"", "\"\"") ?: ""}\",")
                    writer.append("${track.duration},")
                    writer.append("${dateFormat.format(Date(track.firstSeen))}\n")
                }
                
                writer.append("\n\n")
                
                // Sheet 4: Summary Statistics
                writer.append("### SHEET: Summary_Statistics ###\n")
                writer.append("# Records: Summary data\n")
                writer.append("# Description: Aggregated statistics and insights\n")
                writer.append("metric,value,description\n")
                
                // Calculate summary statistics
                val totalPlayTime = playbackRecords.sumOf { it.durationPlayed }
                val avgPlayPercentage = if (playbackRecords.isNotEmpty()) {
                    playbackRecords.map { it.playPercentage }.average()
                } else 0.0
                val qualifyingPlays = playbackRecords.count { it.qualifiesForRoyalty() }
                val uniqueTracks = playbackRecords.map { it.contentId }.distinct().size
                val totalShares = sharingRecords.size
                val totalTracksInLibrary = trackMetadata.size
                
                // Time-based insights
                val morningPlays = playbackRecords.count { it.timeOfDayBucket.name == "MORNING" }
                val afternoonPlays = playbackRecords.count { it.timeOfDayBucket.name == "AFTERNOON" }
                val eveningPlays = playbackRecords.count { it.timeOfDayBucket.name == "EVENING" }
                val nightPlays = playbackRecords.count { it.timeOfDayBucket.name == "NIGHT" }
                
                val weekdayPlays = playbackRecords.count { it.dayOfWeek.name == "WEEKDAY" }
                val weekendPlays = playbackRecords.count { it.dayOfWeek.name == "WEEKEND" }
                
                // Playback mode insights
                val sequentialPlays = playbackRecords.count { it.playbackMode.name == "SEQUENTIAL" }
                val shufflePlays = playbackRecords.count { it.playbackMode.name == "SHUFFLE" }
                val repeatPlays = playbackRecords.count { it.repeatFlag }
                
                // Audio output insights
                val speakerPlays = playbackRecords.count { it.audioOutputType.name == "SPEAKER" }
                val headphoneePlays = playbackRecords.count { 
                    it.audioOutputType.name == "WIRED_HEADPHONES" || it.audioOutputType.name == "BLUETOOTH_HEADPHONES" 
                }
                
                // Write summary statistics
                writer.append("Total Playback Records,${playbackRecords.size},Number of individual playback events\n")
                writer.append("Total Play Time (seconds),${totalPlayTime},Total seconds of music played\n")
                writer.append("Total Play Time (hours),${totalPlayTime / 3600.0},Total hours of music played\n")
                writer.append("Average Play Percentage,${String.format("%.2f", avgPlayPercentage * 100)}%,Average percentage of tracks completed\n")
                writer.append("Qualifying Plays,${qualifyingPlays},Plays that qualify for royalty (30s or 50% of track)\n")
                writer.append("Unique Tracks Played,${uniqueTracks},Number of different tracks played\n")
                writer.append("Total Sharing Records,${totalShares},Number of music sharing events\n")
                writer.append("Total Tracks in Library,${totalTracksInLibrary},Number of tracks in music library\n")
                writer.append("\n")
                writer.append("Morning Plays,${morningPlays},Plays between 6AM-12PM\n")
                writer.append("Afternoon Plays,${afternoonPlays},Plays between 12PM-6PM\n")
                writer.append("Evening Plays,${eveningPlays},Plays between 6PM-12AM\n")
                writer.append("Night Plays,${nightPlays},Plays between 12AM-6AM\n")
                writer.append("\n")
                writer.append("Weekday Plays,${weekdayPlays},Plays on Monday-Friday\n")
                writer.append("Weekend Plays,${weekendPlays},Plays on Saturday-Sunday\n")
                writer.append("\n")
                writer.append("Sequential Plays,${sequentialPlays},Tracks played in order\n")
                writer.append("Shuffle Plays,${shufflePlays},Tracks played in random order\n")
                writer.append("Repeat Plays,${repeatPlays},Tracks played with repeat enabled\n")
                writer.append("\n")
                writer.append("Speaker Plays,${speakerPlays},Plays through device speakers\n")
                writer.append("Headphone Plays,${headphoneePlays},Plays through headphones (wired or Bluetooth)\n")
                
                writer.append("\n### END OF EXPORT ###\n")
            }
            
            Log.d(TAG, "Successfully exported comprehensive CSV with multiple sheets: ${file.absolutePath} (${file.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export comprehensive CSV: ${file.absolutePath}", e)
            throw e
        }
    }
    
    // JSON Export Functions
    private fun exportPlaybackRecordsToJSON(records: List<PlaybackRecord>, file: File) {
        val jsonArray = JSONArray()
        
        records.forEach { record ->
            val jsonObject = JSONObject().apply {
                put("record_id", record.recordId)
                put("content_id", record.contentId)
                put("device_id", record.deviceId)
                put("timestamp", record.timestamp)
                put("timestamp_formatted", dateFormat.format(Date(record.timestamp)))
                put("duration_played", record.durationPlayed)
                put("track_duration", record.trackDuration)
                put("play_percentage", record.playPercentage)
                put("skip_count", record.skipCount)
                put("repeat_flag", record.repeatFlag)
                put("source_type", record.sourceType.toString())
            }
            jsonArray.put(jsonObject)
        }
        
        val rootObject = JSONObject().apply {
            put("export_type", "playback_records")
            put("export_timestamp", System.currentTimeMillis())
            put("export_timestamp_formatted", dateFormat.format(Date()))
            put("record_count", records.size)
            put("records", jsonArray)
        }
        
        FileWriter(file).use { writer ->
            writer.write(rootObject.toString(2)) // Pretty print with 2-space indent
        }
    }
    
    private fun exportSharingRecordsToJSON(records: List<SharingRecord>, file: File) {
        val jsonArray = JSONArray()
        
        records.forEach { record ->
            val jsonObject = JSONObject().apply {
                put("record_id", record.recordId)
                put("content_id", record.contentId)
                put("sharer_device_id", record.sharerDeviceId)
                put("recipient_device_id", record.recipientDeviceId)
                put("timestamp", record.timestamp)
                put("timestamp_formatted", dateFormat.format(Date(record.timestamp)))
                put("file_size", record.fileSize)
                put("share_method", record.shareMethod.toString())
            }
            jsonArray.put(jsonObject)
        }
        
        val rootObject = JSONObject().apply {
            put("export_type", "sharing_records")
            put("export_timestamp", System.currentTimeMillis())
            put("export_timestamp_formatted", dateFormat.format(Date()))
            put("record_count", records.size)
            put("records", jsonArray)
        }
        
        FileWriter(file).use { writer ->
            writer.write(rootObject.toString(2))
        }
    }
    
    private fun exportTrackMetadataToJSON(tracks: List<TrackMetadata>, file: File) {
        val jsonArray = JSONArray()
        
        tracks.forEach { track ->
            val jsonObject = JSONObject().apply {
                put("content_id", track.contentId)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("duration", track.duration)
                put("first_seen", track.firstSeen)
                put("first_seen_formatted", dateFormat.format(Date(track.firstSeen)))
            }
            jsonArray.put(jsonObject)
        }
        
        val rootObject = JSONObject().apply {
            put("export_type", "track_metadata")
            put("export_timestamp", System.currentTimeMillis())
            put("export_timestamp_formatted", dateFormat.format(Date()))
            put("record_count", tracks.size)
            put("tracks", jsonArray)
        }
        
        FileWriter(file).use { writer ->
            writer.write(rootObject.toString(2))
        }
    }
    
    private fun exportComprehensiveReportToJSON(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        file: File
    ) {
        val rootObject = JSONObject().apply {
            put("export_type", "comprehensive_report")
            put("export_timestamp", System.currentTimeMillis())
            put("export_timestamp_formatted", dateFormat.format(Date()))
            
            // Summary statistics
            put("summary", JSONObject().apply {
                put("total_playback_records", playbackRecords.size)
                put("total_sharing_records", sharingRecords.size)
                put("total_track_metadata", trackMetadata.size)
                put("unique_tracks", trackMetadata.map { it.contentId }.toSet().size)
                put("unique_devices", (playbackRecords.map { it.deviceId } + 
                                     sharingRecords.map { it.sharerDeviceId }).toSet().size)
            })
            
            // Individual data sections
            put("playback_records", JSONArray().apply {
                playbackRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("record_id", record.recordId)
                        put("content_id", record.contentId)
                        put("device_id", record.deviceId)
                        put("timestamp", record.timestamp)
                        put("duration_played", record.durationPlayed)
                        put("track_duration", record.trackDuration)
                        put("play_percentage", record.playPercentage)
                        put("skip_count", record.skipCount)
                        put("repeat_flag", record.repeatFlag)
                        put("source_type", record.sourceType.toString())
                    })
                }
            })
            
            put("sharing_records", JSONArray().apply {
                sharingRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("record_id", record.recordId)
                        put("content_id", record.contentId)
                        put("sharer_device_id", record.sharerDeviceId)
                        put("recipient_device_id", record.recipientDeviceId)
                        put("timestamp", record.timestamp)
                        put("file_size", record.fileSize)
                        put("share_method", record.shareMethod.toString())
                    })
                }
            })
            
            put("track_metadata", JSONArray().apply {
                trackMetadata.forEach { track ->
                    put(JSONObject().apply {
                        put("content_id", track.contentId)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("duration", track.duration)
                        put("first_seen", track.firstSeen)
                    })
                }
            })
        }
        
        FileWriter(file).use { writer ->
            writer.write(rootObject.toString(2))
        }
    }
    
    // XML Export Functions (simplified implementation)
    private fun exportPlaybackRecordsToXML(records: List<PlaybackRecord>, file: File) {
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<playback_records export_timestamp=\"${System.currentTimeMillis()}\" record_count=\"${records.size}\">\n")
            
            records.forEach { record ->
                writer.append("  <record>\n")
                writer.append("    <record_id>${record.recordId}</record_id>\n")
                writer.append("    <content_id>${record.contentId}</content_id>\n")
                writer.append("    <device_id>${record.deviceId}</device_id>\n")
                writer.append("    <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("    <duration_played>${record.durationPlayed}</duration_played>\n")
                writer.append("    <track_duration>${record.trackDuration}</track_duration>\n")
                writer.append("    <play_percentage>${record.playPercentage}</play_percentage>\n")
                writer.append("    <skip_count>${record.skipCount}</skip_count>\n")
                writer.append("    <repeat_flag>${record.repeatFlag}</repeat_flag>\n")
                writer.append("    <source_type>${record.sourceType}</source_type>\n")
                writer.append("  </record>\n")
            }
            
            writer.append("</playback_records>\n")
        }
    }
    
    private fun exportSharingRecordsToXML(records: List<SharingRecord>, file: File) {
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<sharing_records export_timestamp=\"${System.currentTimeMillis()}\" record_count=\"${records.size}\">\n")
            
            records.forEach { record ->
                writer.append("  <record>\n")
                writer.append("    <record_id>${record.recordId}</record_id>\n")
                writer.append("    <content_id>${record.contentId}</content_id>\n")
                writer.append("    <sharer_device_id>${record.sharerDeviceId}</sharer_device_id>\n")
                writer.append("    <recipient_device_id>${record.recipientDeviceId ?: ""}</recipient_device_id>\n")
                writer.append("    <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("    <file_size>${record.fileSize}</file_size>\n")
                writer.append("    <share_method>${record.shareMethod}</share_method>\n")
                writer.append("  </record>\n")
            }
            
            writer.append("</sharing_records>\n")
        }
    }
    
    private fun exportTrackMetadataToXML(tracks: List<TrackMetadata>, file: File) {
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<track_metadata export_timestamp=\"${System.currentTimeMillis()}\" record_count=\"${tracks.size}\">\n")
            
            tracks.forEach { track ->
                writer.append("  <track>\n")
                writer.append("    <content_id>${track.contentId}</content_id>\n")
                writer.append("    <title><![CDATA[${track.title}]]></title>\n")
                writer.append("    <artist><![CDATA[${track.artist}]]></artist>\n")
                writer.append("    <album><![CDATA[${track.album ?: ""}]]></album>\n")
                writer.append("    <duration>${track.duration}</duration>\n")
                writer.append("    <first_seen>${track.firstSeen}</first_seen>\n")
                writer.append("  </track>\n")
            }
            
            writer.append("</track_metadata>\n")
        }
    }
    
    private fun exportComprehensiveReportToXML(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        file: File
    ) {
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<comprehensive_report export_timestamp=\"${System.currentTimeMillis()}\">\n")
            
            // Summary
            writer.append("  <summary>\n")
            writer.append("    <total_playback_records>${playbackRecords.size}</total_playback_records>\n")
            writer.append("    <total_sharing_records>${sharingRecords.size}</total_sharing_records>\n")
            writer.append("    <total_track_metadata>${trackMetadata.size}</total_track_metadata>\n")
            writer.append("  </summary>\n")
            
            // Playback records section
            writer.append("  <playback_records>\n")
            playbackRecords.forEach { record ->
                writer.append("    <record>\n")
                writer.append("      <record_id>${record.recordId}</record_id>\n")
                writer.append("      <content_id>${record.contentId}</content_id>\n")
                writer.append("      <device_id>${record.deviceId}</device_id>\n")
                writer.append("      <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("      <duration_played>${record.durationPlayed}</duration_played>\n")
                writer.append("      <track_duration>${record.trackDuration}</track_duration>\n")
                writer.append("      <play_percentage>${record.playPercentage}</play_percentage>\n")
                writer.append("      <skip_count>${record.skipCount}</skip_count>\n")
                writer.append("      <repeat_flag>${record.repeatFlag}</repeat_flag>\n")
                writer.append("      <source_type>${record.sourceType}</source_type>\n")
                writer.append("    </record>\n")
            }
            writer.append("  </playback_records>\n")
            
            // Sharing records section
            writer.append("  <sharing_records>\n")
            sharingRecords.forEach { record ->
                writer.append("    <record>\n")
                writer.append("      <record_id>${record.recordId}</record_id>\n")
                writer.append("      <content_id>${record.contentId}</content_id>\n")
                writer.append("      <sharer_device_id>${record.sharerDeviceId}</sharer_device_id>\n")
                writer.append("      <recipient_device_id>${record.recipientDeviceId ?: ""}</recipient_device_id>\n")
                writer.append("      <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("      <file_size>${record.fileSize}</file_size>\n")
                writer.append("      <share_method>${record.shareMethod}</share_method>\n")
                writer.append("    </record>\n")
            }
            writer.append("  </sharing_records>\n")
            
            // Track metadata section
            writer.append("  <track_metadata>\n")
            trackMetadata.forEach { track ->
                writer.append("    <track>\n")
                writer.append("      <content_id>${track.contentId}</content_id>\n")
                writer.append("      <title><![CDATA[${track.title}]]></title>\n")
                writer.append("      <artist><![CDATA[${track.artist}]]></artist>\n")
                writer.append("      <album><![CDATA[${track.album ?: ""}]]></album>\n")
                writer.append("      <duration>${track.duration}</duration>\n")
                writer.append("      <first_seen>${track.firstSeen}</first_seen>\n")
                writer.append("    </track>\n")
            }
            writer.append("  </track_metadata>\n")
            
            writer.append("</comprehensive_report>\n")
        }
    }
    
    private fun generateFilename(prefix: String, format: ExportFormat): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = when (format) {
            ExportFormat.CSV -> "csv"
            ExportFormat.JSON -> "json"
            ExportFormat.XML -> "xml"
            ExportFormat.XLSX -> "xlsx"
        }
        return "${prefix}_${timestamp}.${extension}"
    }
    
    /**
     * Get the export file location - uses app-specific external files directory
     * which is accessible via FileProvider for sharing
     */
    private fun getExportFile(filename: String): File {
        // Use app-specific external files directory with exports subdirectory
        val exportDir = context.getExternalFilesDir("exports") ?: File(context.filesDir, "exports")
        
        // Ensure directory exists
        if (!exportDir.exists()) {
            val created = exportDir.mkdirs()
            Log.d(TAG, "Created export directory: ${exportDir.absolutePath}, success: $created")
        }
        
        val file = File(exportDir, filename)
        Log.d(TAG, "Export file path: ${file.absolutePath}")
        Log.d(TAG, "Export directory exists: ${exportDir.exists()}, canWrite: ${exportDir.canWrite()}")
        
        return file
    }

    // Excel Export Functions
    private fun exportComprehensiveReportToExcel(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        file: File
    ) {
        val workbook = XSSFWorkbook()

        try {
            // Sheet 1: Summary Statistics (Aggregated Data)
            val summarySheet = workbook.createSheet("Summary_Statistics")
            var rowNum = 0
            
            // Header
            var row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue("Metric")
            row.createCell(1).setCellValue("Value")
            row.createCell(2).setCellValue("Description")

            // Calculate stats
            val totalPlayTime = playbackRecords.sumOf { it.durationPlayed }
            val avgPlayPercentage = if (playbackRecords.isNotEmpty()) {
                playbackRecords.map { it.playPercentage }.average()
            } else 0.0
            val qualifyingPlays = playbackRecords.count { it.qualifiesForRoyalty() }
            val uniqueTracks = playbackRecords.map { it.contentId }.distinct().size
            val totalShares = sharingRecords.size
            val totalTracksInLibrary = trackMetadata.size

            // Populate rows
            val summaryData = listOf(
                Triple("Total Playback Records", playbackRecords.size.toString(), "Number of individual playback events"),
                Triple("Total Play Time (seconds)", totalPlayTime.toString(), "Total seconds of music played"),
                Triple("Total Play Time (hours)", (totalPlayTime / 3600.0).toString(), "Total hours of music played"),
                Triple("Average Play Percentage", String.format("%.2f%%", avgPlayPercentage * 100), "Average percentage of tracks completed"),
                Triple("Qualifying Plays", qualifyingPlays.toString(), "Plays that qualify for royalty (30s or 50% of track)"),
                Triple("Unique Tracks Played", uniqueTracks.toString(), "Number of different tracks played"),
                Triple("Total Sharing Records", totalShares.toString(), "Number of music sharing events"),
                Triple("Total Tracks in Library", totalTracksInLibrary.toString(), "Number of tracks in music library")
            )

            summaryData.forEach { (metric, value, desc) ->
                row = summarySheet.createRow(rowNum++)
                row.createCell(0).setCellValue(metric)
                row.createCell(1).setCellValue(value)
                row.createCell(2).setCellValue(desc)
            }
            
            // Add Time of Day stats
             rowNum++ // spacer
            val timeHeader = summarySheet.createRow(rowNum++)
            timeHeader.createCell(0).setCellValue("Time of Day Analysis")
            
            val timeData = listOf(
                 "MORNING" to playbackRecords.count { it.timeOfDayBucket.name == "MORNING" },
                 "AFTERNOON" to playbackRecords.count { it.timeOfDayBucket.name == "AFTERNOON" },
                 "EVENING" to playbackRecords.count { it.timeOfDayBucket.name == "EVENING" },
                 "NIGHT" to playbackRecords.count { it.timeOfDayBucket.name == "NIGHT" }
            )
             timeData.forEach { (time, count) ->
                row = summarySheet.createRow(rowNum++)
                row.createCell(0).setCellValue(time)
                row.createCell(1).setCellValue(count.toString())
            }

            // Sheet 2: Playback Records
            val playbackSheet = workbook.createSheet("Playback_Records")
            rowNum = 0
            
            // Header
            val playbackHeaders = listOf(
                "record_id", "content_id", "device_id", "timestamp", "duration_played", 
                "track_duration", "play_percentage", "skip_count", "repeat_flag", 
                "source_type", "time_of_day_bucket", "day_of_week", "session_duration", 
                "playback_mode", "volume_level_avg", "audio_output_type"
            )
            row = playbackSheet.createRow(rowNum++)
            playbackHeaders.forEachIndexed { index, header ->
                row.createCell(index).setCellValue(header)
            }

            // Data
            playbackRecords.forEach { record ->
                row = playbackSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(record.recordId)
                row.createCell(1).setCellValue(record.contentId)
                row.createCell(2).setCellValue(record.deviceId)
                row.createCell(3).setCellValue(dateFormat.format(Date(record.timestamp)))
                row.createCell(4).setCellValue(record.durationPlayed.toDouble())
                row.createCell(5).setCellValue(record.trackDuration.toDouble())
                row.createCell(6).setCellValue(record.playPercentage.toDouble())
                row.createCell(7).setCellValue(record.skipCount.toDouble())
                row.createCell(8).setCellValue(record.repeatFlag)
                row.createCell(9).setCellValue(record.sourceType.toString())
                row.createCell(10).setCellValue(record.timeOfDayBucket.toString())
                row.createCell(11).setCellValue(record.dayOfWeek.toString())
                row.createCell(12).setCellValue(record.sessionDuration.toDouble())
                row.createCell(13).setCellValue(record.playbackMode.toString())
                row.createCell(14).setCellValue(record.volumeLevelAvg.toDouble())
                row.createCell(15).setCellValue(record.audioOutputType.toString())
            }

            // Sheet 3: Sharing Records
            val sharingSheet = workbook.createSheet("Sharing_Records")
            rowNum = 0
            val sharingHeaders = listOf(
                 "record_id", "content_id", "sharer_device_id", "recipient_device_id", 
                 "timestamp", "file_size", "share_method"
            )
            row = sharingSheet.createRow(rowNum++)
            sharingHeaders.forEachIndexed { index, header ->
                 row.createCell(index).setCellValue(header)
            }
            
            sharingRecords.forEach { record ->
                row = sharingSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(record.recordId)
                row.createCell(1).setCellValue(record.contentId)
                row.createCell(2).setCellValue(record.sharerDeviceId)
                row.createCell(3).setCellValue(record.recipientDeviceId ?: "")
                row.createCell(4).setCellValue(dateFormat.format(Date(record.timestamp)))
                row.createCell(5).setCellValue(record.fileSize.toDouble())
                row.createCell(6).setCellValue(record.shareMethod.toString())
            }

            // Sheet 4: Track Metadata
            val trackSheet = workbook.createSheet("Track_Metadata")
            rowNum = 0
            val trackHeaders = listOf(
                "content_id", "title", "artist", "album", "duration", "first_seen"
            )
            row = trackSheet.createRow(rowNum++)
            trackHeaders.forEachIndexed { index, header ->
                row.createCell(index).setCellValue(header)
            }
            
            trackMetadata.forEach { track ->
                row = trackSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(track.contentId)
                row.createCell(1).setCellValue(track.title)
                row.createCell(2).setCellValue(track.artist)
                row.createCell(3).setCellValue(track.album ?: "")
                row.createCell(4).setCellValue(track.duration.toDouble())
                row.createCell(5).setCellValue(dateFormat.format(Date(track.firstSeen)))
            }
            
            // Auto-size columns (optional, can be slow for large data, maybe skip for performance or limit to first few rows)
            // For now, let's skip autosizing to ensure speed.

            // Write to file
             FileOutputStream(file).use { fileOut ->
                workbook.write(fileOut)
            }
            Log.i(TAG, "Successfully exported comprehensive Excel report to ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export comprehensive Excel report", e)
            throw e
        } finally {
            workbook.close()
        }
    }

    private fun <T> exportRecordsToExcel(records: List<T>, sheetName: String, file: File) {
         val workbook = XSSFWorkbook()
         try {
             val sheet = workbook.createSheet(sheetName)
             var rowNum = 0
             var row = sheet.createRow(rowNum++)
             
             // Dynamic reflection or specific logic based on type T
             // Since we have specific lists, it's safer to handle them explicitly or reuse the logic above.
             // But for this generic helper to work with List<T>, we need to know the type T.
             // Instead of complex reflection, let's do a simple check on the first item or pass a writer lambda.
             // Given the context, we can just delegate to specific implementations if we want strict typing, 
             // or since we are inside the class, we know what we passed.
             
             // Actually, let's implement simple specific versions for the single exports to match the pattern:
             if (records.isNotEmpty()) {
                 val first = records.first()
                 when (first) {
                     is PlaybackRecord -> {
                         // Headers
                         val headers = listOf("record_id", "content_id", "device_id", "timestamp", "duration_played", "track_duration", "play_percentage", "skip_count", "repeat_flag", "source_type", "time_of_day_bucket", "day_of_week", "session_duration", "playback_mode", "volume_level_avg", "audio_output_type")
                         headers.forEachIndexed { i, h -> row.createCell(i).setCellValue(h) }
                         
                         @Suppress("UNCHECKED_CAST")
                         (records as List<PlaybackRecord>).forEach { record ->
                            row = sheet.createRow(rowNum++)
                            row.createCell(0).setCellValue(record.recordId)
                            row.createCell(1).setCellValue(record.contentId)
                            row.createCell(2).setCellValue(record.deviceId)
                            row.createCell(3).setCellValue(dateFormat.format(Date(record.timestamp)))
                            row.createCell(4).setCellValue(record.durationPlayed.toDouble())
                            row.createCell(5).setCellValue(record.trackDuration.toDouble())
                            row.createCell(6).setCellValue(record.playPercentage.toDouble())
                            row.createCell(7).setCellValue(record.skipCount.toDouble())
                            row.createCell(8).setCellValue(record.repeatFlag)
                            row.createCell(9).setCellValue(record.sourceType.toString())
                            row.createCell(10).setCellValue(record.timeOfDayBucket.toString())
                            row.createCell(11).setCellValue(record.dayOfWeek.toString())
                            row.createCell(12).setCellValue(record.sessionDuration.toDouble())
                            row.createCell(13).setCellValue(record.playbackMode.toString())
                            row.createCell(14).setCellValue(record.volumeLevelAvg.toDouble())
                            row.createCell(15).setCellValue(record.audioOutputType.toString())
                         }
                     }
                     is SharingRecord -> {
                         val headers = listOf("record_id", "content_id", "sharer_device_id", "recipient_device_id", "timestamp", "file_size", "share_method")
                         headers.forEachIndexed { i, h -> row.createCell(i).setCellValue(h) }
                         
                         @Suppress("UNCHECKED_CAST")
                         (records as List<SharingRecord>).forEach { record ->
                             row = sheet.createRow(rowNum++)
                             row.createCell(0).setCellValue(record.recordId)
                             row.createCell(1).setCellValue(record.contentId)
                             row.createCell(2).setCellValue(record.sharerDeviceId)
                             row.createCell(3).setCellValue(record.recipientDeviceId ?: "")
                             row.createCell(4).setCellValue(dateFormat.format(Date(record.timestamp)))
                             row.createCell(5).setCellValue(record.fileSize.toDouble())
                             row.createCell(6).setCellValue(record.shareMethod.toString())
                         }
                     }
                     is TrackMetadata -> {
                         val headers = listOf("content_id", "title", "artist", "album", "duration", "first_seen")
                         headers.forEachIndexed { i, h -> row.createCell(i).setCellValue(h) }
                         
                         @Suppress("UNCHECKED_CAST")
                         (records as List<TrackMetadata>).forEach { track ->
                             row = sheet.createRow(rowNum++)
                             row.createCell(0).setCellValue(track.contentId)
                             row.createCell(1).setCellValue(track.title)
                             row.createCell(2).setCellValue(track.artist)
                             row.createCell(3).setCellValue(track.album ?: "")
                             row.createCell(4).setCellValue(track.duration.toDouble())
                             row.createCell(5).setCellValue(dateFormat.format(Date(track.firstSeen)))
                         }
                     }
                 }
             }

             FileOutputStream(file).use { fileOut ->
                workbook.write(fileOut)
            }
             
          } catch(e: Exception) {
             Log.e(TAG, "Failed to export single sheet excel", e)
             throw e
          } finally {
             workbook.close()
          }
     }
    
    // Extended export functions with TransferRecords support
    private fun exportComprehensiveReportToJSONWithTransfers(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        transferRecords: List<com.bitchat.android.music.model.TransferRecord>,
        file: File
    ) {
        val rootObject = JSONObject().apply {
            put("export_type", "comprehensive_report")
            put("export_timestamp", System.currentTimeMillis())
            put("export_timestamp_formatted", dateFormat.format(Date()))
            
            // Summary statistics
            put("summary", JSONObject().apply {
                put("total_playback_records", playbackRecords.size)
                put("total_sharing_records", sharingRecords.size)
                put("total_track_metadata", trackMetadata.size)
                put("total_transfer_records", transferRecords.size)
                put("unique_tracks", trackMetadata.map { it.contentId }.toSet().size)
                put("unique_devices", (playbackRecords.map { it.deviceId } + 
                                     sharingRecords.map { it.sharerDeviceId }).toSet().size)
            })
            
            // Individual data sections
            put("playback_records", JSONArray().apply {
                playbackRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("record_id", record.recordId)
                        put("content_id", record.contentId)
                        put("device_id", record.deviceId)
                        put("timestamp", record.timestamp)
                        put("duration_played", record.durationPlayed)
                        put("track_duration", record.trackDuration)
                        put("play_percentage", record.playPercentage)
                        put("skip_count", record.skipCount)
                        put("repeat_flag", record.repeatFlag)
                        put("source_type", record.sourceType.toString())
                    })
                }
            })
            
            put("sharing_records", JSONArray().apply {
                sharingRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("record_id", record.recordId)
                        put("content_id", record.contentId)
                        put("sharer_device_id", record.sharerDeviceId)
                        put("recipient_device_id", record.recipientDeviceId)
                        put("timestamp", record.timestamp)
                        put("file_size", record.fileSize)
                        put("share_method", record.shareMethod.toString())
                    })
                }
            })
            
            put("track_metadata", JSONArray().apply {
                trackMetadata.forEach { track ->
                    put(JSONObject().apply {
                        put("content_id", track.contentId)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("duration", track.duration)
                        put("first_seen", track.firstSeen)
                    })
                }
            })
            
            put("transfer_records", JSONArray().apply {
                transferRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("transfer_id", record.transferId)
                        put("content_id", record.contentId)
                        put("source_device_id", record.sourceDeviceId)
                        put("target_device_id", record.targetDeviceId)
                        put("timestamp", record.timestamp)
                        put("file_size", record.fileSize)
                        put("transfer_method", record.transferMethod.toString())
                        put("transfer_status", record.transferStatus.toString())
                    })
                }
            })
        }
        
        FileWriter(file).use { writer ->
            writer.write(rootObject.toString(2))
        }
    }
    
    private fun exportComprehensiveReportToXMLWithTransfers(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        transferRecords: List<com.bitchat.android.music.model.TransferRecord>,
        file: File
    ) {
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<comprehensive_report export_timestamp=\"${System.currentTimeMillis()}\">\n")
            
            // Summary
            writer.append("  <summary>\n")
            writer.append("    <total_playback_records>${playbackRecords.size}</total_playback_records>\n")
            writer.append("    <total_sharing_records>${sharingRecords.size}</total_sharing_records>\n")
            writer.append("    <total_track_metadata>${trackMetadata.size}</total_track_metadata>\n")
            writer.append("    <total_transfer_records>${transferRecords.size}</total_transfer_records>\n")
            writer.append("  </summary>\n")
            
            // Playback records section
            writer.append("  <playback_records>\n")
            playbackRecords.forEach { record ->
                writer.append("    <record>\n")
                writer.append("      <record_id>${record.recordId}</record_id>\n")
                writer.append("      <content_id>${record.contentId}</content_id>\n")
                writer.append("      <device_id>${record.deviceId}</device_id>\n")
                writer.append("      <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("      <duration_played>${record.durationPlayed}</duration_played>\n")
                writer.append("      <track_duration>${record.trackDuration}</track_duration>\n")
                writer.append("      <play_percentage>${record.playPercentage}</play_percentage>\n")
                writer.append("      <skip_count>${record.skipCount}</skip_count>\n")
                writer.append("      <repeat_flag>${record.repeatFlag}</repeat_flag>\n")
                writer.append("      <source_type>${record.sourceType}</source_type>\n")
                writer.append("    </record>\n")
            }
            writer.append("  </playback_records>\n")
            
            // Sharing records section
            writer.append("  <sharing_records>\n")
            sharingRecords.forEach { record ->
                writer.append("    <record>\n")
                writer.append("      <record_id>${record.recordId}</record_id>\n")
                writer.append("      <content_id>${record.contentId}</content_id>\n")
                writer.append("      <sharer_device_id>${record.sharerDeviceId}</sharer_device_id>\n")
                writer.append("      <recipient_device_id>${record.recipientDeviceId ?: ""}</recipient_device_id>\n")
                writer.append("      <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("      <file_size>${record.fileSize}</file_size>\n")
                writer.append("      <share_method>${record.shareMethod}</share_method>\n")
                writer.append("    </record>\n")
            }
            writer.append("  </sharing_records>\n")
            
            // Track metadata section
            writer.append("  <track_metadata>\n")
            trackMetadata.forEach { track ->
                writer.append("    <track>\n")
                writer.append("      <content_id>${track.contentId}</content_id>\n")
                writer.append("      <title><![CDATA[${track.title}]]></title>\n")
                writer.append("      <artist><![CDATA[${track.artist}]]></artist>\n")
                writer.append("      <album><![CDATA[${track.album ?: ""}]]></album>\n")
                writer.append("      <duration>${track.duration}</duration>\n")
                writer.append("      <first_seen>${track.firstSeen}</first_seen>\n")
                writer.append("    </track>\n")
            }
            writer.append("  </track_metadata>\n")
            
            // Transfer records section
            writer.append("  <transfer_records>\n")
            transferRecords.forEach { record ->
                writer.append("    <record>\n")
                writer.append("      <transfer_id>${record.transferId}</transfer_id>\n")
                writer.append("      <content_id>${record.contentId}</content_id>\n")
                writer.append("      <source_device_id>${record.sourceDeviceId}</source_device_id>\n")
                writer.append("      <target_device_id>${record.targetDeviceId ?: ""}</target_device_id>\n")
                writer.append("      <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("      <file_size>${record.fileSize}</file_size>\n")
                writer.append("      <transfer_method>${record.transferMethod}</transfer_method>\n")
                writer.append("      <transfer_status>${record.transferStatus}</transfer_status>\n")
                writer.append("    </record>\n")
            }
            writer.append("  </transfer_records>\n")
            
            writer.append("</comprehensive_report>\n")
        }
    }
    
    private fun exportComprehensiveReportToCSVWithTransfers(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        transferRecords: List<com.bitchat.android.music.model.TransferRecord>,
        file: File
    ) {
        FileWriter(file).use { writer ->
            // File header with export information
            writer.append("# Comprehensive Music Analytics Export\n")
            writer.append("# Generated: ${dateFormat.format(Date())}\n")
            writer.append("# Total Records: ${playbackRecords.size + sharingRecords.size + trackMetadata.size + transferRecords.size}\n")
            writer.append("# \n")
            writer.append("# This file contains multiple data sheets separated by sheet markers\n")
            writer.append("# Import instructions: Use sheet markers to split data when importing\n")
            writer.append("# \n")
            writer.append("\n")
            
            // Sheet 1: Playback Records
            writer.append("### SHEET: Playback_Records ###\n")
            writer.append("# Records: ${playbackRecords.size}\n")
            writer.append("# Description: Detailed playback analytics with listening context data\n")
            writer.append("record_id,content_id,device_id,timestamp,duration_played,track_duration,play_percentage,skip_count,repeat_flag,source_type,time_of_day_bucket,day_of_week,session_duration,playback_mode,volume_level_avg,audio_output_type\n")
            
            playbackRecords.forEach { record ->
                writer.append("${record.recordId},")
                writer.append("${record.contentId},")
                writer.append("${record.deviceId},")
                writer.append("${dateFormat.format(Date(record.timestamp))},")
                writer.append("${record.durationPlayed},")
                writer.append("${record.trackDuration},")
                writer.append("${record.playPercentage},")
                writer.append("${record.skipCount},")
                writer.append("${record.repeatFlag},")
                writer.append("${record.sourceType},")
                writer.append("${record.timeOfDayBucket},")
                writer.append("${record.dayOfWeek},")
                writer.append("${record.sessionDuration},")
                writer.append("${record.playbackMode},")
                writer.append("${record.volumeLevelAvg},")
                writer.append("${record.audioOutputType}\n")
            }
            
            writer.append("\n\n")
            
            // Sheet 2: Sharing Records
            writer.append("### SHEET: Sharing_Records ###\n")
            writer.append("# Records: ${sharingRecords.size}\n")
            writer.append("# Description: Music sharing and transfer activity\n")
            writer.append("record_id,content_id,sharer_device_id,recipient_device_id,timestamp,file_size,share_method\n")
            
            sharingRecords.forEach { record ->
                writer.append("${record.recordId},")
                writer.append("${record.contentId},")
                writer.append("${record.sharerDeviceId},")
                writer.append("${record.recipientDeviceId ?: ""},")
                writer.append("${dateFormat.format(Date(record.timestamp))},")
                writer.append("${record.fileSize},")
                writer.append("${record.shareMethod}\n")
            }
            
            writer.append("\n\n")
            
            // Sheet 3: Track Metadata
            writer.append("### SHEET: Track_Metadata ###\n")
            writer.append("# Records: ${trackMetadata.size}\n")
            writer.append("# Description: Track information and metadata\n")
            writer.append("content_id,title,artist,album,duration,first_seen\n")
            
            trackMetadata.forEach { track ->
                writer.append("${track.contentId},")
                writer.append("\"${track.title.replace("\"", "\"\"")}\",")
                writer.append("\"${track.artist.replace("\"", "\"\"")}\",")
                writer.append("\"${track.album?.replace("\"", "\"\"") ?: ""}\",")
                writer.append("${track.duration},")
                writer.append("${dateFormat.format(Date(track.firstSeen))}\n")
            }
            
            writer.append("\n\n")
            
            // Sheet 4: Transfer Records
            writer.append("### SHEET: Transfer_Records ###\n")
            writer.append("# Records: ${transferRecords.size}\n")
            writer.append("# Description: Music file transfer metadata\n")
            writer.append("transfer_id,content_id,source_device_id,target_device_id,timestamp,file_size,transfer_method,transfer_status\n")
            
            transferRecords.forEach { record ->
                writer.append("${record.transferId},")
                writer.append("${record.contentId},")
                writer.append("${record.sourceDeviceId},")
                writer.append("${record.targetDeviceId ?: ""},")
                writer.append("${dateFormat.format(Date(record.timestamp))},")
                writer.append("${record.fileSize},")
                writer.append("${record.transferMethod},")
                writer.append("${record.transferStatus}\n")
            }
            
            writer.append("\n\n")
            
            // Sheet 5: Summary Statistics
            writer.append("### SHEET: Summary_Statistics ###\n")
            writer.append("# Records: Summary data\n")
            writer.append("# Description: Aggregated statistics and insights\n")
            writer.append("metric,value,description\n")
            
            // Calculate summary statistics
            val totalPlayTime = playbackRecords.sumOf { it.durationPlayed }
            val avgPlayPercentage = if (playbackRecords.isNotEmpty()) {
                playbackRecords.map { it.playPercentage }.average()
            } else 0.0
            val qualifyingPlays = playbackRecords.count { it.qualifiesForRoyalty() }
            val uniqueTracks = playbackRecords.map { it.contentId }.distinct().size
            val totalShares = sharingRecords.size
            val totalTracksInLibrary = trackMetadata.size
            val totalTransfers = transferRecords.size
            
            // Write summary statistics
            writer.append("Total Playback Records,${playbackRecords.size},Number of individual playback events\n")
            writer.append("Total Play Time (seconds),${totalPlayTime},Total seconds of music played\n")
            writer.append("Total Play Time (hours),${totalPlayTime / 3600.0},Total hours of music played\n")
            writer.append("Average Play Percentage,${String.format("%.2f", avgPlayPercentage * 100)}%,Average percentage of tracks completed\n")
            writer.append("Qualifying Plays,${qualifyingPlays},Plays that qualify for royalty (30s or 50% of track)\n")
            writer.append("Unique Tracks Played,${uniqueTracks},Number of different tracks played\n")
            writer.append("Total Sharing Records,${totalShares},Number of music sharing events\n")
            writer.append("Total Transfer Records,${totalTransfers},Number of file transfer events\n")
            writer.append("Total Tracks in Library,${totalTracksInLibrary},Number of tracks in music library\n")
            
            writer.append("\n### END OF EXPORT ###\n")
        }
    }
    
    private fun exportComprehensiveReportToExcelWithTransfers(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        transferRecords: List<com.bitchat.android.music.model.TransferRecord>,
        file: File
    ) {
        val workbook = XSSFWorkbook()

        try {
            // Sheet 1: Summary Statistics
            val summarySheet = workbook.createSheet("Summary_Statistics")
            var rowNum = 0
            
            // Header
            var row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue("Metric")
            row.createCell(1).setCellValue("Value")
            row.createCell(2).setCellValue("Description")

            // Calculate stats
            val totalPlayTime = playbackRecords.sumOf { it.durationPlayed }
            val avgPlayPercentage = if (playbackRecords.isNotEmpty()) {
                playbackRecords.map { it.playPercentage }.average()
            } else 0.0
            val qualifyingPlays = playbackRecords.count { it.qualifiesForRoyalty() }
            val uniqueTracks = playbackRecords.map { it.contentId }.distinct().size
            val totalShares = sharingRecords.size
            val totalTracksInLibrary = trackMetadata.size
            val totalTransfers = transferRecords.size

            // Populate rows
            val summaryData = listOf(
                Triple("Total Playback Records", playbackRecords.size.toString(), "Number of individual playback events"),
                Triple("Total Play Time (seconds)", totalPlayTime.toString(), "Total seconds of music played"),
                Triple("Total Play Time (hours)", (totalPlayTime / 3600.0).toString(), "Total hours of music played"),
                Triple("Average Play Percentage", String.format("%.2f%%", avgPlayPercentage * 100), "Average percentage of tracks completed"),
                Triple("Qualifying Plays", qualifyingPlays.toString(), "Plays that qualify for royalty (30s or 50% of track)"),
                Triple("Unique Tracks Played", uniqueTracks.toString(), "Number of different tracks played"),
                Triple("Total Sharing Records", totalShares.toString(), "Number of music sharing events"),
                Triple("Total Transfer Records", totalTransfers.toString(), "Number of file transfer events"),
                Triple("Total Tracks in Library", totalTracksInLibrary.toString(), "Number of tracks in music library")
            )

            summaryData.forEach { (metric, value, desc) ->
                row = summarySheet.createRow(rowNum++)
                row.createCell(0).setCellValue(metric)
                row.createCell(1).setCellValue(value)
                row.createCell(2).setCellValue(desc)
            }
            
            // Sheet 2: Playback Records
            val playbackSheet = workbook.createSheet("Playback_Records")
            rowNum = 0
            
            val playbackHeaders = listOf(
                "record_id", "content_id", "device_id", "timestamp", "duration_played", 
                "track_duration", "play_percentage", "skip_count", "repeat_flag", 
                "source_type", "time_of_day_bucket", "day_of_week", "session_duration", 
                "playback_mode", "volume_level_avg", "audio_output_type"
            )
            row = playbackSheet.createRow(rowNum++)
            playbackHeaders.forEachIndexed { index, header ->
                row.createCell(index).setCellValue(header)
            }

            playbackRecords.forEach { record ->
                row = playbackSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(record.recordId)
                row.createCell(1).setCellValue(record.contentId)
                row.createCell(2).setCellValue(record.deviceId)
                row.createCell(3).setCellValue(dateFormat.format(Date(record.timestamp)))
                row.createCell(4).setCellValue(record.durationPlayed.toDouble())
                row.createCell(5).setCellValue(record.trackDuration.toDouble())
                row.createCell(6).setCellValue(record.playPercentage.toDouble())
                row.createCell(7).setCellValue(record.skipCount.toDouble())
                row.createCell(8).setCellValue(record.repeatFlag)
                row.createCell(9).setCellValue(record.sourceType.toString())
                row.createCell(10).setCellValue(record.timeOfDayBucket.toString())
                row.createCell(11).setCellValue(record.dayOfWeek.toString())
                row.createCell(12).setCellValue(record.sessionDuration.toDouble())
                row.createCell(13).setCellValue(record.playbackMode.toString())
                row.createCell(14).setCellValue(record.volumeLevelAvg.toDouble())
                row.createCell(15).setCellValue(record.audioOutputType.toString())
            }

            // Sheet 3: Sharing Records
            val sharingSheet = workbook.createSheet("Sharing_Records")
            rowNum = 0
            val sharingHeaders = listOf(
                 "record_id", "content_id", "sharer_device_id", "recipient_device_id", 
                 "timestamp", "file_size", "share_method"
            )
            row = sharingSheet.createRow(rowNum++)
            sharingHeaders.forEachIndexed { index, header ->
                 row.createCell(index).setCellValue(header)
            }
            
            sharingRecords.forEach { record ->
                row = sharingSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(record.recordId)
                row.createCell(1).setCellValue(record.contentId)
                row.createCell(2).setCellValue(record.sharerDeviceId)
                row.createCell(3).setCellValue(record.recipientDeviceId ?: "")
                row.createCell(4).setCellValue(dateFormat.format(Date(record.timestamp)))
                row.createCell(5).setCellValue(record.fileSize.toDouble())
                row.createCell(6).setCellValue(record.shareMethod.toString())
            }

            // Sheet 4: Track Metadata
            val trackSheet = workbook.createSheet("Track_Metadata")
            rowNum = 0
            val trackHeaders = listOf(
                "content_id", "title", "artist", "album", "duration", "first_seen"
            )
            row = trackSheet.createRow(rowNum++)
            trackHeaders.forEachIndexed { index, header ->
                row.createCell(index).setCellValue(header)
            }
            
            trackMetadata.forEach { track ->
                row = trackSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(track.contentId)
                row.createCell(1).setCellValue(track.title)
                row.createCell(2).setCellValue(track.artist)
                row.createCell(3).setCellValue(track.album ?: "")
                row.createCell(4).setCellValue(track.duration.toDouble())
                row.createCell(5).setCellValue(dateFormat.format(Date(track.firstSeen)))
            }
            
            // Sheet 5: Transfer Records
            val transferSheet = workbook.createSheet("Transfer_Records")
            rowNum = 0
            val transferHeaders = listOf(
                "transfer_id", "content_id", "source_device_id", "target_device_id",
                "timestamp", "file_size", "transfer_method", "transfer_status"
            )
            row = transferSheet.createRow(rowNum++)
            transferHeaders.forEachIndexed { index, header ->
                row.createCell(index).setCellValue(header)
            }
            
            transferRecords.forEach { record ->
                row = transferSheet.createRow(rowNum++)
                row.createCell(0).setCellValue(record.transferId)
                row.createCell(1).setCellValue(record.contentId)
                row.createCell(2).setCellValue(record.sourceDeviceId)
                row.createCell(3).setCellValue(record.targetDeviceId ?: "")
                row.createCell(4).setCellValue(dateFormat.format(Date(record.timestamp)))
                row.createCell(5).setCellValue(record.fileSize.toDouble())
                row.createCell(6).setCellValue(record.transferMethod.toString())
                row.createCell(7).setCellValue(record.transferStatus.toString())
            }
            
            // Write to file
             FileOutputStream(file).use { fileOut ->
                workbook.write(fileOut)
            }
            Log.i(TAG, "Successfully exported comprehensive Excel report with transfers to ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export comprehensive Excel report with transfers", e)
            throw e
        } finally {
            workbook.close()
        }
    }
    
    // Transfer record export functions
    private fun exportTransferRecordsToCSV(records: List<com.bitchat.android.music.model.TransferRecord>, file: File) {
        FileWriter(file).use { writer ->
            writer.append("transfer_id,content_id,source_device_id,target_device_id,timestamp,file_size,transfer_method,transfer_status\n")
            
            records.forEach { record ->
                writer.append("${record.transferId},")
                writer.append("${record.contentId},")
                writer.append("${record.sourceDeviceId},")
                writer.append("${record.targetDeviceId ?: ""},")
                writer.append("${dateFormat.format(Date(record.timestamp))},")
                writer.append("${record.fileSize},")
                writer.append("${record.transferMethod},")
                writer.append("${record.transferStatus}\n")
            }
        }
    }
    
    private fun exportTransferRecordsToJSON(records: List<com.bitchat.android.music.model.TransferRecord>, file: File) {
        val jsonArray = JSONArray()
        
        records.forEach { record ->
            val jsonObject = JSONObject().apply {
                put("transfer_id", record.transferId)
                put("content_id", record.contentId)
                put("source_device_id", record.sourceDeviceId)
                put("target_device_id", record.targetDeviceId)
                put("timestamp", record.timestamp)
                put("timestamp_formatted", dateFormat.format(Date(record.timestamp)))
                put("file_size", record.fileSize)
                put("transfer_method", record.transferMethod.toString())
                put("transfer_status", record.transferStatus.toString())
            }
            jsonArray.put(jsonObject)
        }
        
        val rootObject = JSONObject().apply {
            put("export_type", "transfer_records")
            put("export_timestamp", System.currentTimeMillis())
            put("export_timestamp_formatted", dateFormat.format(Date()))
            put("record_count", records.size)
            put("records", jsonArray)
        }
        
        FileWriter(file).use { writer ->
            writer.write(rootObject.toString(2))
        }
    }
    
    private fun exportTransferRecordsToXML(records: List<com.bitchat.android.music.model.TransferRecord>, file: File) {
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<transfer_records export_timestamp=\"${System.currentTimeMillis()}\" record_count=\"${records.size}\">\n")
            
            records.forEach { record ->
                writer.append("  <record>\n")
                writer.append("    <transfer_id>${record.transferId}</transfer_id>\n")
                writer.append("    <content_id>${record.contentId}</content_id>\n")
                writer.append("    <source_device_id>${record.sourceDeviceId}</source_device_id>\n")
                writer.append("    <target_device_id>${record.targetDeviceId ?: ""}</target_device_id>\n")
                writer.append("    <timestamp>${record.timestamp}</timestamp>\n")
                writer.append("    <file_size>${record.fileSize}</file_size>\n")
                writer.append("    <transfer_method>${record.transferMethod}</transfer_method>\n")
                writer.append("    <transfer_status>${record.transferStatus}</transfer_status>\n")
                writer.append("  </record>\n")
            }
            
            writer.append("</transfer_records>\n")
        }
    }
    
    private fun exportTransferRecordsToExcel(records: List<com.bitchat.android.music.model.TransferRecord>, file: File) {
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Transfer_Records")
            var rowNum = 0
            var row = sheet.createRow(rowNum++)
            
            val headers = listOf("transfer_id", "content_id", "source_device_id", "target_device_id",
                "timestamp", "file_size", "transfer_method", "transfer_status")
            headers.forEachIndexed { i, h -> row.createCell(i).setCellValue(h) }
            
            records.forEach { record ->
                row = sheet.createRow(rowNum++)
                row.createCell(0).setCellValue(record.transferId)
                row.createCell(1).setCellValue(record.contentId)
                row.createCell(2).setCellValue(record.sourceDeviceId)
                row.createCell(3).setCellValue(record.targetDeviceId ?: "")
                row.createCell(4).setCellValue(dateFormat.format(Date(record.timestamp)))
                row.createCell(5).setCellValue(record.fileSize.toDouble())
                row.createCell(6).setCellValue(record.transferMethod.toString())
                row.createCell(7).setCellValue(record.transferStatus.toString())
            }
            
            FileOutputStream(file).use { fileOut ->
                workbook.write(fileOut)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export transfer records to Excel", e)
            throw e
        } finally {
            workbook.close()
        }
    }
}