package com.bitchat.android.music.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.music.model.TransferMethod
import com.bitchat.android.music.MusicLibraryService
import com.bitchat.android.ui.usb.UsbTransferScreen

/**
 * File transfer screen for selecting and sharing music files via various methods
 * Selects from the music library instead of using a file picker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    viewModel: MusicAnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTracks by remember { mutableStateOf<Set<MusicLibraryService.AudioTrack>>(emptySet()) }
    var showTransferOptions by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    // Get music library
    val audioTracks by viewModel.libraryService.audioFiles.collectAsStateWithLifecycle()
    val isScanning by viewModel.libraryService.isScanning.collectAsStateWithLifecycle()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Music Library") },
                icon = { Icon(Icons.Default.Share, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("USB Transfer") },
                icon = { Icon(Icons.Default.Usb, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("History") },
                icon = { Icon(Icons.Default.History, contentDescription = null) }
            )
        }
        
        // Tab Content
        when (selectedTab) {
            0 -> MusicLibraryTransferContent(
                audioTracks = audioTracks,
                isScanning = isScanning,
                selectedTracks = selectedTracks,
                onTracksSelected = { selectedTracks = it },
                onShowTransferOptions = { showTransferOptions = true },
                onRefreshLibrary = { viewModel.libraryService.scanLibrary() },
                modifier = Modifier.weight(1f)
            )
            1 -> UsbTransferScreen(
                selectedFiles = selectedTracks.map { track ->
                    java.io.File(track.path)
                },
                onFilesSelected = { files ->
                    // Convert files back to tracks for consistency
                    // This is a simplified approach - in a real implementation,
                    // you might want to maintain separate state for USB transfers
                },
                modifier = Modifier.weight(1f)
            )
            2 -> TransferTrackingScreen(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
        }
    }
    
    // Transfer options dialog
    if (showTransferOptions) {
        TransferOptionsDialog(
            tracks = selectedTracks.toList(),
            onDismiss = { showTransferOptions = false },
            onTransfer = { method ->
                // Start the appropriate transfer method
                val trackUris = selectedTracks.map { it.uri }
                when (method) {
                    TransferMethod.BLUETOOTH -> {
                        shareViaBluetoothIntent(context, trackUris)
                    }
                    TransferMethod.USB_OTG -> {
                        shareViaFileManager(context, trackUris)
                    }
                    TransferMethod.WIFI_DIRECT -> {
                        shareViaNearbyShare(context, trackUris)
                    }
                    else -> {
                        shareViaGenericIntent(context, trackUris)
                    }
                }
                
                // Log the transfer attempt
                selectedTracks.forEach { track ->
                    viewModel.logManualTransfer(
                        contentId = "transfer_${track.id}_${System.currentTimeMillis()}",
                        method = method,
                        fileSize = track.size
                    )
                }
                
                showTransferOptions = false
                selectedTracks = emptySet()
            }
        )
    }
}

@Composable
private fun MusicLibraryTransferContent(
    audioTracks: List<MusicLibraryService.AudioTrack>,
    isScanning: Boolean,
    selectedTracks: Set<MusicLibraryService.AudioTrack>,
    onTracksSelected: (Set<MusicLibraryService.AudioTrack>) -> Unit,
    onShowTransferOptions: () -> Unit,
    onRefreshLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Header
        Text(
            text = "Select Music to Transfer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                        text = "How to Transfer Music",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "1. Select music tracks from your library\n" +
                          "2. Choose transfer method (Bluetooth, USB, etc.)\n" +
                          "3. Complete transfer using your device's sharing options\n" +
                          "4. Transfer metadata will be automatically tracked",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Library controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Music Library (${audioTracks.size} tracks)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            
            if (selectedTracks.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onTracksSelected(emptySet()) }
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }
            
            IconButton(
                onClick = onRefreshLibrary,
                enabled = !isScanning
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh library")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Selection controls
        if (audioTracks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onTracksSelected(audioTracks.toSet()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select All")
                }
                
                if (selectedTracks.isNotEmpty()) {
                    Button(
                        onClick = onShowTransferOptions,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transfer (${selectedTracks.size})")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Music library list
        if (isScanning) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Scanning music library...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (audioTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No music found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Add music files to your device and refresh",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(audioTracks) { track ->
                    MusicTrackItem(
                        track = track,
                        isSelected = selectedTracks.contains(track),
                        onSelectionChanged = { isSelected ->
                            if (isSelected) {
                                onTracksSelected(selectedTracks + track)
                            } else {
                                onTracksSelected(selectedTracks - track)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicTrackItem(
    track: MusicLibraryService.AudioTrack,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = { onSelectionChanged(!isSelected) }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Icon(
                Icons.Default.AudioFile,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
                Text(
                    text = "${formatFileSize(track.size)} • ${formatDuration(track.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

@Composable
private fun TransferOptionsDialog(
    tracks: List<MusicLibraryService.AudioTrack>,
    onDismiss: () -> Unit,
    onTransfer: (TransferMethod) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Transfer Method") },
        text = {
            Column {
                Text("Select how you want to transfer ${tracks.size} track(s):")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TransferOptionItem(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth",
                    description = "Share via Bluetooth to nearby devices",
                    onClick = { onTransfer(TransferMethod.BLUETOOTH) }
                )
                
                TransferOptionItem(
                    icon = Icons.Default.Usb,
                    title = "USB/File Manager",
                    description = "Copy to USB drive or use file manager",
                    onClick = { onTransfer(TransferMethod.USB_OTG) }
                )
                
                TransferOptionItem(
                    icon = Icons.Default.Wifi,
                    title = "Nearby Share/WiFi",
                    description = "Share via WiFi Direct or Nearby Share",
                    onClick = { onTransfer(TransferMethod.WIFI_DIRECT) }
                )
                
                TransferOptionItem(
                    icon = Icons.Default.Share,
                    title = "Other Apps",
                    description = "Share via other installed apps",
                    onClick = { onTransfer(TransferMethod.MANUAL_IMPORT) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TransferOptionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// Helper functions for file operations and sharing
private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun shareViaBluetoothIntent(context: android.content.Context, files: List<Uri>) {
    val intent = Intent().apply {
        action = if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        type = "audio/*"
        
        if (files.size == 1) {
            putExtra(Intent.EXTRA_STREAM, files.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        }
        
        // Try to target Bluetooth specifically
        setPackage("com.android.bluetooth")
    }
    
    try {
        context.startActivity(Intent.createChooser(intent, "Share via Bluetooth"))
    } catch (e: Exception) {
        // Fallback to generic share
        shareViaGenericIntent(context, files)
    }
}

private fun shareViaFileManager(context: android.content.Context, files: List<Uri>) {
    val intent = Intent().apply {
        action = if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        type = "audio/*"
        
        if (files.size == 1) {
            putExtra(Intent.EXTRA_STREAM, files.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        }
    }
    
    context.startActivity(Intent.createChooser(intent, "Copy files"))
}

private fun shareViaNearbyShare(context: android.content.Context, files: List<Uri>) {
    val intent = Intent().apply {
        action = if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        type = "audio/*"
        
        if (files.size == 1) {
            putExtra(Intent.EXTRA_STREAM, files.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        }
        
        // Try to target Nearby Share
        setPackage("com.google.android.gms")
    }
    
    try {
        context.startActivity(Intent.createChooser(intent, "Share via Nearby Share"))
    } catch (e: Exception) {
        // Fallback to generic share
        shareViaGenericIntent(context, files)
    }
}

private fun shareViaGenericIntent(context: android.content.Context, files: List<Uri>) {
    val intent = Intent().apply {
        action = if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        type = "audio/*"
        
        if (files.size == 1) {
            putExtra(Intent.EXTRA_STREAM, files.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        }
    }
    
    context.startActivity(Intent.createChooser(intent, "Share files"))
}