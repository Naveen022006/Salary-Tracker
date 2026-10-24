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
import androidx.compose.ui.graphics.lerp
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
    Icon(icon, contentDescription = null, tint = AppColors.current.secondaryLabel.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
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
    // Apple style: card with a soft tone tint and a coloured icon tile; text stays label colour (4.5:1).
    val tile = when (tone) {
        BannerTone.Info -> colors.accent
        BannerTone.Positive -> colors.positiveFill
        BannerTone.Warning -> colors.warningFill
        BannerTone.Danger -> colors.dangerFill
    }
    val container = androidx.compose.ui.graphics.lerp(colors.card, tile, if (colors.isDark) 0.16f else 0.08f)
    Surface(modifier.fillMaxWidth(), shape = SectionShape, color = container, contentColor = colors.label) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, tile, size = 32.dp)
            Column(Modifier.weight(1f).padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.label)
                if (action != null) action()
            }
        }
    }
}

/** Small pill: teal "Allowed" with a tick when [ok], amber "Needs permission" when not. Labels can be changed. */
@Composable
fun StatusChip(
    ok: Boolean,
    modifier: Modifier = Modifier,
    okLabel: String = "Allowed",
    neededLabel: String = "Needs permission",
) {
    val colors = AppColors.current
    val tint = if (ok) colors.positive else colors.warning
    // Light theme: darken the label a little so 12sp text keeps 4.5:1 on the tint.
    val textTint = textTint(tint)
    Surface(modifier, shape = CapsuleShape, color = tint.copy(alpha = if (colors.isDark) 0.24f else 0.15f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.ErrorOutline, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (ok) okLabel else neededLabel, style = MaterialTheme.typography.bodySmall, color = textTint, fontWeight = FontWeight.SemiBold)
        }
    }
}
