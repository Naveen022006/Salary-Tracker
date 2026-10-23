package com.local.salarytracker

import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.BudgetSnapshot
import com.local.salarytracker.budget.SpendItem
import com.local.salarytracker.data.Repository
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.data.TxnEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun TxnEntity.toSpendItem(zone: ZoneId) = SpendItem(
    date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate(),
    amountPaise = amountPaise,
    category = category,
    excluded = excluded,
    type = type,
    merchant = merchant,
)

object BudgetService {
    /** Stored tracking start; on the first run of a version that has it, today is stored and used. */
    fun trackingStart(ctx: android.content.Context, s: SettingsEntity, today: LocalDate = LocalDate.now()): LocalDate? {
        Prefs.trackingStart(ctx)?.let { return it }
        if (!s.onboardingDone) return null
        Prefs.setTrackingStart(ctx, today)
        return today
    }

    /** Snapshot for the pay period that contains today. */
    suspend fun snapshotNow(
        ctx: android.content.Context,
        repo: Repository,
        s: SettingsEntity,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): BudgetSnapshot {
        val period = BudgetEngine.currentPeriod(today, s.payDay)
        val txns = repo.getTxns(period.startMillis(zone), period.endExclusiveMillis(zone))
        return BudgetEngine.compute(period, today, s.salaryPaise, s.savingsGoalPaise, txns.map { it.toSpendItem(zone) }, trackingStart(ctx, s, today))
    }
}
