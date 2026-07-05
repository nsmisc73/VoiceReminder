package com.niraj.voicereminder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VoiceReminderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminder alarm notifications"
                setBypassDnd(true)
            }

            val snoozeChannel = NotificationChannel(
                CHANNEL_SNOOZE,
                "Snooze",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Snoozed reminder notifications"
            }

            mgr.createNotificationChannel(alarmChannel)
            mgr.createNotificationChannel(snoozeChannel)
        }
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_channel"
        const val CHANNEL_SNOOZE = "snooze_channel"
    }
}
