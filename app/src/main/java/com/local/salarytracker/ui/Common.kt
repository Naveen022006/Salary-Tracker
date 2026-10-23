package com.local.salarytracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.salarytracker.budget.Period
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val day = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val shortYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(day)
    }

    fun shortDate(d: LocalDate): String = d.format(short)
    fun fullDate(d: LocalDate): String = d.format(shortYear)

    /** "7 Oct – 6 Nov" */
    fun period(p: Period): String = "${p.start.format(short)} – ${p.endInclusive.format(short)}"

    /** "7 Oct – 6 Nov 2026" */
    fun periodYear(p: Period): String = "${p.start.format(short)} – ${p.endInclusive.format(shortYear)}"

    fun time(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(time)

    /** "Fri, 23 Oct" */
    fun weekdayDate(d: LocalDate): String = d.format(day)

    fun greeting(hour: Int): String = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    fun hourMinute(h: Int, m: Int): String = java.time.LocalTime.of(h, m).format(time)
}

fun showDatePicker(ctx: Context, current: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, current.year, current.monthValue - 1, current.dayOfMonth).show()
}

fun showTimePicker(ctx: Context, hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h, m) }, hour, minute, false).show()
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
