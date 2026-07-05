package com.niraj.voicereminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        private val SDF = SimpleDateFormat("dd MMM  •  hh:mm a", Locale.getDefault())

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = ReminderRepository(context)
                val now = System.currentTimeMillis()
                val upcoming = repo.getActiveRemindersSync()
                    .filter { it.triggerAtMillis > now }
                    .sortedBy { it.triggerAtMillis }
                    .take(5)

                val views = RemoteViews(context.packageName, R.layout.widget_reminders)

                // Tap header → open app
                val openAppIntent = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_header, openAppIntent)

                // Count label
                views.setTextViewText(
                    R.id.widget_count,
                    context.resources.getQuantityString(
                        R.plurals.pending_reminders,
                        upcoming.size,
                        upcoming.size
                    )
                )

                // Reminder rows — use static slots (5 rows pre-built in layout)
                val titles = listOf(
                    R.id.widget_title_1, R.id.widget_title_2, R.id.widget_title_3,
                    R.id.widget_title_4, R.id.widget_title_5
                )
                val times = listOf(
                    R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3,
                    R.id.widget_time_4, R.id.widget_time_5
                )
                val rows = listOf(
                    R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
                    R.id.widget_row_4, R.id.widget_row_5
                )

                for (i in 0 until 5) {
                    if (i < upcoming.size) {
                        val r = upcoming[i]
                        views.setViewVisibility(rows[i], android.view.View.VISIBLE)
                        views.setTextViewText(titles[i], r.title)
                        views.setTextViewText(times[i], SDF.format(Date(r.triggerAtMillis)))
                    } else {
                        views.setViewVisibility(rows[i], android.view.View.GONE)
                    }
                }

                if (upcoming.isEmpty()) {
                    views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
