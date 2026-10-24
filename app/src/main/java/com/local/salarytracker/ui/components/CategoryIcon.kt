package com.local.salarytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Apple system colour of a category in the current theme (dark theme uses the brighter dark variants). */
@Composable
fun Category.tint(): Color = if (AppColors.current.isDark) Color(DarkCategoryColors.getValue(this)) else Color(color)

private val DarkCategoryColors: Map<Category, Long> = mapOf(
    Category.FOOD to 0xFFFF453A, Category.GROCERIES to 0xFF30D158, Category.TRAVEL to 0xFF64D2FF,
    Category.BILLS to 0xFFFFD60A, Category.RENT_EMI to 0xFF5E5CE6, Category.SHOPPING to 0xFFFF375F,
    Category.HEALTH to 0xFF63E6E2, Category.ENTERTAINMENT to 0xFFBF5AF2, Category.EDUCATION to 0xFF0A84FF,
    Category.PEOPLE to 0xFFFF9F0A, Category.CASH to 0xFF40C8E0, Category.TRANSFER to 0xFF98989D,
    Category.OTHER to 0xFFAC8E68,
)

/**
 * Category icon as an Apple-style tile: white glyph on a rounded square of the category colour.
 * Use 40.dp in lists, 48.dp in pickers. [credit] shows the "money received" icon on green instead.
 */
@Composable
fun CategoryIcon(category: Category, size: Dp = 40.dp, modifier: Modifier = Modifier, credit: Boolean = false, custom: String? = null) {
    if (custom != null && !credit) {
        val look = customLook(custom)
        Box(
            modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(Color(look.color)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                look.emoji,
                fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.5f).toSp() },
            )
        }
        return
    }
    val fill = if (credit) AppColors.current.positiveFill else category.tint()
    // Yellow and mint are light: a dark glyph keeps the icon readable on them in light theme.
    val light = !AppColors.current.isDark && !credit && (category == Category.BILLS || category == Category.HEALTH)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (credit) Icons.Filled.SouthWest else category.icon,
            contentDescription = null,
            tint = if (light) Color(0xFF3A3A3C) else Color.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** The user's own categories by lower-case name (provided by MainScreen). */
val LocalCustomCategories = androidx.compose.runtime.compositionLocalOf<Map<String, com.local.salarytracker.data.CustomCategoryEntity>> { emptyMap() }

/** Look (emoji, colour) of an own category; a default look when it came from another phone. */
@Composable
fun customLook(name: String): com.local.salarytracker.data.CustomCategoryEntity =
    LocalCustomCategories.current[name.lowercase()] ?: com.local.salarytracker.data.CustomCategories.fallback(name)

/** Name shown for an entry's category: its own category name, else the built-in label. */
fun categoryName(category: Category, custom: String?): String = custom ?: category.label

/** Colour of an entry's category (own category colour, else the built-in tint). */
@Composable
fun categoryTint(category: Category, custom: String?): Color = if (custom != null) Color(customLook(custom).color) else category.tint()
