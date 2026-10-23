package com.local.salarytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorSheet(vm: AppViewModel, req: EditorRequest) {
    var loaded by remember(req) { mutableStateOf(req.txnId == null) }
    var txn by remember(req) { mutableStateOf<TxnEntity?>(null) }
    LaunchedEffect(req) {
        if (req.txnId != null) {
            val t = vm.repo.getTxn(req.txnId)
            if (t == null) vm.closeEditor() else {
                txn = t
                loaded = true
            }
        }
    }
    if (!loaded) return
    ModalBottomSheet(
        onDismissRequest = { vm.closeEditor() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        EditorForm(vm, txn)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(vm: AppViewModel, original: TxnEntity?) {
    val ctx = LocalContext.current
    val zone: ZoneId = vm.zone
    val isNew = original == null
    var amountText by remember { mutableStateOf(original?.let { Money.toInput(it.amountPaise) } ?: "") }
    var category by remember { mutableStateOf(original?.category ?: Category.OTHER) }
    var excluded by remember { mutableStateOf(original?.excluded ?: false) }
    var text by remember { mutableStateOf(original?.merchant ?: original?.note ?: "") }
    val initial = remember {
        original?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDateTime() } ?: java.time.LocalDateTime.now()
    }
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var time by remember { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var askRule by remember { mutableStateOf<TxnEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun timestamp(): Long = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text(if (isNew) "Add cash spending" else "Edit entry", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = amountText,
            onValueChange = { v -> amountText = Money.cleanInput(v); error = null },
            label = { Text("Amount (₹)") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Category", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val ordered = Category.entries.filter { it != Category.TRANSFER } + Category.TRANSFER
            for (c in ordered) {
                val label = if (c == Category.TRANSFER) "Not spending" else "${c.emoji} ${c.displayName}"
                FilterChip(
                    selected = category == c,
                    onClick = {
                        category = c
                        excluded = c == Category.TRANSFER
                    },
                    label = { Text(label) },
                )
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            label = { Text("Shop or note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showDatePicker(ctx, date) { date = it } }, modifier = Modifier.weight(1f)) {
                Text(Fmt.fullDate(date))
            }
            OutlinedButton(onClick = { showTimePicker(ctx, time.hour, time.minute) { h, m -> time = LocalTime.of(h, m) } }, modifier = Modifier.weight(1f)) {
                Text(Fmt.hourMinute(time.hour, time.minute))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not my spending (transfer to myself, card bill)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = excluded,
                onCheckedChange = {
                    excluded = it
                    if (it) category = Category.TRANSFER else if (category == Category.TRANSFER) category = Category.OTHER
                },
            )
        }

        original?.rawText?.takeIf { it.isNotBlank() }?.let {
            Text("Original message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (!isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            Button(
                onClick = {
                    val paise = Money.parseToPaise(amountText)
                    if (paise == null) {
                        error = "Type an amount"
                        return@Button
                    }
                    val name = text.trim().takeIf { it.isNotEmpty() }
                    val cat = if (excluded) Category.TRANSFER else category
                    if (original == null) {
                        vm.addEntry(paise, cat, name, timestamp(), excluded)
                        vm.closeEditor()
                    } else {
                        val updated = original.copy(
                            amountPaise = paise,
                            category = cat,
                            excluded = excluded,
                            merchant = name,
                            timestamp = timestamp(),
                        )
                        val categoryChanged = cat != original.category && !excluded && original.type == TxnType.DEBIT
                        if (categoryChanged && name != null) {
                            askRule = updated
                        } else {
                            vm.updateEntry(updated, null)
                            vm.closeEditor()
                        }
                    }
                },
                modifier = Modifier.weight(if (isNew) 1f else 1.4f).height(52.dp),
            ) { Text("Save", fontSize = 18.sp) }
        }
    }

    askRule?.let { u ->
        AlertDialog(
            onDismissRequest = { askRule = null },
            title = { Text("Always use ${u.category.displayName} for ${u.merchant}?") },
            text = { Text("The app will use this category for the next ${u.merchant} entries too.") },
            confirmButton = {
                TextButton(onClick = { vm.updateEntry(u, u.category); askRule = null; vm.closeEditor() }) { Text("Yes, always") }
            },
            dismissButton = {
                TextButton(onClick = { vm.updateEntry(u, null); askRule = null; vm.closeEditor() }) { Text("Only this one") }
            },
        )
    }
    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("${Money.format(original.amountPaise)} will be removed.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteEntry(original); confirmDelete = false; vm.closeEditor() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
