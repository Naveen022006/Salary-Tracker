package com.local.salarytracker

import com.local.salarytracker.budget.DueState
import com.local.salarytracker.budget.LoanMath
import com.local.salarytracker.data.LoanEntryEntity
import com.local.salarytracker.data.PersonEntity
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanKind.BORROWED
import com.local.salarytracker.model.LoanKind.GAVE
import com.local.salarytracker.model.LoanKind.GOT_BACK
import com.local.salarytracker.model.LoanKind.PAID_BACK
import com.local.salarytracker.model.LoanMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoanMathTest {
    private val today = LocalDate.of(2026, 10, 24)
    private var seq = 0L

    private fun e(
        kind: LoanKind,
        rupees: Long,
        at: Long = 1000L + seq,
        due: String? = null,
        person: String = "p1",
        id: String = "e${++seq}",
    ) = LoanEntryEntity(
        id = seq, remoteId = id, personRemoteId = person, kind = kind, amountPaise = rupees * 100,
        method = LoanMethod.CASH, occurredAt = at, dueDate = due,
    )

    private fun person(id: String, name: String) = PersonEntity(id = 0, remoteId = id, name = name, createdAt = 5)

    @Test fun emptyBalanceIsZero() = assertEquals(0L, LoanMath.balance(emptyList()))

    @Test fun gaveMakesThemOweYou() = assertEquals(200_000L, LoanMath.balance(listOf(e(GAVE, 2000))))

    @Test fun borrowedMakesYouOweThem() = assertEquals(-50_000L, LoanMath.balance(listOf(e(BORROWED, 500))))

    @Test fun partialRepaymentLeavesRemainder() {
        val b = LoanMath.balance(listOf(e(GAVE, 2000), e(GOT_BACK, 500)))
        assertEquals(150_000L, b)
    }

    @Test fun fullRepaymentIsSettled() {
        assertEquals(0L, LoanMath.balance(listOf(e(GAVE, 1000), e(GOT_BACK, 400), e(GOT_BACK, 600))))
    }

    @Test fun mixedDirectionsNet() {
        // gave 1000, borrowed 300 from the same person, paid back 100 of that: 1000 - 300 + 100
        assertEquals(80_000L, LoanMath.balance(listOf(e(GAVE, 1000), e(BORROWED, 300), e(PAID_BACK, 100))))
    }

    @Test fun overRepaymentFlipsDirection() {
        assertEquals(-20_000L, LoanMath.balance(listOf(e(GAVE, 1000), e(GOT_BACK, 1200))))
    }

    @Test fun paidBackReducesWhatYouOwe() {
        assertEquals(-30_000L, LoanMath.balance(listOf(e(BORROWED, 500), e(PAID_BACK, 200))))
    }

    @Test fun totalsSplitPositiveAndNegative() {
        val t = LoanMath.totals(listOf(100_000L, -40_000L, 0L, 25_000L, -10_000L))
        assertEquals(125_000L, t.youWillGetPaise)
        assertEquals(50_000L, t.youNeedToPayPaise)
        assertEquals(2, t.peopleOwingYou)
        assertEquals(2, t.peopleYouOwe)
    }

    @Test fun totalsOfNobodyAreZero() {
        val t = LoanMath.totals(emptyList())
        assertEquals(0L, t.youWillGetPaise)
        assertEquals(0L, t.youNeedToPayPaise)
    }

    @Test fun runningBalanceFollowsTimeNotListOrder() {
        val a = e(GAVE, 1000, at = 100, id = "a")
        val b = e(GOT_BACK, 300, at = 300, id = "b")
        val c = e(BORROWED, 200, at = 200, id = "c")
        val run = LoanMath.runningBalances(listOf(b, a, c)) // newest-first / shuffled input
        assertEquals(100_000L, run["a"])
        assertEquals(80_000L, run["c"])
        assertEquals(50_000L, run["b"])
        assertEquals(listOf("a", "c", "b"), run.keys.toList())
    }

    @Test fun runningBalanceTieBreaksByInsertionId() {
        val a = e(GAVE, 100, at = 5, id = "a").copy(id = 1)
        val b = e(GOT_BACK, 100, at = 5, id = "b").copy(id = 2)
        assertEquals(0L, LoanMath.runningBalances(listOf(b, a))["b"])
    }

    @Test fun overdueWhenDueDatePassedAndStillOwed() {
        val g = e(GAVE, 1000, due = "2026-10-20")
        assertEquals(DueState.OVERDUE, LoanMath.dueState(g, 100_000L, today))
        assertEquals(4, LoanMath.daysLate(g, today))
    }

    @Test fun notOverdueOnceSettled() {
        val g = e(GAVE, 1000, due = "2026-10-20")
        assertEquals(DueState.NONE, LoanMath.dueState(g, 0L, today))
    }

    @Test fun partialRepaymentStaysOverdue() {
        val g = e(GAVE, 1000, due = "2026-10-20")
        val bal = LoanMath.balance(listOf(g, e(GOT_BACK, 400)))
        assertEquals(DueState.OVERDUE, LoanMath.dueState(g, bal, today))
    }

    @Test fun borrowedIsOverdueOnlyWhileYouStillOwe() {
        val b = e(BORROWED, 500, due = "2026-10-01")
        assertEquals(DueState.OVERDUE, LoanMath.dueState(b, -50_000L, today))
        assertEquals(DueState.NONE, LoanMath.dueState(b, 0L, today))
        assertEquals(DueState.NONE, LoanMath.dueState(b, 20_000L, today)) // they owe you now: nothing to pay
    }

    @Test fun gaveDueDateIgnoredWhenBalanceFlippedToYouOwe() {
        assertEquals(DueState.NONE, LoanMath.dueState(e(GAVE, 100, due = "2026-10-01"), -10_000L, today))
    }

    @Test fun dueTodayAndUpcoming() {
        assertEquals(DueState.DUE_TODAY, LoanMath.dueState(e(GAVE, 100, due = "2026-10-24"), 10_000L, today))
        assertEquals(DueState.UPCOMING, LoanMath.dueState(e(GAVE, 100, due = "2026-10-25"), 10_000L, today))
        assertEquals(0, LoanMath.daysLate(e(GAVE, 100, due = "2026-10-25"), today))
    }

    @Test fun entriesWithoutDueDateOrOfReturnKindsAreNeverDue() {
        assertEquals(DueState.NONE, LoanMath.dueState(e(GAVE, 100), 10_000L, today))
        assertEquals(DueState.NONE, LoanMath.dueState(e(GOT_BACK, 100, due = "2026-10-01"), 10_000L, today))
        assertEquals(DueState.NONE, LoanMath.dueState(e(PAID_BACK, 100, due = "2026-10-01"), -10_000L, today))
    }

    @Test fun badDueDateIsTreatedAsNone() {
        assertNull(e(GAVE, 100, due = "not-a-date").dueLocalDate)
        assertEquals(DueState.NONE, LoanMath.dueState(e(GAVE, 100, due = "not-a-date"), 10_000L, today))
    }

    @Test fun summarizeBuildsOneRowPerPersonSortedByName() {
        val ps = listOf(person("p2", "zoya"), person("p1", "Anil"), person("p3", "Empty"))
        val es = listOf(
            e(GAVE, 1000, person = "p1", due = "2026-10-01", at = 10),
            e(GOT_BACK, 200, person = "p1", at = 20),
            e(BORROWED, 300, person = "p2", at = 30),
        )
        val rows = LoanMath.summarize(ps, es, today)
        assertEquals(listOf("Anil", "Empty", "zoya"), rows.map { it.person.name })
        assertEquals(80_000L, rows[0].balancePaise)
        assertEquals(1, rows[0].overdueCount)
        assertEquals(20L, rows[0].lastActivityAt)
        assertEquals(0L, rows[1].balancePaise)
        assertEquals(5L, rows[1].lastActivityAt) // no entries: the person's createdAt
        assertEquals(-30_000L, rows[2].balancePaise)
    }

    @Test fun entriesOfUnknownPeopleAreIgnored() {
        val rows = LoanMath.summarize(listOf(person("p1", "A")), listOf(e(GAVE, 100, person = "ghost")), today)
        assertEquals(0L, rows.single().balancePaise)
    }

    @Test fun dueLoansListsOverdueAndTodayMostLateFirst() {
        val ps = listOf(person("p1", "A"), person("p2", "B"), person("p3", "C"))
        val es = listOf(
            e(GAVE, 100, person = "p1", due = "2026-10-23"),
            e(GAVE, 100, person = "p2", due = "2026-10-24"),
            e(GAVE, 100, person = "p3", due = "2026-10-01"),
            e(GAVE, 100, person = "p3", due = "2026-12-01"),
        )
        val due = LoanMath.dueLoans(ps, es, today)
        assertEquals(listOf("C", "A", "B"), due.map { it.person.name })
        assertEquals(DueState.DUE_TODAY, due.last().state)
    }

    @Test fun phoneNormalisation() {
        assertEquals("+919876543210", LoanMath.normalizePhone(" +91 98765-43210 "))
        assertEquals("9876543210", LoanMath.normalizePhone("(987) 654 3210"))
        assertNull(LoanMath.normalizePhone("  "))
        assertNull(LoanMath.normalizePhone("abc"))
        assertNull(LoanMath.normalizePhone(null))
        assertEquals(20, LoanMath.normalizePhone("+" + "1".repeat(40))!!.length)
    }
}
