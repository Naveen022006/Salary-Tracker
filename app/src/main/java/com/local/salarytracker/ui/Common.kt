package com.local.salarytracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.salarytracker.budget.Period
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.util.Money
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

    fun hourMinute(h: Int, m: Int): String = java.time.LocalTime.of(h, m).format(time)
}

fun showDatePicker(ctx: Context, current: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, current.year, current.monthValue - 1, current.dayOfMonth).show()
}

fun showTimePicker(ctx: Context, hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h, m) }, hour, minute, false).show()
}

@Composable
fun SourceChip(source: TxnSource) {
    val label = when (source) {
        TxnSource.SMS -> "SMS"
        TxnSource.NOTIFICATION -> "App"
        TxnSource.MANUAL -> "Cash"
    }
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun EntryRow(t: TxnEntity, zone: ZoneId, onClick: () -> Unit) {
    val credit = t.type == TxnType.CREDIT
    val title = t.merchant?.takeIf { it.isNotBlank() }
        ?: t.note?.takeIf { it.isNotBlank() }
        ?: if (credit) "Money received" else t.category.displayName
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color(t.category.color).copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (credit) "💰" else t.category.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Fmt.time(t.timestamp, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SourceChip(t.source)
                if (t.excluded) Text("Not spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        val amount = (if (credit) "+" else "") + Money.format(t.amountPaise)
        Text(
            amount,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                credit -> creditColor()
                t.excluded -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
fun PeriodSwitcher(label: String, canGoBack: Boolean, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canGoBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier month") }
        Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onForward, enabled = canGoForward) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later month") }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
