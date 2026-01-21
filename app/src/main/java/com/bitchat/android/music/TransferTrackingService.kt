package com.bitchat.android.music

import android.content.Context
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.bluetooth.BluetoothAdapter
import android.net.wifi.p2p.WifiP2pManager
import android.os.FileObserver
import android.util.Log
import com.bitchat.android.music.model.TransferMethod
import com.bitchat.android.music.model.TransferRecord
import com.bitchat.android.music.model.TransferStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.*

/**
 * Service for tracking music file transfers via various methods
 * Monitors USB OTG, Bluetooth, WiFi Direct, and manual imports
 */
class TransferTrackingService(
    private val context: Context,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val contentIdGenerator: ContentIdGenerator,
    private val analyticsTracker: PlaybackAnalyticsTracker
) {
    
    companion object {
        private const val TAG = "TransferTrackingService"
        private val MUSIC_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav")
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Transfer records
    private val _transferRecords = MutableStateFlow<List<TransferRecord>>(emptyList())
    val transferRecords: StateFlow<List<TransferRecord>> = _transferRecords.asStateFlow()
    
    // Active transfers
    private val _activeTransfers = MutableStateFlow<Map<String, TransferRecord>>(emptyMap())
    val activeTransfers: StateFlow<Map<String, TransferRecord>> = _activeTransfers.asStateFlow()
    
    // File observers for different directories
    private val fileObservers = mutableMapOf<String, FileObserver>()
    
    // Receivers for hardware events
    private var usbReceiver: android.content.BroadcastReceiver? = null
    private var bluetoothReceiver: android.content.BroadcastReceiver? = null
    
    init {
        setupTransferMonitoring()
    }
    
    /**
     * Setup monitoring for various transfer methods
     */
    private fun setupTransferMonitoring() {
        setupUsbMonitoring()
        setupBluetoothMonitoring()
        setupFileSystemMonitoring()
    }
    
    /**
     * Monitor USB OTG connections and file transfers
     */
    private fun setupUsbMonitoring() {
        usbReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        Log.d(TAG, "USB device attached")
                        onUsbDeviceConnected()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.d(TAG, "USB device detached")
                        onUsbDeviceDisconnected()
                    }
                }
            }
        }
        
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        try {
            context.registerReceiver(usbReceiver, usbFilter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register USB receiver", e)
        }
    }
    
    /**
     * Monitor Bluetooth connections and file transfers
     */
    private fun setupBluetoothMonitoring() {
        bluetoothReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_ON) {
                            Log.d(TAG, "Bluetooth enabled")
                        }
                    }
                }
            }
        }
        
        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        
        try {
            context.registerReceiver(bluetoothReceiver, bluetoothFilter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Bluetooth receiver", e)
        }
    }
    
    /**
     * Monitor file system for new music files
     */
    private fun setupFileSystemMonitoring() {
        val musicDirectories = getMusicDirectories()
        
        musicDirectories.forEach { directory ->
            if (directory.exists() && directory.isDirectory) {
                val observer = object : FileObserver(directory.absolutePath, CREATE or MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && isMusicFile(path)) {
                            val file = File(directory, path)
                            if (file.exists()) {
                                onNewMusicFileDetected(file, TransferMethod.UNKNOWN)
                            }
                        }
                    }
                }
                
                fileObservers[directory.absolutePath] = observer
                observer.startWatching()
                Log.d(TAG, "Started monitoring directory: ${directory.absolutePath}")
            }
        }
    }
    
    /**
     * Get common music directories to monitor
     */
    private fun getMusicDirectories(): List<File> {
        val directories = mutableListOf<File>()
        
        // Primary external storage
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)?.let {
            directories.add(it)
        }
        
        // Download directory (common for transfers)
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)?.let {
            directories.add(it)
        }
        
        // App-specific directories
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.let {
            directories.add(it)
        }
        
        return directories
    }
    
    /**
     * Check if file is a music file based on extension
     */
    private fun isMusicFile(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extension in MUSIC_EXTENSIONS
    }
    
    /**
     * Handle USB device connection
     */
    private fun onUsbDeviceConnected() {
        serviceScope.launch {
            // Start monitoring for file transfers from USB
            delay(2000) // Give system time to mount
            scanForNewFiles(TransferMethod.USB_OTG)
        }
    }
    
    /**
     * Handle USB device disconnection
     */
    private fun onUsbDeviceDisconnected() {
        // Complete any pending USB transfers
        completeTransfersByMethod(TransferMethod.USB_OTG)
    }
    
    /**
     * Handle new music file detection
     */
    private fun onNewMusicFileDetected(file: File, method: TransferMethod) {
        serviceScope.launch {
            try {
                val contentId = contentIdGenerator.generateContentId(file.absolutePath)
                if (contentId != null) {
                    val transferRecord = TransferRecord(
                        contentId = contentId,
                        sourceDeviceId = "unknown", // We don't know the source device
                        targetDeviceId = deviceIdentificationService.getDeviceId(),
                        transferMethod = method,
                        fileSize = file.length(),
                        transferStatus = TransferStatus.COMPLETED
                    )
                    
                    // Sign the record
                    val signature = deviceIdentificationService.signTransferRecord(transferRecord)
                    val signedRecord = transferRecord.copy(deviceSignature = signature)
                    
                    recordTransfer(signedRecord)
                    Log.i(TAG, "Recorded transfer: ${file.name} via $method")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing new music file: ${file.name}", e)
            }
        }
    }
    
    /**
     * Scan for new files that might have been transferred
     */
    private suspend fun scanForNewFiles(method: TransferMethod) {
        val musicDirectories = getMusicDirectories()
        val cutoffTime = System.currentTimeMillis() - 60000 // Files modified in last minute
        
        musicDirectories.forEach { directory ->
            if (directory.exists()) {
                directory.listFiles()?.forEach { file ->
                    if (file.isFile && isMusicFile(file.name) && file.lastModified() > cutoffTime) {
                        onNewMusicFileDetected(file, method)
                    }
                }
            }
        }
    }
    
    /**
     * Complete transfers by method
     */
    private fun completeTransfersByMethod(method: TransferMethod) {
        val currentActive = _activeTransfers.value.toMutableMap()
        val completed = mutableListOf<TransferRecord>()
        
        currentActive.values.filter { it.transferMethod == method }.forEach { transfer ->
            val completedTransfer = transfer.copy(
                transferStatus = TransferStatus.COMPLETED,
                transferDuration = (System.currentTimeMillis() - transfer.timestamp) / 1000
            )
            completed.add(completedTransfer)
            currentActive.remove(transfer.transferId)
        }
        
        if (completed.isNotEmpty()) {
            _activeTransfers.value = currentActive
            val currentRecords = _transferRecords.value.toMutableList()
            currentRecords.addAll(completed)
            _transferRecords.value = currentRecords
            
            Log.d(TAG, "Completed ${completed.size} transfers via $method")
        }
    }
    
    /**
     * Manually log a transfer (user-initiated)
     */
    fun logManualTransfer(
        contentId: String,
        method: TransferMethod,
        fileSize: Long,
        targetDeviceId: String? = null
    ) {
        serviceScope.launch {
            val transferRecord = TransferRecord(
                contentId = contentId,
                sourceDeviceId = deviceIdentificationService.getDeviceId(),
                targetDeviceId = targetDeviceId,
                transferMethod = method,
                fileSize = fileSize,
                transferStatus = TransferStatus.COMPLETED
            )
            
            // Sign the record
            val signature = deviceIdentificationService.signTransferRecord(transferRecord)
            val signedRecord = transferRecord.copy(deviceSignature = signature)
            
            recordTransfer(signedRecord)
            Log.i(TAG, "Manually logged transfer: $contentId via $method")
        }
    }
    
    /**
     * Record a transfer event
     */
    private fun recordTransfer(transfer: TransferRecord) {
        val currentRecords = _transferRecords.value.toMutableList()
        currentRecords.add(transfer)
        _transferRecords.value = currentRecords
        
        // Remove from active transfers if it was there
        val currentActive = _activeTransfers.value.toMutableMap()
        currentActive.remove(transfer.transferId)
        _activeTransfers.value = currentActive
        
        // Record in analytics tracker for mesh sync
        serviceScope.launch {
            try {
                analyticsTracker.recordTransfer(transfer)
            } catch (e: Exception) {
                Log.e(TAG, "Error recording transfer in analytics tracker", e)
            }
        }
    }
    
    /**
     * Get transfer statistics
     */
    fun getTransferStats(): TransferStats {
        val records = _transferRecords.value
        return TransferStats(
            totalTransfers = records.size,
            transfersByMethod = records.groupBy { it.transferMethod }.mapValues { it.value.size },
            totalBytesTransferred = records.sumOf { it.fileSize },
            completedTransfers = records.count { it.transferStatus == TransferStatus.COMPLETED },
            failedTransfers = records.count { it.transferStatus == TransferStatus.FAILED }
        )
    }
    
    /**
     * Get all transfer records for sync
     */
    fun getAllTransferRecords(): List<TransferRecord> = _transferRecords.value
    
    /**
     * Clear old transfer records (for cleanup)
     */
    fun clearOldRecords(olderThanDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        val filtered = _transferRecords.value.filter { it.timestamp > cutoffTime }
        _transferRecords.value = filtered
        Log.d(TAG, "Cleared old transfer records, kept ${filtered.size} records")
    }
    
    /**
     * Release resources
     */
    fun release() {
        // Stop file observers
        fileObservers.values.forEach { it.stopWatching() }
        fileObservers.clear()
        
        // Unregister receivers
        try {
            usbReceiver?.let { context.unregisterReceiver(it) }
            bluetoothReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receivers", e)
        }
        
        serviceScope.cancel()
        Log.d(TAG, "TransferTrackingService released")
    }
    
    data class TransferStats(
        val totalTransfers: Int,
        val transfersByMethod: Map<TransferMethod, Int>,
        val totalBytesTransferred: Long,
        val completedTransfers: Int,
        val failedTransfers: Int
    )
}