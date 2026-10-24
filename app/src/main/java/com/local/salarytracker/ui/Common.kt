package com.local.salarytracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import java.time.ZoneOffset
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

/** Holds the date / time dialog that is currently open. Place [Host] once in the composable that opens pickers. */
@Stable
class Pickers {
    private var datePick by mutableStateOf<DatePick?>(null)
    private var timePick by mutableStateOf<TimePick?>(null)

    private class DatePick(val initial: LocalDate, val max: LocalDate?, val min: LocalDate?, val onPick: (LocalDate) -> Unit)
    private class TimePick(val hour: Int, val minute: Int, val onPick: (Int, Int) -> Unit)

    fun date(current: LocalDate, max: LocalDate? = null, min: LocalDate? = null, onPick: (LocalDate) -> Unit) {
        datePick = DatePick(current, max, min, onPick)
    }

    fun time(hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
        timePick = TimePick(hour, minute, onPick)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Host() {
        datePick?.let { req ->
            // DatePickerState works in UTC midnight millis; convert with UTC (not the device zone) to avoid off-by-one days.
            val maxMillis = req.max?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            val minMillis = req.min?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            val selectable = remember(req) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        (maxMillis == null || utcTimeMillis <= maxMillis) && (minMillis == null || utcTimeMillis >= minMillis)
                }
            }
            val state = rememberDatePickerState(
                initialSelectedDateMillis = req.initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = selectable,
            )
            DatePickerDialog(
                onDismissRequest = { datePick = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { ms ->
                                req.onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            datePick = null
                        },
                        enabled = state.selectedDateMillis != null,
                    ) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { datePick = null }) { Text("Cancel") } },
            ) { DatePicker(state = state) }
        }
        timePick?.let { req ->
            val state = rememberTimePickerState(initialHour = req.hour, initialMinute = req.minute, is24Hour = false)
            AlertDialog(
                onDismissRequest = { timePick = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
                confirmButton = {
                    TextButton(onClick = { req.onPick(state.hour, state.minute); timePick = null }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { timePick = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
fun rememberPickers(): Pickers = remember { Pickers() }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** A text value kept in the view model (survives leaving the app and the screen being rebuilt). */
@Composable
fun rememberDraft(vm: AppViewModel, key: String, initial: String): androidx.compose.runtime.MutableState<String> {
    remember(key) { if (!vm.drafts.containsKey(key)) vm.drafts[key] = initial }
    return remember(key) {
        object : androidx.compose.runtime.MutableState<String> {
            override var value: String
                get() = vm.drafts[key] ?: initial
                set(v) { vm.drafts[key] = v }
            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/** Same as [rememberDraft] for any value that can be written as text. */
@Composable
fun <T> rememberDraftOf(vm: AppViewModel, key: String, initial: T, encode: (T) -> String, decode: (String) -> T?): androidx.compose.runtime.MutableState<T> {
    remember(key) { if (!vm.drafts.containsKey(key)) vm.drafts[key] = encode(initial) }
    return remember(key) {
        object : androidx.compose.runtime.MutableState<T> {
            override var value: T
                get() = vm.drafts[key]?.let(decode) ?: initial
                set(v) { vm.drafts[key] = encode(v) }
            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberDraftDate(vm: AppViewModel, key: String, initial: LocalDate): androidx.compose.runtime.MutableState<LocalDate> =
    rememberDraftOf(vm, key, initial, { it.toString() }, { runCatching { LocalDate.parse(it) }.getOrNull() })

@Composable
fun rememberDraftNullableDate(vm: AppViewModel, key: String, initial: LocalDate?): androidx.compose.runtime.MutableState<LocalDate?> =
    rememberDraftOf(vm, key, initial, { it?.toString() ?: "" }, { if (it.isEmpty()) null else runCatching { LocalDate.parse(it) }.getOrNull() })

@Composable
fun rememberDraftBool(vm: AppViewModel, key: String, initial: Boolean): androidx.compose.runtime.MutableState<Boolean> =
    rememberDraftOf(vm, key, initial, { it.toString() }, { it.toBooleanStrictOrNull() })
