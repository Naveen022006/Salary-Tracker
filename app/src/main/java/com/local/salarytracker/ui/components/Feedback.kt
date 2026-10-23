package com.local.salarytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.local.salarytracker.ui.theme.AppColors

/** Colour family of an [InfoBanner]. */
enum class BannerTone { Info, Positive, Warning, Danger }

/** Centred icon + [title] + [text] for an empty list. [action] is an optional button. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconCircle(icon)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
private fun IconCircle(icon: ImageVector) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
    }
}

/** Rounded message strip with an [icon] and [text]; [action] (for example a button) goes under the text. */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AppColors.current
    val (container, content) = when (tone) {
        BannerTone.Info -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        BannerTone.Positive -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BannerTone.Warning -> colors.warning.copy(alpha = 0.16f) to MaterialTheme.colorScheme.onSurface
        BannerTone.Danger -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val iconTint = when (tone) {
        BannerTone.Warning -> colors.warning
        BannerTone.Danger -> colors.danger
        else -> content
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = container, contentColor = content) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (action != null) action()
            }
        }
    }
}

/** Small pill: green "Allowed" with a tick when [ok], amber "Needs permission" when not. Labels can be changed. */
@Composable
fun StatusChip(
    ok: Boolean,
    modifier: Modifier = Modifier,
    okLabel: String = "Allowed",
    neededLabel: String = "Needs permission",
) {
    val colors = AppColors.current
    val tint = if (ok) colors.positive else colors.warning
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.ErrorOutline, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (ok) okLabel else neededLabel, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}
