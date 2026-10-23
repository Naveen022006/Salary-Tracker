package com.local.salarytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.util.Money
import java.time.LocalDate

/** "Your month: 7 Oct – 6 Nov" for a pay day. */
fun payPeriodText(payDay: Int): String = "Your month: " + Fmt.period(BudgetEngine.currentPeriod(LocalDate.now(), payDay))

/** 1..30 grid plus a "Last day of month" button (stored as 31). */
@Composable
fun PayDayPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in (1..30).chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (d in row) {
                    DayCell(d.toString(), selected == d, Modifier.weight(1f)) { onSelect(d) }
                }
                repeat(7 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        FilterChip(
            selected = selected == 31,
            onClick = { onSelect(31) },
            label = { Text("Last day of month", fontSize = 16.sp) },
        )
    }
}

@Composable
private fun DayCell(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.aspectRatio(1f).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 16.sp) }
}

/** Dialog to type an amount. [allowEmpty] = empty means zero (e.g. savings goal). */
@Composable
fun MoneyDialog(title: String, initialPaise: Long, allowEmpty: Boolean, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(Money.toInput(initialPaise)) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = Money.cleanInput(v); error = false },
                singleLine = true,
                label = { Text("Amount (₹)") },
                isError = error,
                supportingText = if (error) ({ Text("Type an amount") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val p = Money.parseToPaise(text)
                when {
                    p != null -> onSave(p)
                    text.isBlank() && allowEmpty -> onSave(0L)
                    else -> error = true
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PayDayDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var day by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which day do you get your salary?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PayDayPicker(day) { day = it }
                Text(payPeriodText(day), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(day) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Range choice + Import button + result line (used in onboarding and in Settings). */
@Composable
fun ImportBlock(vm: AppViewModel, smsAllowed: Boolean) {
    var range by remember { mutableStateOf(ImportRange.M3) }
    val state = vm.importState.collectAsStateWithLifecycle().value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Import from:", style = MaterialTheme.typography.titleSmall)
        for (r in ImportRange.entries) {
            Row(
                Modifier.fillMaxWidth().clickable { range = r }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = range == r, onClick = { range = r })
                Text(r.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        OutlinedButton(
            onClick = { vm.runImport(range) },
            enabled = smsAllowed && state != ImportState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state == ImportState.Running) "Reading SMS..." else "Import SMS now") }
        if (!smsAllowed) {
            Text("Allow SMS first.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        when (state) {
            is ImportState.Done -> Text(
                if (state.permissionMissing) "No SMS permission." else "Found ${state.expenses} expenses",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.permissionMissing) MaterialTheme.colorScheme.error else creditColor(),
            )
            else -> {}
        }
    }
}
