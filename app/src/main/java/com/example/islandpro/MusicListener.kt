package com.example.islandpro

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: MusicListener? = null

        fun importanceOf(sbn: StatusBarNotification): Int {
            return try {
                val l = instance
                    ?: return android.app.NotificationManager.IMPORTANCE_DEFAULT
                val r = NotificationListenerService.Ranking()
                if (l.currentRanking.getRanking(sbn.key, r)) r.importance
                else android.app.NotificationManager.IMPORTANCE_DEFAULT
            } catch (e: Exception) {
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            IslandService.instance?.onNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) {
            IslandService.instance?.onNotificationRemoved(sbn)
        }
    }
}
