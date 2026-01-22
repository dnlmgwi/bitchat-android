package com.bitchat.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.service.AggregatorModeManager
import com.bitchat.android.service.AggregatorStats

/**
 * Persistent aggregator mode status indicator that appears across all screens
 */
@Composable
fun AggregatorModeIndicator(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val aggregatorManager = remember { AggregatorModeManager.getInstance() }
    val isEnabled by aggregatorManager.isEnabled.collectAsState()
    val aggregatorId by aggregatorManager.aggregatorId.collectAsState()
    val syncStatus by aggregatorManager.syncStatus.collectAsState()
    val packetsAggregated by aggregatorManager.packetsAggregated.collectAsState()
    
    AnimatedVisibility(
        visible = isEnabled,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable { onClick() }
                    else Modifier
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side - Status info
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated sync indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (syncStatus) {
                                    "Active - Restored", "Syncing", "Active" -> androidx.compose.ui.graphics.Color.White
                                    "Starting..." -> androidx.compose.ui.graphics.Color(0xFFFFA500) // Orange
                                    "Stopped" -> androidx.compose.ui.graphics.Color.Red
                                    else -> androidx.compose.ui.graphics.Color.Gray
                                }
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = "Aggregator Mode",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = aggregatorId.ifEmpty { "Initializing..." },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Center - Sync status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    if (packetsAggregated > 0) {
                        Text(
                            text = "$packetsAggregated packets",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // Right side - Icon
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = "Aggregator Mode Active",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Compact version for bottom sheets or smaller spaces
 */
@Composable
fun CompactAggregatorModeIndicator(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val aggregatorManager = remember { AggregatorModeManager.getInstance() }
    val isEnabled by aggregatorManager.isEnabled.collectAsState()
    val syncStatus by aggregatorManager.syncStatus.collectAsState()
    
    AnimatedVisibility(
        visible = isEnabled,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .then(
                    if (onClick != null) Modifier.clickable { onClick() }
                    else Modifier
                ),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when (syncStatus) {
                                "Active - Restored", "Syncing", "Active" -> androidx.compose.ui.graphics.Color.White
                                "Starting..." -> androidx.compose.ui.graphics.Color(0xFFFFA500) // Orange
                                else -> androidx.compose.ui.graphics.Color.Gray
                            }
                        )
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = "AGG",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}