package com.bitchat.android.features.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages USB device detection and permission handling for USB flash drives
 */
class UsbDeviceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UsbDeviceManager"
        private const val ACTION_USB_PERMISSION = "com.bitchat.android.USB_PERMISSION"
        
        // USB Mass Storage Class
        private const val USB_CLASS_MASS_STORAGE = 8
        private const val USB_SUBCLASS_SCSI = 6
        private const val USB_PROTOCOL_BULK_ONLY = 80
    }
    
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    private val _connectedDevices = MutableStateFlow<List<UsbStorageDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbStorageDevice>> = _connectedDevices.asStateFlow()
    
    private val _permissionStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionStatus: StateFlow<Map<String, Boolean>> = _permissionStatus.asStateFlow()
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.d(TAG, "Permission granted for device: ${it.deviceName}")
                                updatePermissionStatus(it.deviceName, true)
                            }
                        } else {
                            Log.d(TAG, "Permission denied for device: ${device?.deviceName}")
                            device?.let { updatePermissionStatus(it.deviceName, false) }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB device attached: ${it.deviceName}")
                        scanForStorageDevices()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB device detached: ${it.deviceName}")
                        scanForStorageDevices()
                    }
                }
            }
        }
    }
    
    init {
        registerReceiver()
        scanForStorageDevices()
    }
    
    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbReceiver, filter)
    }
    
    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering USB receiver", e)
        }
    }
    
    /**
     * Scan for connected USB storage devices
     */
    fun scanForStorageDevices() {
        val devices = mutableListOf<UsbStorageDevice>()
        
        usbManager.deviceList.values.forEach { device ->
            if (isStorageDevice(device)) {
                val storageDevice = UsbStorageDevice(
                    device = device,
                    name = device.productName ?: "USB Storage Device",
                    deviceName = device.deviceName,
                    hasPermission = usbManager.hasPermission(device)
                )
                devices.add(storageDevice)
                Log.d(TAG, "Found storage device: ${storageDevice.name} (${device.deviceName})")
            }
        }
        
        _connectedDevices.value = devices
    }
    
    /**
     * Check if a USB device is a mass storage device
     */
    private fun isStorageDevice(device: UsbDevice): Boolean {
        // Check if device has mass storage interface
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == USB_CLASS_MASS_STORAGE) {
                Log.d(TAG, "Device ${device.deviceName} is mass storage class")
                return true
            }
        }
        
        // Additional check for common USB storage vendor/product IDs
        val vendorId = device.vendorId
        val productId = device.productId
        
        // Common USB storage device vendor IDs
        val storageVendorIds = setOf(
            0x0781, // SanDisk
            0x058F, // Alcor Micro
            0x090C, // Silicon Motion
            0x13FE, // Kingston
            0x0951, // Kingston
            0x8564, // Transcend
            0x0930, // Toshiba
            0x0BC2, // Seagate
            0x1058, // Western Digital
            0x04E8, // Samsung
            0x0424, // Standard Microsystems
            0x152D, // JMicron
            0x174C, // ASMedia
            0x1F75, // Innostor
            0x0BDA  // Realtek
        )
        
        val isKnownStorage = vendorId in storageVendorIds
        if (isKnownStorage) {
            Log.d(TAG, "Device ${device.deviceName} matches known storage vendor ID: ${String.format("0x%04X", vendorId)}")
        }
        
        return isKnownStorage
    }
    
    /**
     * Request permission for a USB device
     */
    fun requestPermission(device: UsbStorageDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device.device, permissionIntent)
        Log.d(TAG, "Requesting permission for device: ${device.deviceName}")
    }
    
    private fun updatePermissionStatus(deviceName: String, granted: Boolean) {
        val currentStatus = _permissionStatus.value.toMutableMap()
        currentStatus[deviceName] = granted
        _permissionStatus.value = currentStatus
        
        // Update device list with new permission status
        scanForStorageDevices()
    }
    
    /**
     * Get available USB storage devices
     */
    fun getStorageDevices(): List<UsbStorageDevice> {
        return _connectedDevices.value
    }
    
    /**
     * Check if any USB storage devices are connected
     */
    fun hasStorageDevices(): Boolean {
        return _connectedDevices.value.isNotEmpty()
    }
}

/**
 * Represents a USB storage device
 */
data class UsbStorageDevice(
    val device: UsbDevice,
    val name: String,
    val deviceName: String,
    val hasPermission: Boolean
) {
    val vendorId: Int get() = device.vendorId
    val productId: Int get() = device.productId
    val serialNumber: String? get() = device.serialNumber
}