package com.local.salarytracker.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.LoanMath
import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.data.PersonEntity
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.components.appChipColors
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.util.Money
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

// ------------------------------------------------------------------ Add / edit person

internal class PickedContact(val name: String?, val phone: String?)

/** One row of the phone-number picker (name + chosen number). */
internal fun readPhoneRow(ctx: Context, uri: Uri): PickedContact? =
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) PickedContact(c.getString(0), c.getString(1)) else null }

/** Fallback: the contact picker returns a contact; read its name and, if the system allows it, the first number. */
internal fun readContact(ctx: Context, uri: Uri): PickedContact? {
    var id: String? = null
    var name: String? = null
    var hasPhone = false
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            id = c.getString(0)
            name = c.getString(1)
            hasPhone = c.getInt(2) > 0
        }
    }
    if (id == null && name == null) return null
    var number: String? = null
    if (hasPhone && id != null) {
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(id),
                null,
            )?.use { c -> if (c.moveToFirst()) number = c.getString(0) }
        } catch (_: SecurityException) {
            // The picker only shares the picked contact row; the number then has to be typed.
        }
    }
    return PickedContact(name, number)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSheet(vm: AppViewModel, req: PersonSheetRequest) {
    ModalBottomSheet(
        onDismissRequest = { vm.personSheet.value = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets(0) },
    ) { PersonForm(vm, req.person) }
}

@Composable
private fun PersonForm(vm: AppViewModel, existing: PersonEntity?) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by rememberDraft(vm, "person.name", existing?.name ?: "")
    var phone by rememberDraft(vm, "person.phone", existing?.phone ?: "")
    var note by rememberDraft(vm, "person.note", existing?.note ?: "")
    var contactNote by rememberDraftOf<String?>(vm, "person.contactNote", null, { it ?: "" }, { it.ifEmpty { null } })
    var duplicate by remember { mutableStateOf<String?>(null) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (existing == null) {
            try {
                focus.requestFocus()
                keyboard?.show()
            } catch (_: Exception) {
            }
        }
    }
    // Duplicate-name hint (never blocks saving).
    LaunchedEffect(name) {
        delay(250)
        duplicate = name.trim().takeIf { it.isNotEmpty() }
            ?.let { vm.findPerson(it) }
            ?.takeIf { it.remoteId != existing?.remoteId }
            ?.name
    }

    fun save() {
        if (name.isBlank()) return
        keyboard?.hide()
        vm.savePerson(existing, name.trim(), phone.trim().ifEmpty { null }, note.trim().ifEmpty { null })
        vm.personSheet.value = null
    }

    Column(Modifier.fillMaxWidth().imePadding()) {
        Text(
            if (existing == null) "Add person" else "Edit person",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(LoanMath.NAME_MAX) },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                supportingText = duplicate?.let { d ->
                    { Text(if (existing == null) "You already have $d. Add another?" else "You already have another person called $d.") }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }.take(LoanMath.PHONE_MAX + 6) },
                label = { Text("Phone (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { vm.pickContact() },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            ) {
                Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pick from contacts")
            }
            if (contactNote != null) {
                Text(contactNote!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(LoanMath.NOTE_MAX) },
                label = { Text("Note (optional)") },
                shape = RoundedCornerShape(12.dp),
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        SaveBar(enabled = name.isNotBlank(), onSave = { save() })
    }
}

@Composable
private fun SaveBar(enabled: Boolean, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).defaultMinSize(minHeight = 56.dp),
            ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

// ------------------------------------------------------------------ Entry sheet

private val METHOD_CHIPS = listOf("Cash", "GPay", "PhonePe", "Paytm", "Navi", "Bank transfer", "Other")
private val UPI_APPS = setOf("GPay", "PhonePe", "Paytm", "Navi")

private fun chipFor(e: LoanEntryEntity?): String = when {
    e == null -> "Cash"
    e.method == LoanMethod.CASH -> "Cash"
    e.method == LoanMethod.UPI && e.appName in UPI_APPS -> e.appName!!
    e.method == LoanMethod.BANK -> "Bank transfer"
    else -> "Other"
}

private fun sheetTitle(k: LoanKind) = when (k) {
    LoanKind.GAVE -> "You gave money"
    LoanKind.GOT_BACK -> "You got money back"
    LoanKind.BORROWED -> "You borrowed money"
    LoanKind.PAID_BACK -> "You paid money back"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanEntrySheet(vm: AppViewModel, req: LoanSheetRequest) {
    ModalBottomSheet(
        onDismissRequest = { vm.loanSheet.value = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets(0) },
    ) { LoanForm(vm, req) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanForm(vm: AppViewModel, req: LoanSheetRequest) {
    val ctx = LocalContext.current
    val pickers = rememberPickers()
    pickers.Host()
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val original = req.entry
    val isNew = original == null
    val today = LocalDate.now()
    val originalDate = remember { original?.let { Instant.ofEpochMilli(it.occurredAt).atZone(vm.zone).toLocalDate() } }

    var kind by rememberDraftOf(vm, "loan.kind", req.kind, { it.name }, { runCatching { LoanKind.valueOf(it) }.getOrNull() })
    var amountText by rememberDraft(vm, "loan.amount", original?.let { Money.toInput(it.amountPaise) } ?: "")
    var chip by rememberDraft(vm, "loan.chip", chipFor(original))
    var chipTouched by rememberDraftBool(vm, "loan.chipTouched", false)
    var date by rememberDraftDate(vm, "loan.date", originalDate ?: today)
    var due by rememberDraftNullableDate(vm, "loan.due", original?.dueLocalDate)
    var note by rememberDraft(vm, "loan.note", original?.note ?: "")
    var confirmDelete by remember { mutableStateOf(false) }

    val person = remember(req.personRemoteId) { vm.repo.observePerson(req.personRemoteId) }.collectAsStateWithLifecycle(initialValue = null).value
    val entries = remember(req.personRemoteId) { vm.repo.observeEntries(req.personRemoteId) }.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val who = person?.name ?: "this person"

    val paise = Money.parseToPaise(amountText)
    val badAmount = amountText.isNotEmpty() && paise == null
    val outstanding = LoanMath.balance(entries.filter { it.remoteId != original?.remoteId })
    val warning = when {
        paise == null -> null
        kind == LoanKind.GOT_BACK && paise > maxOf(outstanding, 0L) -> "You are getting back more than $who owes"
        kind == LoanKind.PAID_BACK && paise > maxOf(-outstanding, 0L) -> "You are paying back more than you owe $who"
        else -> null
    }
    val canDue = kind == LoanKind.GAVE || kind == LoanKind.BORROWED

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isNew) {
            try {
                focus.requestFocus()
                keyboard?.show()
            } catch (_: Exception) {
            }
        }
    }

    fun save() {
        val amount = paise ?: return
        val (method, app) = if (!chipTouched && original != null) {
            original.method to original.appName
        } else when (chip) {
            "Cash" -> LoanMethod.CASH to null
            "Bank transfer" -> LoanMethod.BANK to null
            "Other" -> LoanMethod.OTHER to null
            else -> LoanMethod.UPI to chip
        }
        val occurred = when {
            original != null && date == originalDate -> original.occurredAt
            date == LocalDate.now() -> System.currentTimeMillis()
            else -> date.atTime(12, 0).atZone(vm.zone).toInstant().toEpochMilli()
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        keyboard?.hide()
        vm.saveLoan(original, req.personRemoteId, kind, amount, method, app, occurred, if (canDue) due else null, note.trim().ifEmpty { null })
        vm.loanSheet.value = null
    }

    Column(Modifier.fillMaxWidth().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(sheetTitle(kind), style = MaterialTheme.typography.titleLarge)
                Text("with $who", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
            // Kind switch (2 x 2 so it fits big text sizes)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pair in LoanKind.entries.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (k in pair) KindTile(k, kind == k, Modifier.weight(1f)) { kind = k }
                    }
                }
            }

            AmountField(
                value = amountText,
                onChange = { amountText = it },
                fontSize = 40.sp,
                isError = badAmount,
                focusRequester = focus,
            )
            if (badAmount) {
                Text("Type an amount bigger than 0", style = MaterialTheme.typography.bodySmall, color = AppColors.current.danger)
            }
            if (warning != null) {
                InfoBanner(Icons.Filled.Warning, warning, tone = BannerTone.Warning)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in METHOD_CHIPS) {
                        FilterChip(
                            selected = chip == m,
                            onClick = { chip = m; chipTouched = true },
                            label = { Text(m) },
                            colors = appChipColors(),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { pickers.date(date) { date = it } },
                        label = { Text(Fmt.dayLabel(date, today)) },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                    if (canDue) {
                        AssistChip(
                            onClick = { pickers.date(due ?: today) { due = it } },
                            label = { Text(due?.let { "Return by " + smartDate(it, today) } ?: "Return by (optional)") },
                            leadingIcon = { Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        )
                        if (due != null) {
                            IconButton(onClick = { due = null }) { Icon(Icons.Filled.Close, contentDescription = "Remove return date") }
                        }
                    }
                }
                if (canDue && due != null) {
                    Text(
                        "You get a reminder on this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(LoanMath.NOTE_MAX) },
                label = { Text("Note (optional)") },
                shape = RoundedCornerShape(12.dp),
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        SaveBar(enabled = paise != null, onSave = { save() })
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("${kindTitle(original.kind)} ${Money.format(original.amountPaise)} will be removed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteLoan(original); vm.loanSheet.value = null }) {
                    Text("Delete", color = AppColors.current.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun KindTile(kind: LoanKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppColors.current
    Surface(
        modifier.defaultMinSize(minHeight = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(kind), contentDescription = null, tint = if (kindIsIncoming(kind)) colors.positive else colors.danger, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                kind.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
