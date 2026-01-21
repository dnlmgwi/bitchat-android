package com.bitchat.android.service

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for aggregator mode functionality
 */
class AggregatorModeTest {

    private lateinit var aggregatorManager: AggregatorModeManager

    @Before
    fun setup() {
        aggregatorManager = AggregatorModeManager.getInstance()
    }

    @Test
    fun testAggregatorModeToggle() = runBlocking {
        // Initially disabled
        assertFalse(aggregatorManager.isEnabled.first())
        
        // Start aggregator mode
        aggregatorManager.startAggregatorMode("TEST-AGG")
        
        // Should be enabled
        assertTrue(aggregatorManager.isEnabled.first())
        assertEquals("TEST-AGG", aggregatorManager.aggregatorId.first())
        
        // Stop aggregator mode
        aggregatorManager.stopAggregatorMode()
        
        // Should be disabled
        assertFalse(aggregatorManager.isEnabled.first())
    }

    @Test
    fun testPacketAggregation() = runBlocking {
        // Start aggregator mode
        aggregatorManager.startAggregatorMode("TEST-AGG")
        
        // Initial packet count should be 0
        assertEquals(0, aggregatorManager.packetsAggregated.first())
        
        // Simulate packet aggregation
        aggregatorManager.onPacketAggregated()
        aggregatorManager.onPacketAggregated()
        aggregatorManager.onPacketAggregated()
        
        // Should have 3 packets
        assertEquals(3, aggregatorManager.packetsAggregated.first())
    }

    @Test
    fun testAggregatorStats() = runBlocking {
        // Start aggregator mode
        aggregatorManager.startAggregatorMode("STATS-TEST")
        
        // Add some packets
        aggregatorManager.onPacketAggregated()
        aggregatorManager.onPacketAggregated()
        
        val stats = aggregatorManager.getStats()
        
        assertTrue(stats.isEnabled)
        assertEquals("STATS-TEST", stats.aggregatorId)
        assertEquals(2, stats.packetsAggregated)
        assertTrue(stats.uptimeMs >= 0)
        assertNotNull(stats.uptimeFormatted)
    }

    @Test
    fun testUptimeFormatting() {
        val stats1 = AggregatorStats(
            isEnabled = true,
            aggregatorId = "TEST",
            uptimeMs = 0,
            packetsAggregated = 0,
            syncStatus = "Active"
        )
        assertEquals("0s", stats1.uptimeFormatted)
        
        val stats2 = AggregatorStats(
            isEnabled = true,
            aggregatorId = "TEST",
            uptimeMs = 65000, // 1 minute 5 seconds
            packetsAggregated = 0,
            syncStatus = "Active"
        )
        assertEquals("1m 5s", stats2.uptimeFormatted)
        
        val stats3 = AggregatorStats(
            isEnabled = true,
            aggregatorId = "TEST",
            uptimeMs = 3665000, // 1 hour 1 minute 5 seconds
            packetsAggregated = 0,
            syncStatus = "Active"
        )
        assertEquals("1h 1m", stats3.uptimeFormatted)
    }
}