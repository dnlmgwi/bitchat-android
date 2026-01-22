package com.bitchat.android.features.usb

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Integration example showing how to use USB transfer functionality
 * This demonstrates the complete workflow for USB file transfers
 */
class UsbTransferIntegration(private val context: Context) {
    
    private val usbDeviceManager = UsbDeviceManager(context)
    private val usbFileTransferManager = UsbFileTransferManager(context)
    
    private val _transferState = MutableStateFlow<UsbTransferState>(UsbTransferState.Idle)
    val transferState: StateFlow<UsbTransferState> = _transferState.asStateFlow()
    
    /**
     * Complete workflow for transferring files to USB
     */
    suspend fun transferFilesToUsb(
        files: List<File>,
        usbUri: Uri
    ): TransferResult {
        try {
            _transferState.value = UsbTransferState.Preparing
            
            // Validate USB URI
            if (!usbFileTransferManager.isUsbDrive(usbUri)) {
                return TransferResult.Error("Selected location is not a USB drive")
            }
            
            // Check if files exist and are readable
            val validFiles = files.filter { it.exists() && it.canRead() }
            if (validFiles.isEmpty()) {
                return TransferResult.Error("No valid files to transfer")
            }
            
            _transferState.value = UsbTransferState.Transferring
            
            // Start transfer
            val result = usbFileTransferManager.transferFilesToUsb(
                files = validFiles,
                usbRootUri = usbUri,
                destinationFolder = "Bitchat"
            )
            
            _transferState.value = when (result) {
                is TransferResult.Success -> UsbTransferState.Success(result)
                is TransferResult.PartialSuccess -> UsbTransferState.PartialSuccess(result)
                is TransferResult.Error -> UsbTransferState.Error(result.message)
            }
            
            return result
            
        } catch (e: Exception) {
            val errorMessage = "Transfer failed: ${e.message}"
            _transferState.value = UsbTransferState.Error(errorMessage)
            return TransferResult.Error(errorMessage)
        }
    }
    
    /**
     * Get connected USB storage devices
     */
    fun getConnectedUsbDevices(): List<UsbStorageDevice> {
        return usbDeviceManager.getStorageDevices()
    }
    
    /**
     * Request permission for a USB device
     */
    fun requestUsbPermission(device: UsbStorageDevice) {
        usbDeviceManager.requestPermission(device)
    }
    
    /**
     * Scan for USB devices
     */
    fun scanForUsbDevices() {
        usbDeviceManager.scanForStorageDevices()
    }
    
    /**
     * Cancel ongoing transfer
     */
    fun cancelTransfer() {
        usbFileTransferManager.cancelTransfer()
        _transferState.value = UsbTransferState.Cancelled
    }
    
    /**
     * Get transfer progress
     */
    fun getTransferProgress(): StateFlow<TransferProgress?> {
        return usbFileTransferManager.transferProgress
    }
    
    /**
     * Get transfer history
     */
    fun getTransferHistory(): StateFlow<List<TransferRecord>> {
        return usbFileTransferManager.transferHistory
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        usbDeviceManager.unregister()
    }
}

/**
 * Represents the state of USB transfer operation
 */
sealed class UsbTransferState {
    object Idle : UsbTransferState()
    object Preparing : UsbTransferState()
    object Transferring : UsbTransferState()
    data class Success(val result: TransferResult.Success) : UsbTransferState()
    data class PartialSuccess(val result: TransferResult.PartialSuccess) : UsbTransferState()
    data class Error(val message: String) : UsbTransferState()
    object Cancelled : UsbTransferState()
}

/**
 * Example ViewModel showing how to integrate USB transfer functionality
 */
class UsbTransferViewModel(context: Context) : ViewModel() {
    
    private val usbIntegration = UsbTransferIntegration(context)
    
    val transferState = usbIntegration.transferState
    val transferProgress = usbIntegration.getTransferProgress()
    val transferHistory = usbIntegration.getTransferHistory()
    
    fun transferFiles(files: List<File>, usbUri: Uri) {
        viewModelScope.launch {
            usbIntegration.transferFilesToUsb(files, usbUri)
        }
    }
    
    fun getUsbDevices(): List<UsbStorageDevice> {
        return usbIntegration.getConnectedUsbDevices()
    }
    
    fun requestUsbPermission(device: UsbStorageDevice) {
        usbIntegration.requestUsbPermission(device)
    }
    
    fun scanForDevices() {
        usbIntegration.scanForUsbDevices()
    }
    
    fun cancelTransfer() {
        usbIntegration.cancelTransfer()
    }
    
    override fun onCleared() {
        super.onCleared()
        usbIntegration.cleanup()
    }
}