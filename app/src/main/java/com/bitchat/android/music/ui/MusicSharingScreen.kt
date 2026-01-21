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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.music.MusicSharingService
import com.bitchat.android.music.model.ShareMethod
import com.bitchat.android.music.model.TransferStatus

/**
 * Music sharing screen showing active transfers and discovered music
 * Integrates with the mesh network for file sharing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSharingScreen(
    sharingService: MusicSharingService,
    onShareFile: (String, ShareMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTransfers by sharingService.activeTransfers.collectAsStateWithLifecycle()
    val sharingStats by sharingService.sharingStats.collectAsStateWithLifecycle()
    val discoveredMusic by sharingService.discoveredMusic.collectAsStateWithLifecycle()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Music Sharing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sharing Statistics
        SharingStatsCard(sharingStats)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Active Transfers
        if (activeTransfers.isNotEmpty()) {
            ActiveTransfersSection(
                transfers = activeTransfers.values.toList(),
                onCancelTransfer = { recordId ->
                    sharingService.cancelTransfer(recordId)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Discovered Music
        DiscoveredMusicSection(
            discoveredMusic = discoveredMusic.values.toList(),
            onRequestMusic = { contentId, sharerDeviceId ->
                // Request shared music from another device
                // This would be handled by the sharing service
            }
        )
    }
}

@Composable
private fun SharingStatsCard(
    stats: MusicSharingService.SharingStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sharing Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Total Shares",
                    value = stats.totalShares.toString(),
                    icon = Icons.Default.Share
                )
                
                StatItem(
                    label = "Successful",
                    value = stats.successfulShares.toString(),
                    icon = Icons.Default.CheckCircle
                )
                
                StatItem(
                    label = "Failed",
                    value = stats.failedShares.toString(),
                    icon = Icons.Default.Error
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Data Shared",
                    value = formatFileSize(stats.totalBytesShared),
                    icon = Icons.Default.CloudUpload
                )
                
                StatItem(
                    label = "Avg Time",
                    value = "${stats.averageTransferTime / 1000}s",
                    icon = Icons.Default.Timer
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = value,
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
private fun ActiveTransfersSection(
    transfers: List<MusicSharingService.TransferInfo>,
    onCancelTransfer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Active Transfers (${transfers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            transfers.forEach { transfer ->
                TransferItem(
                    transfer = transfer,
                    onCancel = { onCancelTransfer(transfer.recordId) }
                )
                
                if (transfer != transfers.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TransferItem(
    transfer: MusicSharingService.TransferInfo,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = java.io.File(transfer.filePath).name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "${transfer.shareMethod.name} • ${formatFileSize(transfer.totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (transfer.status == TransferStatus.IN_PROGRESS) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancel transfer"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress indicator
            val progress = transfer.transferredBytes.toFloat() / transfer.totalSize.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transfer.status.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (transfer.status) {
                        TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiscoveredMusicSection(
    discoveredMusic: List<MusicSharingService.SharedMusicInfo>,
    onRequestMusic: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Discovered Music (${discoveredMusic.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (discoveredMusic.isEmpty()) {
                Text(
                    text = "No shared music discovered nearby",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredMusic) { music ->
                        DiscoveredMusicItem(
                            music = music,
                            onRequest = { onRequestMusic(music.contentId, music.sharerDeviceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredMusicItem(
    music: MusicSharingService.SharedMusicInfo,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = music.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${formatDuration(music.duration)} • ${formatFileSize(music.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (music.isAvailable) {
                Button(
                    onClick = onRequest,
                    modifier = Modifier.size(width = 80.dp, height = 36.dp)
                ) {
                    Text("Get", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    text = "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}