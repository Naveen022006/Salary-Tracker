package com.local.salarytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.LendingTotals
import com.local.salarytracker.budget.PersonBalance
import com.local.salarytracker.cloud.SyncStatus
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.EmptyState
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.components.PersonAvatar
import com.local.salarytracker.ui.components.ScreenScaffold
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.SectionHeader
import com.local.salarytracker.ui.components.TintPill
import com.local.salarytracker.ui.components.appChipColors
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money
import java.time.Instant
import java.time.LocalDate

/** "21 Oct", or "21 Oct 2025" when it is not this year. */
internal fun smartDate(d: LocalDate, today: LocalDate): String = if (d.year == today.year) Fmt.shortDate(d) else Fmt.fullDate(d)

/** The Lending tab: the home list, or the person screen when one is open. */
@Composable
fun LendingTab(vm: AppViewModel, listState: LazyListState) {
    val person = vm.selectedPerson.collectAsStateWithLifecycle().value
    if (person != null) {
        PersonScreen(vm, person)
    } else {
        LendingHome(vm, listState)
    }
}

@Composable
private fun LendingHome(vm: AppViewModel, listState: LazyListState) {
    val open = vm.lendingSearchOpen.collectAsStateWithLifecycle().value
    val query = vm.lendingQuery.collectAsStateWithLifecycle().value
    BackHandler(enabled = open) { vm.closeLendingSearch() }
    ScreenScaffold(
        title = "Lending",
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        titleContent = if (open) {
            { SearchField(query, "Search by name") { vm.lendingQuery.value = it } }
        } else null,
        navigationIcon = {
            if (open) {
                IconButton(onClick = { vm.closeLendingSearch() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            }
        },
        actions = {
            if (!open) {
                IconButton(onClick = { vm.lendingSearchOpen.value = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search people")
                }
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { vm.lendingQuery.value = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search text")
                }
            }
        },
    ) { pad -> LendingList(vm, pad, listState) }
}

@Composable
private fun LendingList(vm: AppViewModel, pad: PaddingValues, listState: LazyListState) {
    val people = vm.lendingPeople.collectAsStateWithLifecycle().value
    val totals = vm.lendingTotals.collectAsStateWithLifecycle().value
    val filter = vm.lendingFilter.collectAsStateWithLifecycle().value
    val query = vm.lendingQuery.collectAsStateWithLifecycle().value.trim()
    val hint = SyncStatus.hint.collectAsStateWithLifecycle().value
    val today = vm.today.collectAsStateWithLifecycle().value
    if (people == null || totals == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val filtered = people.filter { b ->
        val okFilter = when (filter) {
            LendingFilter.ALL -> true
            LendingFilter.OWE_ME -> b.balancePaise > 0
            LendingFilter.I_OWE -> b.balancePaise < 0
            LendingFilter.SETTLED -> b.balancePaise == 0L
        }
        okFilter && (query.isEmpty() || b.person.name.contains(query, ignoreCase = true))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hint != null) {
            item(key = "hint") {
                InfoBanner(Icons.Filled.Info, hint, modifier = Modifier.padding(horizontal = 16.dp), tone = BannerTone.Info)
            }
        }
        if (people.isEmpty()) {
            item(key = "empty") {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(
                        icon = Icons.Outlined.Handshake,
                        title = "Track money you lend and borrow",
                        text = "Add a person, then record what you gave or got back. This does not change your daily budget.",
                        action = {
                            Button(onClick = { vm.openPersonSheet(null) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add person")
                            }
                        },
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "hero") {
            LendingHero(totals, people.count { it.overdueCount > 0 }, Modifier.padding(horizontal = 16.dp))
        }
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (f in LendingFilter.entries) {
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.lendingFilter.value = f },
                        label = { Text(f.label) },
                        colors = appChipColors(),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    )
                }
            }
        }
        item(key = "people") {
            SectionCard(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(0.dp)) {
                SectionHeader(
                    "People",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    subtitle = "${filtered.size} ${if (filtered.size == 1) "person" else "people"}",
                )
                if (filtered.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "No one here",
                        text = if (query.isNotEmpty()) "No name matches \"$query\"." else "No one in this list yet.",
                        action = if (filter != LendingFilter.ALL || query.isNotEmpty()) {
                            {
                                OutlinedButton(onClick = { vm.lendingFilter.value = LendingFilter.ALL; vm.closeLendingSearch() }) { Text("Show everyone") }
                            }
                        } else null,
                    )
                } else {
                    filtered.forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        PersonRow(b, vm, today) { vm.selectedPerson.value = b.person.remoteId; vm.closeLendingSearch() }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Gradient card: "You will get" and "You need to pay". */
@Composable
private fun LendingHero(totals: LendingTotals, overduePeople: Int, modifier: Modifier) {
    val colors = AppColors.current
    val onCard = colors.onHero
    SectionCard(
        modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd))),
        containerColor = Color.Transparent,
        contentColor = onCard,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroColumn("You will get", totals.youWillGetPaise, "from ${totals.peopleOwingYou} ${if (totals.peopleOwingYou == 1) "person" else "people"}", Modifier.weight(1f))
            HeroColumn("You need to pay", totals.youNeedToPayPaise, "to ${totals.peopleYouOwe} ${if (totals.peopleYouOwe == 1) "person" else "people"}", Modifier.weight(1f))
        }
        if (overduePeople > 0) {
            Spacer(Modifier.height(12.dp))
            val accent = colors.heroAccent
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("$overduePeople overdue", style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
    }
}

@Composable
private fun HeroColumn(label: String, paise: Long, caption: String, modifier: Modifier) {
    val onCard = AppColors.current.onHero
    val text = Money.formatWhole(paise)
    val size = when {
        text.length <= 7 -> 24.sp
        text.length == 8 -> 21.sp
        text.length == 9 -> 19.sp
        else -> 17.sp
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = onCard.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(text, style = MoneyStyle.Card.copy(fontSize = size, lineHeight = size * 1.3f), color = onCard, maxLines = 1, overflow = TextOverflow.Clip)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = onCard.copy(alpha = 0.75f))
    }
}

@Composable
private fun PersonRow(b: PersonBalance, vm: AppViewModel, today: LocalDate, onClick: () -> Unit) {
    val colors = AppColors.current
    val p = b.person
    val caption = if (b.entryCount > 0) {
        val last = Instant.ofEpochMilli(b.lastActivityAt).atZone(vm.zone).toLocalDate()
        "Last: ${smartDate(last, today)} · ${b.entryCount} ${if (b.entryCount == 1) "entry" else "entries"}"
    } else {
        p.phone?.takeIf { it.isNotBlank() } ?: "No entries yet"
    }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(p.name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                b.balancePaise > 0 -> TintPill("Owes you ${Money.format(b.balancePaise)}", colors.positive)
                b.balancePaise < 0 -> TintPill("You owe ${Money.format(-b.balancePaise)}", colors.danger)
                else -> TintPill("Settled", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                b.overdueCount > 0 -> TintPill("Overdue", colors.danger, strong = true)
                b.dueTodayCount > 0 -> TintPill("Due today", colors.accent, strong = true)
                else -> {}
            }
        }
    }
}
