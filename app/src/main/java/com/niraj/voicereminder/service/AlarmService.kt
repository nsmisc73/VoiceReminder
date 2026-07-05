package com.niraj.voicereminder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.niraj.voicereminder.R
import com.niraj.voicereminder.VoiceReminderApp
import com.niraj.voicereminder.data.AlarmScheduler
import com.niraj.voicereminder.ui.AlarmFireActivity

class AlarmService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId  = intent?.getIntExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1) ?: -1
        val title       = intent?.getStringExtra(AlarmScheduler.EXTRA_REMINDER_TITLE) ?: getString(R.string.app_name)
        val desc        = intent?.getStringExtra(AlarmScheduler.EXTRA_REMINDER_DESC) ?: ""
        val ringtoneUri = intent?.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
        val vibrate     = intent?.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true) ?: true

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(title, desc, reminderId))

        // Launch full-screen alarm activity
        startActivity(
            Intent(this, AlarmFireActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID,    reminderId)
                putExtra(AlarmScheduler.EXTRA_REMINDER_TITLE, title)
                putExtra(AlarmScheduler.EXTRA_REMINDER_DESC,  desc)
                putExtra(AlarmScheduler.EXTRA_RINGTONE_URI,   ringtoneUri)
                putExtra(AlarmScheduler.EXTRA_VIBRATE,        vibrate)
            }
        )

        stopSelf()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(VoiceReminderApp.CHANNEL_ALARM) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        VoiceReminderApp.CHANNEL_ALARM,
                        getString(R.string.channel_alarm_name),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { setSound(null, null) }
                )
            }
        }
    }

    private fun buildNotification(title: String, desc: String, reminderId: Int): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this, reminderId,
            Intent(this, AlarmFireActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
                putExtra(AlarmScheduler.EXTRA_REMINDER_TITLE, title)
                putExtra(AlarmScheduler.EXTRA_REMINDER_DESC, desc)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VoiceReminderApp.CHANNEL_ALARM)
            .setContentTitle(title)
            .setContentText(desc.ifEmpty { getString(R.string.tap_to_view) })
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 9001
    }
}
