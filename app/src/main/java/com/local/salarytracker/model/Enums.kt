package com.local.salarytracker.model

/** Direction of money. */
enum class TxnType { DEBIT, CREDIT }

/** Where a transaction came from. */
enum class TxnSource { SMS, NOTIFICATION, MANUAL }

/**
 * Spending category. [color] is a stable ARGB value (0xAARRGGBB as Long).
 * [isFixed] categories shrink the month pool but never count as "overspent today".
 */
enum class Category(
    val displayName: String,
    val emoji: String,
    val color: Long,
    val isFixed: Boolean = false,
) {
    FOOD("Food", "🍔", 0xFFEF6C00),
    GROCERIES("Groceries", "🛒", 0xFF43A047),
    TRAVEL("Travel & Fuel", "🚗", 0xFF1E88E5),
    BILLS("Bills & Recharge", "💡", 0xFFFDD835, true),
    RENT_EMI("Rent / EMI", "🏠", 0xFF6D4C41, true),
    SHOPPING("Shopping", "🛍️", 0xFF8E24AA),
    HEALTH("Health", "💊", 0xFFE53935),
    ENTERTAINMENT("Entertainment", "🎬", 0xFFD81B60),
    EDUCATION("Education", "🎓", 0xFF3949AB),
    PEOPLE("People", "👤", 0xFF00ACC1),
    CASH("Cash / ATM", "💵", 0xFF7CB342),
    TRANSFER("Transfer", "🔁", 0xFF90A4AE),
    OTHER("Other", "📦", 0xFF757575);

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
