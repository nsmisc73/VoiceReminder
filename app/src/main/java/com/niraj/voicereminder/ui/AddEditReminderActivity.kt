package com.niraj.voicereminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.niraj.voicereminder.R
import com.niraj.voicereminder.data.Reminder
import com.niraj.voicereminder.databinding.ActivityAddEditReminderBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddEditReminderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMINDER_ID   = "extra_reminder_id"
        const val EXTRA_PREFILL_TITLE = "extra_prefill_title"
        const val EXTRA_PREFILL_TIME  = "extra_prefill_time"
        const val EXTRA_PREFILL_DESC  = "extra_prefill_desc"
    }

    private lateinit var binding: ActivityAddEditReminderBinding
    private val viewModel: ReminderViewModel by viewModels()
    private var existingReminder: Reminder? = null
    private val calendar = Calendar.getInstance()
    private var ringtoneUri: Uri? = null
    private val sdf = SimpleDateFormat("EEE, dd MMM yyyy  •  hh:mm a", Locale.getDefault())

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ringtoneUri = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            updateRingtoneButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Pre-fill from voice parse
        intent.getStringExtra(EXTRA_PREFILL_TITLE)?.let { binding.etTitle.setText(it) }
        intent.getStringExtra(EXTRA_PREFILL_DESC)?.let  { binding.etDescription.setText(it) }
        val prefillTime = intent.getLongExtra(EXTRA_PREFILL_TIME, 0L)
        if (prefillTime > 0L) calendar.timeInMillis = prefillTime

        // Load existing reminder for edit mode
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId != -1) {
            supportActionBar?.title = getString(R.string.edit_reminder)
            viewModel.getReminderById(reminderId) { reminder ->
                reminder?.let {
                    existingReminder = it
                    populateFields(it)
                }
            }
        } else {
            supportActionBar?.title = getString(R.string.new_reminder)
        }

        updateDateTimeDisplay()
        setupListeners()
    }

    private fun populateFields(r: Reminder) {
        binding.etTitle.setText(r.title)
        binding.etDescription.setText(r.description)
        calendar.timeInMillis = r.triggerAtMillis
        binding.switchVibrate.isChecked = r.vibrate
        r.ringtoneUri?.let { ringtoneUri = Uri.parse(it) }
        updateDateTimeDisplay()
        updateRingtoneButton()
    }

    private fun setupListeners() {
        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnPickTime.setOnClickListener { showTimePicker() }
        binding.btnPickRingtone.setOnClickListener { launchRingtonePicker() }
        binding.btnSave.setOnClickListener { saveReminder() }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                updateDateTimeDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                updateDateTimeDisplay()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun launchRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_ringtone))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri)
        }
        ringtoneLauncher.launch(intent)
    }

    private fun updateDateTimeDisplay() {
        binding.tvSelectedDateTime.text = sdf.format(calendar.time)
    }

    private fun updateRingtoneButton() {
        val name = if (ringtoneUri != null)
            RingtoneManager.getRingtone(this, ringtoneUri)?.getTitle(this) ?: getString(R.string.custom)
        else
            getString(R.string.system_default)
        binding.btnPickRingtone.text = getString(R.string.ringtone_label, name)
    }

    private fun saveReminder() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.tilTitle.error = getString(R.string.title_required)
            return
        }
        binding.tilTitle.error = null

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, getString(R.string.time_in_past), Toast.LENGTH_SHORT).show()
            return
        }

        val base = existingReminder ?: Reminder(title = "", triggerAtMillis = 0L)
        val toSave = base.copy(
            title          = title,
            description    = binding.etDescription.text.toString().trim(),
            triggerAtMillis = calendar.timeInMillis,
            ringtoneUri    = ringtoneUri?.toString(),
            vibrate        = binding.switchVibrate.isChecked,
            isActive       = true,
            isSnoozed      = false
        )

        if (existingReminder != null) {
            viewModel.updateReminder(toSave)
            Toast.makeText(this, getString(R.string.reminder_updated), Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addReminder(toSave)
            Toast.makeText(this, getString(R.string.reminder_saved, title), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
