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

/** Colours with a meaning that Material's scheme has no slot for. Read them with [AppColors.current]. */
@Immutable
data class AppColors(
    /** Good state (same hue as the brand colour). */
    val positive: Color,
    /** Close to the limit (80% of today's allowance used). */
    val warning: Color,
    /** Over the limit, delete, errors. */
    val danger: Color,
    /** Money that came in. */
    val credit: Color,
    /** True in dark theme. */
    val isDark: Boolean,
) {
    companion object {
        /** The colours of the current theme. */
        val current: AppColors
            @Composable @ReadOnlyComposable get() = LocalAppColors.current
    }
}

val LocalAppColors = staticCompositionLocalOf {
    AppColors(EmeraldLight, WarningLight, DangerLight, CreditLight, false)
}

private val LightScheme = lightColorScheme(
    primary = EmeraldLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F0E2),
    onPrimaryContainer = Color(0xFF053D28),
    secondary = Color(0xFF3F6B58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F0E2),
    onSecondaryContainer = Color(0xFF053D28),
    tertiary = WarningLight,
    background = LightBackground,
    onBackground = LightText,
    surface = LightBackground,
    onSurface = LightText,
    surfaceVariant = LightContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = EmeraldLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightCard,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = Color(0xFFE7ECE9),
    surfaceContainerHighest = Color(0xFFE0E6E3),
    outline = Color(0xFF7D8982),
    outlineVariant = LightOutlineVariant,
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFFADBDB),
    onErrorContainer = Color(0xFF5C1111),
    inverseSurface = Color(0xFF2B332E),
    inverseOnSurface = Color(0xFFEEF2F0),
    inversePrimary = EmeraldDark,
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF14503A),
    onPrimaryContainer = Color(0xFFC9F4E0),
    secondary = Color(0xFF9CCDB6),
    onSecondary = Color(0xFF0B3626),
    secondaryContainer = Color(0xFF1F3A2F),
    onSecondaryContainer = Color(0xFFCDEBDD),
    tertiary = WarningDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkBackground,
    onSurface = DarkText,
    surfaceVariant = DarkContainer,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = EmeraldDark,
    surfaceContainerLowest = Color(0xFF090D0B),
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = Color(0xFF252D29),
    surfaceContainerHighest = DarkOutlineVariant,
    outline = Color(0xFF6B7770),
    outlineVariant = DarkOutlineVariant,
    error = DangerDark,
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF5A2020),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = DarkText,
    inverseOnSurface = Color(0xFF17201B),
    inversePrimary = EmeraldLight,
    scrim = Color.Black,
)

private val LightApp = AppColors(EmeraldLight, WarningLight, DangerLight, CreditLight, false)
private val DarkApp = AppColors(EmeraldDark, WarningDark, DangerDark, CreditDark, true)

/** App theme. No dynamic colour: the brand stays emerald. */
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
