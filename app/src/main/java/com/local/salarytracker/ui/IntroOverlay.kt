package com.local.salarytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.local.salarytracker.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * In-app opening intro: looks identical to the system splash (blue window, 288 dp icon canvas = the
 * Android 12 splash icon size without icon background), then the ring spins and the overlay fades out.
 * Starts only once [started] is true (the system splash has been handed over).
 */
@Composable
fun IntroOverlay(started: Boolean, onExitStart: () -> Unit, onDone: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    val exitStart by rememberUpdatedState(onExitStart)
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        rotation.animateTo(720f, tween(900, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))
        exitStart.invoke()
        coroutineScope {
            launch { alpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1.08f, tween(280, easing = FastOutSlowInEasing)) }
        }
        done.invoke()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color(0xFF007AFF))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            Image(
                painterResource(R.drawable.intro_ring), null,
                Modifier.size(288.dp).graphicsLayer { rotationZ = rotation.value },
            )
            Image(painterResource(R.drawable.intro_rupee), null, Modifier.size(288.dp))
        }
    }
}
