package com.local.salarytracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7F3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F0DA),
    onPrimaryContainer = Color(0xFF06361A),
    secondaryContainer = Color(0xFFE1ECE3),
    background = Color(0xFFFBFDF8),
    surface = Color(0xFFFBFDF8),
    surfaceVariant = Color(0xFFE3EAE2),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DDA93),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF14522A),
    onPrimaryContainer = Color(0xFFCFF3D6),
    secondaryContainer = Color(0xFF2B3A2F),
    background = Color(0xFF101411),
    surface = Color(0xFF101411),
    surfaceVariant = Color(0xFF2C332D),
    errorContainer = Color(0xFF7A1F1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun SalaryTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Green used for money that came in. */
@Composable
fun creditColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
