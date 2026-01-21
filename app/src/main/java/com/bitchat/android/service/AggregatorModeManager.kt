package com.bitchat.android.service

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages aggregator mode state and behavior.
 * Aggregator mode increases sync frequency and packet retention for better mesh coordination.
 */
class AggregatorModeManager private constructor() {
    
    companion object {
        private const val TAG = "AggregatorModeManager"
        
        @Volatile
        private var INSTANCE: AggregatorModeManager? = null
        
        fun getInstance(): AggregatorModeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AggregatorModeManager().also { INSTANCE = it }
            }
        }
    }

    // Aggregator mode state
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _aggregatorId = MutableStateFlow("")
    val aggregatorId: StateFlow<String> = _aggregatorId.asStateFlow()
    
    private val _startTime = MutableStateFlow(0L)
    val startTime: StateFlow<Long> = _startTime.asStateFlow()
    
    private val _packetsAggregated = MutableStateFlow(0)
    val packetsAggregated: StateFlow<Int> = _packetsAggregated.asStateFlow()
    
    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    // Callbacks for mesh service integration
    var onAggregatorModeChanged: ((Boolean, String) -> Unit)? = null

    init {
        // Load persisted state
        try {
            _isEnabled.value = AggregatorModePreferenceManager.isEnabled(false)
            _aggregatorId.value = AggregatorModePreferenceManager.getAggregatorId("")
            _startTime.value = AggregatorModePreferenceManager.getStartTime(0L)
            _packetsAggregated.value = AggregatorModePreferenceManager.getPacketsAggregated(0)
            
            if (_isEnabled.value) {
                Log.i(TAG, "Restored aggregator mode: ${_aggregatorId.value}")
                updateSyncStatus("Active - Restored")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load aggregator mode state: ${e.message}")
        }
    }

    /**
     * Start aggregator mode with optional custom ID
     */
    fun startAggregatorMode(customId: String? = null) {
        val id = customId ?: generateAggregatorId()
        
        _isEnabled.value = true
        _aggregatorId.value = id
        _startTime.value = System.currentTimeMillis()
        _packetsAggregated.value = 0
        
        // Persist state
        AggregatorModePreferenceManager.setEnabled(true)
        AggregatorModePreferenceManager.setAggregatorId(id)
        AggregatorModePreferenceManager.resetStats()
        
        updateSyncStatus("Starting...")
        
        // Notify mesh service
        onAggregatorModeChanged?.invoke(true, id)
        
        Log.i(TAG, "Started aggregator mode with ID: $id")
    }

    /**
     * Stop aggregator mode
     */
    fun stopAggregatorMode() {
        val wasEnabled = _isEnabled.value
        
        _isEnabled.value = false
        updateSyncStatus("Stopped")
        
        // Persist state
        AggregatorModePreferenceManager.setEnabled(false)
        
        // Notify mesh service
        if (wasEnabled) {
            onAggregatorModeChanged?.invoke(false, "")
        }
        
        Log.i(TAG, "Stopped aggregator mode")
    }

    /**
     * Toggle aggregator mode on/off
     */
    fun toggleAggregatorMode() {
        if (_isEnabled.value) {
            stopAggregatorMode()
        } else {
            startAggregatorMode()
        }
    }

    /**
     * Update sync status for UI display
     */
    fun updateSyncStatus(status: String) {
        _syncStatus.value = status
    }

    /**
     * Increment packet count (called by sync manager)
     */
    fun onPacketAggregated() {
        if (_isEnabled.value) {
            _packetsAggregated.value = _packetsAggregated.value + 1
            AggregatorModePreferenceManager.incrementPacketsAggregated()
        }
    }

    /**
     * Get aggregator mode statistics
     */
    fun getStats(): AggregatorStats {
        val uptime = if (_isEnabled.value && _startTime.value > 0) {
            System.currentTimeMillis() - _startTime.value
        } else 0L
        
        return AggregatorStats(
            isEnabled = _isEnabled.value,
            aggregatorId = _aggregatorId.value,
            uptimeMs = uptime,
            packetsAggregated = _packetsAggregated.value,
            syncStatus = _syncStatus.value
        )
    }

    private fun generateAggregatorId(): String {
        return "AGG-${UUID.randomUUID().toString().take(8).uppercase()}"
    }
}

/**
 * Data class for aggregator mode statistics
 */
data class AggregatorStats(
    val isEnabled: Boolean,
    val aggregatorId: String,
    val uptimeMs: Long,
    val packetsAggregated: Int,
    val syncStatus: String
) {
    val uptimeFormatted: String
        get() {
            if (uptimeMs <= 0) return "0s"
            
            val seconds = uptimeMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            
            return when {
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }
}