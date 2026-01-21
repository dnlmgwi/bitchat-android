package com.bitchat.android.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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
import com.bitchat.android.music.MusicPlayerService
import com.bitchat.android.music.PlaybackAnalyticsTracker
import com.bitchat.android.music.MusicAnalyticsMeshSync
import com.bitchat.android.music.MusicLibraryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main music player screen with integrated analytics tracking
 * Follows bitchat's Jetpack Compose UI patterns
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    musicPlayerService: MusicPlayerService,
    analyticsTracker: PlaybackAnalyticsTracker,
    meshSync: MusicAnalyticsMeshSync,
    libraryService: MusicLibraryService,
    onShareTrack: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPlaying by musicPlayerService.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by musicPlayerService.currentPosition.collectAsStateWithLifecycle()
    val duration by musicPlayerService.duration.collectAsStateWithLifecycle()
    val currentTrack by musicPlayerService.currentTrackInfo.collectAsStateWithLifecycle()
    
    val analyticsStats by analyticsTracker.totalRecords.collectAsStateWithLifecycle()
    val pendingSyncRecords by analyticsTracker.pendingSyncRecords.collectAsStateWithLifecycle()
    val syncStatus by meshSync.syncStatus.collectAsStateWithLifecycle()
    val discoveredAggregators by meshSync.discoveredAggregators.collectAsStateWithLifecycle()
    
    var showLibrary by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Music Player",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Current Track Info
        CurrentTrackCard(
            track = currentTrack,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPause = {
                if (isPlaying) {
                    musicPlayerService.pause()
                } else {
                    musicPlayerService.play()
                }
            },
            onStop = { musicPlayerService.stop() },
            onSeek = { position -> musicPlayerService.seekTo(position) },
            onShare = { 
                currentTrack?.let { track ->
                    onShareTrack(track.filePath)
                }
            },
            onOpenLibrary = { showLibrary = true }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Analytics Dashboard
        AnalyticsDashboard(
            totalRecords = analyticsStats,
            pendingSyncRecords = pendingSyncRecords,
            syncStatus = syncStatus,
            discoveredAggregators = discoveredAggregators.size
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Music Library Preview
        if (!showLibrary) {
            MusicLibraryPreview(
                libraryService = libraryService,
                onTrackSelected = { track ->
                    // Load and play the selected track
                    CoroutineScope(Dispatchers.Main).launch {
                        val success = musicPlayerService.loadTrack(track.path)
                        if (success) {
                            musicPlayerService.play()
                        }
                    }
                },
                onOpenFullLibrary = { showLibrary = true }
            )
        }
    }
    
    // Full library screen
    if (showLibrary) {
        MusicLibraryScreen(
            libraryService = libraryService,
            onTrackSelected = { track ->
                // Load and play the selected track
                CoroutineScope(Dispatchers.Main).launch {
                    val success = musicPlayerService.loadTrack(track.path)
                    if (success) {
                        musicPlayerService.play()
                    }
                }
                showLibrary = false
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CurrentTrackCard(
    track: MusicPlayerService.TrackInfo?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
    onShare: () -> Unit = {},
    onOpenLibrary: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (track != null) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress bar
                Column {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    FloatingActionButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            } else {
                Text(
                    text = "No track loaded",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onOpenLibrary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Music Library")
                }
            }
        }
    }
}

@Composable
private fun AnalyticsDashboard(
    totalRecords: Int,
    pendingSyncRecords: Int,
    syncStatus: MusicAnalyticsMeshSync.SyncStatus,
    discoveredAggregators: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Analytics Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Statistics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Total Records",
                    value = totalRecords.toString(),
                    icon = Icons.Default.Analytics
                )
                
                StatisticItem(
                    label = "Pending Sync",
                    value = pendingSyncRecords.toString(),
                    icon = Icons.Default.Sync
                )
                
                StatisticItem(
                    label = "Aggregators",
                    value = discoveredAggregators.toString(),
                    icon = Icons.Default.Hub
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sync status
            SyncStatusIndicator(syncStatus)
        }
    }
}

@Composable
private fun StatisticItem(
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
private fun SyncStatusIndicator(syncStatus: MusicAnalyticsMeshSync.SyncStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, color, text) = when (syncStatus) {
            is MusicAnalyticsMeshSync.SyncStatus.Idle -> Triple(
                Icons.Default.CloudOff,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Idle"
            )
            is MusicAnalyticsMeshSync.SyncStatus.Discovering -> Triple(
                Icons.Default.Search,
                MaterialTheme.colorScheme.primary,
                "Discovering aggregators..."
            )
            is MusicAnalyticsMeshSync.SyncStatus.Syncing -> Triple(
                Icons.Default.CloudSync,
                MaterialTheme.colorScheme.primary,
                "Syncing to ${syncStatus.aggregatorId} (${(syncStatus.progress * 100).toInt()}%)"
            )
            is MusicAnalyticsMeshSync.SyncStatus.Success -> Triple(
                Icons.Default.CloudDone,
                MaterialTheme.colorScheme.primary,
                "Synced ${syncStatus.recordsSynced} records to ${syncStatus.aggregatorId}"
            )
            is MusicAnalyticsMeshSync.SyncStatus.Error -> Triple(
                Icons.Default.Error,
                MaterialTheme.colorScheme.error,
                "Error: ${syncStatus.message}"
            )
        }
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun MusicLibraryPreview(
    libraryService: MusicLibraryService,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit,
    onOpenFullLibrary: () -> Unit
) {
    val audioFiles by libraryService.audioFiles.collectAsStateWithLifecycle()
    val isScanning by libraryService.isScanning.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    text = "Music Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onOpenFullLibrary) {
                    Text("View All")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            when {
                isScanning -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scanning library...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                audioFiles.isEmpty() -> {
                    Text(
                        text = "No music files found. Tap 'View All' to manage folders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "${audioFiles.size} tracks available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Show first few tracks
                    audioFiles.take(3).forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackSelected(track) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (audioFiles.size > 3) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "and ${audioFiles.size - 3} more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}