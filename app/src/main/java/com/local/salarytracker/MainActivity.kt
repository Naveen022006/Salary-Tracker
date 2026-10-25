package com.local.salarytracker

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.model.ThemeMode
import com.local.salarytracker.ui.AppRoot
import com.local.salarytracker.ui.AppViewModel
import com.local.salarytracker.ui.IntroOverlay
import com.local.salarytracker.ui.theme.SalaryTrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val motion = motionEnabled()
        val t0 = SystemClock.uptimeMillis()
        val fromNotification = intent?.let {
            it.getBooleanExtra(Extras.OPEN_ADD_CASH, false) || it.getBooleanExtra(Extras.OPEN_REPORT, false) ||
                it.getBooleanExtra(Extras.OPEN_ASK, false) ||
                !it.getStringExtra(Extras.LOAN_PERSON_ID).isNullOrEmpty() || it.getLongExtra(Extras.TXN_ID, -1L) > 0
        } == true
        if (savedInstanceState == null && motion && !fromNotification) vm.showIntro.value = true
        splash.setKeepOnScreenCondition {
            !vm.isFirstScreenReady.value && SystemClock.uptimeMillis() - t0 < SPLASH_CAP_MS
        }
        splash.setOnExitAnimationListener { provider ->
            if (vm.showIntro.value) {
                // The overlay underneath looks identical to the system splash, so the hand-off is invisible.
                provider.remove()
                vm.introStarted.value = true
            } else exitSplash(provider, motion)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Registered here (not in composition) so the contact-picker result is delivered even if the screen was rebuilt meanwhile.
        val phoneLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
            vm.onPhonePicked(r.resultCode == android.app.Activity.RESULT_OK, r.data?.data)
        }
        val contactLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickContact()) { uri ->
            vm.onContactPicked(uri)
        }
        vm.launchPhonePicker = {
            phoneLauncher.launch(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        vm.launchContactPicker = { contactLauncher.launch(null) }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status / navigation bar icons must match the chosen theme, not only the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            val exiting by vm.splashExiting.collectAsStateWithLifecycle()
            val progress by animateFloatAsState(
                targetValue = if (exiting) 1f else 0f,
                animationSpec = if (motion) tween(280, easing = FastOutSlowInEasing) else snap(),
                label = "contentEntrance",
            )
            val rise = with(LocalDensity.current) { 12.dp.toPx() }
            val showIntro by vm.showIntro.collectAsStateWithLifecycle()
            val introStarted by vm.introStarted.collectAsStateWithLifecycle()
            if (showIntro) {
                // White status / navigation bar icons over the blue overlay.
                DisposableEffect(Unit) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    )
                    onDispose {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                        )
                    }
                }
            }
            SalaryTrackerTheme(darkTheme = dark) {
                Box {
                    Box(Modifier.graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * rise
                    }) { AppRoot(vm) }
                    if (showIntro) {
                        IntroOverlay(
                            started = introStarted,
                            onExitStart = { vm.splashExiting.value = true },
                            onDone = { vm.showIntro.value = false },
                        )
                    }
                }
            }
        }
    }

    private fun motionEnabled(): Boolean =
        ValueAnimator.areAnimatorsEnabled() &&
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun exitSplash(provider: SplashScreenViewProvider, motion: Boolean) {
        vm.splashExiting.value = true
        if (!motion) {
            provider.remove()
            return
        }
        val splashView = provider.view
        val icon = try { provider.iconView } catch (_: Throwable) { null }
        val animators = ArrayList<Animator>()
        animators += ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f).apply {
            duration = 220L
            interpolator = LinearInterpolator()
        }
        if (icon != null) {
            animators += ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f),
            ).apply {
                duration = 220L
                interpolator = PathInterpolator(0.4f, 0f, 1f, 1f) // FastOutLinearIn
            }
            animators += ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f).apply {
                duration = 220L
                interpolator = LinearInterpolator()
            }
        }
        AnimatorSet().apply {
            playTogether(animators)
            doOnEnd { provider.remove() }
            start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        vm.launchPhonePicker = null
        vm.launchContactPicker = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDate()
        vm.catchUp()
        vm.syncIfStale()
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        if (i.getBooleanExtra(Extras.OPEN_ADD_CASH, false)) {
            vm.openAddCash()
            i.removeExtra(Extras.OPEN_ADD_CASH)
        }
        if (i.getBooleanExtra(Extras.OPEN_REPORT, false)) {
            vm.settingsOpen.value = false
            vm.tab.value = 3
            i.removeExtra(Extras.OPEN_REPORT)
        }
        if (i.getBooleanExtra(Extras.OPEN_ASK, false)) {
            vm.settingsOpen.value = false
            vm.tab.value = 4
            i.removeExtra(Extras.OPEN_ASK)
        }
        i.getStringExtra(Extras.LOAN_PERSON_ID)?.takeIf { it.isNotEmpty() }?.let {
            vm.openLoanPerson(it)
            i.removeExtra(Extras.LOAN_PERSON_ID)
        }
        val id = i.getLongExtra(Extras.TXN_ID, -1L)
        if (id > 0) {
            vm.openEntry(id)
            i.removeExtra(Extras.TXN_ID)
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

        /** Never hold the splash longer than this while waiting for the first screen's data. */
        const val SPLASH_CAP_MS = 1500L
    }
}
