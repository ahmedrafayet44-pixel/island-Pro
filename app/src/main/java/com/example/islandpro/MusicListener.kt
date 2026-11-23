package com.example.islandpro

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: MusicListener? = null
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
