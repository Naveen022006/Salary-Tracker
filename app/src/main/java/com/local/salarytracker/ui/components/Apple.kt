package com.local.salarytracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.salarytracker.ui.theme.AppColors

/*
 * Apple-style building blocks (docs/DESIGN_GLASS.md, sections C and E).
 */

val CapsuleShape = RoundedCornerShape(percent = 50)
val SectionShape = RoundedCornerShape(20.dp)
val GlassCardShape = RoundedCornerShape(24.dp)
val FieldShape = RoundedCornerShape(14.dp)

/** Grey upper-case title above a grouped section ("SALARY & BUDGET"). */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AppColors.current.secondaryLabel,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** Small grey text under a grouped section. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.current.secondaryLabel,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

/** Solid grouped inset section (white / #1C1C1E, radius 20). Rows inside draw their own inset separators. */
@Composable
fun GroupedSection(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (header != null) GroupHeader(header)
        Column(Modifier.fillMaxWidth().clip(SectionShape).background(AppColors.current.card), content = content)
        if (footer != null) GroupFooter(footer)
    }
}

/** 0.5dp hairline starting at [start] (the text's leading edge). */
@Composable
fun Hairline(start: Dp = 16.dp, modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = start), thickness = 0.5.dp, color = AppColors.current.separator)
}

/** Apple-Settings icon: white glyph on a coloured rounded square. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 29.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f)) }
}

/** Scale-down press feedback (0.96), skipped when animations are off. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val s by animateFloatAsState(if (pressed && !reduced) 0.96f else 1f, if (pressed) tween(90) else spring(0.6f, 600f), label = "press")
    return this.scale(s)
}

enum class ButtonStyle { Filled, Tinted, Glass, Destructive, Plain }

/**
 * Capsule button. [small] = 36dp inline action (Call, Sync now), otherwise 50dp. [color] overrides the accent
 * for Filled / Tinted (for example green "I got back").
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    icon: ImageVector? = null,
    small: Boolean = false,
    enabled: Boolean = true,
    color: Color? = null,
) {
    val c = AppColors.current
    val base = color ?: if (style == ButtonStyle.Destructive) c.dangerFill else c.accent
    val (bg, fg) = when (style) {
        ButtonStyle.Filled, ButtonStyle.Destructive -> base to Color.White
        ButtonStyle.Tinted -> base.copy(alpha = if (c.isDark) 0.24f else 0.15f) to (color?.let { if (c.isDark) it else textTint(it) } ?: c.accent)
        ButtonStyle.Glass -> Color.Unspecified to c.label
        ButtonStyle.Plain -> Color.Transparent to (color ?: c.accent)
    }
    val interaction = remember { MutableInteractionSource() }
    val height = if (small) 44.dp else 50.dp // 44 = Apple's smallest comfortable tap size
    var m = modifier.pressScale(interaction).heightIn(min = height).clip(CapsuleShape)
    m = if (style == ButtonStyle.Glass) m.glassBlur(GlassMaterial.Thin, CapsuleShape) else m.background(if (enabled) bg else c.fill3)
    Row(
        m.clickable(interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 20.dp, vertical = if (small) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val tint = if (enabled) fg else c.tertiaryLabel
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (small) 18.dp else 20.dp))
        Text(
            text,
            style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A bright system colour darkened for text on a light card (keeps 4.5:1). Unchanged in dark theme. */
@Composable
fun textTint(color: Color): Color = if (AppColors.current.isDark) color else androidx.compose.ui.graphics.lerp(color, Color.Black, 0.30f)

/** Apple switch: green track when on, grey fill when off, white thumb, no icon. */
@Composable
fun appleSwitchColors(): SwitchColors {
    val c = AppColors.current
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = c.positiveFill,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = c.fill1,
        uncheckedBorderColor = Color.Transparent,
        disabledCheckedTrackColor = c.positiveFill.copy(alpha = 0.4f),
        disabledUncheckedTrackColor = c.fill3,
    )
}

/** Apple segmented control: grey capsule track, white (dark: #636366) selected segment. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(c.fill2).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) (if (c.isDark) Color(0xFF636366) else Color.White) else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .then(if (on) Modifier.shadow(3.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.12f)) else Modifier)
                    .clip(RoundedCornerShape(8.dp)).background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Capsule chip (36dp visual, 48dp touch). Selected = accent tint (or [color] tint for category chips).
 */
@Composable
fun AppleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AppColors.current
    val tintBase = color ?: c.accent
    val bg = if (selected) tintBase.copy(alpha = if (c.isDark) 0.28f else 0.15f) else c.fill3
    val fg = if (selected) (if (color != null) (if (c.isDark) color else textTint(color)) else c.accent) else c.label
    Box(modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier.heightIn(min = 36.dp).clip(CapsuleShape).background(bg)
                .then(if (selected) Modifier.border(BorderStroke(1.dp, tintBase.copy(alpha = 0.35f)), CapsuleShape) else Modifier)
                .padding(start = if (leading != null) 8.dp else 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                leading?.invoke()
                Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
                trailing?.invoke()
            }
        }
    }
}

/** Round glass button for headers and floating controls (refracting glass on Android 12+). */
@Composable
fun RoundGlassButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    state: dev.chrisbanes.haze.HazeState? = LocalScreenHaze.current,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(size).pressScale(interaction).glassBlur(GlassMaterial.Thin, CircleShapeRounded, state)
            .clickable(interaction, indication = androidx.compose.material3.ripple(bounded = true), role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides AppColors.current.label) { content() }
    }
}

/** Fully rounded corners as a RoundedCornerShape (the glass shader needs a RoundedCornerShape). */
val CircleShapeRounded = RoundedCornerShape(percent = 50)

/** Content padding for grouped lists (16 side gutter). */
fun gutter(top: Dp = 0.dp, bottom: Dp = 0.dp) = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom)

/** Row of [ColumnScope] helper: a 44dp+ Apple list row with optional leading tile, title/subtitle and trailing. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleColor: Color = AppColors.current.label,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().defaultMinSize(minHeight = if (subtitle != null) 56.dp else 44.dp)
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.current.secondaryLabel)
        }
        trailing?.invoke(this)
    }
}

/** Bottom sheets: 36dp top corners. */
val SheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

/** Apple form field: white (dark #1C1C1E) fill, no outline, blue ring when focused. */
@Composable
fun appleFieldColors(): androidx.compose.material3.TextFieldColors {
    val c = AppColors.current
    return androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedContainerColor = sheetCard(),
        unfocusedContainerColor = sheetCard(),
        disabledContainerColor = sheetCard(),
        errorContainerColor = sheetCard(),
        focusedBorderColor = c.accent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        unfocusedLabelColor = c.secondaryLabel,
        focusedLabelColor = c.accent,
        unfocusedPlaceholderColor = c.tertiaryLabel,
        focusedPlaceholderColor = c.tertiaryLabel,
    )
}

/** Sheet background: grouped grey in light, elevated #1C1C1E in dark (so the sheet stands out from the page). */
@Composable
fun sheetBackground(): Color = if (AppColors.current.isDark) AppColors.current.card else AppColors.current.grouped

/** Fields and tiles inside a sheet: white in light, #2C2C2E in dark. */
@Composable
fun sheetCard(): Color = if (AppColors.current.isDark) AppColors.current.nested else AppColors.current.card

/** Sheets start below the status bar with a small gap, like iOS. */
@Composable
fun Modifier.sheetTopGap(): Modifier = this.then(Modifier.padding(top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp))
