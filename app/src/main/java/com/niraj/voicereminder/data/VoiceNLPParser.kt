package com.niraj.voicereminder.data

import java.util.Calendar
import java.util.Locale

/**
 * Parses a spoken reminder string to extract task title and trigger time.
 *
 * Supported patterns:
 *  "Call John tomorrow at 9 AM"
 *  "Doctor appointment on Monday at 3:30 PM"
 *  "Take medicine today at 8 PM"
 *  "Submit report in 2 hours"
 *  "Meeting on 15th July at 11 AM"
 *  "Pay bill on 30 June at 9 AM"
 */
object VoiceNLPParser {

    data class ParseResult(
        val title: String,
        val triggerAtMillis: Long,
        val confidence: Float
    )

    private val MONTH_MAP = mapOf(
        "january" to 0, "jan" to 0,
        "february" to 1, "feb" to 1,
        "march" to 2, "mar" to 2,
        "april" to 3, "apr" to 3,
        "may" to 4,
        "june" to 5, "jun" to 5,
        "july" to 6, "jul" to 6,
        "august" to 7, "aug" to 7,
        "september" to 8, "sep" to 8, "sept" to 8,
        "october" to 9, "oct" to 9,
        "november" to 10, "nov" to 10,
        "december" to 11, "dec" to 11
    )

    private val DAY_MAP = mapOf(
        "sunday" to Calendar.SUNDAY, "sun" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY, "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "tue" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "thu" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "sat" to Calendar.SATURDAY
    )

    fun parse(rawText: String): ParseResult {
        val text = rawText.lowercase(Locale.getDefault()).trim()
        val cal = Calendar.getInstance()
        var confidence = 0.5f
        var timeSet = false
        var dateSet = false

        // Strip common preamble
        var cleaned = text
            .replace(Regex("^(please\\s+)?remind\\s+me\\s+(to\\s+)?"), "")
            .replace(Regex("^(please\\s+)?set\\s+(a\\s+)?reminder\\s+(to\\s+|for\\s+)?"), "")
            .replace(Regex("^(hey\\s+)?reminder\\s+(for\\s+|to\\s+)?"), "")
            .trim()

        // ── RELATIVE TIME ────────────────────────────────────────────────────
        val inMinRx = Regex("\\bin\\s+(\\d+)\\s+minutes?\\b")
        val inHrRx  = Regex("\\bin\\s+(\\d+)\\s+hours?\\b")
        val inDayRx = Regex("\\bin\\s+(\\d+)\\s+days?\\b")

        inMinRx.find(cleaned)?.let {
            cal.add(Calendar.MINUTE, it.groupValues[1].toInt())
            cleaned = cleaned.replace(it.value, "")
            confidence = 0.92f; timeSet = true; dateSet = true
        }
        if (!timeSet) inHrRx.find(cleaned)?.let {
            cal.add(Calendar.HOUR_OF_DAY, it.groupValues[1].toInt())
            cleaned = cleaned.replace(it.value, "")
            confidence = 0.92f; timeSet = true; dateSet = true
        }
        if (!timeSet) inDayRx.find(cleaned)?.let {
            cal.add(Calendar.DAY_OF_MONTH, it.groupValues[1].toInt())
            cleaned = cleaned.replace(it.value, "")
            confidence = 0.88f; dateSet = true
        }

        // ── NAMED DAY ────────────────────────────────────────────────────────
        if (!dateSet) {
            for ((dayName, dayConst) in DAY_MAP) {
                if (Regex("\\b$dayName\\b").containsMatchIn(cleaned)) {
                    val today = cal.get(Calendar.DAY_OF_WEEK)
                    var diff = dayConst - today
                    if (diff <= 0) diff += 7
                    cal.add(Calendar.DAY_OF_MONTH, diff)
                    cleaned = cleaned.replace(Regex("\\b(on\\s+)?$dayName\\b"), "")
                    confidence = 0.88f; dateSet = true
                    break
                }
            }
        }

        // today / tomorrow
        if (!dateSet && Regex("\\btoday\\b").containsMatchIn(cleaned)) {
            cleaned = cleaned.replace(Regex("\\btoday\\b"), "")
            confidence = 0.90f; dateSet = true
        }
        if (!dateSet && Regex("\\btomorrow\\b").containsMatchIn(cleaned)) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cleaned = cleaned.replace(Regex("\\btomorrow\\b"), "")
            confidence = 0.90f; dateSet = true
        }

        // ── CALENDAR DATE ─────────────────────────────────────────────────────
        // "15th july" or "july 15"
        val monthNames = MONTH_MAP.keys.joinToString("|")
        val ordinalDateRx = Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+($monthNames)")
        val monthFirstRx  = Regex("($monthNames)\\s+(\\d{1,2})(?:st|nd|rd|th)?")

        if (!dateSet) {
            ordinalDateRx.find(cleaned)?.let { m ->
                val day   = m.groupValues[1].toInt()
                val month = MONTH_MAP[m.groupValues[2]] ?: cal.get(Calendar.MONTH)
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.MONTH, month)
                cleaned = cleaned.replace(m.value, "")
                confidence = 0.95f; dateSet = true
            }
        }
        if (!dateSet) {
            monthFirstRx.find(cleaned)?.let { m ->
                val month = MONTH_MAP[m.groupValues[1]] ?: cal.get(Calendar.MONTH)
                val day   = m.groupValues[2].toInt()
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.MONTH, month)
                cleaned = cleaned.replace(m.value, "")
                confidence = 0.95f; dateSet = true
            }
        }

        // ── TIME ──────────────────────────────────────────────────────────────
        if (!timeSet) {
            // "9 AM", "9:30 AM", "9:30am", "9am"
            val time12Rx = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)")
            val time24Rx = Regex("(?:at\\s+)?(\\d{1,2}):(\\d{2})(?!\\s*[aApP])")

            time12Rx.find(cleaned)?.let { m ->
                var hour   = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val ampm   = m.groupValues[3]
                if (ampm == "pm" && hour != 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cleaned = cleaned.replace(m.value, "")
                confidence = maxOf(confidence, 0.92f); timeSet = true
            }

            if (!timeSet) {
                time24Rx.find(cleaned)?.let { m ->
                    val hour   = m.groupValues[1].toInt()
                    val minute = m.groupValues[2].toIntOrNull() ?: 0
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cleaned = cleaned.replace(m.value, "")
                    confidence = maxOf(confidence, 0.88f); timeSet = true
                }
            }

            if (!timeSet) {
                // Default to 9 AM if no time detected
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                confidence = minOf(confidence, 0.45f)
            }
        }

        // If trigger time is in the past, push to next day
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // ── CLEAN UP TITLE ────────────────────────────────────────────────────
        var title = cleaned
        title = title.replace(Regex("\\bon\\b"), "")
        title = title.replace(Regex("\\bat\\b"), "")
        title = title.replace(Regex("\\bthe\\b"), "")
        title = title.replace(Regex("\\s+"), " ").trim()
        title = title.trimEnd(',', '.', '\'', '\"')

        return ParseResult(
            title = title.capitalizeWords().ifBlank { "Reminder" },
            triggerAtMillis = cal.timeInMillis,
            confidence = confidence
        )
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }
}
