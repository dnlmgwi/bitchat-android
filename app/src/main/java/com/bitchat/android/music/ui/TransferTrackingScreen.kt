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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.music.model.TransferMethod
import com.bitchat.android.music.model.TransferRecord
import com.bitchat.android.music.model.TransferStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for displaying transfer tracking information
 * Shows detected transfers and allows manual transfer logging
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferTrackingScreen(
    viewModel: MusicAnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val transferRecords by viewModel.getTransferRecords().collectAsStateWithLifecycle()
    val activeTransfers by viewModel.getActiveTransfers().collectAsStateWithLifecycle()
    
    var showManualTransferDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Transfer Tracking",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Statistics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Transfer Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Total", transferRecords.size.toString())
                    StatItem("Completed", transferRecords.count { it.transferStatus == TransferStatus.COMPLETED }.toString())
                    StatItem("Failed", transferRecords.count { it.transferStatus == TransferStatus.FAILED }.toString())
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showManualTransferDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Transfer")
            }
            
            OutlinedButton(
                onClick = { viewModel.transferTracker.clearOldRecords() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Old")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Transfer History
        Text(
            text = "Transfer History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transferRecords.sortedByDescending { it.timestamp }) { transfer ->
                TransferRecordItem(transfer = transfer)
            }
        }
    }
    
    // Manual Transfer Dialog
    if (showManualTransferDialog) {
        ManualTransferDialog(
            onDismiss = { showManualTransferDialog = false },
            onConfirm = { method, fileSize ->
                viewModel.logManualTransfer(
                    contentId = "manual_${System.currentTimeMillis()}",
                    method = method,
                    fileSize = fileSize
                )
                showManualTransferDialog = false
            }
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ActiveTransferItem(
    transfer: TransferRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Transfer in progress",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${transfer.transferMethod.name} • ${formatBytes(transfer.fileSize)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TransferRecordItem(
    transfer: TransferRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getTransferMethodIcon(transfer.transferMethod),
                contentDescription = null,
                tint = getStatusColor(transfer.transferStatus)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.transferMethod.name.replace("_", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatBytes(transfer.fileSize)} • ${formatTimestamp(transfer.timestamp)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (transfer.transferDuration != null) {
                    Text(
                        text = "Duration: ${transfer.transferDuration}s",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Icon(
                imageVector = getStatusIcon(transfer.transferStatus),
                contentDescription = transfer.transferStatus.name,
                tint = getStatusColor(transfer.transferStatus)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTransferDialog(
    onDismiss: () -> Unit,
    onConfirm: (TransferMethod, Long) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(TransferMethod.USB_OTG) }
    var fileSizeText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Manual Transfer") },
        text = {
            Column {
                Text("Select transfer method:")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TransferMethod.values().forEach { method ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(method.name.replace("_", " "))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = fileSizeText,
                    onValueChange = { fileSizeText = it },
                    label = { Text("File Size (MB)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fileSize = fileSizeText.toLongOrNull()?.times(1024 * 1024) ?: 0L
                    onConfirm(selectedMethod, fileSize)
                }
            ) {
                Text("Log Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getTransferMethodIcon(method: TransferMethod): ImageVector {
    return when (method) {
        TransferMethod.USB_OTG -> Icons.Default.Usb
        TransferMethod.BLUETOOTH -> Icons.Default.Bluetooth
        TransferMethod.WIFI_DIRECT -> Icons.Default.Wifi
        TransferMethod.MANUAL_IMPORT -> Icons.Default.FileUpload
        TransferMethod.UNKNOWN -> Icons.Default.QuestionMark
    }
}

private fun getStatusIcon(status: TransferStatus): ImageVector {
    return when (status) {
        TransferStatus.COMPLETED -> Icons.Default.CheckCircle
        TransferStatus.FAILED -> Icons.Default.Error
        TransferStatus.CANCELLED -> Icons.Default.Cancel
        TransferStatus.IN_PROGRESS -> Icons.Default.Schedule
        TransferStatus.INITIATED -> Icons.Default.Schedule
    }
}

@Composable
private fun getStatusColor(status: TransferStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        TransferStatus.FAILED -> MaterialTheme.colorScheme.error
        TransferStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        TransferStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        TransferStatus.INITIATED -> MaterialTheme.colorScheme.outline
    }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}