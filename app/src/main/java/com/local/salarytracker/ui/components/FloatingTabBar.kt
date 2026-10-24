package com.local.salarytracker.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One tab: outlined icon, filled icon (the bar shows filled icons, like Apple Music). */
data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** What the shrunk bar shows in its middle pill: money left today and the used share of today's budget. */
data class TodayPill(val leftText: String, val caption: String, val progress: Float, val over: Boolean)

private val BarHeight = 64.dp
private val CircleSize = 58.dp
private val Gap = 10.dp
private const val LENS_LIFT = 1.16f // Apple: the selected tab grows 16% while the lens is lifted

/**
 * iOS 26 / Apple Music style bottom bar, with Apple's motion:
 * - one tinted glass **lens** marks the selected tab and slides between tabs on a spring (its edges move at different
 *   speeds, so it stretches like a drop of liquid);
 * - **press** lifts the lens (16% bigger, clearer) and it follows the finger; sliding across tabs ticks; releasing
 *   selects the tab under the finger;
 * - **shrinking** (while scrolling down): the glass shrinks over the tabs (they blur and fade in place), the selected
 *   icon slides into the circle, and the "left today" pill moves in beside it (the "+" is in the page header). Everything is one spring, so it can be
 *   reversed at any moment. Reduced motion: no lift, no stretch, short fades.
 */
@Composable
fun FloatingTabBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    collapsed: Boolean,
    onExpand: () -> Unit,
    today: TodayPill?,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val c = AppColors.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 0 = full bar, 1 = shrunk. One spring for the whole morph (UIKit-like: critically damped, quick).
    val t by animateFloatAsState(
        if (collapsed) 1f else 0f,
        if (reduced) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.86f, stiffness = 320f),
        label = "collapse",
    )

    BoxWithConstraints(modifier.fillMaxWidth().height(BarHeight)) {
        val total = maxWidth
        val fullBar = total
        val barW = lerp(fullBar, CircleSize, t)
        val barH = lerp(BarHeight, CircleSize, t)
        val pillW = (total - CircleSize - Gap).coerceAtLeast(0.dp)
        val inner = fullBar - 8.dp // 4dp bar padding on each side (UIKit)
        val itemW = inner / items.size
        val itemPx = with(density) { itemW.toPx() }

        // Lens edges (px, inside the bar's padding). Leading edge is stiffer than trailing edge -> stretch.
        val left = remember { Animatable(selected * itemPx) }
        val right = remember { Animatable((selected + 1) * itemPx) }
        var pressed by remember { mutableStateOf(false) }
        var hover by remember { mutableFloatStateOf(-1f) } // finger x while pressed
        val lift by animateFloatAsState(if (pressed && !reduced) 1f else 0f, spring(0.7f, 600f), label = "lift")

        suspend fun moveLensTo(start: Float, end: Float) {
            if (reduced) {
                left.snapTo(start); right.snapTo(end); return
            }
            val toRight = start > left.value
            val fast = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)
            val slow = spring<Float>(dampingRatio = 0.72f, stiffness = 260f)
            scope.launch { left.animateTo(start, if (toRight) slow else fast) }
            scope.launch { right.animateTo(end, if (toRight) fast else slow) }
        }
        LaunchedEffect(selected, itemPx) {
            if (!pressed) moveLensTo(selected * itemPx, (selected + 1) * itemPx)
        }
        val currentSelected by rememberUpdatedState(selected)
        val currentOnSelect by rememberUpdatedState(onSelect)

        // ---- the bar (glass shrinks from full width to the circle)
        Box(
            Modifier.width(barW).height(barH).align(Alignment.CenterStart)
                .graphicsLayer {
                    val s = 1f + 0.02f * lift // the bar swells a little under the finger
                    scaleX = s; scaleY = s
                }
                .liquidGlass(CapsuleShape)
                .clip(CapsuleShape)
                .then(
                    if (t < 0.5f) {
                        Modifier.pointerInput(itemPx, items.size) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                pressed = true
                                var x = (down.position.x - 4.dp.toPx()).coerceIn(0f, itemPx * items.size - 1f)
                                var slot = (x / itemPx).toInt()
                                hover = x
                                scope.launch { moveLensTo(x - itemPx / 2, x + itemPx / 2) }
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val change = ev.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    x = (change.position.x - 4.dp.toPx()).coerceIn(0f, itemPx * items.size - 1f)
                                    hover = x
                                    val s = (x / itemPx).toInt()
                                    if (s != slot) {
                                        slot = s
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    }
                                    scope.launch { moveLensTo(x - itemPx / 2, x + itemPx / 2) }
                                    change.consume()
                                }
                                pressed = false
                                hover = -1f
                                val target = slot.coerceIn(0, items.size - 1)
                                if (target != currentSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentOnSelect(target)
                                scope.launch { moveLensTo(target * itemPx, (target + 1) * itemPx) }
                            }
                        }
                    } else {
                        Modifier.clickable(role = Role.Button, onClick = onExpand)
                            .semantics { contentDescription = "Show tabs. Now: ${items[selected].label}" }
                    },
                ),
        ) {
            // Tabs layer: laid out at full width, clipped by the shrinking glass; blur + fade in place.
            val tabsAlpha = (1f - t * 2.5f).coerceIn(0f, 1f) // gone by 40%, before the pill shows
            Box(
                Modifier.width(fullBar).fillMaxHeight().padding(4.dp).alpha(tabsAlpha)
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t > 0f) Modifier.blur((8 * t).dp) else Modifier),
            ) {
                // The lens (behind the icons).
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val l = left.value
                        val r = right.value
                        val w = (r - l).coerceAtLeast(itemPx * 0.6f)
                        val cx = l + (r - l) / 2
                        val scale = 1f + (LENS_LIFT - 1f) * lift
                        val lw = w * scale
                        val lh = size.height * (1f + 0.06f * lift)
                        val top = (size.height - lh) / 2
                        val tint = if (lift > 0.5f) Color.White.copy(alpha = if (c.isDark) 0.10f else 0.35f)
                        else c.accent.copy(alpha = if (c.isDark) 0.24f else 0.14f)
                        drawRoundRect(tint, Offset(cx - lw / 2, top), Size(lw, lh), CornerRadius(lh / 2))
                        drawRoundRect(
                            Color.White.copy(alpha = if (c.isDark) 0.18f else 0.6f), Offset(cx - lw / 2, top), Size(lw, lh),
                            CornerRadius(lh / 2), style = Stroke(0.75.dp.toPx()),
                        )
                    },
                )
                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { i, item ->
                        val on = i == selected
                        // The tab under the finger grows with the lifted lens.
                        val under = hover >= 0f && (hover / itemPx).toInt() == i
                        val grow by animateFloatAsState(if (under && !reduced) LENS_LIFT else 1f, spring(0.7f, 600f), label = "grow")
                        val tint = if (on) c.accent else c.label
                        Column(
                            Modifier.width(itemW).fillMaxHeight()
                                .graphicsLayer { scaleX = grow; scaleY = grow }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = on
                                    onClick { currentOnSelect(i); true }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Icon(item.selectedIcon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
                            BasicText(
                                item.label,
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                                color = { tint },
                                // softWrap on: the auto-size check then sees a too-long label and shrinks it.
                                maxLines = 1,
                                softWrap = true,
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                            )
                        }
                    }
                }
            }
            // The selected icon slides from its tab into the circle.
            val iconAlpha = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
            if (iconAlpha > 0f) {
                val from = 4.dp + itemW * selected + itemW / 2
                val to = CircleSize / 2
                val x = lerp(from, to, t)
                Icon(
                    items[selected].selectedIcon, contentDescription = null, tint = c.accent,
                    modifier = Modifier.size(26.dp).align(Alignment.CenterStart)
                        .offset { IntOffset(((x - 13.dp).toPx()).roundToInt(), 0) }
                        .alpha(iconAlpha),
                )
            }
        }

        // ---- the "left today" pill moves in beside the circle
        if (t > 0.01f && pillW > 0.dp) {
            val slide = with(density) { ((1f - t) * 40.dp.toPx()).roundToInt() }
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = CircleSize + Gap)
                    .offset { IntOffset(slide, 0) }
                    .graphicsLayer {
                        alpha = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f) // comes in after the tabs have faded
                        scaleX = 0.9f + 0.1f * t; scaleY = 0.9f + 0.1f * t
                    },
            ) { TodayPillView(today, onExpand, Modifier.width(pillW)) }
        }
    }
}

/** Middle pill of the shrunk bar: tiny ring + "₹2,903 left today" (like Apple Music's mini player). */
@Composable
private fun TodayPillView(today: TodayPill?, onClick: () -> Unit, modifier: Modifier) {
    val c = AppColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.height(CircleSize).pressScale(interaction).liquidGlass(CapsuleShape)
            .clickable(interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (today == null) {
            Text("Show tabs", style = MaterialTheme.typography.titleSmall, color = c.label)
            return@Row
        }
        val ring = when {
            today.over -> c.dangerFill
            today.progress < 0.2f -> c.warningFill
            else -> c.positiveFill
        }
        Canvas(Modifier.size(30.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(c.fill2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(ring, -90f, 360f * today.progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(today.leftText, style = MoneyStyle.Row, color = if (today.over) c.danger else c.label, maxLines = 1)
            Text(today.caption, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
