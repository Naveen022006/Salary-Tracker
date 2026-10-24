package com.local.salarytracker.ui.components

import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.local.salarytracker.ui.theme.AppColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Bottom space the floating tab bar needs (provided by the main shell; 0 elsewhere). */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Opens the Settings page (provided by the main shell; null = no settings button). */
val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }

/** First letter of the account name for the settings button. */
val LocalAccountInitial = compositionLocalOf { "" }

private val BarHeight = 52.dp
private val CollapseDistance = 56.dp

/**
 * Apple-style screen: a large title that scrolls away and a small centred title on a thin glass bar that fades in
 * (Liquid Glass, docs/DESIGN_GLASS.md section E). The content scrolls under the bar and gets the padding for it
 * (top) and for the floating tab bar (bottom): put [content]'s padding into the list's `contentPadding`.
 *
 * [subtitle] shows under the large title. [titleContent] replaces the title (for example a search field) and
 * turns the large title off. Pass the list's [listState] when you have it for an exact collapse.
 * [navigationIcon] and [actions] sit on round glass; the settings button is added when [showSettings] and the
 * shell provides [LocalOpenSettings]. [contentWindowInsets] is ignored (kept for older callers).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets(0),
    subtitle: String? = null,
    largeTitle: Boolean = true,
    listState: LazyListState? = null,
    showSettings: Boolean = true,
    hasNavigation: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val haze = rememberHazeState()
    val large = largeTitle && titleContent == null
    val collapsePx = with(density) { CollapseDistance.toPx() }
    var scrolledPx by remember { mutableFloatStateOf(0f) }
    var largeHeightPx by remember { mutableIntStateOf(0) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                scrolledPx = (scrolledPx - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }
    // How far the content has scrolled under the bar: exact from the list state when given.
    val scroll by remember(listState) {
        derivedStateOf {
            if (listState == null) scrolledPx
            else if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val fraction = if (large) (scroll / collapsePx).coerceIn(0f, 1f) else 1f
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val largeHeight = with(density) { largeHeightPx.toDp() }
    val bottomPad = LocalBottomBarPadding.current
    val openSettings = LocalOpenSettings.current
    // The tab's "+" sits top-right in the header (iOS style), not in the bottom bar.
    val add = if (!hasNavigation && titleContent == null && showSettings) LocalAddAction.current else null
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalScreenHaze provides haze) {
            Box(Modifier.fillMaxSize().hazeSource(haze).nestedScroll(connection)) {
                // An opaque backdrop inside the source, so the bar never blurs see-through pixels.
                AppWallpaper(Modifier.matchParentSize())
                content(PaddingValues(top = statusTop + BarHeight + (if (large) largeHeight else 4.dp), bottom = bottomPad))
            }

            if (large) {
                val shift = with(density) { scroll.coerceAtMost(largeHeightPx.toFloat() + 1f).toInt() }
                Column(
                    Modifier.fillMaxWidth().padding(top = statusTop + BarHeight)
                        .offset { IntOffset(0, -shift) }
                        .alpha(1f - fraction)
                        .onSizeChanged { largeHeightPx = it.height }
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.displaySmall, color = AppColors.current.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.current.secondaryLabel, maxLines = 2)
                    }
                }
            }

            // The bar: thin glass that fades in as the content scrolls under it.
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.matchParentSize().alpha(fraction)
                        .glassBlur(GlassMaterial.Thin, RectangleShape, haze, rim = false),
                ) {
                    HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = AppColors.current.separator.copy(alpha = 0.5f))
                }
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = BarHeight).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasNavigation) {
                        // The screen's own IconButton handles the tap; the circle is only the glass.
                        Box(Modifier.size(44.dp).glassBlur(GlassMaterial.Thin, CircleShapeRounded, haze), contentAlignment = Alignment.Center) { navigationIcon() }
                    } else {
                        Box { navigationIcon() }
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        if (titleContent != null) {
                            titleContent()
                        } else {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = AppColors.current.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.alpha(fraction),
                            )
                        }
                    }
                    // Action buttons share one frosted glass capsule, like iOS.
                    if (actions != null || add != null) {
                        Row(
                            Modifier.glassBlur(GlassMaterial.Thin, CapsuleShape, haze),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions?.invoke(this)
                            if (add != null) {
                                androidx.compose.material3.IconButton(onClick = {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                                    add.onClick()
                                }) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Add, contentDescription = add.label,
                                        tint = AppColors.current.accent, modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (showSettings && openSettings != null) {
                        Spacer(Modifier.width(6.dp))
                        RoundGlassButton(onClick = openSettings, contentDescription = "Settings", size = 44.dp, state = haze) {
                            val initial = LocalAccountInitial.current
                            if (initial.isNotEmpty()) {
                                Text(initial, style = MaterialTheme.typography.titleSmall, color = AppColors.current.accent)
                            } else {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = bottomPad)) { floatingActionButton() }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad)) { snackbarHost() }
        }
    }
}

/** Goes up by one each time the current tab is tapped again (like iOS: the list scrolls back to the top). */
val LocalScrollToTop = androidx.compose.runtime.compositionLocalOf { 0 }

/** Scrolls [state] to the top when the current tab is tapped again (not when the screen first shows). */
@Composable
fun ScrollToTopEffect(state: LazyListState) {
    val signal = LocalScrollToTop.current
    val first = androidx.compose.runtime.remember { signal }
    androidx.compose.runtime.LaunchedEffect(signal) { if (signal != first) state.animateScrollToItem(0) }
}

/** The current tab's add button ("Add cash", "Add person"), shown top-right in the header. */
class AddAction(val label: String, val onClick: () -> Unit)

val LocalAddAction = androidx.compose.runtime.compositionLocalOf<AddAction?> { null }
