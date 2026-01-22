# USB File Transfer Implementation for Bitchat Android

This document describes the USB file transfer functionality implemented for the Bitchat Android app, enabling users to transfer files directly to USB flash drives with progress tracking.

## Overview

The USB transfer implementation provides:
- **USB Device Detection**: Automatic detection of connected USB storage devices
- **Permission Management**: Handles USB device permissions seamlessly
- **File Transfer with Progress**: Real-time progress tracking during transfers
- **Storage Access Framework Integration**: Uses Android's SAF for secure file operations
- **Transfer History**: Maintains a history of completed transfers
- **Error Handling**: Comprehensive error handling and user feedback

## Architecture

### Core Components

1. **UsbDeviceManager** (`app/src/main/java/com/bitchat/android/features/usb/UsbDeviceManager.kt`)
   - Detects USB storage devices
   - Manages USB permissions
   - Monitors device connection/disconnection events

2. **UsbFileTransferManager** (`app/src/main/java/com/bitchat/android/features/usb/UsbFileTransferManager.kt`)
   - Handles file transfers to USB drives
   - Provides progress tracking
   - Maintains transfer history

3. **UsbTransferScreen** (`app/src/main/java/com/bitchat/android/ui/usb/UsbTransferScreen.kt`)
   - Complete UI for USB file transfers
   - File selection, device selection, and progress display
   - Transfer history viewing

4. **UsbTransferIntegration** (`app/src/main/java/com/bitchat/android/features/usb/UsbTransferIntegration.kt`)
   - High-level integration layer
   - Example ViewModel implementation
   - State management for transfers

## Features

### USB Device Detection
- Automatically detects USB mass storage devices
- Supports common USB storage vendor IDs
- Handles device permission requests
- Real-time device connection monitoring

### File Transfer Capabilities
- Transfer multiple files simultaneously
- Real-time progress tracking (per-file and overall)
- Automatic folder creation on USB drive
- MIME type detection and handling
- Transfer cancellation support

### User Interface
- Intuitive file selection interface
- USB device selection with permission status
- Real-time progress visualization
- Transfer history with detailed records
- Error handling with user-friendly messages

### Storage Access Framework Integration
- Secure file access using Android's SAF
- No special permissions required beyond USB host
- Persistent URI permissions for USB drives
- Automatic cleanup and resource management

## Usage

### Basic Integration

```kotlin
// Initialize USB transfer components
val usbDeviceManager = UsbDeviceManager(context)
val usbFileTransferManager = UsbFileTransferManager(context)

// Scan for USB devices
usbDeviceManager.scanForStorageDevices()

// Get connected devices
val devices = usbDeviceManager.getStorageDevices()

// Request permission for a device
if (!device.hasPermission) {
    usbDeviceManager.requestPermission(device)
}

// Transfer files
val files = listOf(File("path/to/file1.mp3"), File("path/to/file2.jpg"))
val result = usbFileTransferManager.transferFilesToUsb(
    files = files,
    usbRootUri = selectedUsbUri,
    destinationFolder = "Bitchat"
)
```

### Using the Complete UI

```kotlin
// Add USB transfer screen to your navigation
UsbTransferScreen(
    selectedFiles = selectedFiles,
    onFilesSelected = { files -> /* handle file selection */ },
    modifier = Modifier.fillMaxSize()
)
```

### ViewModel Integration

```kotlin
class MyViewModel(context: Context) : ViewModel() {
    private val usbIntegration = UsbTransferIntegration(context)
    
    val transferState = usbIntegration.transferState
    val transferProgress = usbIntegration.getTransferProgress()
    
    fun transferFiles(files: List<File>, usbUri: Uri) {
        viewModelScope.launch {
            usbIntegration.transferFilesToUsb(files, usbUri)
        }
    }
}
```

## Implementation Details

### USB Device Detection Logic

The system detects USB storage devices using two methods:

1. **Interface Class Detection**: Checks for USB Mass Storage class (class 8)
2. **Vendor ID Matching**: Recognizes common USB storage vendor IDs:
   - SanDisk (0x0781)
   - Kingston (0x13FE, 0x0951)
   - Transcend (0x8564)
   - Toshiba (0x0930)
   - Seagate (0x0BC2)
   - Western Digital (0x1058)
   - Samsung (0x04E8)
   - And many more...

### File Transfer Process

1. **Validation**: Verify USB URI and file accessibility
2. **Folder Creation**: Create destination folder on USB drive
3. **Transfer Loop**: Copy files with progress tracking
4. **Progress Updates**: Real-time progress via StateFlow
5. **Completion**: Update transfer history and notify user

### Progress Tracking

```kotlin
data class TransferProgress(
    val currentFile: String,
    val fileProgress: Float,      // 0.0 to 1.0
    val overallProgress: Float,   // 0.0 to 1.0
    val transferredBytes: Long,
    val totalBytes: Long,
    val isActive: Boolean
)
```

### Transfer Results

```kotlin
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
```

## Integration with Existing Bitchat Features

### File Transfer Screen Enhancement

The existing `FileTransferScreen` has been enhanced with a new USB transfer tab:

1. **Music Library Tab**: Select music files for transfer
2. **USB Transfer Tab**: Complete USB transfer interface
3. **History Tab**: View transfer history

### Navigation Integration

USB transfer is accessible through the main navigation:
- **Transfers Tab**: Access to all transfer methods including USB
- **Music Tab**: Direct access to music library with USB transfer options

### File Compatibility

The USB transfer system works with all file types supported by Bitchat:
- **Audio Files**: MP3, M4A, WAV, OGG
- **Image Files**: JPEG, PNG, GIF, WebP
- **Document Files**: PDF, DOC, TXT, JSON
- **Archive Files**: ZIP, RAR, 7Z
- **Generic Files**: Any file type via Storage Access Framework

## Security Considerations

### Permissions
- **USB Host Permission**: Required for USB device communication
- **Storage Access Framework**: No additional storage permissions needed
- **Scoped Storage Compliance**: Fully compatible with Android's scoped storage

### Data Protection
- Files are copied securely using Android's content resolver
- No sensitive data is stored on USB drives by default
- Transfer history is stored locally and can be cleared

### Privacy
- No tracking of transfer activities
- USB device information is not transmitted over the network
- Transfer operations are completely offline

## Testing

### Unit Tests
- USB device detection logic
- File transfer progress tracking
- MIME type detection
- Transfer result handling

### Integration Tests
- End-to-end transfer workflows
- USB permission handling
- Storage Access Framework integration
- Error scenarios and recovery

### Manual Testing Scenarios
1. Connect various USB flash drives
2. Transfer different file types and sizes
3. Test permission flows
4. Verify progress tracking accuracy
5. Test cancellation and error handling

## Troubleshooting

### Common Issues

1. **USB Device Not Detected**
   - Ensure USB OTG support on device
   - Check USB drive compatibility
   - Try different USB ports/adapters

2. **Permission Denied**
   - Grant USB device permission when prompted
   - Check USB debugging settings
   - Restart app if permissions seem stuck

3. **Transfer Failures**
   - Verify USB drive has sufficient space
   - Check file permissions and accessibility
   - Ensure USB drive is not write-protected

4. **Slow Transfer Speeds**
   - USB 2.0 vs 3.0 speed differences
   - File size and fragmentation effects
   - Device performance limitations

### Debug Information

Enable verbose logging to troubleshoot issues:
```kotlin
Log.d("UsbTransfer", "Device detected: ${device.name}")
Log.d("UsbTransfer", "Transfer progress: ${progress.overallProgress}")
```

## Future Enhancements

### Potential Improvements
1. **Batch Operations**: Queue multiple transfer operations
2. **Resume Capability**: Resume interrupted transfers
3. **Compression**: Optional file compression before transfer
4. **Encryption**: Encrypt files during transfer
5. **Cloud Integration**: Hybrid USB/cloud transfer options

### Performance Optimizations
1. **Parallel Transfers**: Transfer multiple files simultaneously
2. **Buffer Optimization**: Tune buffer sizes for different devices
3. **Background Transfers**: Continue transfers when app is backgrounded
4. **Smart Retry**: Automatic retry for failed transfers

## Conclusion

The USB file transfer implementation provides a comprehensive solution for transferring files from the Bitchat Android app to USB flash drives. It integrates seamlessly with the existing architecture while providing a robust, user-friendly experience with real-time progress tracking and comprehensive error handling.

The implementation follows Android best practices for USB device interaction and file operations, ensuring compatibility across a wide range of devices and USB storage solutions.