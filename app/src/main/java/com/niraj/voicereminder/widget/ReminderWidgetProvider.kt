package com.niraj.voicereminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.niraj.voicereminder.R
import com.niraj.voicereminder.data.ReminderRepository
import com.niraj.voicereminder.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        private val SDF = SimpleDateFormat("EEE dd MMM  •  hh:mm a", Locale.getDefault())

        private val ROW_IDS = listOf(
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5
        )
        private val TITLE_IDS = listOf(
            R.id.widget_title_1, R.id.widget_title_2, R.id.widget_title_3,
            R.id.widget_title_4, R.id.widget_title_5
        )
        private val TIME_IDS = listOf(
            R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3,
            R.id.widget_time_4, R.id.widget_time_5
        )
        private val DIV_IDS = listOf(
            R.id.widget_div_1, R.id.widget_div_2, R.id.widget_div_3,
            R.id.widget_div_4, R.id.widget_div_5
        )

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val now = System.currentTimeMillis()
                val upcoming = ReminderRepository(context)
                    .getActiveRemindersSync()
                    .filter { it.triggerAtMillis > now }
                    .sortedBy { it.triggerAtMillis }
                    .take(5)

                val views = RemoteViews(context.packageName, R.layout.widget_reminders)

                // ── Mic button → opens MainActivity with voice intent ──────────
                val voiceIntent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_LAUNCH_VOICE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val voicePi = PendingIntent.getActivity(
                    context, 100, voiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_mic, voicePi)

                // ── Tap widget body → open main app ───────────────────────────
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPi = PendingIntent.getActivity(
                    context, 101, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_count, openPi)

                // ── Count label ───────────────────────────────────────────────
                val countText = when (upcoming.size) {
                    0    -> "No pending reminders"
                    1    -> "1 pending reminder"
                    else -> "${upcoming.size} pending reminders"
                }
                views.setTextViewText(R.id.widget_count, countText)

                // ── Empty state ───────────────────────────────────────────────
                views.setViewVisibility(
                    R.id.widget_empty,
                    if (upcoming.isEmpty()) View.VISIBLE else View.GONE
                )

                // ── Reminder rows ─────────────────────────────────────────────
                for (i in 0 until 5) {
                    if (i < upcoming.size) {
                        val r = upcoming[i]
                        views.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                        views.setTextViewText(TITLE_IDS[i], r.title)
                        views.setTextViewText(TIME_IDS[i], SDF.format(Date(r.triggerAtMillis)))
                        // Show divider between rows (not after last)
                        views.setViewVisibility(
                            DIV_IDS[i],
                            if (i < upcoming.size - 1) View.VISIBLE else View.GONE
                        )
                    } else {
                        views.setViewVisibility(ROW_IDS[i], View.GONE)
                        views.setViewVisibility(DIV_IDS[i], View.GONE)
                    }
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        // Call this after any reminder CRUD to refresh widget instantly
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, ReminderWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, mgr, it) }
        }
    }
}
