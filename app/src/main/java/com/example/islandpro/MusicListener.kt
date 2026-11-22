package com.example.islandpro

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            IslandService.instance?.onNotification(sbn)
        }
    }
}
