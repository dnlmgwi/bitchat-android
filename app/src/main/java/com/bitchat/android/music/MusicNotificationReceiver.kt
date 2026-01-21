package com.bitchat.android.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for handling media notification actions
 * Routes notification button presses to the music notification manager
 */
class MusicNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MusicNotificationReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received notification action: $action")
        
        if (action != null) {
            // Get the notification manager instance from the application
            val application = context.applicationContext as? com.bitchat.android.BitchatApplication
            application?.getMusicNotificationManager()?.handleNotificationAction(action)
        }
    }
}