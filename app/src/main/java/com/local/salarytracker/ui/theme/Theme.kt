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
    /** Good state (teal). */
    val positive: Color,
    /** Close to the limit (80% of today's allowance used). */
    val warning: Color,
    /** Over the limit, delete, errors. */
    val danger: Color,
    /** Money that came in (teal). */
    val credit: Color,
    /** Saffron highlight: FAB, allowance line, today markers, hero ring when low. */
    val accent: Color,
    /** Hero card gradient start / end. */
    val heroStart: Color,
    val heroEnd: Color,
    /** Text and icons on the hero gradient. */
    val onHero: Color,
    /** Saffron on the hero gradient (low-left ring, rupee mark) and the coral for over-budget on it. */
    val heroAccent: Color,
    val heroOver: Color,
    /** True in dark theme. */
    val isDark: Boolean,
) {
    companion object {
        /** The colours of the current theme. */
        val current: AppColors
            @Composable @ReadOnlyComposable get() = LocalAppColors.current
    }
}

private val LightApp = AppColors(TealLight, WarningLight, DangerLight, TealLight, SaffronLight, HeroStartLight, HeroEndLight, Color.White, SaffronDark, Color(0xFFFCA5A5), false)
private val DarkApp = AppColors(TealDark, WarningDark, DangerDark, TealDark, SaffronDark, HeroStartDark, HeroEndDark, Color.White, SaffronDark, Color(0xFFFCA5A5), true)

val LocalAppColors = staticCompositionLocalOf { LightApp }

private val LightScheme = lightColorScheme(
    primary = IndigoLight,
    onPrimary = Color.White,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoDeep,
    secondary = SaffronLight,
    onSecondary = Color.White,
    secondaryContainer = SaffronContainerLight,
    onSecondaryContainer = OnSaffronContainerLight,
    tertiary = WarningLight,
    background = LightBackground,
    onBackground = LightText,
    surface = LightBackground,
    onSurface = LightText,
    surfaceVariant = LightContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = IndigoLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightCard,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF5C1111),
    inverseSurface = Color(0xFF24273D),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = IndigoDark,
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = IndigoDeep,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoContainerLight,
    secondary = SaffronDark,
    onSecondary = Color(0xFF3D2E0B),
    secondaryContainer = SaffronContainerDark,
    onSecondaryContainer = OnSaffronContainerDark,
    tertiary = WarningDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkBackground,
    onSurface = DarkText,
    surfaceVariant = DarkContainer,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = IndigoDark,
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DangerDark,
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF5A2020),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = DarkText,
    inverseOnSurface = Color(0xFF16182B),
    inversePrimary = IndigoLight,
    scrim = Color.Black,
)

/** App theme. No dynamic colour: the brand stays indigo/saffron. */
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

/** Teal used for money that came in. */
@Composable
@ReadOnlyComposable
fun creditColor(): Color = LocalAppColors.current.credit
