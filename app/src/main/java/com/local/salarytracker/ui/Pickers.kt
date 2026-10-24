package com.local.salarytracker.ui

import com.local.salarytracker.ui.components.GlassMaterial
import com.local.salarytracker.ui.components.solidTint
import com.local.salarytracker.ui.components.AppleChip
import com.local.salarytracker.ui.components.IconTile
import com.local.salarytracker.ui.components.SectionShape
import com.local.salarytracker.ui.components.CapsuleButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.rememberAppTween
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money
import java.time.LocalDate

/** "Your month: 7 Oct – 6 Nov" for a pay day. */
fun payPeriodText(payDay: Int): String = "Your month: " + Fmt.period(BudgetEngine.currentPeriod(LocalDate.now(), payDay))

/** 1..30 grid plus a "Last day of month" chip (stored as 31). [columns] = 7 on a wide screen, 6 in a dialog. */
@Composable
fun PayDayPicker(selected: Int, onSelect: (Int) -> Unit, columns: Int = 7) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in (1..30).chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (d in row) {
                    DayCell(d, selected == d, Modifier.weight(1f)) { onSelect(d) }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        AppleChip("Last day of month", selected = selected == 31, onClick = { onSelect(31) })
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) AppColors.current.accent else AppColors.current.fill3
    val fg = if (selected) Color.White else AppColors.current.label
    Box(
        modifier.aspectRatio(1f).clip(CircleShape).background(bg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Day $day" },
        contentAlignment = Alignment.Center,
    ) { Text(day.toString(), color = fg, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")) }
}

/** Big amount box: "₹ 0" faded when empty. Digits only (one dot allowed). */
@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 36.sp,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = AppColors.current.fill3) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(Money.cleanInput(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            singleLine = true,
            textStyle = MoneyStyle.Input.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
                color = if (isError) AppColors.current.danger else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(AppColors.current.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone() }),
            visualTransformation = RupeeTransformation(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
    }
}

/** Rounded dialog with a title, scrolling content and Cancel / confirm buttons. Wide enough for a 6-column grid. */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Save",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            // Solid: a dialog is its own window, so it cannot blur what is behind it.
            color = com.local.salarytracker.ui.components.sheetCard(),
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.bodyLarge, color = AppColors.current.accent) }
                    TextButton(onClick = onConfirm) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = AppColors.current.accent) }
                }
            }
        }
    }
}

/** Dialog to type an amount. [allowEmpty] = empty means zero (e.g. savings goal). */
@Composable
fun MoneyDialog(title: String, initialPaise: Long, allowEmpty: Boolean, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(Money.toInput(initialPaise)) }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun save() {
        val p = Money.parseToPaise(text)
        when {
            p != null -> onSave(p)
            text.isBlank() && allowEmpty -> onSave(0L)
            else -> error = true
        }
    }
    AppDialog(title, onDismiss, onConfirm = { save() }) {
        AmountField(text, { text = it; error = false }, isError = error, focusRequester = focus, onDone = { save() })
        val parsed = Money.parseToPaise(text)
        when {
            error -> Text("Type an amount bigger than 0", style = MaterialTheme.typography.bodySmall, color = AppColors.current.danger)
            parsed != null -> Text(Money.format(parsed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            allowEmpty -> Text("Leave empty for no savings goal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PayDayDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var day by remember { mutableStateOf(initial) }
    AppDialog("Which day do you get your salary?", onDismiss, onConfirm = { onSave(day) }) {
        PayDayPicker(day, { day = it }, columns = 6)
        PayMonthCard(day)
    }
}

/** Small card: "Your month: 1 Oct – 31 Oct". */
@Composable
fun PayMonthCard(payDay: Int, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = SectionShape, color = AppColors.current.card) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.CalendarMonth, AppColors.current.dangerFill)
            Spacer(Modifier.padding(start = 12.dp))
            Text(payPeriodText(payDay), style = MaterialTheme.typography.titleMedium, color = AppColors.current.label)
        }
    }
}

private fun rangeHint(r: ImportRange) = when (r) {
    ImportRange.M3 -> "A quick start"
    ImportRange.M6 -> "More history"
    ImportRange.M12 -> "A full year"
    ImportRange.ALL -> "Every bank SMS on this phone. Can take longer."
}

/**
 * Range cards + progress + result. The Import button is outside (onboarding and the Settings sheet each have their own).
 * [onAllowSms] is shown as a button when SMS permission is missing.
 */
@Composable
fun ImportPanel(
    range: ImportRange,
    onRange: (ImportRange) -> Unit,
    state: ImportState,
    smsAllowed: Boolean,
    onAllowSms: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in ImportRange.entries) {
            val sel = range == r
            Surface(
                shape = SectionShape,
                color = if (sel) AppColors.current.accent.copy(alpha = if (AppColors.current.isDark) 0.24f else 0.10f) else com.local.salarytracker.ui.components.sheetCard(),
                border = if (sel) BorderStroke(1.5.dp, AppColors.current.accent) else null,
            ) {
                Row(
                    Modifier.fillMaxWidth().selectable(selected = sel, role = Role.RadioButton, enabled = state != ImportState.Running) { onRange(r) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = sel, onClick = null, modifier = Modifier.padding(8.dp),
                        colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = AppColors.current.accent),
                    )
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(r.label, style = MaterialTheme.typography.titleMedium)
                        Text(rangeHint(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (!smsAllowed) {
            InfoBanner(
                icon = Icons.Filled.ErrorOutline,
                text = "Allow SMS first, so the app can read your old messages.",
                tone = BannerTone.Warning,
                action = { CapsuleButton("Allow SMS", onClick = onAllowSms, small = true) },
            )
        }
        when (state) {
            ImportState.Running -> {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Reading your SMS. This can take a minute.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ImportState.Done -> {
                if (state.permissionMissing) {
                    InfoBanner(Icons.Filled.ErrorOutline, "No SMS permission. Allow SMS and try again.", tone = BannerTone.Danger)
                } else {
                    InfoBanner(
                        Icons.Filled.CheckCircle,
                        if (state.expenses == 0) "No new expenses found." else "Found ${state.expenses} new ${if (state.expenses == 1) "expense" else "expenses"}.",
                        tone = BannerTone.Positive,
                    )
                }
            }
            ImportState.Idle -> {}
        }
    }
}
