package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.music.ui.MusicAnalyticsViewModel
import com.bitchat.android.music.ui.MusicAnalyticsViewModelFactory
import com.bitchat.android.music.ui.MusicSharingScreen
import com.bitchat.android.music.ui.MediaPlayerWidget
import com.bitchat.android.music.ui.MusicLibraryMainScreen
import com.bitchat.android.music.ui.FileTransferScreen
import com.bitchat.android.music.ui.AggregatorModeScreen

/**
 * Main navigation screen that includes both chat and music analytics
 * Follows bitchat's single-activity architecture with bottom navigation
 * Enhanced with modern media player widget
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen(
    chatViewModel: ChatViewModel,
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.CHAT) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showMusicLibrary by remember { mutableStateOf(false) }
    var showAggregatorModeSheet by remember { mutableStateOf(false) }
    var showFullAggregatorScreen by remember { mutableStateOf(false) }
    
    // Create music analytics ViewModel
    val musicAnalyticsViewModel: MusicAnalyticsViewModel = viewModel(
        factory = MusicAnalyticsViewModelFactory(
            application = chatViewModel.getApplication<android.app.Application>(),
            meshService = meshService
        )
    )
    
    // Observe music playing state for widget visibility
    val musicUiState by musicAnalyticsViewModel.uiState.collectAsStateWithLifecycle()
    val isPlayerVisible = !showMusicLibrary && !showFullAggregatorScreen && (musicUiState.isPlaying || musicUiState.currentTrack != null)
    
    // Handle back press when in aggregator mode
    BackHandler(enabled = showFullAggregatorScreen) {
        showFullAggregatorScreen = false
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // Only show bottom nav when player is not expanded and library is not open and aggregator screen is not open
                AnimatedVisibility(
                    visible = !isPlayerExpanded && !showMusicLibrary && !showFullAggregatorScreen,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    NavigationBar {
                        NavigationTab.values().forEach { tab ->
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                selected = selectedTab == tab,
                                onClick = { 
                                    selectedTab = tab
                                    // Close expanded player when switching tabs
                                    if (isPlayerExpanded) isPlayerExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Persistent Aggregator Mode Indicator at top
                    AggregatorModeIndicator(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        onClick = { showAggregatorModeSheet = true }
                    )
                    
                    // Main content
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        when {
                            showFullAggregatorScreen -> {
                                AggregatorModeScreen(
                                    onBack = { showFullAggregatorScreen = false }
                                )
                            }
                            showMusicLibrary -> {
                                MusicLibraryMainScreen(
                                    libraryService = musicAnalyticsViewModel.libraryService,
                                    onTrackSelected = { track ->
                                        // Create playlist from all available tracks and start from selected track
                                        CoroutineScope(Dispatchers.Main).launch {
                                            val allTracks = musicAnalyticsViewModel.libraryService.audioFiles.value
                                            val trackPaths = allTracks.map { it.path }
                                            val selectedIndex = allTracks.indexOfFirst { it.path == track.path }
                                            
                                            musicAnalyticsViewModel.playerService?.let { player ->
                                                if (selectedIndex >= 0) {
                                                    player.setPlaylist(trackPaths, selectedIndex)
                                                    player.play()
                                                } else {
                                                    // Fallback to single track
                                                    val success = player.loadTrack(track.path)
                                                    if (success) {
                                                        player.play()
                                                    }
                                                }
                                            }
                                        }
                                        showMusicLibrary = false
                                    },
                                    onBack = { showMusicLibrary = false },
                                    isPlayerVisible = isPlayerVisible
                                )
                            }
                            else -> {
                                when (selectedTab) {
                                    NavigationTab.CHAT -> {
                                        ChatScreen(
                                            viewModel = chatViewModel,
                                            isPlayerVisible = isPlayerVisible
                                        )
                                    }
                                    NavigationTab.MUSIC -> {
                                        MusicLibraryMainScreen(
                                            libraryService = musicAnalyticsViewModel.libraryService,
                                            onTrackSelected = { track ->
                                                // Create playlist from all available tracks and start from selected track
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    val allTracks = musicAnalyticsViewModel.libraryService.audioFiles.value
                                                    val trackPaths = allTracks.map { it.path }
                                                    val selectedIndex = allTracks.indexOfFirst { it.path == track.path }
                                                    
                                                    musicAnalyticsViewModel.playerService?.let { player ->
                                                        if (selectedIndex >= 0) {
                                                            player.setPlaylist(trackPaths, selectedIndex)
                                                            player.play()
                                                        } else {
                                                            // Fallback to single track
                                                            val success = player.loadTrack(track.path)
                                                            if (success) {
                                                                player.play()
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onBack = null, // No back action for main tab
                                            isPlayerVisible = isPlayerVisible
                                        )
                                    }
                                    NavigationTab.TRANSFERS -> {
                                        FileTransferScreen(
                                            viewModel = musicAnalyticsViewModel
                                        )
                                    }
                                    NavigationTab.ANALYTICS -> {
                                        AnalyticsOverviewScreen(
                                            viewModel = musicAnalyticsViewModel,
                                            onShowAggregatorMode = { showAggregatorModeSheet = true },
                                            isPlayerVisible = isPlayerVisible
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Media player widget overlay
                musicAnalyticsViewModel.playerService?.let { playerService ->
                    AnimatedVisibility(
                        visible = isPlayerVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(
                            if (isPlayerExpanded) Alignment.Center else Alignment.BottomCenter
                        )
                    ) {
                        MediaPlayerWidget(
                            musicPlayerService = playerService,
                            isExpanded = isPlayerExpanded,
                            onExpandedChange = { isPlayerExpanded = it },
                            onOpenLibrary = { 
                                // Navigate to Music tab and collapse player
                                selectedTab = NavigationTab.MUSIC
                                isPlayerExpanded = false
                            },
                            modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isPlayerExpanded) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                }
                            )
                    )
                }
            }
            }
        }
    }
    
    // Aggregator Mode Sheet
    if (showAggregatorModeSheet) {
        AggregatorModeSheet(
            onDismiss = { showAggregatorModeSheet = false },
            onNavigateToFullScreen = { showFullAggregatorScreen = true }
        )
    }
}

/**
 * Enhanced analytics overview screen showing detailed statistics and insights
 */
@Composable
private fun AnalyticsOverviewScreen(
    viewModel: MusicAnalyticsViewModel,
    onShowAggregatorMode: () -> Unit = {},
    isPlayerVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailedStats by viewModel.getDetailedStats().collectAsStateWithLifecycle()
    val deviceInfo = remember { viewModel.getDeviceInfo() }
    
    // Get music library tracks count for accurate "Total Tracks" display
    val libraryTracks by viewModel.libraryService.audioFiles.collectAsStateWithLifecycle()
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = if (isPlayerVisible) 80.dp else 0.dp), // Conditional bottom padding
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Music Analytics Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Quick Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickStatCard(
                    title = "Total Tracks",
                    value = libraryTracks.size.toString(),
                    icon = Icons.Default.MusicNote,
                    modifier = Modifier.weight(1f)
                )
                
                QuickStatCard(
                    title = "Play Time",
                    value = formatPlayTime(detailedStats.totalPlayTimeSeconds),
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickStatCard(
                    title = "Total Plays",
                    value = detailedStats.totalRecords.toString(),
                    icon = Icons.Default.Repeat,
                    modifier = Modifier.weight(1f)
                )
                
                QuickStatCard(
                    title = "Played Tracks",
                    value = detailedStats.uniqueTracks.toString(),
                    icon = Icons.Default.PlayCircle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Device Information Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Device Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InfoRow("Device ID", deviceInfo.deviceId.take(16) + "...")
                    InfoRow("Mesh Peer ID", deviceInfo.meshPeerId)
                    InfoRow("Public Key", deviceInfo.publicKeyHex.take(32) + "...")
                }
            }
        }
        
        // Playback Statistics Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Playback Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InfoRow("Total Records", detailedStats.totalRecords.toString())
                    InfoRow("Qualifying Plays", detailedStats.qualifyingPlays.toString())
                    InfoRow("Unique Tracks", detailedStats.uniqueTracks.toString())
                    InfoRow("Average Play Time", 
                        if (detailedStats.totalRecords > 0) 
                            formatPlayTime(detailedStats.totalPlayTimeSeconds / detailedStats.totalRecords)
                        else "0s"
                    )
                }
            }
        }
        
        // Mesh Network Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mesh Network Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InfoRow("Pending Sync Records", detailedStats.pendingSyncRecords.toString())
                    InfoRow("Sync Status", if (detailedStats.pendingSyncRecords > 0) "Pending" else "Up to date")
                    
                    if (detailedStats.pendingSyncRecords > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { 
                                val synced = detailedStats.totalRecords - detailedStats.pendingSyncRecords
                                if (detailedStats.totalRecords > 0) synced.toFloat() / detailedStats.totalRecords.toFloat() else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        
        // Actions Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerSync() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.cleanupOldRecords() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cleanup")
                        }
                    }
                }
            }
        }
        
        // Aggregator Mode Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aggregator Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Enable this device as an aggregation node for collecting analytics data from nearby devices in the mesh network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = onShowAggregatorMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure Aggregator Mode")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatPlayTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${remainingSeconds}s"
        minutes > 0 -> "${minutes}m ${remainingSeconds}s"
        else -> "${remainingSeconds}s"
    }
}

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

enum class NavigationTab(
    val title: String,
    val icon: ImageVector
) {
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    MUSIC("Music", Icons.Default.LibraryMusic),
    TRANSFERS("Transfers", Icons.Default.SwapHoriz),
    ANALYTICS("Analytics", Icons.Default.Analytics)
}