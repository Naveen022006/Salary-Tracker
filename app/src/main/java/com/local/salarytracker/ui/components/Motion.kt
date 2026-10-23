package com.local.salarytracker.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True when the user turned animations off in system settings (reduced motion). */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/** A tween of [millis] that becomes instant when the user prefers reduced motion. */
@Composable
fun <T> rememberAppTween(millis: Int = 600): FiniteAnimationSpec<T> {
    val reduced = rememberReducedMotion()
    return tween(if (reduced) 0 else millis)
}
