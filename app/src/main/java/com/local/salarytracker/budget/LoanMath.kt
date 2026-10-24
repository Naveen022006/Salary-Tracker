package com.local.salarytracker.budget

import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.data.PersonEntity
import com.local.salarytracker.model.LoanKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** "You will get" / "You need to pay" for the Lending home card. All values in paise. */
data class LendingTotals(
    /** Sum of the positive balances (people who owe you). */
    val youWillGetPaise: Long,
    /** Sum of the absolute value of the negative balances (people you owe). */
    val youNeedToPayPaise: Long,
    val peopleOwingYou: Int,
    val peopleYouOwe: Int,
)

enum class DueState {
    /** No due date, not a "someone should pay back" entry, or the balance is already settled in that direction. */
    NONE,
    /** Due date is after today. */
    UPCOMING,
    /** Due date is today and still open. */
    DUE_TODAY,
    /** Due date passed and still open. */
    OVERDUE,
}

/** One row of the Lending home list. */
data class PersonBalance(
    val person: PersonEntity,
    /** Positive = they owe you, negative = you owe them, 0 = settled. */
    val balancePaise: Long,
    val entryCount: Int,
    /** occurredAt of the newest entry, or the person's createdAt when there is none. */
    val lastActivityAt: Long,
    val overdueCount: Int,
    val dueTodayCount: Int,
) {
    val isSettled: Boolean get() = balancePaise == 0L
}

/** A due or overdue entry (for the reminder worker). [daysLate] is 0 for "due today", > 0 for overdue. */
data class DueLoan(
    val person: PersonEntity,
    val entry: LoanEntryEntity,
    val state: DueState,
    val daysLate: Int,
    /** The person's balance at the moment of the check (same sign convention as [PersonBalance]). */
    val balancePaise: Long,
)

/**
 * Pure lending maths, no Android code. Sign convention: balance = GAVE - GOT_BACK - BORROWED + PAID_BACK,
 * so a positive balance means the person owes you and a negative one means you owe them.
 * Lending never touches the daily budget (it is not part of TxnEntity).
 */
object LoanMath {
    /** The amount with the sign it has on the balance. */
    fun signed(kind: LoanKind, amountPaise: Long): Long = kind.sign * amountPaise

    /** Balance of one person's entries (deleted entries must already be filtered out). */
    fun balance(entries: List<LoanEntryEntity>): Long = entries.sumOf { signed(it.kind, it.amountPaise) }

    /** Totals over the balances of all people. */
    fun totals(balances: Collection<Long>): LendingTotals {
        var get = 0L
        var pay = 0L
        var owing = 0
        var owe = 0
        for (b in balances) {
            if (b > 0) { get += b; owing++ } else if (b < 0) { pay += -b; owe++ }
        }
        return LendingTotals(get, pay, owing, owe)
    }

    /**
     * Balance after each entry, in time order (occurredAt, then insertion id). Key = entry remoteId.
     * Use it for the "running balance" column of the person timeline.
     */
    fun runningBalances(entries: List<LoanEntryEntity>): Map<String, Long> {
        var sum = 0L
        val out = LinkedHashMap<String, Long>()
        for (e in entries.sortedWith(compareBy<LoanEntryEntity>({ it.occurredAt }, { it.id }))) {
            sum += signed(e.kind, e.amountPaise)
            out[e.remoteId] = sum
        }
        return out
    }

    /** Is the person's balance still open in the direction of this kind? (GAVE: they owe you; BORROWED: you owe them.) */
    fun isOpenInDirection(kind: LoanKind, balancePaise: Long): Boolean = when (kind) {
        LoanKind.GAVE -> balancePaise > 0
        LoanKind.BORROWED -> balancePaise < 0
        LoanKind.GOT_BACK, LoanKind.PAID_BACK -> false
    }

    /**
     * Due state of one entry given the person's CURRENT balance. Only GAVE / BORROWED entries with a due date can be
     * due; they count only while the balance is still open in their direction (so a fully repaid loan is never overdue).
     */
    fun dueState(entry: LoanEntryEntity, personBalancePaise: Long, today: LocalDate): DueState {
        val due = entry.dueLocalDate ?: return DueState.NONE
        if (entry.deleted || !isOpenInDirection(entry.kind, personBalancePaise)) return DueState.NONE
        return when {
            due.isBefore(today) -> DueState.OVERDUE
            due.isEqual(today) -> DueState.DUE_TODAY
            else -> DueState.UPCOMING
        }
    }

    /** Whole days between the due date and [today] (0 when not late). */
    fun daysLate(entry: LoanEntryEntity, today: LocalDate): Int {
        val due = entry.dueLocalDate ?: return 0
        return ChronoUnit.DAYS.between(due, today).coerceAtLeast(0).toInt()
    }

    /** One [PersonBalance] per person, sorted by name (case-insensitive). Entries of unknown people are ignored. */
    fun summarize(people: List<PersonEntity>, entries: List<LoanEntryEntity>, today: LocalDate): List<PersonBalance> {
        val byPerson = entries.groupBy { it.personRemoteId }
        return people.map { p ->
            val mine = byPerson[p.remoteId].orEmpty()
            val bal = balance(mine)
            val states = mine.map { dueState(it, bal, today) }
            PersonBalance(
                person = p,
                balancePaise = bal,
                entryCount = mine.size,
                lastActivityAt = mine.maxOfOrNull { it.occurredAt } ?: p.createdAt,
                overdueCount = states.count { it == DueState.OVERDUE },
                dueTodayCount = states.count { it == DueState.DUE_TODAY },
            )
        }.sortedBy { it.person.name.lowercase() }
    }

    /** Due-today and overdue entries (not upcoming ones) for the given people, most overdue first. */
    fun dueLoans(people: List<PersonEntity>, entries: List<LoanEntryEntity>, today: LocalDate): List<DueLoan> {
        val byId = people.associateBy { it.remoteId }
        val balances = entries.groupBy { it.personRemoteId }.mapValues { balance(it.value) }
        return entries.mapNotNull { e ->
            val p = byId[e.personRemoteId] ?: return@mapNotNull null
            val bal = balances[e.personRemoteId] ?: 0L
            val st = dueState(e, bal, today)
            if (st == DueState.OVERDUE || st == DueState.DUE_TODAY) DueLoan(p, e, st, daysLate(e, today), bal) else null
        }.sortedWith(compareByDescending<DueLoan> { it.daysLate }.thenBy { it.person.name.lowercase() })
    }

    // ------------------------------------------------------------ input clean-up
    const val NAME_MAX = 80
    const val PHONE_MAX = 20
    const val NOTE_MAX = 500
    const val APP_NAME_MAX = 40
    const val AMOUNT_MAX_PAISE = 100_000_000_000L

    /** Keeps a leading "+" and digits only, at most [PHONE_MAX] characters; null when no digit is left. */
    fun normalizePhone(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        val digits = trimmed.filter { it in '0'..'9' }
        if (digits.isEmpty()) return null
        val plus = if (trimmed.startsWith("+")) "+" else ""
        return (plus + digits).take(PHONE_MAX)
    }
}
