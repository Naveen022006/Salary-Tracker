package com.local.salarytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.local.salarytracker.ui.theme.AppColors

/** Tile colour for [SettingRow]s inside a settings group (Apple Settings: one colour per group). */
val LocalSettingTint = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.graphics.Color?> { null }

/** Start of the text in a [SettingRow] (16 + 29 tile + 12): use it for separators. */
val SettingTextStart = 57.dp

/**
 * Apple Settings row: white icon on a coloured rounded tile, [title], [value] as small grey text under it, and a
 * [trailing] slot (a Switch, a StatusChip, ...). With [onClick] and no trailing, a grey chevron is shown.
 * [danger] makes the tile and title red.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AppColors.current
    val tile = if (danger) c.dangerFill else LocalSettingTint.current ?: c.accent
    val base = modifier.fillMaxWidth().defaultMinSize(minHeight = if (value != null) 56.dp else 48.dp)
    val clickable = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Row(clickable.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tile)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) c.danger else c.label)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.tertiaryLabel)
        }
    }
}
