package com.local.salarytracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.salarytracker.budget.CategoryTotal
import com.local.salarytracker.budget.DailyPoint
import com.local.salarytracker.budget.Period
import java.time.temporal.ChronoUnit

@Composable
fun DonutChart(totals: List<CategoryTotal>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 36.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val total = totals.sumOf { it.amountPaise }
            if (total <= 0) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                for (t in totals) {
                    val sweep = 360f * t.amountPaise / total
                    drawArc(
                        Color(t.category.color), start, (sweep - 1.2f).coerceAtLeast(0.6f), false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke),
                    )
                    start += sweep
                }
            }
        }
        center()
    }
}

/** Bars = daily spending; line = that day's allowance. Bars are red when spending is above the allowance. */
@Composable
fun DailyBarChart(
    period: Period,
    daily: List<DailyPoint>,
    baseDailyPaise: Long,
    modifier: Modifier = Modifier,
    showAllowance: Boolean = true,
) {
    val tm = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val lineColor = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val n = period.days
        if (n <= 0) return@Canvas
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val h = size.height - bottomPad - topPad
        val slot = size.width / n
        val barW = slot * 0.62f
        // y-axis cap: the allowance line can get huge on the last days, so it is clipped (marked with a triangle).
        val maxSpend = daily.maxOfOrNull { it.dailySpendPaise } ?: 0L
        val maxV = maxOf(1L, if (showAllowance) maxOf(2 * baseDailyPaise, (maxSpend * 1.1).toLong()) else (maxSpend * 1.1).toLong())
        val baseY = topPad + h
        drawLine(grid, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())
        drawLine(grid, Offset(0f, topPad), Offset(size.width, topPad), strokeWidth = 1.dp.toPx())

        val path = Path()
        var first = true
        for (d in daily) {
            val i = ChronoUnit.DAYS.between(period.start, d.date).toInt()
            if (i < 0 || i >= n) continue
            val x = i * slot + (slot - barW) / 2
            val bh = (h * d.dailySpendPaise / maxV).coerceAtMost(h)
            if (bh > 0f) {
                drawRoundRect(
                    if (showAllowance && d.dailySpendPaise > d.allowancePaise) overColor else barColor,
                    Offset(x, baseY - bh), Size(barW, bh), CornerRadius(2.dp.toPx()),
                )
            }
            val cx = i * slot + slot / 2
            if (!showAllowance) continue
            val clipped = d.allowancePaise > maxV
            val ly = baseY - h * d.allowancePaise.coerceIn(0, maxV) / maxV
            if (clipped) {
                val m = tm.measure("▲", labelStyle)
                drawText(m, topLeft = Offset((i * slot + slot / 2 - m.size.width / 2f).coerceIn(0f, size.width - m.size.width), 0f))
            }
            if (first) { path.moveTo(cx, ly); first = false } else path.lineTo(cx, ly)
        }
        if (showAllowance && !first) drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

        for (i in 0 until n) {
            if (i % 5 != 0 && i != n - 1) continue
            val label = period.start.plusDays(i.toLong()).dayOfMonth.toString()
            val layout = tm.measure(label, labelStyle)
            val cx = i * slot + slot / 2
            val x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, baseY + 3.dp.toPx()))
        }
    }
}
