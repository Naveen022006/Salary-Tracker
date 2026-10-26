package com.local.salarytracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IntroBlue = Color(0xFF007AFF)

/**
 * In-app opening intro: the app opens out of its own icon. The blue window and the 288 dp icon canvas
 * (the Android 12 splash icon size without an icon background) match the system splash exactly, so the
 * hand-over is invisible; then the icon springs up to full size while the ring turns once, and the whole
 * thing bursts open into the app. About 1.1 s.
 *
 * Starts only once [started] is true (the system splash has been handed over). [onExitStart] fires when the
 * fade-out begins, so the app content can run its own entrance; [onDone] when the overlay is finished.
 */
@Composable
fun IntroOverlay(started: Boolean, onExitStart: () -> Unit, onDone: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.55f) }
    val overlayAlpha = remember { Animatable(1f) }
    val exitStart by rememberUpdatedState(onExitStart)
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        coroutineScope {
            launch {
                scale.animateTo(
                    1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            launch { iconAlpha.animateTo(1f, tween(200, easing = LinearEasing)) }
            launch { rotation.animateTo(360f, tween(700, easing = FastOutSlowInEasing)) }
        }
        delay(120)
        exitStart.invoke()
        coroutineScope {
            launch { overlayAlpha.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1.8f, tween(320, easing = FastOutSlowInEasing)) }
        }
        done.invoke()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(IntroBlue)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = iconAlpha.value }) {
            Image(
                painterResource(R.drawable.intro_ring), null,
                Modifier.size(288.dp).graphicsLayer { rotationZ = rotation.value },
            )
            Image(painterResource(R.drawable.intro_rupee), null, Modifier.size(288.dp))
        }
    }
}
