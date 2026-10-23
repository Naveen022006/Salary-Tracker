package com.local.salarytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.salarytracker.model.Category
import com.local.salarytracker.ui.theme.AppColors

/** The Material icon for a category (the spec's mapping). */
val Category.icon: ImageVector
    get() = when (this) {
        Category.FOOD -> Icons.Filled.Restaurant
        Category.GROCERIES -> Icons.Filled.ShoppingCart
        Category.TRAVEL -> Icons.Filled.DirectionsCar
        Category.BILLS -> Icons.AutoMirrored.Filled.ReceiptLong
        Category.RENT_EMI -> Icons.Filled.Home
        Category.SHOPPING -> Icons.Filled.ShoppingBag
        Category.HEALTH -> Icons.Filled.LocalHospital
        Category.ENTERTAINMENT -> Icons.Filled.Movie
        Category.EDUCATION -> Icons.Filled.School
        Category.PEOPLE -> Icons.Filled.Person
        Category.CASH -> Icons.Filled.Payments
        Category.TRANSFER -> Icons.Filled.SwapHoriz
        Category.OTHER -> Icons.Filled.Category
    }

/** Name to show for a category in the UI ("Not spending" for the transfer category). */
val Category.label: String get() = if (this == Category.TRANSFER) "Not spending" else displayName

/**
 * Category icon on a circle of the category colour at 16% (light) / 22% (dark) alpha. Use 40.dp in lists, 48.dp in pickers.
 * [credit] shows the "money received" icon in teal instead.
 */
@Composable
fun CategoryIcon(category: Category, size: Dp = 40.dp, modifier: Modifier = Modifier, credit: Boolean = false) {
    val dark = AppColors.current.isDark
    // Dark theme: lift the category colour 15% toward white so it keeps 3:1 on its 22% circle.
    val tint = if (credit) AppColors.current.credit else Color(category.color).let { if (dark) lerp(it, Color.White, 0.15f) else it }
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (dark) 0.22f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (credit) Icons.Filled.SouthWest else category.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
