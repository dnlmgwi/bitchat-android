package com.bitchat.android.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.music.MusicLibraryService

/**
 * Music library screen with folder management and track selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    libraryService: MusicLibraryService,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning by libraryService.isScanning.collectAsStateWithLifecycle()
    val audioFiles by libraryService.audioFiles.collectAsStateWithLifecycle()
    val folders by libraryService.folders.collectAsStateWithLifecycle()
    
    var showFolderManager by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(ViewMode.TRACKS) }
    
    val displayedTracks = remember(audioFiles, searchQuery) {
        if (searchQuery.isBlank()) {
            audioFiles
        } else {
            libraryService.searchTracks(searchQuery)
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header with controls
        LibraryHeader(
            isScanning = isScanning,
            trackCount = audioFiles.size,
            folderCount = folders.count { !it.isExcluded },
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            onRefresh = { libraryService.scanLibrary() },
            onManageFolders = { showFolderManager = true }
        )
        
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Content
        when {
            isScanning -> {
                LoadingContent()
            }
            audioFiles.isEmpty() -> {
                EmptyLibraryContent(
                    onRefresh = { libraryService.scanLibrary() }
                )
            }
            else -> {
                when (viewMode) {
                    ViewMode.TRACKS -> {
                        TracksList(
                            tracks = displayedTracks,
                            onTrackSelected = onTrackSelected
                        )
                    }
                    ViewMode.FOLDERS -> {
                        FoldersList(
                            folders = folders.filter { !it.isExcluded },
                            libraryService = libraryService,
                            onTrackSelected = onTrackSelected
                        )
                    }
                }
            }
        }
    }
    
    // Folder management dialog
    if (showFolderManager) {
        FolderManagerDialog(
            folders = folders,
            onToggleFolder = { folder ->
                libraryService.toggleFolderExclusion(folder.path)
            },
            onDismiss = { showFolderManager = false }
        )
    }
}

@Composable
private fun LibraryHeader(
    isScanning: Boolean,
    trackCount: Int,
    folderCount: Int,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onRefresh: () -> Unit,
    onManageFolders: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Sync else Icons.Default.Refresh,
                            contentDescription = "Refresh library"
                        )
                    }
                    
                    IconButton(onClick = onManageFolders) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Manage folders"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Stats
            Text(
                text = "$trackCount tracks in $folderCount folders",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // View mode toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { onViewModeChange(ViewMode.TRACKS) },
                    label = { Text("All Tracks") },
                    selected = viewMode == ViewMode.TRACKS,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                FilterChip(
                    onClick = { onViewModeChange(ViewMode.FOLDERS) },
                    label = { Text("By Folder") },
                    selected = viewMode == ViewMode.FOLDERS,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search tracks, artists, albums...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        singleLine = true
    )
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning music library...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun EmptyLibraryContent(
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MusicOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No music found",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = "Make sure you have music files on your device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Again")
            }
        }
    }
}

@Composable
private fun TracksList(
    tracks: List<MusicLibraryService.AudioTrack>,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks) { track ->
            TrackItem(
                track = track,
                onClick = { onTrackSelected(track) }
            )
        }
    }
}

@Composable
private fun FoldersList(
    folders: List<MusicLibraryService.MusicFolder>,
    libraryService: MusicLibraryService,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folders) { folder ->
            FolderItem(
                folder = folder,
                libraryService = libraryService,
                onTrackSelected = onTrackSelected
            )
        }
    }
}

@Composable
private fun TrackItem(
    track: MusicLibraryService.AudioTrack,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${formatDuration(track.duration)} • ${formatFileSize(track.size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: MusicLibraryService.MusicFolder,
    libraryService: MusicLibraryService,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val tracks = remember(folder.path) {
        libraryService.getTracksInFolder(folder.path)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Folder header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "${folder.trackCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            // Tracks in folder
            if (expanded) {
                tracks.forEach { track ->
                    TrackItem(
                        track = track,
                        onClick = { onTrackSelected(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderManagerDialog(
    folders: List<MusicLibraryService.MusicFolder>,
    onToggleFolder: (MusicLibraryService.MusicFolder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Music Folders") },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp)
            ) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = !folder.isExcluded,
                            onCheckedChange = { onToggleFolder(folder) }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${folder.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
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

enum class ViewMode {
    TRACKS, FOLDERS
}