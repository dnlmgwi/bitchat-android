package com.bitchat.android.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.music.MusicPlayerService
import com.bitchat.android.music.MusicLibraryService

/**
 * Modern media player widget with full and minimized modes
 * Follows bitchat's UI patterns with Material 3 design
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MediaPlayerWidget(
    musicPlayerService: MusicPlayerService,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by musicPlayerService.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by musicPlayerService.currentPosition.collectAsStateWithLifecycle()
    val duration by musicPlayerService.duration.collectAsStateWithLifecycle()
    val currentTrack by musicPlayerService.currentTrackInfo.collectAsStateWithLifecycle()
    val repeatMode by musicPlayerService.repeatMode.collectAsStateWithLifecycle()
    val isShuffleMode by musicPlayerService.isShuffleMode.collectAsStateWithLifecycle()

    // Handle back button when player is expanded to minimize it
    BackHandler(enabled = isExpanded) {
        onExpandedChange(false)
    }

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!isExpanded) },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    slideInVertically(initialOffsetY = { if (targetState) it else -it }) + fadeIn() togetherWith
                    slideOutVertically(targetOffsetY = { if (targetState) -it else it }) + fadeOut()
                },
                label = "player_mode"
            ) { expanded ->
                if (expanded) {
                    ExpandedPlayerContent(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        repeatMode = repeatMode,
                        isShuffleMode = isShuffleMode,
                        onPlayPause = {
                            if (isPlaying) musicPlayerService.pause()
                            else musicPlayerService.play()
                        },
                        onSeek = { musicPlayerService.seekTo(it) },
                        onPrevious = { musicPlayerService.playPrevious() },
                        onNext = { musicPlayerService.playNext() },
                        onShuffle = { musicPlayerService.toggleShuffle() },
                        onRepeat = { musicPlayerService.toggleRepeat() },
                        onCollapse = { onExpandedChange(false) },
                        onOpenLibrary = onOpenLibrary
                    )
                } else {
                    MinimizedPlayerContent(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        onPlayPause = {
                            if (isPlaying) musicPlayerService.pause()
                            else musicPlayerService.play()
                        },
                        onExpand = { onExpandedChange(true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimizedPlayerContent(
    track: MusicPlayerService.TrackInfo?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier.padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Track info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track?.title ?: "No track",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Play/pause button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Progress bar
        if (duration > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { currentPosition.toFloat() / duration.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun ExpandedPlayerContent(
    track: MusicPlayerService.TrackInfo?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    repeatMode: MusicPlayerService.RepeatMode,
    isShuffleMode: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp)
    ) {
        // Header with collapse and library buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            IconButton(onClick = onOpenLibrary) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Library",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Large album art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Track info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = track?.title ?: "No track",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = track?.artist ?: "Unknown artist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Progress section
        Column {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Large play/pause button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            IconButton(onClick = onRepeat) {
                val (icon, tint) = when (repeatMode) {
                    MusicPlayerService.RepeatMode.OFF -> Icons.Default.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
                    MusicPlayerService.RepeatMode.ONE -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary
                    MusicPlayerService.RepeatMode.ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = when (repeatMode) {
                        MusicPlayerService.RepeatMode.OFF -> "Repeat Off"
                        MusicPlayerService.RepeatMode.ONE -> "Repeat One"
                        MusicPlayerService.RepeatMode.ALL -> "Repeat All"
                    },
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}