package com.local.salarytracker.ui

import androidx.compose.material.icons.filled.Add
import com.local.salarytracker.ui.components.ScrollToTopEffect
import com.local.salarytracker.ui.components.GlassSurface
import com.local.salarytracker.ui.components.GlassMaterial
import com.local.salarytracker.ui.components.GlassCardShape
import com.local.salarytracker.ui.components.GroupedSection
import com.local.salarytracker.ui.components.Hairline
import com.local.salarytracker.ui.components.IconTile
import com.local.salarytracker.ui.components.CapsuleButton
import com.local.salarytracker.ui.theme.InterText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.DayBar
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.EmptyState
import com.local.salarytracker.ui.components.EntryRow
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.SectionHeader
import com.local.salarytracker.ui.components.StatTile
import com.local.salarytracker.ui.components.rememberAppTween
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private enum class Gauge { Ok, Warn, Over }

/** True at the top of the list or while the user scrolls up; false while scrolling down (FAB collapses). */
@Composable
fun LazyListState.isScrollingUpOrAtTop(): Boolean {
    var prevIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var prevOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    val up by remember(this) {
        androidx.compose.runtime.derivedStateOf {
            val result = if (prevIndex != firstVisibleItemIndex) prevIndex > firstVisibleItemIndex else prevOffset >= firstVisibleItemScrollOffset
            prevIndex = firstVisibleItemIndex
            prevOffset = firstVisibleItemScrollOffset
            result
        }
    }
    val atTop by remember(this) { androidx.compose.runtime.derivedStateOf { firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset < 24 } }
    return up || atTop
}

@Composable
fun TodayScreen(vm: AppViewModel, padding: PaddingValues, listState: LazyListState) {
    val state = vm.todayState.collectAsStateWithLifecycle().value ?: return
    val snap = state.snapshot
    val left = snap.leftTodayPaise
    val allowance = maxOf(snap.todayAllowancePaise, 0L)
    val spentToday = snap.days.lastOrNull()?.takeIf { it.date == state.today }?.dailySpendPaise ?: 0L
    val ratio = when {
        snap.todayAllowancePaise <= 0 -> if (spentToday > 0) 2f else 1f
        else -> spentToday.toFloat() / snap.todayAllowancePaise
    }
    val gauge = when {
        left < 0 || ratio > 1f -> Gauge.Over
        ratio >= 0.8f -> Gauge.Warn
        else -> Gauge.Ok
    }
    val spentList = state.todayTxns.filter { it.type == com.local.salarytracker.model.TxnType.DEBIT && !it.excluded }.sumOf { it.amountPaise }

    LazyColumn(
        state = listState.also { ScrollToTopEffect(it) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            HeroCard(gauge, if (gauge == Gauge.Over) 1f else (1f - ratio).coerceIn(0f, 1f), left, allowance, spentToday, Modifier.padding(horizontal = 16.dp))
        }
        item(key = "tiles") {
            val monthLeft = snap.leftMonthPaise
            val budget = snap.budgetPaise
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = if (monthLeft < 0) "Over this month" else "Left this month",
                        value = Money.formatWhole(if (monthLeft < 0) -monthLeft else monthLeft),
                        caption = "of ${Money.formatWhole(budget)}",
                        progress = if (budget > 0) snap.spentTotalPaise.toFloat() / budget else 1f,
                        valueColor = if (monthLeft < 0) AppColors.current.danger else AppColors.current.label,
                        progressColor = if (monthLeft < 0) AppColors.current.dangerFill else AppColors.current.accent,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    StatTile(
                        label = "This month",
                        value = "${snap.daysLeft} ${if (snap.daysLeft == 1) "day" else "days"} left",
                        caption = "Daily budget ${Money.formatWhole(snap.baseDailyPaise)}",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Text(
                    "Your month: ${Fmt.period(snap.period)}" +
                        (state.trackingStart?.let { " · Tracking since ${Fmt.shortDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.current.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (state.tip.isNotEmpty()) {
            item(key = "tip") {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Lightbulb, AppColors.current.warningFill, size = 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(state.tip, style = MaterialTheme.typography.bodyMedium, color = AppColors.current.label)
                }
            }
        }
        item(key = "last7") {
            GroupedSection(Modifier.padding(horizontal = 16.dp), header = "Last 7 days") {
                Column(Modifier.padding(16.dp)) {
                  if (state.last7.all { it.spendPaise <= 0L }) {
                    // Nothing to draw: a short message instead of an empty chart with one dashed line.
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "No spending in the last 7 days",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.current.label,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Your daily budget is ${Money.formatWhole(snap.baseDailyPaise)}.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.current.secondaryLabel,
                            textAlign = TextAlign.Center,
                        )
                    }
                  } else {
                    Last7DaysChart(state.last7, allowance)
                    if (allowance > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Dashed line: today's budget ${Money.formatWhole(allowance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.current.secondaryLabel,
                        )
                    }
                  }
                }
            }
        }
        state.salaryCredit?.let { c ->
            item(key = "salary") {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn() + slideInVertically { -it / 4 },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    InfoBanner(
                        icon = Icons.Filled.Payments,
                        text = "Salary received: ${Money.format(c.amountPaise)}",
                        tone = BannerTone.Positive,
                        action = {
                            CapsuleButton("Use as my salary", onClick = { vm.updateSettings { it.copy(salaryPaise = c.amountPaise) } }, small = true)
                        },
                    )
                }
            }
        }
        item(key = "list") {
            val count = state.todayTxns.size
            GroupedSection(
                Modifier.padding(horizontal = 16.dp),
                header = if (count == 0) "Today's spending" else "Today's spending · ${Money.format(spentList)} · $count ${if (count == 1) "entry" else "entries"}",
            ) {
                if (state.todayTxns.isEmpty()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = "No spending yet today",
                        text = "Bank SMS and payment alerts show up here by themselves. Paid in cash? Add it.",
                        action = {
                            com.local.salarytracker.ui.components.CapsuleButton(
                                "Add cash", onClick = { vm.openAddCash() },
                                icon = androidx.compose.material.icons.Icons.Filled.Add,
                                style = com.local.salarytracker.ui.components.ButtonStyle.Tinted, small = true,
                            )
                        },
                    )
                } else {
                    state.todayTxns.forEachIndexed { i, t ->
                        if (i > 0) Hairline(start = 68.dp)
                        EntryRow(t, vm.zone) { vm.openEntry(t.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(gauge: Gauge, progress: Float, left: Long, allowance: Long, spent: Long, modifier: Modifier) {
    val colors = AppColors.current
    val ringColor = when (gauge) {
        Gauge.Ok -> colors.positiveFill
        Gauge.Warn -> colors.warningFill
        Gauge.Over -> colors.dangerFill
    }
    val textColor = when (gauge) {
        Gauge.Ok -> colors.label
        Gauge.Warn -> colors.warning
        Gauge.Over -> colors.danger
    }
    val animated by animateFloatAsState(progress, rememberAppTween(600), label = "ring")
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    val ringSize = (112 * fontScale).dp
    val over = left < 0
    val big = Money.formatWhole(if (over) -left else left)
    val bigSize = when {
        big.length <= 6 -> 40.sp
        big.length == 7 -> 34.sp
        else -> 28.sp
    }
    val description = if (over) "${Money.formatWhole(-left)} over today" else "${Money.formatWhole(left)} left today"
    GlassSurface(GlassMaterial.Regular, GlassCardShape, modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp).semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(ringSize)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(colors.fill2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (animated > 0.001f) {
                    drawArc(ringColor, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (over) "Over today" else "Left today",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.secondaryLabel,
                )
                Text(
                    big,
                    style = MoneyStyle.Hero.copy(fontSize = bigSize, lineHeight = bigSize * 1.15f),
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Spent ${Money.formatWhole(spent)} of ${Money.formatWhole(allowance)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = colors.secondaryLabel,
                )
            }
        }
    }
}

/** Seven bars (today highlighted) with a dashed line for today's allowance. Tabular labels, no fixed text sizes beyond sp. */
@Composable
private fun Last7DaysChart(bars: List<DayBar>, allowancePaise: Long) {
    if (bars.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val primary = AppColors.current.accent
    val barColor = primary.copy(alpha = 0.45f)
    val stub = AppColors.current.fill2
    val lineColor = AppColors.current.warningFill
    val labelColor = AppColors.current.secondaryLabel
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontFamily = InterText, fontSize = 12.sp, color = labelColor)
    val todayStyle = TextStyle(fontFamily = InterText, fontSize = 12.sp, color = AppColors.current.accent, fontWeight = FontWeight.Bold)
    val valueStyleBase = TextStyle(fontFamily = InterText, fontSize = 11.sp, color = primary, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val valueStyle = valueStyleBase
    val today = bars.last()
    val appColors = AppColors.current
    val todayColor = when {
        allowancePaise > 0L && today.spendPaise > allowancePaise -> appColors.dangerFill
        else -> appColors.accent
    }
    val disabledLabel = labelColor.copy(alpha = 0.4f)
    val description = "Spending in the last 7 days. Today ${Money.formatWhole(today.spendPaise)}." +
        if (allowancePaise > 0) " Today's budget ${Money.formatWhole(allowancePaise)}." else ""
    val chartHeight = with(density) { (150.dp.toPx() * density.fontScale.coerceIn(1f, 1.3f)).toDp() }
    Canvas(Modifier.fillMaxWidth().height(chartHeight).semantics { contentDescription = description }) {
        val labelH = measurer.measure("M", labelStyle).size.height.toFloat()
        val topPad = measurer.measure("0", valueStyle).size.height.toFloat() + 4.dp.toPx()
        val baseY = size.height - labelH - 6.dp.toPx()
        val plotH = (baseY - topPad).coerceAtLeast(1f)
        val maxBar = bars.maxOf { it.spendPaise }
        val maxV = maxOf(allowancePaise * 1.3, maxBar * 1.1, 1.0)
        val slot = size.width / bars.size
        val barW = minOf(slot * 0.5f, 28.dp.toPx())
        val radius = CornerRadius(minOf(barW / 2, 6.dp.toPx()))
        bars.forEachIndexed { i, b ->
            val isToday = i == bars.lastIndex
            val cx = slot * i + slot / 2
            val h = (b.spendPaise / maxV * plotH).toFloat().let { if (b.spendPaise > 0) maxOf(it, 4.dp.toPx()) else it }
            if (!b.tracked) {
                // not tracked: no bar
            } else if (b.spendPaise <= 0L) {
                drawRoundRect(stub, Offset(cx - barW / 2, baseY - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(1.5f.dp.toPx()))
            } else {
                drawRoundRect(if (isToday) todayColor else barColor, Offset(cx - barW / 2, baseY - h), Size(barW, h), radius)
            }
            val name = b.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.ENGLISH)
            val layout = measurer.measure(name, if (!b.tracked) labelStyle.copy(color = disabledLabel) else if (isToday) todayStyle.copy(color = todayColor) else labelStyle)
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, baseY + 6.dp.toPx()))
            if (isToday && b.spendPaise > 0L) {
                val v = measurer.measure(Money.formatWhole(b.spendPaise), valueStyle.copy(color = todayColor))
                val x = (cx - v.size.width / 2f).coerceIn(0f, size.width - v.size.width)
                var top = baseY - h - v.size.height - 2.dp.toPx()
                if (allowancePaise > 0) {
                    // Never sit on the dashed allowance line: move the label above it.
                    val ly = baseY - (allowancePaise / maxV * plotH).toFloat()
                    if (ly > top - 3.dp.toPx() && ly < top + v.size.height + 3.dp.toPx()) top = minOf(top, ly - v.size.height - 4.dp.toPx())
                }
                drawText(v, topLeft = Offset(x, top.coerceAtLeast(0f)))
            }
        }
        if (allowancePaise > 0) {
            val y = baseY - (allowancePaise / maxV * plotH).toFloat()
            drawLine(
                lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
            )
        }
        drawLine(stub, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())
    }
}
