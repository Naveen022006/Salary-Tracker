package com.local.salarytracker.ui

import com.local.salarytracker.ui.components.AppWallpaper
import com.local.salarytracker.ui.components.CapsuleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.R
import com.local.salarytracker.cloud.CloudConfig
import com.local.salarytracker.cloud.LoginState
import com.local.salarytracker.ui.components.BannerTone
import com.local.salarytracker.ui.components.InfoBanner
import com.local.salarytracker.ui.theme.AppColors

/** First screen for everyone who is not signed in. Google sign-in is required. */
@Composable
fun LoginScreen(vm: AppViewModel) {
    val hero = AppColors.current
    val login = vm.account.login.collectAsStateWithLifecycle().value
    val ctx = LocalContext.current
    val configured = CloudConfig.isConfigured
    val working = login == LoginState.Working

    Box(Modifier.fillMaxSize()) {
        AppWallpaper(Modifier.fillMaxSize(), strong = true)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppMark(96.dp)
                Text(
                    "Salary Tracker",
                    style = MaterialTheme.typography.headlineLarge,
                    color = hero.label,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Know what you can spend today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = hero.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))

            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!configured) {
                    InfoBanner(
                        icon = Icons.Filled.Info,
                        text = "Cloud sign-in is not set up in this build. See docs/SUPABASE_GOOGLE_SETUP.md.",
                        tone = BannerTone.Info,
                    )
                }
                if (login is LoginState.Error) {
                    Text(
                        login.text,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = hero.danger,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = { vm.account.signIn(ctx) },
                    enabled = configured && !working,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = CapsuleShape,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, hero.separator),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = hero.card,
                        contentColor = hero.label,
                        disabledContainerColor = hero.card.copy(alpha = 0.6f),
                        disabledContentColor = hero.label.copy(alpha = 0.5f),
                    ),
                ) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = hero.label)
                        Spacer(Modifier.size(12.dp))
                        Text("Signing in...", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    "Your data is saved in your own private account. SMS text never leaves your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = hero.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Spinner shown while the account hand-over runs (first download, sign out). */
@Composable
fun AccountLoading(text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** The first download failed (usually no internet). */
@Composable
fun AccountLoadError(text: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = AppColors.current.danger, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)) { Text("Try again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)) { Text("Sign out") }
    }
}

/** Another account's data is on this phone. */
@Composable
fun WipeConfirmDialog(onRemove: () -> Unit, onSignOut: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Different account") },
        text = { Text("This phone has data from another account. Remove it from this phone and load yours?") },
        confirmButton = { TextButton(onClick = onRemove) { Text("Remove and load mine", color = AppColors.current.danger) } },
        dismissButton = { TextButton(onClick = onSignOut) { Text("Sign out") } },
    )
}

/** App mark: white rupee on a blue rounded square (app-icon style). */
@Composable
fun AppMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.23f))
            .background(Brush.linearGradient(listOf(Color(0xFF3A9BFF), Color(0xFF0060DF)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("₹", fontSize = (size.value * 0.54f).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
