package com.example.islandpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences("island", Context.MODE_PRIVATE)
            if (prefs.getBoolean("autostart", true) && Settings.canDrawOverlays(context)) {
                try {
                    context.startForegroundService(Intent(context, IslandService::class.java))
                } catch (e: Exception) {
                }
            }
        }
    }
}
