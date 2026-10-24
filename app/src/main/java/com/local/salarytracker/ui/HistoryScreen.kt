package com.local.salarytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.EmptyState
import com.local.salarytracker.ui.components.EntryRow
import com.local.salarytracker.ui.components.MoneyText
import com.local.salarytracker.ui.components.PeriodSwitcher
import com.local.salarytracker.ui.components.ScreenScaffold
import com.local.salarytracker.ui.components.label
import com.local.salarytracker.ui.theme.MoneyStyle
import java.time.Instant
import java.time.LocalDate

/** The History tab's app bar: title with a search action, or a search field while searching. */
@Composable
fun HistoryScaffold(vm: AppViewModel, content: @Composable (PaddingValues) -> Unit) {
    val open = vm.historySearchOpen.collectAsStateWithLifecycle().value
    val query = vm.historyQuery.collectAsStateWithLifecycle().value
    BackHandler(enabled = open) { vm.closeHistorySearch() }
    ScreenScaffold(
        title = "History",
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        titleContent = if (open) {
            { SearchField(query) { vm.historyQuery.value = it } }
        } else null,
        navigationIcon = {
            if (open) {
                IconButton(onClick = { vm.closeHistorySearch() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            }
        },
        actions = {
            if (!open) {
                IconButton(onClick = { vm.historySearchOpen.value = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search history")
                }
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { vm.historyQuery.value = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search text")
                }
            }
        },
        content = content,
    )
}

@Composable
internal fun SearchField(query: String, placeholder: String = "Search shop or note", onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        singleLine = true,
        placeholder = { Text(placeholder) },
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

private val SOURCES = listOf<Pair<TxnSource?, String>>(
    null to "All",
    TxnSource.SMS to "Bank SMS",
    TxnSource.NOTIFICATION to "App alert",
    TxnSource.MANUAL to "Cash",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: AppViewModel, padding: PaddingValues) {
    val state = vm.historyState.collectAsStateWithLifecycle().value ?: return
    val today = vm.today.collectAsStateWithLifecycle().value
    val query = vm.historyQuery.collectAsStateWithLifecycle().value.trim()
    var catName by rememberSaveable { mutableStateOf<String?>(null) }
    var srcName by rememberSaveable { mutableStateOf<String?>(null) }
    val catFilter = catName?.let { Category.fromName(it) }
    val srcFilter = srcName?.let { n -> TxnSource.entries.firstOrNull { it.name == n } }
    var sourceMenu by remember { mutableStateOf(false) }

    val filtered = state.txns.filter { t ->
        (catFilter == null || t.category == catFilter) &&
            (srcFilter == null || t.source == srcFilter) &&
            (query.isEmpty() || t.merchant?.contains(query, ignoreCase = true) == true || t.note?.contains(query, ignoreCase = true) == true)
    }
    val groups: List<Pair<LocalDate, List<TxnEntity>>> = filtered
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(vm.zone).toLocalDate() }
        .toList()
        .sortedByDescending { it.first }
        .map { (d, l) -> d to l.sortedByDescending { it.timestamp } }
    val spent = filtered.filter { it.type == TxnType.DEBIT && !it.excluded }.sumOf { it.amountPaise }
    val anyFilter = catFilter != null || srcFilter != null || query.isNotEmpty()

    fun clearFilters() {
        catName = null
        srcName = null
        vm.historyQuery.value = ""
    }

    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
    ) {
        item(key = "period") {
            PeriodSwitcher(
                label = Fmt.periodYear(state.period),
                canGoBack = state.offset < state.maxOffset,
                canGoForward = state.offset > 0,
                onBack = { vm.historyOffset.value = state.offset + 1 },
                onForward = { vm.historyOffset.value = (state.offset - 1).coerceAtLeast(0) },
            )
        }
        item(key = "summary") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Spent ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(spent, style = MoneyStyle.Row)
                Text(
                    " · ${filtered.size} ${if (filtered.size == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "filters") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    FilterChip(
                        selected = srcFilter != null,
                        onClick = { sourceMenu = true },
                        label = { Text("Source: " + SOURCES.first { it.first == srcFilter }.second) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose source") },
                    )
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        for ((src, name) in SOURCES) {
                            DropdownMenuItem(
                                text = { Text(name, fontWeight = if (src == srcFilter) FontWeight.SemiBold else FontWeight.Normal) },
                                onClick = { srcName = src?.name; sourceMenu = false },
                            )
                        }
                    }
                }
                for (c in Category.entries) {
                    FilterChip(
                        selected = catFilter == c,
                        onClick = { catName = if (catFilter == c) null else c.name },
                        label = { Text(c.label) },
                        leadingIcon = { CategoryIcon(c, 24.dp) },
                    )
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                if (state.txns.isEmpty()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "No entries in this month",
                        text = "Bank SMS and payment alerts show up here by themselves.",
                    )
                } else {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "No entries for these filters",
                        text = if (query.isNotEmpty()) "Try a different word." else "Try a different filter.",
                        action = if (anyFilter) {
                            { OutlinedButton(onClick = { clearFilters() }) { Text("Clear filters") } }
                        } else null,
                    )
                }
            }
        }
        for ((day, list) in groups) {
            stickyHeader(key = "h$day") {
                val total = list.filter { it.type == TxnType.DEBIT && !it.excluded }.sumOf { it.amountPaise }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Fmt.dayLabel(day, today),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (total > 0) MoneyText(total, style = MoneyStyle.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { t ->
                EntryRow(t, vm.zone) { vm.openEntry(t.id) }
                HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
