package com.niraj.voicereminder.ui

import android.app.AlertDialog
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.niraj.voicereminder.R
import com.niraj.voicereminder.data.AlarmScheduler
import com.niraj.voicereminder.databinding.ActivityAlarmFireBinding
import com.niraj.voicereminder.receiver.SnoozeReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent

class AlarmFireActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmFireBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val sdf = SimpleDateFormat("hh:mm a  •  EEE, dd MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen and wake the display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityAlarmFireBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reminderId   = intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1)
        val title        = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_TITLE) ?: getString(R.string.reminder)
        val desc         = intent.getStringExtra(AlarmScheduler.EXTRA_REMINDER_DESC) ?: ""
        val ringtoneStr  = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
        val shouldVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true)

        binding.tvAlarmTitle.text = title
        binding.tvAlarmDesc.text  = desc
        binding.tvAlarmTime.text  = sdf.format(Date())

        playRingtone(ringtoneStr)
        if (shouldVibrate) startVibration()

        val prefs   = PreferenceManager.getDefaultSharedPreferences(this)
        val snooze1 = prefs.getInt(getString(R.string.pref_snooze_1), 15)
        val snooze2 = prefs.getInt(getString(R.string.pref_snooze_2), 30)
        val snooze3 = prefs.getInt(getString(R.string.pref_snooze_3), 60)
        val snooze4 = prefs.getInt(getString(R.string.pref_snooze_4), 120)

        binding.btnSnooze1.text = formatMinutes(snooze1)
        binding.btnSnooze2.text = formatMinutes(snooze2)
        binding.btnSnooze3.text = formatMinutes(snooze3)
        binding.btnSnooze4.text = getString(R.string.custom)

        binding.btnSnooze1.setOnClickListener { doSnooze(reminderId, snooze1) }
        binding.btnSnooze2.setOnClickListener { doSnooze(reminderId, snooze2) }
        binding.btnSnooze3.setOnClickListener { doSnooze(reminderId, snooze3) }
        binding.btnSnooze4.setOnClickListener { showCustomSnoozeDialog(reminderId) }
        binding.btnDismiss.setOnClickListener { doDismiss(reminderId) }
    }

    private fun formatMinutes(minutes: Int): String = when {
        minutes < 60   -> getString(R.string.fmt_minutes, minutes)
        minutes == 60  -> getString(R.string.fmt_one_hour)
        minutes % 60 == 0 -> getString(R.string.fmt_hours, minutes / 60)
        else           -> getString(R.string.fmt_minutes, minutes)
    }

    private fun playRingtone(uriStr: String?) {
        val uri = uriStr?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            try {
                setDataSource(this@AlarmFireActivity, uri)
                isLooping = true
                prepare()
                start()
            } catch (e: Exception) {
                val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                reset()
                try {
                    setDataSource(this@AlarmFireActivity, fallback)
                    isLooping = true
                    prepare()
                    start()
                } catch (ignored: Exception) { }
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 300, 500, 300, 1000)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun doSnooze(reminderId: Int, minutes: Int) {
        stopAlarm()
        sendBroadcast(Intent(this, SnoozeReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(SnoozeReceiver.EXTRA_SNOOZE_MINUTES, minutes)
        })
        finish()
    }

    private fun showCustomSnoozeDialog(reminderId: Int) {
        val input = EditText(this).apply {
            hint = getString(R.string.snooze_minutes_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_snooze))
            .setView(input)
            .setPositiveButton(getString(R.string.snooze_btn)) { _, _ ->
                val mins = input.text.toString().toIntOrNull() ?: 15
                doSnooze(reminderId, mins)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doDismiss(reminderId: Int) {
        stopAlarm()
        sendBroadcast(Intent(this, SnoozeReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(SnoozeReceiver.EXTRA_DISMISS, true)
        })
        finish()
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.apply { if (isPlaying) stop(); release() }
        } catch (_: Exception) {}
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
