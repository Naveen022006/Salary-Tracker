package com.local.salarytracker.model

/** Direction of money. */
enum class TxnType { DEBIT, CREDIT }

/** App colour theme choice (stored in Prefs). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    FOOD("Food", "🍔", 0xFFF97316),
    GROCERIES("Groceries", "🛒", 0xFF22A06B),
    TRAVEL("Travel & Fuel", "🚗", 0xFF3B82F6),
    BILLS("Bills & Recharge", "💡", 0xFFEAB308, true),
    RENT_EMI("Rent / EMI", "🏠", 0xFFA16207, true),
    SHOPPING("Shopping", "🛍️", 0xFFC026D3),
    HEALTH("Health", "💊", 0xFFF43F5E),
    ENTERTAINMENT("Movies & fun", "🎬", 0xFFEC4899),
    EDUCATION("Education", "🎓", 0xFF6366F1),
    PEOPLE("People", "👤", 0xFF0EA5E9),
    CASH("Cash / ATM", "💵", 0xFF84CC16),
    TRANSFER("Transfer", "🔁", 0xFF94A3B8),
    OTHER("Other", "📦", 0xFFA8A29E);

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
