package com.bitchat.android.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * ContentID generator for music tracks
 * Creates unique identifiers using audio fingerprinting combined with metadata hashing
 * Based on the Offline Music Analytics specification section 3.1
 */
class ContentIdGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "ContentIdGenerator"
        private const val FINGERPRINT_DURATION_MS = 30000 // First 30 seconds
        private const val DURATION_BUCKET_SECONDS = 5 // Round to nearest 5 seconds
    }
    
    /**
     * Generate ContentID for a music file
     * ContentID = SHA256(audio_fingerprint + normalized_title + normalized_artist + duration_bucket)[0:32]
     */
    fun generateContentId(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return null
            }
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            // Extract metadata
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0"
            val duration = durationStr.toLongOrNull() ?: 0L
            
            retriever.release()
            
            // Generate audio fingerprint (simplified spectral hash)
            val audioFingerprint = generateAudioFingerprint(filePath)
            
            // Normalize metadata
            val normalizedTitle = normalizeString(title)
            val normalizedArtist = normalizeString(artist)
            val durationBucket = ((duration / 1000) / DURATION_BUCKET_SECONDS) * DURATION_BUCKET_SECONDS
            
            // Combine all components
            val combined = audioFingerprint + normalizedTitle + normalizedArtist + durationBucket.toString()
            
            // Generate SHA256 hash and take first 32 characters
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
            val hexString = hash.joinToString("") { "%02x".format(it) }
            
            hexString.take(32)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating ContentID for $filePath", e)
            null
        }
    }
    
    /**
     * Generate a simplified audio fingerprint
     * This is a lightweight implementation suitable for low-spec devices
     * In production, consider using Chromaprint or similar library
     */
    private fun generateAudioFingerprint(filePath: String): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            // Extract first 30 seconds of audio at 8kHz mono (simplified approach)
            // In a real implementation, this would use FFT and spectral analysis
            val waveform = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            // Generate a simple hash based on file size and first few bytes
            // This is a placeholder - real implementation would use proper audio fingerprinting
            val file = File(filePath)
            val fileSize = file.length()
            val firstBytes = file.inputStream().use { stream ->
                val buffer = ByteArray(1024)
                stream.read(buffer)
                buffer
            }
            
            val digest = MessageDigest.getInstance("MD5")
            digest.update(fileSize.toString().toByteArray())
            digest.update(firstBytes)
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating audio fingerprint", e)
            // Fallback to file hash
            generateFileHash(filePath)
        }
    }
    
    /**
     * Fallback method to generate file hash when audio fingerprinting fails
     */
    private fun generateFileHash(filePath: String): String {
        return try {
            val file = File(filePath)
            val digest = MessageDigest.getInstance("MD5")
            
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating file hash", e)
            "unknown_${System.currentTimeMillis()}"
        }
    }
    
    /**
     * Normalize string for consistent matching
     * Converts to lowercase and removes punctuation
     */
    private fun normalizeString(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Extract track metadata for TrackMetadata object
     */
    fun extractTrackMetadata(filePath: String, contentId: String): com.bitchat.android.music.model.TrackMetadata? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: File(filePath).nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0"
            val duration = (durationStr.toLongOrNull() ?: 0L) / 1000 // Convert to seconds
            
            retriever.release()
            
            // Generate compact audio fingerprint (placeholder implementation)
            val audioFingerprint = generateAudioFingerprint(filePath).take(32).toByteArray(Charsets.UTF_8)
            
            com.bitchat.android.music.model.TrackMetadata(
                contentId = contentId,
                title = title,
                artist = artist,
                album = album,
                duration = duration.toInt(),
                audioFingerprint = audioFingerprint
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting track metadata", e)
            null
        }
    }
}