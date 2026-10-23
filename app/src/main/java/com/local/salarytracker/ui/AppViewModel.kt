package com.local.salarytracker.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.salarytracker.Prefs
import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.budget.BudgetSnapshot
import com.local.salarytracker.budget.Period
import com.local.salarytracker.budget.PeriodReport
import com.local.salarytracker.capture.InboxScanner
import com.local.salarytracker.data.AppDatabase
import com.local.salarytracker.data.CategoryRuleEntity
import com.local.salarytracker.data.Repository
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.data.TxnEntity
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.NotificationParser
import com.local.salarytracker.parse.SmsParser
import com.local.salarytracker.toSpendItem
import com.local.salarytracker.work.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ImportRange(val label: String, val periodsBack: Int?) {
    M3("Last 3 months", 3),
    M6("Last 6 months", 6),
    M12("Last 12 months", 12),
    ALL("All SMS", null),
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data class Done(val expenses: Int, val permissionMissing: Boolean) : ImportState
}

data class EditorRequest(val txnId: Long?)

data class TodayState(
    val settings: SettingsEntity,
    val today: LocalDate,
    val snapshot: BudgetSnapshot,
    val todayTxns: List<TxnEntity>,
    val salaryCredit: TxnEntity?,
    val trackingStart: LocalDate?,
)

data class HistoryState(val period: Period, val offset: Int, val maxOffset: Int, val txns: List<TxnEntity>)

data class ReportState(val period: Period, val offset: Int, val maxOffset: Int, val report: PeriodReport, val hasData: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app
    val repo = Repository(AppDatabase.get(app))
    val zone: ZoneId = ZoneId.systemDefault()

    /** null while loading */
    val settings: StateFlow<SettingsEntity?> =
        repo.settings.map<SettingsEntity, SettingsEntity?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val settingsNN = settings.filterNotNull()

    /** First day counted for the budget (kept in Prefs, no DB change). null until onboarding is done. */
    val trackingStart = MutableStateFlow(Prefs.trackingStart(app))

    init {
        viewModelScope.launch {
            settingsNN.collect { s ->
                if (s.onboardingDone && trackingStart.value == null) setTrackingStart(LocalDate.now())
            }
        }
    }

    fun setTrackingStart(d: LocalDate) {
        val v = if (d.isAfter(LocalDate.now())) LocalDate.now() else d
        Prefs.setTrackingStart(appCtx, v)
        trackingStart.value = v
    }
    val today = MutableStateFlow(LocalDate.now())

    val editor = MutableStateFlow<EditorRequest?>(null)
    val importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val rules: StateFlow<List<CategoryRuleEntity>> =
        repo.observeRules().map { l -> l.sortedBy { it.keyword } }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refreshDate() {
        today.value = LocalDate.now()
    }

    // ------------------------------------------------------------ Today
    val todayState: StateFlow<TodayState?> = combine(settingsNN, today, trackingStart) { s, t, ts -> Triple(s, t, ts) }
        .flatMapLatest { (s, t, ts) ->
            val p = BudgetEngine.currentPeriod(t, s.payDay)
            repo.observeTxns(p.startMillis(zone), p.endExclusiveMillis(zone)).map { list ->
                val items = list.map { it.toSpendItem(zone) }
                val snap = BudgetEngine.compute(p, t, s.salaryPaise, s.savingsGoalPaise, items, ts)
                val todays = list.filter { dateOf(it) == t }
                val credit = list.filter { it.type == TxnType.CREDIT && !it.excluded && it.amountPaise != s.salaryPaise && isSalary(it) }
                    .maxByOrNull { it.timestamp }
                TodayState(s, t, snap, todays, credit, ts)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun dateOf(t: TxnEntity): LocalDate = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()

    /** The parser flags salary credits but the flag is not stored, so parse the saved text again. */
    private fun isSalary(t: TxnEntity): Boolean {
        val raw = t.rawText ?: return false
        return when (t.source) {
            TxnSource.SMS -> SmsParser.parse(t.sender ?: "", raw)?.isSalary == true
            TxnSource.NOTIFICATION -> NotificationParser.parse(t.sender ?: "", null, raw)?.isSalary == true
            TxnSource.MANUAL -> false
        }
    }

    // ------------------------------------------------------------ period navigation (History + Report)
    val historyOffset = MutableStateFlow(0)
    val reportOffset = MutableStateFlow(0)

    private val maxOffset: StateFlow<Int> = combine(settingsNN, today, repo.observeEarliest()) { s, t, earliest ->
        if (earliest == null) 0 else {
            val ed = Instant.ofEpochMilli(earliest).atZone(zone).toLocalDate()
            var p = BudgetEngine.currentPeriod(t, s.payDay)
            var n = 0
            while (p.start.isAfter(ed) && n < 240) {
                p = BudgetEngine.previousPeriod(p, s.payDay)
                n++
            }
            n
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private fun periodAt(s: SettingsEntity, t: LocalDate, offset: Int): Period {
        var p = BudgetEngine.currentPeriod(t, s.payDay)
        repeat(offset) { p = BudgetEngine.previousPeriod(p, s.payDay) }
        return p
    }

    val historyState: StateFlow<HistoryState?> = combine(settingsNN, today, historyOffset, maxOffset) { s, t, o, m ->
        arrayOf<Any>(s, t, o, m)
    }.flatMapLatest { a ->
        val s = a[0] as SettingsEntity
        val t = a[1] as LocalDate
        val o = (a[2] as Int).coerceAtLeast(0)
        val m = a[3] as Int
        val p = periodAt(s, t, o)
        repo.observeTxns(p.startMillis(zone), p.endExclusiveMillis(zone)).map { HistoryState(p, o, m, it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val reportState: StateFlow<ReportState?> = combine(settingsNN, today, reportOffset, maxOffset, trackingStart) { s, t, o, m, ts ->
        arrayOf<Any?>(s, t, o, m, ts)
    }.flatMapLatest { a ->
        val s = a[0] as SettingsEntity
        val t = a[1] as LocalDate
        val o = (a[2] as Int).coerceAtLeast(0)
        val m = a[3] as Int
        val p = periodAt(s, t, o)
        repo.observeTxns(p.startMillis(zone), p.endExclusiveMillis(zone)).map { list ->
            val rep = BudgetEngine.report(p, t, s.salaryPaise, s.savingsGoalPaise, list.map { it.toSpendItem(zone) }, a[4] as LocalDate?)
            ReportState(p, o, m, rep, list.isNotEmpty())
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------ settings
    fun updateSettings(reschedule: Boolean = false, transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch {
            repo.updateSettings(transform)
            if (reschedule) ReminderScheduler.sync(appCtx, replace = true)
        }
    }

    fun setReminderEnabled(on: Boolean) {
        Prefs.setReminderEnabled(appCtx, on)
        ReminderScheduler.sync(appCtx, replace = true)
    }

    fun deleteRule(keyword: String) {
        viewModelScope.launch { repo.deleteRule(keyword) }
    }

    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteAllTxns()
            repo.deleteAllRules()
            onDone()
        }
    }

    // ------------------------------------------------------------ entries
    fun openAddCash() {
        editor.value = EditorRequest(null)
    }

    fun openEntry(id: Long) {
        editor.value = EditorRequest(id)
    }

    fun closeEditor() {
        editor.value = null
    }

    fun addEntry(amountPaise: Long, category: Category, text: String?, timestamp: Long, excluded: Boolean) {
        viewModelScope.launch {
            val e = repo.addManual(amountPaise, category, text, timestamp)
            val name = text?.trim()?.takeIf { it.isNotEmpty() }
            if (excluded || name != null) {
                repo.updateTxn(
                    e.copy(
                        merchant = name,
                        note = null,
                        excluded = excluded,
                        category = if (excluded) Category.TRANSFER else category,
                    ),
                )
            }
        }
    }

    /** Saves [updated]; with [rememberCategory] also saves a "always use this category" rule. */
    fun updateEntry(updated: TxnEntity, rememberCategory: Category?) {
        viewModelScope.launch {
            repo.updateTxn(updated)
            if (rememberCategory != null) repo.setCategory(updated, rememberCategory, true)
        }
    }

    fun deleteEntry(t: TxnEntity) {
        viewModelScope.launch { repo.deleteTxn(t) }
    }

    // ------------------------------------------------------------ SMS import
    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(appCtx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun runImport(range: ImportRange) {
        if (importState.value == ImportState.Running) return
        importState.value = ImportState.Running
        viewModelScope.launch {
            val s = repo.getSettings()
            val since = range.periodsBack?.let { BudgetEngine.importStartMillis(LocalDate.now(), s.payDay, zone, it) } ?: 0L
            val r = InboxScanner(appCtx).scan(since)
            importState.value = ImportState.Done(r.expenses, r.permissionMissing)
        }
    }

    fun resetImportState() {
        importState.value = ImportState.Idle
    }

    /** On app open: import SMS that arrived since the last scan. */
    fun catchUp() {
        viewModelScope.launch {
            val s = repo.getSettings()
            if (!s.onboardingDone || !s.smsEnabled || s.lastSmsScanTime <= 0 || !hasSmsPermission()) return@launch
            InboxScanner(appCtx).scan(s.lastSmsScanTime)
        }
    }
}
