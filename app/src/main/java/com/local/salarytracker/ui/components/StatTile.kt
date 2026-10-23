package com.local.salarytracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.local.salarytracker.ui.theme.MoneyStyle

/**
 * Small card with a [label], a big [value], an optional thin progress bar ([progress] 0..1) and a [caption].
 * Put two in a Row with `Modifier.weight(1f)`.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    progress: Float? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    SectionCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MoneyStyle.Card, color = valueColor)
        if (progress != null) {
            val animated by animateFloatAsState(progress.coerceIn(0f, 1f), rememberAppTween(600), label = "tile")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
