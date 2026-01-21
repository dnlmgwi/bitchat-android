package com.bitchat.android.service

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed persistence for aggregator mode settings.
 * Manages aggregator mode state and configuration.
 */
object AggregatorModePreferenceManager {
    private const val PREFS_NAME = "bitchat_aggregator_mode"
    private const val KEY_ENABLED = "aggregator_mode_enabled"
    private const val KEY_AGGREGATOR_ID = "aggregator_id"
    private const val KEY_START_TIME = "aggregator_start_time"
    private const val KEY_PACKETS_AGGREGATED = "packets_aggregated"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ready(): Boolean = ::prefs.isInitialized

    fun isEnabled(default: Boolean = false): Boolean =
        if (ready()) prefs.getBoolean(KEY_ENABLED, default) else default

    fun setEnabled(enabled: Boolean) {
        if (ready()) {
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) {
                // Set start time when enabling
                prefs.edit().putLong(KEY_START_TIME, System.currentTimeMillis()).apply()
            }
        }
    }

    fun getAggregatorId(default: String = ""): String =
        if (ready()) prefs.getString(KEY_AGGREGATOR_ID, default) ?: default else default

    fun setAggregatorId(aggregatorId: String) {
        if (ready()) prefs.edit().putString(KEY_AGGREGATOR_ID, aggregatorId).apply()
    }

    fun getStartTime(default: Long = 0L): Long =
        if (ready()) prefs.getLong(KEY_START_TIME, default) else default

    fun getPacketsAggregated(default: Int = 0): Int =
        if (ready()) prefs.getInt(KEY_PACKETS_AGGREGATED, default) else default

    fun incrementPacketsAggregated() {
        if (ready()) {
            val current = getPacketsAggregated()
            prefs.edit().putInt(KEY_PACKETS_AGGREGATED, current + 1).apply()
        }
    }

    fun resetStats() {
        if (ready()) {
            prefs.edit()
                .putLong(KEY_START_TIME, System.currentTimeMillis())
                .putInt(KEY_PACKETS_AGGREGATED, 0)
                .apply()
        }
    }
}