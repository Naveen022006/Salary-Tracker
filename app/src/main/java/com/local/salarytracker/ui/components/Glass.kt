@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package com.local.salarytracker.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.salarytracker.ui.theme.AppColors
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/*
 * Liquid Glass building blocks (docs/DESIGN_GLASS.md, section B).
 *
 * Two kinds of glass:
 * - Blur materials (ultraThin / thin / regular / thick): backdrop blur + tint + light rim. Used for the collapsed
 *   top bar, hero cards and banners. Android 12+ blurs; older phones get the same tint, more opaque.
 * - Refracting glass ([liquidGlass], Haze glass shader): the floating tab bar and round buttons, like Apple's controls.
 *
 * Sources: the app wallpaper ([LocalWallpaperHaze]) and the whole tab content ([LocalContentHaze]); a screen may
 * also provide its own source for its top bar ([LocalScreenHaze]).
 */

/** The wallpaper behind everything (used by glass cards that sit inside the scrolling content). */
val LocalWallpaperHaze = compositionLocalOf<HazeState?> { null }

/** Wallpaper + tab content (used by the floating tab bar and "+" button). */
val LocalContentHaze = compositionLocalOf<HazeState?> { null }

/** The current screen's scrolling content (used by its collapsed top bar). */
val LocalScreenHaze = compositionLocalOf<HazeState?> { null }

enum class GlassMaterial(
    val blur: Dp,
    val lightAlpha: Float,
    val darkAlpha: Float,
    val fallbackAlpha: Float,
    val rimLight: Float,
    val rimDark: Float,
    val noise: Float,
) {
    // No grain: noise turns see-through edges of the blurred content into dotted lines on Android.
    UltraThin(12.dp, 0.35f, 0.35f, 0.72f, 0.45f, 0.10f, 0f),
    Thin(20.dp, 0.55f, 0.55f, 0.86f, 0.60f, 0.14f, 0f),
    Regular(28.dp, 0.70f, 0.65f, 0.93f, 0.70f, 0.16f, 0f),
    Thick(36.dp, 0.86f, 0.82f, 0.97f, 0.80f, 0.18f, 0f),
}

/** True when this phone can blur behind a surface (Android 12+). */
val canBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun glassBase(dark: Boolean): Color = if (dark) Color(0xFF1C1C1E) else Color.White

/** The tint of a material without blur (also used where blur is not possible, e.g. sheets and dialogs). */
@Composable
fun GlassMaterial.solidTint(): Color {
    val dark = AppColors.current.isDark
    return glassBase(dark).copy(alpha = fallbackAlpha)
}

/**
 * Backdrop blur of [state] in [shape] with the material's tint and light rim. Without a state (or before
 * Android 12) it draws the more opaque fallback tint.
 */
@Composable
fun Modifier.glassBlur(material: GlassMaterial, shape: Shape, state: HazeState? = LocalWallpaperHaze.current, rim: Boolean = true): Modifier {
    val dark = AppColors.current.isDark
    val base = glassBase(dark)
    val tint = base.copy(alpha = if (dark) material.darkAlpha else material.lightAlpha)
    val fallback = base.copy(alpha = material.fallbackAlpha)
    val style = remember(material, dark) {
        HazeBlurStyle {
            blurRadius(material.blur)
            noiseFactor(material.noise)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(fallback))
        }
    }
    val blurred = if (state != null && canBlur) {
        this.clip(shape).hazeBlur(HazeInput.Sources(state), style)
    } else {
        this.clip(shape).background(fallback, shape)
    }
    return if (rim) blurred.border(0.75.dp, Color.White.copy(alpha = if (dark) material.rimDark else material.rimLight), shape) else blurred
}

/** A blurred glass panel (hero cards, banners, bars). [elevated] adds the soft material shadow. */
@Composable
fun GlassSurface(
    material: GlassMaterial,
    shape: Shape,
    modifier: Modifier = Modifier,
    state: HazeState? = LocalWallpaperHaze.current,
    elevated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = AppColors.current.isDark
    val shadowed = if (elevated && material != GlassMaterial.UltraThin) {
        val elevation = when (material) {
            GlassMaterial.Thin -> 8.dp
            GlassMaterial.Regular -> 10.dp
            else -> 16.dp
        }
        modifier.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) 0.40f else 0.06f), spotColor = Color.Black.copy(alpha = if (dark) 0.40f else 0.10f))
    } else {
        modifier
    }
    Box(shadowed.glassBlur(material, shape, state), content = content)
}

/**
 * Apple-style refracting glass for floating controls (tab bar, "+" and round header buttons). Falls back to the
 * Thin blur material when the shader is not available.
 */
@Composable
fun Modifier.liquidGlass(shape: RoundedCornerShape, state: HazeState? = LocalContentHaze.current): Modifier {
    val dark = AppColors.current.isDark
    if (state == null || !canBlur) return this.glassBlur(GlassMaterial.Thin, shape, state)
    val style = remember(shape, dark) {
        GlassStyle.regular.then {
            shape(shape)
            // A little frost so small labels stay readable over busy content.
            tint(glassBase(dark).copy(alpha = if (dark) 0.30f else 0.40f))
        }
    }
    return this.hazeGlass(input = HazeInput.Sources(state), style = style)
}

/**
 * The app background: Apple's plain grouped colour (true black in dark, #F2F2F7 in light), no colour glows.
 * Colour comes only from icons, rings, charts and the accent. [strong] is kept for older callers and has no effect.
 */
@Composable
fun AppWallpaper(modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") strong: Boolean = false) {
    Box(modifier.background(AppColors.current.grouped))
}
