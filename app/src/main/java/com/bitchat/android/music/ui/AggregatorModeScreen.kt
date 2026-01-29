package com.bitchat.android.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitchat.android.music.AggregatedDataSyncManager
import com.bitchat.android.music.AggregatorDataExporter
import com.bitchat.android.service.AggregatorModeManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatorModeScreen(
    viewModel: AggregatorModeViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aggregatorManager = AggregatorModeManager.getInstance()
    
    val isEnabled by aggregatorManager.isEnabled.collectAsState()
    val aggregatorId by aggregatorManager.aggregatorId.collectAsState()
    val syncStatus by aggregatorManager.syncStatus.collectAsState()
    val packetsAggregated by aggregatorManager.packetsAggregated.collectAsState()
    val startTime by aggregatorManager.startTime.collectAsState()
    
    var showExportDialog by remember { mutableStateOf(false) }
    var exportInProgress by remember { mutableStateOf(false) }
    var lastExportResult by remember { mutableStateOf<AggregatorDataExporter.ExportResult?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onBack?.let { backAction ->
                IconButton(onClick = backAction) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
            Text(
                text = "Aggregator Mode",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnabled) "Active" else "Inactive",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { aggregatorManager.toggleAggregatorMode() }
                    )
                }
                
                if (isEnabled) {
                    Divider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Aggregator ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = aggregatorId,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = syncStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Packets Collected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = packetsAggregated.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Uptime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (startTime > 0) {
                                    val uptime = System.currentTimeMillis() - startTime
                                    formatUptime(uptime)
                                } else "0s",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // Data Export Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Data Export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Export aggregated music analytics data for integration with external systems",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        enabled = !exportInProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Data")
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            scope.launch {
                                viewModel.refreshDataCounts()
                            }
                        },
                        enabled = !exportInProgress
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
                
                // Data counts
                val dataCounts by viewModel.dataCounts.collectAsState()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DataCountItem(
                        label = "Playback",
                        count = dataCounts.playbackRecords,
                        icon = Icons.Default.PlayArrow
                    )
                    DataCountItem(
                        label = "Sharing", 
                        count = dataCounts.sharingRecords,
                        icon = Icons.Default.Share
                    )
                    DataCountItem(
                        label = "Tracks",
                        count = dataCounts.trackMetadata,
                        icon = Icons.Default.MusicNote
                    )
                    DataCountItem(
                        label = "Transfers",
                        count = dataCounts.transferRecords,
                        icon = Icons.Default.Sync
                    )
                }
                
                // Last export result
                lastExportResult?.let { result ->
                    Divider()
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            
                            Column {
                                Text(
                                    text = if (result.success) "Export Successful" else "Export Failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                if (result.success) {
                                    Text(
                                        text = "${result.recordCount} records exported",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = result.error ?: "Unknown error",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        
                        // Show file path and sharing options if export was successful
                        if (result.success && result.filePath != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Exported File",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Text(
                                        text = result.filePath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { 
                                                scope.launch {
                                                    viewModel.shareExportedFile(result.filePath)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share")
                                        }
                                        
                                        OutlinedButton(
                                            onClick = { 
                                                scope.launch {
                                                    viewModel.openFileLocation(result.filePath)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Peer Aggregators Section
        val discoveredAggregators by viewModel.discoveredAggregators.collectAsState()
        val syncStatus by viewModel.syncStatus.collectAsState()
        
        if (discoveredAggregators.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Discovered Aggregators",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    discoveredAggregators.values.forEach { announcement ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = announcement.aggregatorId.take(12) + "...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${announcement.recordCounts.playbackRecords} playback, ${announcement.recordCounts.sharingRecords} sharing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Button(
                                onClick = { 
                                    scope.launch {
                                        viewModel.requestDataFromPeer(announcement.aggregatorId)
                                    }
                                },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Sync", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        
        // Sync Status
        syncStatus?.let { status ->
            when (status) {
                is AggregatedDataSyncManager.AggregatorSyncStatus.Syncing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Syncing with ${status.peerId.take(8)}...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is AggregatedDataSyncManager.AggregatorSyncStatus.Exporting -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Exporting ${status.recordCount} records...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                else -> { /* Other states don't need visual indicator */ }
            }
        }
        
        // Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "How Aggregator Mode Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                val instructions = listOf(
                    "Collects music analytics data from nearby devices via Bluetooth mesh",
                    "Syncs aggregated data with other aggregator nodes in the network",
                    "Stores playback records, sharing events, transfers, and track metadata locally",
                    "Provides export functionality for integration with music industry systems",
                    "Supports CSV, JSON, XML, and Excel formats for maximum compatibility",
                    "Ideal for burning centers, distribution hubs, and data collection points"
                )
                
                instructions.forEach { instruction ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format, dataType ->
                scope.launch {
                    exportInProgress = true
                    lastExportResult = viewModel.exportData(format, dataType)
                    exportInProgress = false
                    showExportDialog = false
                }
            }
        )
    }
}

@Composable
private fun DataCountItem(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExport: (AggregatorDataExporter.ExportFormat, ExportDataType) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(AggregatorDataExporter.ExportFormat.JSON) }
    var selectedDataType by remember { mutableStateOf(ExportDataType.COMPREHENSIVE) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Data") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Format selection
                Text(
                    text = "Export Format",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Column {
                    AggregatorDataExporter.ExportFormat.values().forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format }
                            )
                            Text(
                                text = format.name,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                // Data type selection
                Text(
                    text = "Data Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Column {
                    ExportDataType.values().forEach { dataType ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedDataType == dataType,
                                onClick = { selectedDataType = dataType }
                            )
                            Text(
                                text = dataType.displayName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(selectedFormat, selectedDataType) }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class ExportDataType(val displayName: String) {
    PLAYBACK_RECORDS("Playback Records Only"),
    SHARING_RECORDS("Sharing Records Only"),
    TRACK_METADATA("Track Metadata Only"),
    TRANSFER_RECORDS("Transfer Records Only"),
    COMPREHENSIVE("Comprehensive Report (All Data)")
}

private fun formatUptime(uptimeMs: Long): String {
    if (uptimeMs <= 0) return "0s"
    
    val seconds = uptimeMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}