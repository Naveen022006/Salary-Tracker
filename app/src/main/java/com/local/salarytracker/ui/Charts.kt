package com.local.salarytracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.salarytracker.budget.CategoryTotal
import com.local.salarytracker.budget.DailyPoint
import com.local.salarytracker.budget.Period
import com.local.salarytracker.budget.ReportInsights
import com.local.salarytracker.ui.components.rememberAppTween
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

/** Donut with a stroke of 28dp; segments have small rounded gaps between them. [center] sits in the hole. */
@Composable
fun DonutChart(totals: List<CategoryTotal>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceContainer
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val progress by animateFloatAsState(if (go) 1f else 0f, rememberAppTween(700), label = "donut")
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val radius = arcSize.width / 2
            val parts = totals.filter { it.amountPaise > 0 }
            val total = parts.sumOf { it.amountPaise }
            if (total <= 0) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            } else if (parts.size == 1) {
                drawArc(Color(parts[0].category.color), -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            } else {
                val capDeg = Math.toDegrees((stroke / 2 / radius).toDouble()).toFloat()
                val gapDeg = Math.toDegrees((4.dp.toPx() / radius).toDouble()).toFloat()
                var start = -90f
                for (t in parts) {
                    val sweep = 360f * t.amountPaise / total
                    val color = Color(t.category.color)
                    val flat = sweep - 2 * capDeg - gapDeg
                    if (flat >= 1.5f) {
                        // Round caps stick out by half the stroke on each side, so shorten the arc to keep a gap.
                        drawArc(
                            color, start + capDeg + gapDeg / 2, flat * progress, false,
                            Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    } else {
                        drawArc(
                            color, start + gapDeg / 2, (sweep - gapDeg).coerceAtLeast(0.8f) * progress, false,
                            Offset(inset, inset), arcSize, style = Stroke(stroke),
                        )
                    }
                    start += sweep
                }
            }
        }
        center()
    }
}

private val tipDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * Bars = daily spending; dashed line = that day's allowance. Bars turn red when spending is above the allowance.
 * Today's bar has an outline. Tap a bar to see a small tooltip. The y-axis is capped (allowance can get huge at the end
 * of a month) and a small triangle marks days where the allowance line goes above the cap.
 */
@Composable
fun DailyBarChart(
    period: Period,
    daily: List<DailyPoint>,
    baseDailyPaise: Long,
    today: LocalDate,
    modifier: Modifier = Modifier,
    showAllowance: Boolean = true,
) {
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val overColor = AppColors.current.danger
    val lineColor = AppColors.current.accent
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainer
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val tipFg = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val tipStyle = TextStyle(fontSize = 12.sp, color = tipFg)

    val n = period.days
    val maxSpend = daily.maxOfOrNull { it.dailySpendPaise } ?: 0L
    val maxV = maxOf(1L, if (showAllowance) maxOf(2 * baseDailyPaise, (maxSpend * 1.1).toLong()) else (maxSpend * 1.1).toLong())
    val yTicks = listOf(0L, maxV / 2, maxV)
    val yLayouts = yTicks.map { tm.measure(ReportInsights.axisLabel(it), labelStyle) }
    val axisW = yLayouts.maxOf { it.size.width }.toFloat() + with(density) { 8.dp.toPx() }

    val byIndex = remember(period, daily) { daily.associateBy { ChronoUnit.DAYS.between(period.start, it.date).toInt() } }
    var selected by remember(period, daily) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier.fillMaxWidth().height(220.dp)
            .semantics { contentDescription = "Day by day spending chart. Tap a bar to see the amount for that day." }
            .pointerInput(byIndex, axisW, n) {
                detectTapGestures { off ->
                    val slot = (size.width - axisW) / n
                    val i = floor((off.x - axisW) / slot).toInt()
                    selected = if (i in byIndex.keys && i != selected) i else null
                }
            },
    ) {
        if (n <= 0) return@Canvas
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val h = size.height - bottomPad - topPad
        val plotW = size.width - axisW
        val slot = plotW / n
        val barW = slot * 0.62f
        val baseY = topPad + h

        // grid + y labels
        for ((k, v) in yTicks.withIndex()) {
            val y = baseY - h * v / maxV
            drawLine(grid, Offset(axisW, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val lay = yLayouts[k]
            val ly = (y - lay.size.height / 2f).coerceIn(0f, size.height - lay.size.height)
            drawText(lay, topLeft = Offset(axisW - 4.dp.toPx() - lay.size.width, ly))
        }

        selected?.let { i ->
            drawRoundRect(highlight, Offset(axisW + i * slot, topPad), Size(slot, h), CornerRadius(4.dp.toPx()))
        }

        val path = Path()
        var first = true
        for (d in daily) {
            val i = ChronoUnit.DAYS.between(period.start, d.date).toInt()
            if (i < 0 || i >= n) continue
            val x = axisW + i * slot + (slot - barW) / 2
            val bh = (h * d.dailySpendPaise / maxV).coerceAtMost(h).let { if (d.dailySpendPaise > 0) maxOf(it, 4.dp.toPx()) else it }
            val color = if (showAllowance && d.dailySpendPaise > d.allowancePaise) overColor else barColor
            if (bh > 0f) {
                drawRoundRect(color, Offset(x, baseY - bh), Size(barW, bh), CornerRadius(2.dp.toPx()))
            }
            if (d.date == today) {
                val oh = maxOf(bh, 4.dp.toPx())
                drawRoundRect(
                    lineColor, Offset(x - 1.dp.toPx(), baseY - oh - 1.dp.toPx()), Size(barW + 2.dp.toPx(), oh + 2.dp.toPx()),
                    CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()),
                )
            }
            if (!showAllowance) continue
            val cx = axisW + i * slot + slot / 2
            val clipped = d.allowancePaise > maxV
            val ly = baseY - h * d.allowancePaise.coerceIn(0, maxV) / maxV
            if (clipped) {
                val m = tm.measure("▲", labelStyle)
                drawText(m, topLeft = Offset((cx - m.size.width / 2f).coerceIn(axisW, size.width - m.size.width), 0f))
            }
            if (first) { path.moveTo(cx, ly); first = false } else path.lineTo(cx, ly)
        }
        if (showAllowance && !first) {
            drawPath(
                path, lineColor,
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
            )
        }

        // x labels every 5 days
        for (i in 0 until n) {
            if (i % 5 != 0 && !(i == n - 1 && (n - 1) % 5 >= 3)) continue
            val label = period.start.plusDays(i.toLong()).dayOfMonth.toString()
            val layout = tm.measure(label, labelStyle)
            val cx = axisW + i * slot + slot / 2
            val x = (cx - layout.size.width / 2f).coerceIn(axisW, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, baseY + 4.dp.toPx()))
        }

        // tooltip
        selected?.let { i ->
            val d = byIndex[i] ?: return@let
            val text = d.date.format(tipDay) + " · " + Money.formatWhole(d.dailySpendPaise) +
                if (showAllowance) " (budget ${Money.formatWhole(d.allowancePaise)})" else ""
            val pad = 8.dp.toPx()
            val lay = tm.measure(text, tipStyle, constraints = Constraints(maxWidth = (size.width - 2 * pad - 8.dp.toPx()).toInt().coerceAtLeast(1)))
            val w = lay.size.width + 2 * pad
            val hh = lay.size.height + pad
            val cx = axisW + i * slot + slot / 2
            val barTop = baseY - (h * d.dailySpendPaise / maxV).coerceAtMost(h)
            val x = (cx - w / 2).coerceIn(0f, size.width - w)
            val y = (barTop - hh - 6.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(tipBg, Offset(x, y), Size(w, hh), CornerRadius(8.dp.toPx()))
            drawText(lay, topLeft = Offset(x + pad, y + pad / 2))
        }
    }
}
