package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTopBar
import com.bitchat.android.service.AggregatorModeManager
import com.bitchat.android.service.AggregatorStats

/**
 * Bottom sheet for aggregator mode settings and status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatorModeSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aggregatorManager = remember { AggregatorModeManager.getInstance() }
    val isEnabled by aggregatorManager.isEnabled.collectAsState()
    val stats = aggregatorManager.getStats()
    
    var showCustomIdDialog by remember { mutableStateOf(false) }
    var customId by remember { mutableStateOf("") }

    BitchatBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            BitchatSheetTopBar(
                title = { Text("Aggregator Mode") },
                onClose = onDismiss
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Description Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About Aggregator Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Aggregator mode increases sync frequency and packet retention to help coordinate the mesh network. Your device will act as a hub for collecting and redistributing messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Enable Aggregator Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isEnabled) "Currently active" else "Currently inactive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { 
                                aggregatorManager.toggleAggregatorMode()
                            }
                        )
                    }
                    
                    if (!isEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { aggregatorManager.startAggregatorMode() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start")
                            }
                            
                            OutlinedButton(
                                onClick = { showCustomIdDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Custom ID")
                            }
                        }
                    }
                }
            }
            
            // Status Card (only show when enabled)
            if (isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                AggregatorStatusCard(stats = stats)
            }
        }
    }
    
    // Custom ID Dialog
    if (showCustomIdDialog) {
        AlertDialog(
            onDismissRequest = { showCustomIdDialog = false },
            title = { Text("Custom Aggregator ID") },
            text = {
                Column {
                    Text("Enter a custom identifier for this aggregator:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customId,
                        onValueChange = { customId = it },
                        label = { Text("Aggregator ID") },
                        placeholder = { Text("e.g., MAIN-HUB") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customId.isNotBlank()) {
                            aggregatorManager.startAggregatorMode(customId.trim())
                        }
                        showCustomIdDialog = false
                        customId = ""
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showCustomIdDialog = false
                        customId = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AggregatorStatusCard(
    stats: AggregatorStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aggregator Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StatusRow("ID", stats.aggregatorId)
            StatusRow("Status", stats.syncStatus)
            StatusRow("Uptime", stats.uptimeFormatted)
            StatusRow("Packets Aggregated", stats.packetsAggregated.toString())
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}