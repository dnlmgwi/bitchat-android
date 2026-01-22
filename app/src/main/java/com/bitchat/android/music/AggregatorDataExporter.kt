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
        CSV, JSON, XML
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
            }
            
            Log.i(TAG, "Exported ${records.size} sharing records to ${file.absolutePath}")
            ExportResult(true, file.absolutePath, records.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export sharing records", e)
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
            }
            
            Log.i(TAG, "Exported ${tracks.size} track metadata records to ${file.absolutePath}")
            ExportResult(true, file.absolutePath, tracks.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export track metadata", e)
            ExportResult(false, null, 0, e.message)
        }
    }
    
    /**
     * Export comprehensive analytics report (all data types combined)
     */
    suspend fun exportComprehensiveReport(
        playbackRecords: List<PlaybackRecord>,
        sharingRecords: List<SharingRecord>,
        trackMetadata: List<TrackMetadata>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportFilename = filename ?: generateFilename("comprehensive_report", format)
            val file = getExportFile(exportFilename)
            
            val totalRecords = playbackRecords.size + sharingRecords.size + trackMetadata.size
            Log.d(TAG, "Starting comprehensive export of $totalRecords records to ${file.absolutePath}")
            
            when (format) {
                ExportFormat.JSON -> exportComprehensiveReportToJSON(
                    playbackRecords, sharingRecords, trackMetadata, file
                )
                ExportFormat.XML -> exportComprehensiveReportToXML(
                    playbackRecords, sharingRecords, trackMetadata, file
                )
                ExportFormat.CSV -> {
                    // For CSV comprehensive, create a single file with all data sections
                    exportComprehensiveReportToCSV(playbackRecords, sharingRecords, trackMetadata, file)
                }
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
                // Header
                writer.append("record_id,content_id,device_id,timestamp,duration_played,track_duration,play_percentage,skip_count,repeat_flag,source_type\n")
                
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
                    writer.append("${record.sourceType}\n")
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
            Log.d(TAG, "Exporting comprehensive report to CSV: ${file.absolutePath}")
            
            FileWriter(file).use { writer ->
                // Header with summary
                writer.append("# Comprehensive Music Analytics Export\n")
                writer.append("# Generated on: ${dateFormat.format(Date())}\n")
                writer.append("# Total Playback Records: ${playbackRecords.size}\n")
                writer.append("# Total Sharing Records: ${sharingRecords.size}\n")
                writer.append("# Total Track Metadata: ${trackMetadata.size}\n")
                writer.append("\n")
                
                // Playback Records Section
                writer.append("=== PLAYBACK RECORDS ===\n")
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
                
                writer.append("\n")
                
                // Sharing Records Section
                writer.append("=== SHARING RECORDS ===\n")
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
                
                writer.append("\n")
                
                // Track Metadata Section
                writer.append("=== TRACK METADATA ===\n")
                writer.append("content_id,title,artist,album,duration,first_seen\n")
                
                trackMetadata.forEach { track ->
                    writer.append("${track.contentId},")
                    writer.append("\"${track.title.replace("\"", "\"\"")}\",")
                    writer.append("\"${track.artist.replace("\"", "\"\"")}\",")
                    writer.append("\"${track.album?.replace("\"", "\"\"") ?: ""}\",")
                    writer.append("${track.duration},")
                    writer.append("${dateFormat.format(Date(track.firstSeen))}\n")
                }
            }
            
            Log.d(TAG, "Successfully exported comprehensive CSV: ${file.absolutePath} (${file.length()} bytes)")
            
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
}