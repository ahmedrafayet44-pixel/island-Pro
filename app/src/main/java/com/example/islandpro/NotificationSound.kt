package com.example.islandpro

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

object NotificationSound {

    @Synchronized
    fun play(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION
            ) ?: return

            val ringtone = RingtoneManager.getRingtone(
                context.applicationContext,
                uri
            ) ?: return

            ringtone.audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            ringtone.play()

        } catch (_: Exception) {
            // Sound error will not affect Island features.
        }
    }
}
