package com.local.salarytracker.ui

import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.label
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        EditorForm(vm, txn)
    }
}

/** Shows "₹ " in front of the typed digits; when empty shows a faded "₹ 0". */
internal class RupeeTransformation(private val hint: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = if (text.isEmpty()) {
            buildAnnotatedString {
                append("₹ ")
                withStyle(SpanStyle(color = hint)) { append("0") }
            }
        } else AnnotatedString("₹ ") + text
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + 2
            override fun transformedToOriginal(offset: Int) = (offset - 2).coerceIn(0, text.length)
        }
        return TransformedText(shown, map)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(vm: AppViewModel, original: TxnEntity?) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
    var askRule by remember { mutableStateOf<TxnEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    val paise = Money.parseToPaise(amountText)
    val badAmount = amountText.isNotEmpty() && paise == null

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (isNew) {
            try {
                focus.requestFocus()
                keyboard?.show()
            } catch (_: Exception) {
            }
        }
    }

    fun timestamp(): Long = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun save() {
        val amount = paise ?: return
        val name = text.trim().takeIf { it.isNotEmpty() }
        val cat = if (excluded) Category.TRANSFER else category
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (original == null) {
            vm.addEntry(amount, cat, name, timestamp(), excluded)
            vm.closeEditor()
        } else {
            val updated = original.copy(
                amountPaise = amount,
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
    }

    Column(Modifier.fillMaxWidth().imePadding()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isNew) "Add cash spending" else "Edit entry",
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = AppColors.current.danger)
                }
            }
        }

        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Amount
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = Money.cleanInput(v) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = MoneyStyle.Input.copy(
                        color = if (badAmount) AppColors.current.danger else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    visualTransformation = RupeeTransformation(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                )
                if (badAmount) {
                    Text("Type an amount bigger than 0", style = MaterialTheme.typography.bodySmall, color = AppColors.current.danger)
                }
            }

            // Category grid: 4 columns
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ordered = Category.entries.filter { it != Category.TRANSFER } + Category.TRANSFER
                ordered.chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { c ->
                            CategoryTile(
                                c,
                                selected = category == c,
                                onClick = {
                                    category = c
                                    excluded = c == Category.TRANSFER
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // Shop or note
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                label = { Text("Shop or note") },
                leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // When
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { showDatePicker(ctx, date) { date = it } },
                        label = { Text(Fmt.dayLabel(date, LocalDate.now())) },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                    AssistChip(
                        onClick = { showTimePicker(ctx, time.hour, time.minute) { h, m -> time = LocalTime.of(h, m) } },
                        label = { Text(Fmt.hourMinute(time.hour, time.minute)) },
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                }
            }

            // Not my spending
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).toggleable(
                    value = excluded,
                    role = Role.Switch,
                    onValueChange = {
                        excluded = it
                        if (it) category = Category.TRANSFER else if (category == Category.TRANSFER) category = Category.OTHER
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Not my spending", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Transfer to myself, credit card bill, investment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = excluded, onCheckedChange = null)
            }

            // Original message (captured entries)
            original?.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button) { showOriginal = !showOriginal },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Original message", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Icon(
                            if (showOriginal) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showOriginal) "Hide original message" else "Show original message",
                        )
                    }
                    AnimatedVisibility(showOriginal) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text(
                                raw,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Sticky save bar
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { save() },
                    enabled = paise != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).defaultMinSize(minHeight = 56.dp),
                ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
            }
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
                    Text("Delete", color = AppColors.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** One category in the 4-column picker: 48dp icon over a label (2 lines max). */
@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Surface(
            shape = shape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryIcon(category, 48.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    category.label,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
