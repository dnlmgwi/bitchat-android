package com.bitchat.android.features.usb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages file transfers to USB flash drives using Storage Access Framework
 * Provides progress tracking and handles various file types
 */
class UsbFileTransferManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UsbFileTransferManager"
        private const val BUFFER_SIZE = 8192 // 8KB buffer for file copying
    }
    
    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()
    
    private val _transferHistory = MutableStateFlow<List<TransferRecord>>(emptyList())
    val transferHistory: StateFlow<List<TransferRecord>> = _transferHistory.asStateFlow()
    
    /**
     * Transfer files to USB drive using Storage Access Framework
     */
    suspend fun transferFilesToUsb(
        files: List<File>,
        usbRootUri: Uri,
        destinationFolder: String = "Bitchat"
    ): TransferResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting USB transfer of ${files.size} files")
            
            val usbRoot = DocumentFile.fromTreeUri(context, usbRootUri)
                ?: return@withContext TransferResult.Error("Cannot access USB drive")
            
            // Create destination folder if it doesn't exist
            val destFolder = usbRoot.findFile(destinationFolder) 
                ?: usbRoot.createDirectory(destinationFolder)
                ?: return@withContext TransferResult.Error("Cannot create destination folder")
            
            val totalSize = files.sumOf { it.length() }
            var transferredSize = 0L
            val successfulTransfers = mutableListOf<String>()
            val failedTransfers = mutableListOf<Pair<String, String>>()
            
            _transferProgress.value = TransferProgress(
                currentFile = "",
                fileProgress = 0f,
                overallProgress = 0f,
                transferredBytes = 0L,
                totalBytes = totalSize,
                isActive = true
            )
            
            files.forEachIndexed { index, file ->
                try {
                    Log.d(TAG, "Transferring file ${index + 1}/${files.size}: ${file.name}")
                    
                    _transferProgress.value = _transferProgress.value?.copy(
                        currentFile = file.name,
                        fileProgress = 0f,
                        overallProgress = transferredSize.toFloat() / totalSize
                    )
                    
                    // Create file in destination folder
                    val mimeType = getMimeType(file.name)
                    val destFile = destFolder.createFile(mimeType, file.name)
                        ?: throw Exception("Cannot create file on USB drive")
                    
                    // Copy file with progress tracking
                    val outputStream = context.contentResolver.openOutputStream(destFile.uri)
                        ?: throw Exception("Cannot open output stream")
                    
                    copyFileWithProgress(file, outputStream, file.length()) { progress ->
                        _transferProgress.value = _transferProgress.value?.copy(
                            fileProgress = progress,
                            transferredBytes = transferredSize + (file.length() * progress).toLong()
                        )
                    }
                    
                    transferredSize += file.length()
                    successfulTransfers.add(file.name)
                    
                    Log.d(TAG, "Successfully transferred: ${file.name}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to transfer file: ${file.name}", e)
                    failedTransfers.add(file.name to e.message.orEmpty())
                }
            }
            
            _transferProgress.value = _transferProgress.value?.copy(
                isActive = false,
                overallProgress = 1f
            )
            
            // Record transfer in history
            val record = TransferRecord(
                timestamp = System.currentTimeMillis(),
                destination = "USB: ${usbRoot.name ?: "Unknown"}",
                fileCount = files.size,
                totalSize = totalSize,
                successCount = successfulTransfers.size,
                failedCount = failedTransfers.size,
                duration = 0L // Will be calculated when transfer completes
            )
            
            addToHistory(record)
            
            if (failedTransfers.isEmpty()) {
                TransferResult.Success(successfulTransfers.size, transferredSize)
            } else {
                TransferResult.PartialSuccess(
                    successCount = successfulTransfers.size,
                    failedCount = failedTransfers.size,
                    transferredSize = transferredSize,
                    errors = failedTransfers.map { it.second }
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "USB transfer failed", e)
            _transferProgress.value = _transferProgress.value?.copy(isActive = false)
            TransferResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Copy file with progress callback
     */
    private suspend fun copyFileWithProgress(
        sourceFile: File,
        outputStream: OutputStream,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        FileInputStream(sourceFile).use { inputStream ->
            outputStream.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val progress = totalBytesRead.toFloat() / totalSize
                    onProgress(progress)
                }
                
                output.flush()
            }
        }
    }
    
    /**
     * Get MIME type for file extension
     */
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Cancel ongoing transfer
     */
    fun cancelTransfer() {
        _transferProgress.value = _transferProgress.value?.copy(isActive = false)
        Log.d(TAG, "Transfer cancelled by user")
    }
    
    /**
     * Clear transfer progress
     */
    fun clearProgress() {
        _transferProgress.value = null
    }
    
    /**
     * Add transfer record to history
     */
    private fun addToHistory(record: TransferRecord) {
        val currentHistory = _transferHistory.value.toMutableList()
        currentHistory.add(0, record) // Add to beginning
        
        // Keep only last 50 records
        if (currentHistory.size > 50) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _transferHistory.value = currentHistory
    }
    
    /**
     * Clear transfer history
     */
    fun clearHistory() {
        _transferHistory.value = emptyList()
    }
    
    /**
     * Validate that the selected URI is a USB drive
     */
    fun isUsbDrive(uri: Uri): Boolean {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            val name = documentFile?.name?.lowercase() ?: ""
            
            // Check if it's likely a USB drive based on common patterns
            return name.contains("usb") || 
                   name.contains("flash") || 
                   name.contains("drive") ||
                   uri.toString().contains("usb") ||
                   uri.toString().contains("storage")
        } catch (e: Exception) {
            Log.w(TAG, "Error checking if URI is USB drive", e)
            return false
        }
    }
}

/**
 * Represents the progress of a file transfer operation
 */
data class TransferProgress(
    val currentFile: String,
    val fileProgress: Float, // 0.0 to 1.0
    val overallProgress: Float, // 0.0 to 1.0
    val transferredBytes: Long,
    val totalBytes: Long,
    val isActive: Boolean
)

/**
 * Represents a completed transfer record
 */
data class TransferRecord(
    val timestamp: Long,
    val destination: String,
    val fileCount: Int,
    val totalSize: Long,
    val successCount: Int,
    val failedCount: Int,
    val duration: Long
)

/**
 * Result of a transfer operation
 */
sealed class TransferResult {
    data class Success(val fileCount: Int, val totalSize: Long) : TransferResult()
    data class PartialSuccess(
        val successCount: Int,
        val failedCount: Int,
        val transferredSize: Long,
        val errors: List<String>
    ) : TransferResult()
    data class Error(val message: String) : TransferResult()
}