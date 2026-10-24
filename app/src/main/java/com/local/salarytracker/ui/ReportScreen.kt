package com.local.salarytracker.ui

import com.local.salarytracker.ui.components.ScrollToTopEffect
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.layout
import com.local.salarytracker.ui.components.tint
import com.local.salarytracker.ui.components.textTint
import com.local.salarytracker.ui.components.GlassSurface
import com.local.salarytracker.ui.components.GlassMaterial
import com.local.salarytracker.ui.components.GlassCardShape
import com.local.salarytracker.ui.components.CapsuleShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.PeriodReport
import com.local.salarytracker.budget.ReportInsights
import com.local.salarytracker.model.Category
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.EmptyState
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.components.MoneyText
import com.local.salarytracker.ui.components.PeriodSwitcher
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.SectionHeader
import com.local.salarytracker.ui.components.label
import com.local.salarytracker.ui.components.rememberAppTween
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import androidx.compose.ui.unit.sp

@Composable
fun ReportScreen(vm: AppViewModel, padding: PaddingValues) {
    val state = vm.reportState.collectAsStateWithLifecycle().value ?: return
    val today = vm.today.collectAsStateWithLifecycle().value
    val bottom = padding.calculateBottomPadding()
    // The month switcher is the first row of the list, so it scrolls away under the glass bar with the title.
    val switcher: @Composable () -> Unit = {
        PeriodSwitcher(
            label = Fmt.periodYear(state.period),
            canGoBack = state.offset < state.maxOffset,
            canGoForward = state.offset > 0,
            onBack = { vm.reportOffset.value = state.offset + 1 },
            onForward = { vm.reportOffset.value = (state.offset - 1).coerceAtLeast(0) },
            // The list already has the 16dp side gutter.
            modifier = Modifier.layout { m, c ->
                val extra = 32.dp.roundToPx()
                val p = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
                layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
            },
        )
    }
    Crossfade(targetState = state, animationSpec = rememberAppTween(300), label = "report") { st ->
        ReportContent(st, today, padding.calculateTopPadding(), bottom, switcher, onMap = { vm.openSpendingMap() })
    }
}

@Composable
private fun ReportContent(
    state: ReportState,
    today: java.time.LocalDate,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    switcher: @Composable () -> Unit,
    onMap: () -> Unit,
) {
    val r = state.report
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    ScrollToTopEffect(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding + 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "switcher") { switcher() }
        item(key = "summary") { SummaryCard(r) }
        item(key = "map") {
            SectionCard(onClick = onMap, contentPadding = PaddingValues(0.dp)) {
                com.local.salarytracker.ui.components.ListRow(
                    "Spending map",
                    subtitle = "See where you spent this month",
                    leading = { com.local.salarytracker.ui.components.IconTile(Icons.Filled.Map, AppColors.current.positiveFill) },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.current.tertiaryLabel) },
                )
            }
        }

        if (!state.hasData) {
            item(key = "nodata") {
                SectionCard {
                    EmptyState(
                        icon = Icons.Filled.BarChart,
                        title = "No entries in this month yet",
                        text = "Bank SMS, payment alerts and cash spending you add show up here.",
                    )
                }
            }
            return@LazyColumn
        }

        state.comparison?.let { c ->
            item(key = "compare") {
                val colors = AppColors.current
                val tint = when {
                    Math.abs(c.diffPaise) < 50L -> MaterialTheme.colorScheme.onSurfaceVariant
                    c.isLess -> colors.positive
                    else -> colors.danger
                }
                val icon = when {
                    Math.abs(c.diffPaise) < 50L -> Icons.Filled.TrendingFlat
                    c.isLess -> Icons.Filled.TrendingDown
                    else -> Icons.Filled.TrendingUp
                }
                SectionCard {
                    SectionHeader("Compared with last month")
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(ReportInsights.sentence(c), style = MaterialTheme.typography.titleMedium, color = tint, modifier = Modifier.weight(1f))
                    }
                    if (state.comparisonPartial) {
                        Text(
                            "Compared with the same number of days last month.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "donut") {
            SectionCard {
                SectionHeader("Where the money went")
                if (r.byCategory.isEmpty()) {
                    Text(
                        "No spending in this month.",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        DonutChart(r.byCategory) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
                                Text("Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(r.spentPaise, style = MoneyStyle.Card.copy(fontSize = 20.sp, lineHeight = 28.sp), textAlign = TextAlign.Center)
                            }
                        }
                    }
                    val total = r.byCategory.sumOf { it.amountPaise }.coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (ct in r.byCategory) {
                            val share = ct.amountPaise.toFloat() / total
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(ct.category, 32.dp, custom = ct.custom)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(com.local.salarytracker.ui.components.categoryName(ct.category, ct.custom), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.width(8.dp))
                                        MoneyText(ct.amountPaise, style = MoneyStyle.Small)
                                        Text(
                                            "${Math.round(share * 100)}%",
                                            modifier = Modifier.padding(start = 8.dp).widthIn(min = 48.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    ShareBar(share, com.local.salarytracker.ui.components.categoryTint(ct.category, ct.custom))
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "daily") {
            SectionCard {
                SectionHeader("Day by day")
                Spacer(Modifier.height(12.dp))
                DailyBarChart(r.period, r.daily, r.snapshot.baseDailyPaise, today, showAllowance = !r.beforeTracking)
                Spacer(Modifier.height(8.dp))
                FlowRowLegend(showAllowance = !r.beforeTracking)
                Text(
                    "Bills and rent are not shown in the bars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item(key = "places") {
            SectionCard {
                SectionHeader("Top places")
                if (r.topMerchants.isEmpty()) NothingYet()
                r.topMerchants.forEachIndexed { i, m ->
                    RankRow(
                        rank = i + 1,
                        category = m.category ?: Category.OTHER,
                        title = m.merchant,
                        sub = "${m.count} time" + if (m.count == 1) "" else "s",
                        amountPaise = m.amountPaise,
                        whole = true,
                    )
                }
            }
        }

        item(key = "biggest") {
            SectionCard {
                SectionHeader("Biggest expenses")
                if (r.biggest.isEmpty()) NothingYet()
                r.biggest.forEachIndexed { i, b ->
                    RankRow(
                        rank = i + 1,
                        category = b.category,
                        title = b.merchant?.takeIf { it.isNotBlank() } ?: b.category.label,
                        sub = "${Fmt.shortDate(b.date)} · ${b.category.label}",
                        amountPaise = b.amountPaise,
                        whole = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun NothingYet() {
    Text(
        "Nothing to show yet.",
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RankRow(rank: Int, category: Category, title: String, sub: String, amountPaise: Long, whole: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rank.toString(),
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CategoryIcon(category, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(amountPaise, style = MoneyStyle.Row, whole = whole)
    }
}

@Composable
private fun ShareBar(share: Float, color: Color) {
    Box(
        Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLegend(showAllowance: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendDot(MaterialTheme.colorScheme.primary, "Spent")
        if (showAllowance) {
            LegendDot(AppColors.current.danger, "Over allowance")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("- - ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AppColors.current.accent)
                Text("Allowance", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(r: PeriodReport) {
    val colors = AppColors.current
    val stacked = LocalDensity.current.fontScale > 1.15f
    GlassSurface(GlassMaterial.Regular, GlassCardShape, Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
        if (r.beforeTracking) {
            Kpi("Spent", r.spentPaise, colors.label, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                icon = Icons.Outlined.Info,
                text = "Before you started the app — only spending found in SMS is shown.",
                tone = BannerTone.Info,
            )
            return@Column
        }
        val budget = r.budgetPaise
        val left = budget - r.spentPaise
        val leftColor = if (left < 0) colors.danger else colors.positive
        val leftLabel = if (r.finished) (if (left < 0) "Over" else "Saved") else (if (left < 0) "Over" else "Left")
        val items = listOf(
            Triple("Budget", budget, colors.label),
            Triple("Spent", r.spentPaise, colors.label),
            Triple(leftLabel, Math.abs(left), leftColor),
        )
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((l, v, c) in items) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(v, style = MoneyStyle.Row, color = c)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((l, v, c) in items) Kpi(l, v, c, Modifier.weight(1f))
            }
        }
        if (budget > 0) {
            val ratio = (r.spentPaise.toFloat() / budget).coerceIn(0f, 1f)
            val animated by animateFloatAsState(ratio, rememberAppTween(600), label = "spent")
            val barColor = when {
                r.spentPaise > budget -> colors.dangerFill
                r.spentPaise >= budget * 0.8 -> colors.warningFill
                else -> colors.accent
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CapsuleShape),
                color = barColor,
                trackColor = colors.fill2,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                "${Math.round(r.spentPaise * 100.0 / budget)}% of your budget used",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val onBudget = r.daysUnder + r.daysExact
        if (onBudget > 0 || r.daysOver > 0) {
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onBudget > 0) Pill("$onBudget ${if (onBudget == 1) "day" else "days"} on budget", colors.positiveFill, true)
                if (r.daysOver > 0) Pill("${r.daysOver} ${if (r.daysOver == 1) "day" else "days"} over", colors.dangerFill, false)
            }
        }
        r.trackingStart?.let { cut ->
            Text(
                "Tracking since ${Fmt.shortDate(cut)}. Earlier days are not counted.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } }
}

@Composable
private fun Kpi(label: String, paise: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.current.secondaryLabel)
        MoneyText(paise, style = MoneyStyle.Card.copy(fontSize = 22.sp, lineHeight = 28.sp), color = color)
    }
}

@Composable
private fun Pill(text: String, tint: Color, withCheck: Boolean) {
    Surface(shape = CapsuleShape, color = tint.copy(alpha = if (AppColors.current.isDark) 0.24f else 0.15f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (withCheck) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = textTint(tint), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = textTint(tint), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

@Suppress("unused")
private val keepImports: ImageVector? = null
