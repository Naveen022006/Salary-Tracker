package com.local.salarytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Apple-style colours that Material's scheme has no slot for. Read them with [AppColors.current].
 * Text colours ([positive], [warning], [danger]) keep 4.5:1 on cards; the *Fill versions are the bright system
 * colours for rings, bars, icon tiles and filled buttons.
 */
@Immutable
data class AppColors(
    /** systemBlue: buttons, links, selected tab, chart bars. */
    val accent: Color,
    /** Good state / money that came in (text-safe green). */
    val positive: Color,
    /** Close to the limit (text-safe orange). */
    val warning: Color,
    /** Over the limit, delete, errors (text-safe red). */
    val danger: Color,
    /** Money that came in (same as [positive]). */
    val credit: Color,
    val positiveFill: Color,
    val warningFill: Color,
    val dangerFill: Color,
    val label: Color,
    val secondaryLabel: Color,
    /** Decorative only: chevrons, placeholders, disabled. */
    val tertiaryLabel: Color,
    val fill1: Color,
    val fill2: Color,
    val fill3: Color,
    val fill4: Color,
    val separator: Color,
    /** Page background. */
    val grouped: Color,
    /** Card / list section / row. */
    val card: Color,
    /** Something nested inside a card (fields, tiles). */
    val nested: Color,
    /** True in dark theme. */
    val isDark: Boolean,
) {
    companion object {
        /** The colours of the current theme. */
        val current: AppColors
            @Composable @ReadOnlyComposable get() = LocalAppColors.current
    }
}

private val LightApp = AppColors(
    accent = BlueLight, positive = GreenTextLight, warning = OrangeTextLight, danger = Color(0xFFD70015), credit = GreenTextLight,
    positiveFill = GreenLight, warningFill = OrangeLight, dangerFill = RedLight,
    label = LabelLight, secondaryLabel = SecondaryLabelLight, tertiaryLabel = TertiaryLabelLight,
    fill1 = Fill1Light, fill2 = Fill2Light, fill3 = Fill3Light, fill4 = Fill4Light, separator = SeparatorLight,
    grouped = GroupedLight, card = CardLight, nested = NestedLight, isDark = false,
)
private val DarkApp = AppColors(
    accent = BlueDark, positive = GreenDark, warning = OrangeDark, danger = RedDark, credit = GreenDark,
    positiveFill = GreenDark, warningFill = OrangeDark, dangerFill = RedDark,
    label = LabelDark, secondaryLabel = SecondaryLabelDark, tertiaryLabel = TertiaryLabelDark,
    fill1 = Fill1Dark, fill2 = Fill2Dark, fill3 = Fill3Dark, fill4 = Fill4Dark, separator = SeparatorDark,
    grouped = GroupedDark, card = CardDark, nested = NestedDark, isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightApp }

private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = BlueLight,
    secondary = SecondaryLabelLight,
    onSecondary = Color.White,
    secondaryContainer = CardFill3Light,
    onSecondaryContainer = LabelLight,
    tertiary = GreenTextLight,
    onTertiary = Color.White,
    tertiaryContainer = GreenContainerLight,
    onTertiaryContainer = GreenTextLight,
    background = GroupedLight,
    onBackground = LabelLight,
    surface = GroupedLight,
    onSurface = LabelLight,
    surfaceVariant = CardFill3Light,
    onSurfaceVariant = SecondaryLabelLight,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = NestedLight,
    surfaceContainerHigh = CardFill3Light,
    surfaceContainerHighest = CardFill1Light,
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = RedContainerLight,
    onErrorContainer = Color(0xFFD70015),
    inverseSurface = CardDark,
    inverseOnSurface = Color.White,
    inversePrimary = BlueDark,
    scrim = Color(0x66000000),
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = BlueDark,
    secondary = SecondaryLabelDark,
    onSecondary = Color.Black,
    secondaryContainer = CardFill3Dark,
    onSecondaryContainer = LabelDark,
    tertiary = GreenDark,
    onTertiary = Color.Black,
    tertiaryContainer = GreenContainerDark,
    onTertiaryContainer = GreenDark,
    background = GroupedDark,
    onBackground = LabelDark,
    surface = GroupedDark,
    onSurface = LabelDark,
    surfaceVariant = CardFill3Dark,
    onSurfaceVariant = SecondaryLabelDark,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = CardDark,
    surfaceContainer = NestedDark,
    surfaceContainerHigh = CardFill3Dark,
    surfaceContainerHighest = CardFill1Dark,
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
    error = RedDark,
    onError = Color.White,
    errorContainer = RedContainerDark,
    onErrorContainer = RedDark,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color.Black,
    inversePrimary = BlueLight,
    scrim = Color(0x66000000),
)

/** App theme: Apple system colours, Inter, no dynamic colour. */
@Composable
fun SalaryTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides if (darkTheme) DarkApp else LightApp) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Green used for money that came in. */
@Composable
@ReadOnlyComposable
fun creditColor(): Color = LocalAppColors.current.credit
