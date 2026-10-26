package com.local.salarytracker.model

/** Direction of money. */
enum class TxnType { DEBIT, CREDIT }

/** App colour theme choice (stored in Prefs). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where a transaction came from. */
enum class TxnSource { SMS, NOTIFICATION, MANUAL }

/**
 * Spending category. [color] is the Apple system colour (light theme, 0xAARRGGBB as Long); the dark-theme
 * version comes from `Category.tint()` in ui/components/CategoryIcon.kt.
 * [isFixed] categories shrink the month pool but never count as "overspent today".
 */
enum class Category(
    val displayName: String,
    val emoji: String,
    val color: Long,
    val isFixed: Boolean = false,
) {
    FOOD("Food", "🍔", 0xFFFF3B30),
    GROCERIES("Groceries", "🛒", 0xFF34C759),
    TRAVEL("Travel & Fuel", "🚗", 0xFF32ADE6),
    BILLS("Bills & Recharge", "💡", 0xFFFFCC00, true),
    RENT_EMI("Rent / EMI", "🏠", 0xFF5856D6, true),
    SHOPPING("Shopping", "🛍️", 0xFFFF2D55),
    HEALTH("Health", "💊", 0xFF00C7BE),
    ENTERTAINMENT("Movies & fun", "🎬", 0xFFAF52DE),
    EDUCATION("Education", "🎓", 0xFF007AFF),
    PEOPLE("People", "👤", 0xFFFF9500),
    CASH("Cash / ATM", "💵", 0xFF30B0C7),
    TRANSFER("Transfer", "🔁", 0xFF8E8E93),
    OTHER("Other", "📦", 0xFFA2845E);

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * What happened in a lending entry. [sign] says how it moves the person's balance
 * (positive balance = they owe you, negative = you owe them).
 */
enum class LoanKind(val label: String, val sign: Int) {
    GAVE("I gave", +1),
    GOT_BACK("I got back", -1),
    BORROWED("I borrowed", -1),
    PAID_BACK("I paid back", +1);

    companion object {
        fun fromName(name: String?): LoanKind? = entries.firstOrNull { it.name == name }
    }
}

/** How the money moved. The payment app (GPay, PhonePe ...) is kept separately in `LoanEntryEntity.appName`. */
enum class LoanMethod(val label: String) {
    CASH("Cash"), UPI("UPI"), BANK("Bank transfer"), CARD("Card"), OTHER("Other");

    companion object {
        fun fromName(name: String?): LoanMethod = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Opening intro animation (see ui/IntroOverlay.kt). Temporary: kept only until one style is chosen. */
