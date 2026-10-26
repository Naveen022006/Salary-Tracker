package com.local.salarytracker.ui

import com.local.salarytracker.ui.components.ScrollToTopEffect
import com.local.salarytracker.ui.components.Hairline
import com.local.salarytracker.ui.components.GlassSurface
import com.local.salarytracker.ui.components.GlassMaterial
import com.local.salarytracker.ui.components.GlassCardShape
import com.local.salarytracker.ui.components.GroupedSection
import com.local.salarytracker.ui.components.CapsuleButton
import com.local.salarytracker.ui.components.AppleChip
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        hasNavigation = open,
        listState = listState,
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
        state = listState.also { ScrollToTopEffect(it) },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding()),
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
                            CapsuleButton("Add person", onClick = { vm.openPersonSheet(null) }, icon = Icons.Filled.PersonAdd)
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
                    AppleChip(f.label, selected = filter == f, onClick = { vm.lendingFilter.value = f })
                }
            }
        }
        item(key = "people") {
            GroupedSection(Modifier.padding(horizontal = 16.dp), header = "People · ${filtered.size}") {
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
                        if (i > 0) Hairline(start = 72.dp)
                        PersonRow(b, vm, today) { vm.selectedPerson.value = b.person.remoteId; vm.closeLendingSearch() }
                    }
                }
            }
        }
    }
}

/** Glass card: "You will get" (green) and "You need to pay" (red). */
@Composable
private fun LendingHero(totals: LendingTotals, overduePeople: Int, modifier: Modifier) {
    val colors = AppColors.current
    GlassSurface(GlassMaterial.Regular, GlassCardShape, modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroColumn("You will get", totals.youWillGetPaise, colors.positive, "from ${totals.peopleOwingYou} ${if (totals.peopleOwingYou == 1) "person" else "people"}", Modifier.weight(1f))
                HeroColumn("You need to pay", totals.youNeedToPayPaise, colors.danger, "to ${totals.peopleYouOwe} ${if (totals.peopleYouOwe == 1) "person" else "people"}", Modifier.weight(1f))
            }
            if (overduePeople > 0) {
                Spacer(Modifier.height(12.dp))
                TintPill("$overduePeople overdue", colors.dangerFill, strong = true)
            }
        }
    }
}

@Composable
private fun HeroColumn(label: String, paise: Long, color: Color, caption: String, modifier: Modifier) {
    val text = Money.formatWhole(paise)
    val size = when {
        text.length <= 7 -> 28.sp
        text.length == 8 -> 24.sp
        text.length == 9 -> 21.sp
        else -> 18.sp
    }
    val secondary = AppColors.current.secondaryLabel
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = secondary)
        Spacer(Modifier.height(2.dp))
        Text(text, style = MoneyStyle.Card.copy(fontSize = size, lineHeight = size * 1.25f), color = if (paise > 0) color else AppColors.current.label, maxLines = 1, overflow = TextOverflow.Clip)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = secondary)
    }
}

@Composable
private fun PersonRow(b: PersonBalance, vm: AppViewModel, today: LocalDate, onClick: () -> Unit) {
    val colors = AppColors.current
    val p = b.person
    // The balance pill can take most of the width: if the long caption does not fit, show just "Last: 24 Oct".
    var shortCaption by remember(b.person.remoteId) { mutableStateOf(false) }
    val caption = if (b.entryCount > 0) {
        val last = Instant.ofEpochMilli(b.lastActivityAt).atZone(vm.zone).toLocalDate()
        if (shortCaption) "Last: ${smartDate(last, today)}"
        else "Last: ${smartDate(last, today)} · ${b.entryCount} ${if (b.entryCount == 1) "entry" else "entries"}"
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
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow && b.entryCount > 0) shortCaption = true },
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 170.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                b.balancePaise > 0 -> TintPill("Owes you ${Money.format(b.balancePaise)}", colors.positiveFill)
                b.balancePaise < 0 -> TintPill("You owe ${Money.format(-b.balancePaise)}", colors.dangerFill)
                else -> TintPill("Settled", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                b.overdueCount > 0 -> TintPill("Overdue", colors.dangerFill, strong = true)
                b.dueTodayCount > 0 -> TintPill("Due today", colors.warningFill, strong = true)
                else -> {}
            }
        }
    }
}
