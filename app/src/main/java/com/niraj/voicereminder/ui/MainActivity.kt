package com.niraj.voicereminder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.niraj.voicereminder.R
import com.niraj.voicereminder.data.Reminder
import com.niraj.voicereminder.data.VoiceNLPParser
import com.niraj.voicereminder.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ReminderViewModel by viewModels()
    private lateinit var adapter: ReminderAdapter
    private var showAllReminders = false
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault())

    // ── Result launchers ──────────────────────────────────────────────────────

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) handleVoiceInput(text)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchSpeechRecognizer()
        else Toast.makeText(this, getString(R.string.mic_permission_denied), Toast.LENGTH_LONG).show()
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, continue */ }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        requestNotificationPermissionIfNeeded()
        setupRecyclerView()
        setupTabs()
        setupFabs()
        observeReminders()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ReminderAdapter(
            onItemClick = { reminder ->
                startActivity(
                    Intent(this, AddEditReminderActivity::class.java)
                        .putExtra(AddEditReminderActivity.EXTRA_REMINDER_ID, reminder.id)
                )
            },
            onDeleteClick = { reminder -> confirmDelete(reminder) },
            onToggleActive = { reminder ->
                viewModel.updateReminder(reminder.copy(isActive = !reminder.isActive))
            }
        )
        binding.recyclerReminders.layoutManager = LinearLayoutManager(this)
        binding.recyclerReminders.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showAllReminders = (tab?.position == 1)
                refreshList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFabs() {
        binding.fabVoice.setOnClickListener { onVoiceFabClicked() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditReminderActivity::class.java))
        }
    }

    private fun observeReminders() {
        viewModel.activeReminders.observe(this) { if (!showAllReminders) updateList(it) }
        viewModel.allReminders.observe(this)   { if (showAllReminders)  updateList(it) }
    }

    private fun refreshList() {
        val list = if (showAllReminders)
            viewModel.allReminders.value ?: emptyList()
        else
            viewModel.activeReminders.value ?: emptyList()
        updateList(list)
    }

    private fun updateList(list: List<Reminder>) {
        adapter.submitList(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerReminders.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    // ── Voice input ───────────────────────────────────────────────────────────

    private fun onVoiceFabClicked() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED -> launchSpeechRecognizer()
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, getString(R.string.speech_not_available), Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechLauncher.launch(intent)
    }

    private fun handleVoiceInput(spokenText: String) {
        val result = VoiceNLPParser.parse(spokenText)
        val timeFormatted = dateFormat.format(Date(result.triggerAtMillis))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_reminder_title))
            .setMessage(
                getString(R.string.confirm_reminder_msg, spokenText, result.title, timeFormatted)
            )
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val reminder = Reminder(
                    title = result.title,
                    description = spokenText,
                    triggerAtMillis = result.triggerAtMillis
                )
                viewModel.addReminder(reminder)
                Toast.makeText(this, getString(R.string.reminder_saved, result.title), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(getString(R.string.edit)) { _, _ ->
                startActivity(
                    Intent(this, AddEditReminderActivity::class.java).apply {
                        putExtra(AddEditReminderActivity.EXTRA_PREFILL_TITLE, result.title)
                        putExtra(AddEditReminderActivity.EXTRA_PREFILL_TIME, result.triggerAtMillis)
                        putExtra(AddEditReminderActivity.EXTRA_PREFILL_DESC, spokenText)
                    }
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Delete confirmation ───────────────────────────────────────────────────

    private fun confirmDelete(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title))
            .setMessage(getString(R.string.delete_msg, reminder.title))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteReminder(reminder)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Options menu ──────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
