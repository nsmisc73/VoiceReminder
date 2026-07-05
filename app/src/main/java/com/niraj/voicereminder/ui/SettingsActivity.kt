package com.niraj.voicereminder.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.niraj.voicereminder.R
import com.niraj.voicereminder.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            prefs.edit().putString(getString(R.string.pref_default_ringtone), uri?.toString()).apply()
            updateRingtoneLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        loadCurrentValues()
        setupListeners()
    }

    private fun loadCurrentValues() {
        binding.etSnooze1.setText(prefs.getInt(getString(R.string.pref_snooze_1), 15).toString())
        binding.etSnooze2.setText(prefs.getInt(getString(R.string.pref_snooze_2), 30).toString())
        binding.etSnooze3.setText(prefs.getInt(getString(R.string.pref_snooze_3), 60).toString())
        binding.etSnooze4.setText(prefs.getInt(getString(R.string.pref_snooze_4), 120).toString())
        binding.switchVibrate.isChecked = prefs.getBoolean(getString(R.string.pref_default_vibrate), true)
        updateRingtoneLabel()
    }

    private fun setupListeners() {
        binding.btnSaveSnooze.setOnClickListener { saveSnoozeSettings() }

        binding.btnPickRingtone.setOnClickListener {
            val existing = prefs.getString(getString(R.string.pref_default_ringtone), null)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_ringtone))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing?.let { Uri.parse(it) })
            }
            ringtoneLauncher.launch(intent)
        }

        binding.switchVibrate.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(getString(R.string.pref_default_vibrate), checked).apply()
        }
    }

    private fun saveSnoozeSettings() {
        val s1 = binding.etSnooze1.text.toString().toIntOrNull()?.coerceIn(1, 1440) ?: 15
        val s2 = binding.etSnooze2.text.toString().toIntOrNull()?.coerceIn(1, 1440) ?: 30
        val s3 = binding.etSnooze3.text.toString().toIntOrNull()?.coerceIn(1, 1440) ?: 60
        val s4 = binding.etSnooze4.text.toString().toIntOrNull()?.coerceIn(1, 1440) ?: 120

        prefs.edit()
            .putInt(getString(R.string.pref_snooze_1), s1)
            .putInt(getString(R.string.pref_snooze_2), s2)
            .putInt(getString(R.string.pref_snooze_3), s3)
            .putInt(getString(R.string.pref_snooze_4), s4)
            .apply()

        Toast.makeText(this, getString(R.string.snooze_saved), Toast.LENGTH_SHORT).show()
    }

    private fun updateRingtoneLabel() {
        val uriStr = prefs.getString(getString(R.string.pref_default_ringtone), null)
        val name = if (uriStr != null) {
            RingtoneManager.getRingtone(this, Uri.parse(uriStr))?.getTitle(this)
                ?: getString(R.string.custom)
        } else {
            getString(R.string.system_default)
        }
        binding.tvCurrentRingtone.text = getString(R.string.ringtone_label, name)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
