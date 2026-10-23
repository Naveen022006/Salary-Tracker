package com.local.salarytracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.budget.BudgetEngine
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.ui.components.MoneyText
import com.local.salarytracker.ui.components.SectionCard
import com.local.salarytracker.ui.components.rememberAppTween
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money
import java.time.LocalDate

private const val LAST_STEP = 6
private const val STEPS = LAST_STEP + 1

@Composable
fun OnboardingScreen(vm: AppViewModel, initial: SettingsEntity) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var salaryText by rememberSaveable { mutableStateOf(Money.toInput(initial.salaryPaise)) }
    var payDay by rememberSaveable { mutableIntStateOf(initial.payDay) }
    var goalText by rememberSaveable { mutableStateOf(Money.toInput(initial.savingsGoalPaise)) }
    var hour by rememberSaveable { mutableIntStateOf(initial.reminderHour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.reminderMinute) }
    var rangeName by rememberSaveable { mutableStateOf(ImportRange.M3.name) }
    val range = ImportRange.entries.first { it.name == rangeName }
    val perm = rememberPermHolder()
    val actions = rememberPermActions(perm)
    val importState = vm.importState.collectAsStateWithLifecycle().value
    val running = importState == ImportState.Running

    val salary = Money.parseToPaise(salaryText) ?: 0L
    val goal = Money.parseToPaise(goalText) ?: 0L

    LaunchedEffect(step) { if (step == 5) vm.resetImportState() }

    fun next() { step++ }
    fun saveMoney() = vm.updateSettings { it.copy(salaryPaise = salary, payDay = payDay, savingsGoalPaise = goal) }
    fun saveReminder() = vm.updateSettings { it.copy(reminderHour = hour, reminderMinute = minute) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        // Top: back arrow + segmented progress bar
        Centered {
            Row(Modifier.padding(horizontal = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step in 1 until LAST_STEP) {
                    IconButton(onClick = { step-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                ProgressBar(step, Modifier.weight(1f).padding(end = 16.dp))
            }
        }

        // Content
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            val spec = rememberAppTween<Float>(220)
            AnimatedContent(
                targetState = step,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) },
                label = "step",
            ) { s ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (s) {
                        0 -> WelcomeStep()
                        1 -> {
                            StepTitle("What is your monthly salary?", "Take-home pay, after tax")
                            AmountField(salaryText, { salaryText = it }, fontSize = 40.sp)
                            if (salary > 0) {
                                Text(
                                    Money.format(salary),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MoneyStyle.Card,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        2 -> {
                            StepTitle("Which day do you get your salary?", "Each month starts on your pay day.")
                            PayDayPicker(payDay, { payDay = it })
                            PayMonthCard(payDay)
                        }
                        3 -> SavingsStep(salary, goalText, { goalText = it }, payDay)
                        4 -> {
                            StepTitle("Allow these", "You can skip any of them and change them later in Settings.")
                            PermissionCard(Icons.Filled.Sms, "Bank SMS", "To read bank messages about money you spend.", perm.sms, "Allow", actions.askSms)
                            PermissionCard(
                                Icons.Filled.Notifications, "Payment app alerts",
                                "To see alerts from GPay, PhonePe, Paytm and other apps.",
                                perm.listener, "Allow", actions.openListenerSettings,
                            ) {
                                if (!perm.listener) {
                                    Text(RESTRICTED_HINT, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            PermissionCard(
                                Icons.Filled.NotificationsActive, "Evening reminder",
                                "To remind you each evening to add cash spending.",
                                perm.post, "Allow", actions.askPost,
                            ) {
                                OutlinedButton(
                                    onClick = { showTimePicker(ctx, hour, minute) { h, m -> hour = h; minute = m } },
                                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reminder time: " + Fmt.hourMinute(hour, minute))
                                }
                            }
                        }
                        5 -> {
                            StepTitle("Import old SMS?", "Fill in past spending from your bank messages. It all stays on this phone.")
                            ImportPanel(range, { rangeName = it.name }, importState, perm.sms, actions.askSms)
                        }
                        else -> DoneStep(salary, goal, payDay)
                    }
                }
            }
        }

        // Bottom: sticky primary button (+ Skip)
        Surface(color = MaterialTheme.colorScheme.background) {
            Centered {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val importDone = importState is ImportState.Done
                    val enabled = when (step) {
                        1 -> salary > 0
                        5 -> if (importDone) true else perm.sms && !running
                        else -> true
                    }
                    Button(
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        onClick = {
                            when (step) {
                                3 -> { saveMoney(); next() }
                                4 -> { saveReminder(); next() }
                                5 -> if (importDone) next() else vm.runImport(range)
                                LAST_STEP -> {
                                    vm.setTrackingStart(LocalDate.now())
                                    val now = System.currentTimeMillis()
                                    vm.updateSettings(reschedule = true) {
                                        it.copy(onboardingDone = true, lastSmsScanTime = if (it.lastSmsScanTime == 0L) now else it.lastSmsScanTime)
                                    }
                                }
                                else -> next()
                            }
                        },
                    ) {
                        Text(
                            when (step) {
                                0 -> "Get started"
                                5 -> if (importDone) "Next" else if (running) "Reading SMS..." else "Import"
                                LAST_STEP -> "Open my budget"
                                else -> "Next"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if ((step == 4 || step == 5) && !(step == 5 && (importDone || running))) {
                        TextButton(
                            onClick = { if (step == 4) saveReminder(); next() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Skip") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), content = content)
    }
}

@Composable
private fun ProgressBar(step: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Step ${step + 1} of $STEPS" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until STEPS) {
            val on by animateFloatAsState(if (i <= step) 1f else 0f, rememberAppTween(300), label = "seg")
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(Modifier.fillMaxWidth(on).height(4.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, helper: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold))
        if (helper != null) Text(helper, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WelcomeStep() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("₹", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            "Know what you can spend today",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(4.dp))
    }
    Benefit(Icons.Filled.AutoAwesome, "Automatic tracking", "Reads bank SMS and payment alerts for you.")
    Benefit(Icons.Filled.CalendarToday, "Daily budget", "Your salary becomes an amount for each day.")
    Benefit(Icons.Filled.Lock, "Private", "Stays on this phone. No internet.")
}

@Composable
private fun Benefit(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavingsStep(salary: Long, goalText: String, onGoal: (String) -> Unit, payDay: Int) {
    val goal = Money.parseToPaise(goalText) ?: 0L
    StepTitle("Save some money every month?", "This is optional. The app keeps this money aside and budgets the rest.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, pct) in listOf("None" to 0, "10%" to 10, "20%" to 20, "30%" to 30)) {
            val amount = salary * pct / 100
            FilterChip(
                selected = goal == amount,
                onClick = { onGoal(Money.toInput(amount)) },
                label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
            )
        }
    }
    OutlinedTextField(
        value = goalText,
        onValueChange = { onGoal(Money.cleanInput(it)) },
        label = { Text("Or type an amount (₹)") },
        singleLine = true,
        textStyle = MoneyStyle.Row,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    val spendable = (salary - goal).coerceAtLeast(0)
    val days = BudgetEngine.currentPeriod(LocalDate.now(), payDay).days
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(
            "You can spend ${Money.formatWhole(spendable)} a month · about ${Money.formatWhole(spendable / days)} a day",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DoneStep(salary: Long, goal: Long, payDay: Int) {
    val today = LocalDate.now()
    val period = BudgetEngine.currentPeriod(today, payDay)
    val daily = BudgetEngine.compute(period, today, salary, goal, emptyList(), today).todayAllowancePaise
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Text(
            "You are all set",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        )
    }
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your daily budget", style = MaterialTheme.typography.labelMedium)
            MoneyText(daily, style = MoneyStyle.Hero, textAlign = TextAlign.Center)
            Text(Fmt.period(period), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(
        "New bank SMS and payment alerts will show up by themselves. Use \"Add cash\" for cash spending.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
