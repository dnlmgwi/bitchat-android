package com.bitchat.android.ui.usb

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.features.usb.UsbDeviceManager
import com.bitchat.android.features.usb.UsbFileTransferManager
import com.bitchat.android.features.usb.UsbStorageDevice
import com.bitchat.android.features.usb.TransferProgress
import com.bitchat.android.features.usb.TransferResult
import com.bitchat.android.features.file.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for transferring files to USB flash drives
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbTransferScreen(
    selectedFiles: List<File> = emptyList(),
    onFilesSelected: (List<File>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // USB managers
    val usbDeviceManager = remember { UsbDeviceManager(context) }
    val usbTransferManager = remember { UsbFileTransferManager(context) }
    
    // State
    val connectedDevices by usbDeviceManager.connectedDevices.collectAsStateWithLifecycle()
    val transferProgress by usbTransferManager.transferProgress.collectAsStateWithLifecycle()
    val transferHistory by usbTransferManager.transferHistory.collectAsStateWithLifecycle()
    
    var selectedUsbDevice by remember { mutableStateOf<UsbStorageDevice?>(null) }
    var selectedUsbUri by remember { mutableStateOf<Uri?>(null) }
    var showTransferResult by remember { mutableStateOf<TransferResult?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    
    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Copy selected files to app storage for transfer
            scope.launch {
                val files = uris.mapNotNull { uri ->
                    val path = FileUtils.copyFileForSending(context, uri)
                    path?.let { File(it) }
                }
                onFilesSelected(files)
            }
        }
    }
    
    // USB folder picker launcher
    val usbFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission for the USB drive
            context.contentResolver.takePersistableUriPermission(
                uri, 
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedUsbUri = uri
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            usbDeviceManager.unregister()
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Transfer Files") },
                icon = { Icon(Icons.Default.Usb, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Transfer History") },
                icon = { Icon(Icons.Default.History, contentDescription = null) }
            )
        }
        
        // Tab Content
        when (selectedTab) {
            0 -> UsbTransferContent(
                selectedFiles = selectedFiles,
                connectedDevices = connectedDevices,
                selectedUsbDevice = selectedUsbDevice,
                selectedUsbUri = selectedUsbUri,
                transferProgress = transferProgress,
                onSelectFiles = { filePicker.launch(arrayOf("*/*")) },
                onSelectUsbDevice = { device ->
                    selectedUsbDevice = device
                    if (!device.hasPermission) {
                        usbDeviceManager.requestPermission(device)
                    }
                },
                onSelectUsbFolder = { 
                    usbFolderPicker.launch(null)
                },
                onStartTransfer = { files, usbUri ->
                    scope.launch {
                        val result = usbTransferManager.transferFilesToUsb(files, usbUri)
                        showTransferResult = result
                    }
                },
                onCancelTransfer = { usbTransferManager.cancelTransfer() },
                onRefreshDevices = { usbDeviceManager.scanForStorageDevices() },
                modifier = Modifier.weight(1f)
            )
            1 -> UsbTransferHistoryContent(
                transferHistory = transferHistory,
                onClearHistory = { usbTransferManager.clearHistory() },
                modifier = Modifier.weight(1f)
            )
        }
    }
    
    // Transfer result dialog
    showTransferResult?.let { result ->
        TransferResultDialog(
            result = result,
            onDismiss = { 
                showTransferResult = null
                usbTransferManager.clearProgress()
            }
        )
    }
}

@Composable
private fun UsbTransferContent(
    selectedFiles: List<File>,
    connectedDevices: List<UsbStorageDevice>,
    selectedUsbDevice: UsbStorageDevice?,
    selectedUsbUri: Uri?,
    transferProgress: TransferProgress?,
    onSelectFiles: () -> Unit,
    onSelectUsbDevice: (UsbStorageDevice) -> Unit,
    onSelectUsbFolder: () -> Unit,
    onStartTransfer: (List<File>, Uri) -> Unit,
    onCancelTransfer: () -> Unit,
    onRefreshDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Instructions
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "USB File Transfer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "1. Connect your USB flash drive\n" +
                              "2. Select files to transfer\n" +
                              "3. Choose USB destination folder\n" +
                              "4. Start transfer with progress tracking",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // File Selection
        item {
            FileSelectionSection(
                selectedFiles = selectedFiles,
                onSelectFiles = onSelectFiles
            )
        }
        
        // USB Device Detection
        item {
            UsbDeviceSection(
                connectedDevices = connectedDevices,
                selectedDevice = selectedUsbDevice,
                onSelectDevice = onSelectUsbDevice,
                onRefreshDevices = onRefreshDevices
            )
        }
        
        // USB Folder Selection
        item {
            UsbFolderSection(
                selectedUri = selectedUsbUri,
                onSelectFolder = onSelectUsbFolder
            )
        }
        
        // Transfer Progress
        transferProgress?.let { progress ->
            item {
                TransferProgressSection(
                    progress = progress,
                    onCancel = onCancelTransfer
                )
            }
        }
        
        // Transfer Button
        item {
            val canTransfer = selectedFiles.isNotEmpty() && 
                            selectedUsbUri != null && 
                            transferProgress?.isActive != true
            
            Button(
                onClick = { 
                    selectedUsbUri?.let { uri ->
                        onStartTransfer(selectedFiles, uri)
                    }
                },
                enabled = canTransfer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (transferProgress?.isActive == true) "Transferring..." 
                    else "Start Transfer (${selectedFiles.size} files)"
                )
            }
        }
    }
}

@Composable
private fun FileSelectionSection(
    selectedFiles: List<File>,
    onSelectFiles: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Files (${selectedFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                OutlinedButton(onClick = onSelectFiles) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select Files")
                }
            }
            
            if (selectedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                val totalSize = selectedFiles.sumOf { it.length() }
                Text(
                    text = "Total size: ${FileUtils.formatFileSize(totalSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                selectedFiles.take(5).forEach { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${file.name} (${FileUtils.formatFileSize(file.length())})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                if (selectedFiles.size > 5) {
                    Text(
                        text = "... and ${selectedFiles.size - 5} more files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UsbDeviceSection(
    connectedDevices: List<UsbStorageDevice>,
    selectedDevice: UsbStorageDevice?,
    onSelectDevice: (UsbStorageDevice) -> Unit,
    onRefreshDevices: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "USB Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                IconButton(onClick = onRefreshDevices) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh devices")
                }
            }
            
            if (connectedDevices.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.UsbOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No USB storage devices detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                
                connectedDevices.forEach { device ->
                    UsbDeviceItem(
                        device = device,
                        isSelected = selectedDevice == device,
                        onSelect = { onSelectDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UsbDeviceItem(
    device: UsbStorageDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                if (device.hasPermission) Icons.Default.Usb else Icons.Default.UsbOff,
                contentDescription = null,
                tint = if (device.hasPermission) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (device.hasPermission) "Ready" else "Permission required",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (device.hasPermission) {
                        Color.Green
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun UsbFolderSection(
    selectedUri: Uri?,
    onSelectFolder: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Destination Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                OutlinedButton(onClick = onSelectFolder) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select Folder")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (selectedUri != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedUri.lastPathSegment ?: "USB Drive",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No folder selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferProgressSection(
    progress: TransferProgress,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfer Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (progress.isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel transfer")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Current file: ${progress.currentFile}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LinearProgressIndicator(
                progress = progress.fileProgress,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Overall progress: ${(progress.overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            
            LinearProgressIndicator(
                progress = progress.overallProgress,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${FileUtils.formatFileSize(progress.transferredBytes)} / ${FileUtils.formatFileSize(progress.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsbTransferHistoryContent(
    transferHistory: List<com.bitchat.android.features.usb.TransferRecord>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transfer History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            if (transferHistory.isNotEmpty()) {
                OutlinedButton(onClick = onClearHistory) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (transferHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No transfer history",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transferHistory) { record ->
                    TransferHistoryItem(record = record)
                }
            }
        }
    }
}

@Composable
private fun TransferHistoryItem(
    record: com.bitchat.android.features.usb.TransferRecord
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.destination,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${record.fileCount} files",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = FileUtils.formatFileSize(record.totalSize),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (record.failedCount > 0) {
                    Text(
                        text = "${record.failedCount} failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferResultDialog(
    result: TransferResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (result) {
                    is TransferResult.Success -> "Transfer Complete"
                    is TransferResult.PartialSuccess -> "Transfer Partially Complete"
                    is TransferResult.Error -> "Transfer Failed"
                }
            )
        },
        text = {
            Text(
                text = when (result) {
                    is TransferResult.Success -> 
                        "Successfully transferred ${result.fileCount} files (${FileUtils.formatFileSize(result.totalSize)})"
                    is TransferResult.PartialSuccess -> 
                        "Transferred ${result.successCount} files successfully, ${result.failedCount} failed"
                    is TransferResult.Error -> 
                        "Transfer failed: ${result.message}"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}