package com.bitchat.android.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * Main music library screen - focused on browsing and selecting music
 * Now serves as the default Music tab screen with integrated analytics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryMainScreen(
    libraryService: MusicLibraryService,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit,
    onBack: (() -> Unit)? = null, // Optional back action for when used as overlay
    isPlayerVisible: Boolean = false, // Add parameter to know if media widget is visible
    modifier: Modifier = Modifier
) {
    val isScanning by libraryService.isScanning.collectAsStateWithLifecycle()
    val audioFiles by libraryService.audioFiles.collectAsStateWithLifecycle()
    val folders by libraryService.folders.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(LibraryViewMode.TRACKS) }
    var showFolderManager by remember { mutableStateOf(false) }
    
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
        // Top app bar
        TopAppBar(
            title = { 
                Text(
                    text = "Music Library",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                // Only show back button if onBack is provided (overlay mode)
                onBack?.let { backAction ->
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                IconButton(onClick = { libraryService.scanLibrary() }) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Sync else Icons.Default.Refresh,
                        contentDescription = "Refresh library"
                    )
                }
                IconButton(onClick = { showFolderManager = true }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Manage folders")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search tracks, artists, albums...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true
        )
        
        // View mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = { viewMode = LibraryViewMode.TRACKS },
                label = { Text("All Tracks") },
                selected = viewMode == LibraryViewMode.TRACKS,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            
            FilterChip(
                onClick = { viewMode = LibraryViewMode.FOLDERS },
                label = { Text("By Folder") },
                selected = viewMode == LibraryViewMode.FOLDERS,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            
            FilterChip(
                onClick = { viewMode = LibraryViewMode.ARTISTS },
                label = { Text("Artists") },
                selected = viewMode == LibraryViewMode.ARTISTS,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        
        // Library stats
        if (!isScanning && audioFiles.isNotEmpty()) {
            Text(
                text = "${audioFiles.size} tracks • ${folders.count { !it.isExcluded }} folders",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        
        // Content
        when {
            isScanning -> {
                LibraryLoadingContent()
            }
            audioFiles.isEmpty() -> {
                LibraryEmptyContent(
                    onRefresh = { libraryService.scanLibrary() }
                )
            }
            else -> {
                when (viewMode) {
                    LibraryViewMode.TRACKS -> {
                        LibraryTracksList(
                            tracks = displayedTracks,
                            onTrackSelected = onTrackSelected,
                            isPlayerVisible = isPlayerVisible
                        )
                    }
                    LibraryViewMode.FOLDERS -> {
                        LibraryFoldersList(
                            folders = folders.filter { !it.isExcluded },
                            libraryService = libraryService,
                            onTrackSelected = onTrackSelected,
                            isPlayerVisible = isPlayerVisible
                        )
                    }
                    LibraryViewMode.ARTISTS -> {
                        LibraryArtistsList(
                            tracks = displayedTracks,
                            onTrackSelected = onTrackSelected,
                            isPlayerVisible = isPlayerVisible
                        )
                    }
                }
            }
        }
    }
    
    // Folder management dialog
    if (showFolderManager) {
        LibraryFolderManagerDialog(
            folders = folders,
            onToggleFolder = { folder ->
                libraryService.toggleFolderExclusion(folder.path)
            },
            onDismiss = { showFolderManager = false }
        )
    }
}

@Composable
private fun LibraryLoadingContent() {
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
private fun LibraryEmptyContent(
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
private fun LibraryTracksList(
    tracks: List<MusicLibraryService.AudioTrack>,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit,
    isPlayerVisible: Boolean = false
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = if (isPlayerVisible) 88.dp else 8.dp // Extra bottom padding when player is visible
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks) { track ->
            LibraryTrackItem(
                track = track,
                onClick = { onTrackSelected(track) }
            )
        }
    }
}

@Composable
private fun LibraryFoldersList(
    folders: List<MusicLibraryService.MusicFolder>,
    libraryService: MusicLibraryService,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit,
    isPlayerVisible: Boolean = false
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = if (isPlayerVisible) 88.dp else 8.dp // Extra bottom padding when player is visible
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folders) { folder ->
            LibraryFolderItem(
                folder = folder,
                libraryService = libraryService,
                onTrackSelected = onTrackSelected
            )
        }
    }
}

@Composable
private fun LibraryArtistsList(
    tracks: List<MusicLibraryService.AudioTrack>,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit,
    isPlayerVisible: Boolean = false
) {
    val artistGroups = remember(tracks) {
        tracks.groupBy { it.artist }.toList().sortedBy { it.first }
    }
    
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = if (isPlayerVisible) 88.dp else 8.dp // Extra bottom padding when player is visible
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(artistGroups) { (artist, artistTracks) ->
            LibraryArtistItem(
                artist = artist,
                tracks = artistTracks,
                onTrackSelected = onTrackSelected
            )
        }
    }
}

@Composable
private fun LibraryTrackItem(
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
                imageVector = Icons.Default.MusicNote,
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
            
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LibraryFolderItem(
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
                    LibraryTrackItem(
                        track = track,
                        onClick = { onTrackSelected(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryArtistItem(
    artist: String,
    tracks: List<MusicLibraryService.AudioTrack>,
    onTrackSelected: (MusicLibraryService.AudioTrack) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Artist header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "${tracks.size} tracks",
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
            
            // Tracks by artist
            if (expanded) {
                tracks.forEach { track ->
                    LibraryTrackItem(
                        track = track,
                        onClick = { onTrackSelected(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFolderManagerDialog(
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

enum class LibraryViewMode {
    TRACKS, FOLDERS, ARTISTS
}