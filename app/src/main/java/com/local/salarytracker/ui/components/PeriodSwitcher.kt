package com.local.salarytracker.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Pill with back / next arrows around a period [label] (for example "1 Oct – 31 Oct 2026").
 * Set [canGoForward] = false for the current period. The label cross-fades when it changes.
 */
@Composable
fun PeriodSwitcher(
    label: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = CircleShape,
        color = com.local.salarytracker.ui.theme.AppColors.current.fill3,
        contentColor = com.local.salarytracker.ui.theme.AppColors.current.accent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier month")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Crossfade(targetState = label, animationSpec = rememberAppTween(250), label = "period") { text ->
                    Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, color = com.local.salarytracker.ui.theme.AppColors.current.label)
                }
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later month")
            }
        }
    }
}
