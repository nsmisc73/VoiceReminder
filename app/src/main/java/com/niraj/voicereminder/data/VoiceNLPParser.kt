package com.niraj.voicereminder.data

import java.util.Calendar
import java.util.Locale

/**
 * NLP parser — extracts task title + trigger time from spoken reminder text.
 *
 * Handles:
 *  "Call John at 11 AM tomorrow"
 *  "Email Ajay in 2 hours"
 *  "Meeting on Monday at 3:30 PM"
 *  "Doctor on 15th July at 11 AM"
 *  "Remind me to pay bill in 30 minutes"
 *  "Remind me to call abc at 11 am tomorrow"
 */
object VoiceNLPParser {

    data class ParseResult(
        val title: String,
        val triggerAtMillis: Long,
        val confidence: Float
    )

    private val MONTH_MAP = mapOf(
        "january" to 0,  "jan" to 0,
        "february" to 1, "feb" to 1,
        "march" to 2,    "mar" to 2,
        "april" to 3,    "apr" to 3,
        "may" to 4,
        "june" to 5,     "jun" to 5,
        "july" to 6,     "jul" to 6,
        "august" to 7,   "aug" to 7,
        "september" to 8,"sep" to 8, "sept" to 8,
        "october" to 9,  "oct" to 9,
        "november" to 10,"nov" to 10,
        "december" to 11,"dec" to 11
    )

    private val DAY_MAP = mapOf(
        "sunday" to Calendar.SUNDAY,    "sun" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,    "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,  "tue" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,"thu" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,    "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,"sat" to Calendar.SATURDAY
    )

    fun parse(rawText: String): ParseResult {
        val lower = rawText.lowercase(Locale.getDefault()).trim()
        val cal = Calendar.getInstance()
        var confidence = 0.5f
        var timeSet = false
        var dateSet = false

        var remaining = lower

        // ── Strip leading preamble ────────────────────────────────────────────
        remaining = remaining
            .replace(Regex("^(please\\s+)?remind\\s+me\\s+(to\\s+)?"), "")
            .replace(Regex("^(please\\s+)?set\\s+(a\\s+)?reminder\\s+(to\\s+|for\\s+)?"), "")
            .replace(Regex("^(hey\\s+)?reminder\\s+(for\\s+|to\\s+)?"), "")
            .trim()

        // ── 1. RELATIVE TIME — "in X minutes/hours/days" ─────────────────────
        // These set BOTH date and time so skip the absolute time parse
        val inMinRx = Regex("\\bin\\s+(\\d+)\\s+minutes?\\b")
        val inHrRx  = Regex("\\bin\\s+(\\d+(?:\\.\\d+)?)\\s+hours?\\b")
        val inDayRx = Regex("\\bin\\s+(\\d+)\\s+days?\\b")
        val inWkRx  = Regex("\\bin\\s+(\\d+)\\s+weeks?\\b")

        inMinRx.find(remaining)?.let {
            cal.add(Calendar.MINUTE, it.groupValues[1].toInt())
            remaining = remaining.replace(it.value, " ")
            confidence = 0.95f; timeSet = true; dateSet = true
        }
        if (!timeSet) inHrRx.find(remaining)?.let {
            val hrs = it.groupValues[1].toDouble()
            cal.add(Calendar.HOUR_OF_DAY, hrs.toInt())
            cal.add(Calendar.MINUTE, ((hrs - hrs.toInt()) * 60).toInt())
            remaining = remaining.replace(it.value, " ")
            confidence = 0.95f; timeSet = true; dateSet = true
        }
        if (!dateSet) inDayRx.find(remaining)?.let {
            cal.add(Calendar.DAY_OF_MONTH, it.groupValues[1].toInt())
            remaining = remaining.replace(it.value, " ")
            confidence = 0.88f; dateSet = true
        }
        if (!dateSet) inWkRx.find(remaining)?.let {
            cal.add(Calendar.WEEK_OF_YEAR, it.groupValues[1].toInt())
            remaining = remaining.replace(it.value, " ")
            confidence = 0.88f; dateSet = true
        }

        // ── 2. NAMED DAY — "monday", "next friday" ───────────────────────────
        if (!dateSet) {
            for ((dayName, dayConst) in DAY_MAP) {
                val dayRx = Regex("\\b(?:next\\s+)?$dayName\\b")
                if (dayRx.containsMatchIn(remaining)) {
                    val today = cal.get(Calendar.DAY_OF_WEEK)
                    var diff  = dayConst - today
                    if (diff <= 0) diff += 7
                    cal.add(Calendar.DAY_OF_MONTH, diff)
                    remaining = remaining.replace(dayRx, " ")
                    confidence = maxOf(confidence, 0.88f); dateSet = true
                    break
                }
            }
        }

        // ── 3. TODAY / TOMORROW ───────────────────────────────────────────────
        if (!dateSet && Regex("\\btoday\\b").containsMatchIn(remaining)) {
            remaining = remaining.replace(Regex("\\btoday\\b"), " ")
            confidence = maxOf(confidence, 0.90f); dateSet = true
        }
        if (!dateSet && Regex("\\btomorrow\\b").containsMatchIn(remaining)) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            remaining = remaining.replace(Regex("\\btomorrow\\b"), " ")
            confidence = maxOf(confidence, 0.92f); dateSet = true
        }

        // ── 4. CALENDAR DATE — "15th july", "july 15" ────────────────────────
        if (!dateSet) {
            val monthNames = MONTH_MAP.keys.joinToString("|")
            val ordinalRx  = Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+($monthNames)")
            val mFirstRx   = Regex("($monthNames)\\s+(\\d{1,2})(?:st|nd|rd|th)?")

            ordinalRx.find(remaining)?.let { m ->
                cal.set(Calendar.DAY_OF_MONTH, m.groupValues[1].toInt().coerceIn(1, 31))
                cal.set(Calendar.MONTH, MONTH_MAP[m.groupValues[2]] ?: cal.get(Calendar.MONTH))
                remaining = remaining.replace(m.value, " ")
                confidence = maxOf(confidence, 0.95f); dateSet = true
            }
            if (!dateSet) mFirstRx.find(remaining)?.let { m ->
                cal.set(Calendar.MONTH, MONTH_MAP[m.groupValues[1]] ?: cal.get(Calendar.MONTH))
                cal.set(Calendar.DAY_OF_MONTH, m.groupValues[2].toInt().coerceIn(1, 31))
                remaining = remaining.replace(m.value, " ")
                confidence = maxOf(confidence, 0.95f); dateSet = true
            }
        }

        // ── 5. CLOCK TIME — only if not already set by relative time ─────────
        if (!timeSet) {
            val t12Rx = Regex("(?:at\\s+)?(\\d{1,2})(?:[:\\s](\\d{2}))?\\s*(am|pm)\\b")
            val t24Rx = Regex("(?:at\\s+)?(\\d{1,2}):(\\d{2})(?!\\s*[aApP])")

            t12Rx.find(remaining)?.let { m ->
                var hour   = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val ampm   = m.groupValues[3]
                if (ampm == "pm" && hour != 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                remaining = remaining.replace(m.value, " ")
                confidence = maxOf(confidence, 0.95f); timeSet = true
            }
            if (!timeSet) t24Rx.find(remaining)?.let { m ->
                cal.set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
                cal.set(Calendar.MINUTE, m.groupValues[2].toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                remaining = remaining.replace(m.value, " ")
                confidence = maxOf(confidence, 0.90f); timeSet = true
            }

            // No time spoken — defaults
            if (!timeSet) {
                if (dateSet) {
                    // Date given but no time — default to 9 AM that day
                    cal.set(Calendar.HOUR_OF_DAY, 9)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                } else {
                    // Nothing given — set to 1 hour from now
                    cal.add(Calendar.HOUR_OF_DAY, 1)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
                confidence = minOf(confidence, 0.5f)
            }
        }

        // ── 6. PAST GUARD ─────────────────────────────────────────────────────
        // Only push forward if no explicit date was given AND time is past.
        // If user said "tomorrow at 11am", NEVER push forward — trust the date.
        if (!dateSet && cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // ── 7. CLEAN UP TITLE ─────────────────────────────────────────────────
        var title = remaining
        title = title.replace(Regex("\\b(at|on|by|the|a|an|in|from|now)\\b"), " ")
        title = title.replace(Regex("\\s+"), " ").trim().trimEnd(',', '.', ';', ':')

        return ParseResult(
            title = title.capitalizeWords().ifBlank { "Reminder" },
            triggerAtMillis = cal.timeInMillis,
            confidence = confidence
        )
    }

    private fun String.capitalizeWords(): String =
        split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
}
