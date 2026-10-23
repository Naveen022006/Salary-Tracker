package com.local.salarytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.data.SettingsEntity
import com.local.salarytracker.util.Money

private const val LAST_STEP = 6

@Composable
fun OnboardingScreen(vm: AppViewModel, initial: SettingsEntity) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var salaryText by rememberSaveable { mutableStateOf(Money.toInput(initial.salaryPaise)) }
    var payDay by rememberSaveable { mutableIntStateOf(initial.payDay) }
    var goalText by rememberSaveable { mutableStateOf(Money.toInput(initial.savingsGoalPaise)) }
    var hour by rememberSaveable { mutableIntStateOf(initial.reminderHour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.reminderMinute) }
    var error by remember { mutableStateOf<String?>(null) }
    val perm = rememberPermHolder()
    val actions = rememberPermActions(perm)
    val importState = vm.importState.collectAsStateWithLifecycle().value

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Step ${step + 1} of ${LAST_STEP + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (step) {
                0 -> {
                    Text("Welcome", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "This app turns your salary into a daily budget. It reads your bank SMS and payment alerts, and shows how much you can spend today.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("Your data never leaves this phone. This app has no internet permission.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                1 -> {
                    Text("What is your monthly salary?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = salaryText,
                        onValueChange = { v -> salaryText = Money.cleanInput(v); error = null },
                        label = { Text("Salary (₹)") },
                        placeholder = { Text("e.g. 50000") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Money.parseToPaise(salaryText)?.let { Text(Money.format(it), fontSize = 28.sp, color = MaterialTheme.colorScheme.primary) }
                }
                2 -> {
                    Text("Which day do you get your salary?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    PayDayPicker(payDay) { payDay = it }
                    Text(payPeriodText(payDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Each month starts on your pay day and ends the day before the next one.", style = MaterialTheme.typography.bodyMedium)
                }
                3 -> {
                    Text("Save some money every month?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("This is optional. The app keeps this money aside and budgets the rest.", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { v -> goalText = Money.cleanInput(v) },
                        label = { Text("Savings goal per month (₹)") },
                        placeholder = { Text("e.g. 10000") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val sal = Money.parseToPaise(salaryText) ?: 0L
                    val goal = Money.parseToPaise(goalText) ?: 0L
                    Text("You can spend ${Money.format((sal - goal).coerceAtLeast(0))} each month.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                4 -> {
                    Text("Allow these", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    PermRow("SMS", "To read bank messages about money you spend.", perm.sms, "Allow SMS", actions.askSms)
                    PermRow("Notification access", "To see alerts from GPay, PhonePe, Paytm and other apps. " + RESTRICTED_HINT, perm.listener, "Allow notification access", actions.openListenerSettings)
                    PermRow("Reminders", "To remind you each evening to add cash spending.", perm.post, "Allow reminders", actions.askPost)
                    Text("Reminder time", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showTimePicker(ctx, hour, minute) { h, m -> hour = h; minute = m } }, modifier = Modifier.fillMaxWidth()) {
                        Text(Fmt.hourMinute(hour, minute), fontSize = 20.sp)
                    }
                    Text("You can skip any of these and change them later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                5 -> {
                    Text("Import old SMS?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("The app can read your old bank SMS to fill in past spending. It all stays on this phone.", style = MaterialTheme.typography.bodyLarge)
                    ImportBlock(vm, perm.sms)
                }
                else -> {
                    Text("All done!", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Your daily budget is ready. New bank SMS and payment alerts will appear by themselves. Use \"Add cash\" for cash spending.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 0 && step < LAST_STEP) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).height(54.dp)) { Text("Back") }
            }
            val running = importState == ImportState.Running
            Button(
                enabled = !running,
                modifier = Modifier.weight(2f).height(54.dp),
                onClick = {
                    when (step) {
                        1 -> if (Money.parseToPaise(salaryText) == null) error = "Type your salary" else step++
                        3 -> {
                            val sal = Money.parseToPaise(salaryText) ?: 0L
                            val goal = Money.parseToPaise(goalText) ?: 0L
                            vm.updateSettings { it.copy(salaryPaise = sal, payDay = payDay, savingsGoalPaise = goal) }
                            step++
                        }
                        4 -> {
                            vm.updateSettings { it.copy(reminderHour = hour, reminderMinute = minute) }
                            step++
                        }
                        LAST_STEP -> {
                            vm.setTrackingStart(java.time.LocalDate.now())
                            val now = System.currentTimeMillis()
                            vm.updateSettings(reschedule = true) {
                                it.copy(onboardingDone = true, lastSmsScanTime = if (it.lastSmsScanTime == 0L) now else it.lastSmsScanTime)
                            }
                        }
                        else -> step++
                    }
                },
            ) {
                Text(
                    when (step) {
                        0 -> "Start"
                        4 -> "Next"
                        5 -> if (importState is ImportState.Done) "Next" else "Skip for now"
                        LAST_STEP -> "Open my budget"
                        else -> "Next"
                    },
                    fontSize = 18.sp,
                )
            }
        }
    }
}
