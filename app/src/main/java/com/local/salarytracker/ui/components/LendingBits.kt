package com.local.salarytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.salarytracker.model.Category
import com.local.salarytracker.ui.theme.AppColors

/** Colours for the avatar circles. Taken from the category palette so the app keeps one set of colours. */
private val AvatarPalette = listOf(
    Category.TRAVEL, Category.GROCERIES, Category.SHOPPING, Category.PEOPLE, Category.ENTERTAINMENT,
    Category.EDUCATION, Category.FOOD, Category.HEALTH, Category.CASH, Category.RENT_EMI,
).map { it.color }

/** Same name always gives the same colour. */
internal fun avatarColor(name: String): Color {
    val h = name.trim().lowercase().fold(7) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
    return Color(AvatarPalette[h % AvatarPalette.size])
}

/** Round avatar with the first letter of [name]; the colour comes from the name. */
@Composable
fun PersonAvatar(name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val dark = AppColors.current.isDark
    val base = avatarColor(name)
    val tint = if (dark) lerp(base, Color.White, 0.15f) else base
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (dark) 0.22f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        // Light theme: darken the letter a little so it keeps 4.5:1 on the tinted circle.
        val letter = if (dark) tint else lerp(tint, MaterialTheme.colorScheme.onSurface, 0.35f)
        Text(initial, color = letter, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
    }
}

/** Small tinted pill for balances and due badges. The label is darkened in light theme for contrast. */
@Composable
fun TintPill(text: String, tint: Color, modifier: Modifier = Modifier, strong: Boolean = false) {
    val dark = AppColors.current.isDark
    val textTint = if (dark) tint else lerp(tint, MaterialTheme.colorScheme.onSurface, 0.4f)
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = if (strong) 0.2f else 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = textTint,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Text colour for a tint that is used for small text (darker in light theme). */
@Composable
fun textTintFor(tint: Color): Color =
    if (AppColors.current.isDark) tint else lerp(tint, MaterialTheme.colorScheme.onSurface, 0.4f)

/** Filter / choice chips: the selected one uses primaryContainer like the rest of the app (not the saffron default). */
@Composable
fun appChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
