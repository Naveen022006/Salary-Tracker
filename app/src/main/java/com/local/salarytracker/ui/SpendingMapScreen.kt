package com.local.salarytracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.salarytracker.R
import com.local.salarytracker.data.PlacedTxn
import com.local.salarytracker.location.PlaceRecorder
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.ui.components.AppWallpaper
import com.local.salarytracker.ui.components.ButtonStyle
import com.local.salarytracker.ui.components.CapsuleButton
import com.local.salarytracker.ui.components.CapsuleShape
import com.local.salarytracker.ui.components.CategoryIcon
import com.local.salarytracker.ui.components.EmptyState
import com.local.salarytracker.ui.components.GlassCardShape
import com.local.salarytracker.ui.components.GlassMaterial
import com.local.salarytracker.ui.components.GlassSurface
import com.local.salarytracker.ui.components.RoundGlassButton
import com.local.salarytracker.ui.components.glassBlur
import com.local.salarytracker.ui.components.tint
import com.local.salarytracker.ui.theme.AppColors
import com.local.salarytracker.ui.theme.MoneyStyle
import com.local.salarytracker.util.Money
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationState
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

private val WHEN = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH)

/**
 * Spending map: the month shown in Report, one circle per entry with a saved place (category colour, bigger for
 * bigger amounts). Tap a circle for a small glass card; "Open" shows the entry. Back closes the page.
 */
@Composable
fun SpendingMapPage(vm: AppViewModel) {
    val places = vm.mapPlaces.collectAsStateWithLifecycle().value
    val report = vm.reportState.collectAsStateWithLifecycle().value
    val focus = vm.mapFocus.collectAsStateWithLifecycle().value
    val token = stringResource(R.string.mapbox_access_token)
    var selected by remember { mutableStateOf<PlacedTxn?>(null) }
    BackHandler { if (selected != null) selected = null else vm.closeSpendingMap() }

    Box(Modifier.fillMaxSize()) {
        if (token.isBlank()) {
            AppWallpaper(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.Map,
                    title = "Map is not set up",
                    text = "Add your Mapbox key to the app (see docs/MAPBOX_SETUP.md), then build and install it again.",
                )
            }
        } else {
            SpendingMap(places, focus, onSelect = { selected = it })
        }

        // Header: back + title and month on glass.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundGlassButton(onClick = { vm.closeSpendingMap() }, contentDescription = "Back", state = null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.glassBlur(GlassMaterial.Thin, CapsuleShape, null).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Spending map", style = MaterialTheme.typography.titleMedium, color = AppColors.current.label)
                val sub = buildString {
                    report?.let { append(Fmt.period(it.period)) }
                    append(" · ${places.size} ${if (places.size == 1) "place" else "places"}")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = AppColors.current.secondaryLabel)
            }
        }

        if (token.isNotBlank() && places.isEmpty()) {
            GlassSurface(GlassMaterial.Thick, GlassCardShape, Modifier.align(Alignment.Center).padding(32.dp), state = null) {
                Text(
                    "No places this month yet. Turn on Settings → Places → \"Save where I spend\". New expenses then get their place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.current.label,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }

        selected?.let { p ->
            PlaceCard(
                p, vm,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            )
        }
    }
}

@Composable
private fun SpendingMap(places: List<PlacedTxn>, focus: Pair<Double, Double>?, onSelect: (PlacedTxn) -> Unit) {
    val ctx = LocalContext.current
    val dark = AppColors.current.isDark
    val viewport = rememberMapViewportState {
        // India until the pins are known.
        setCameraOptions { center(Point.fromLngLat(78.96, 20.59)); zoom(3.8) }
    }
    val canShowMe = remember { PlaceRecorder.hasForeground(ctx) }
    LaunchedEffect(places, focus) {
        when {
            focus != null -> viewport.flyTo(
                com.mapbox.maps.CameraOptions.Builder().center(Point.fromLngLat(focus.second, focus.first)).zoom(15.5).build(),
            )
            places.size == 1 -> viewport.flyTo(
                com.mapbox.maps.CameraOptions.Builder().center(Point.fromLngLat(places[0].lng, places[0].lat)).zoom(15.0).build(),
            )
            places.size > 1 -> viewport.transitionToOverviewState(
                OverviewViewportStateOptions.Builder()
                    .geometry(MultiPoint.fromLngLats(places.map { Point.fromLngLat(it.lng, it.lat) }))
                    .padding(EdgeInsets(260.0, 140.0, 360.0, 140.0))
                    .maxZoom(16.0)
                    .build(),
            )
        }
    }
    val biggest = (places.maxOfOrNull { it.amountPaise } ?: 1L).coerceAtLeast(1L)
    MapboxMap(
        Modifier.fillMaxSize(),
        mapViewportState = viewport,
        scaleBar = {},
        style = {
            MapboxStandardStyle { lightPreset = if (dark) LightPresetValue.NIGHT else LightPresetValue.DAY }
        },
    ) {
        if (canShowMe) {
            MapEffect(Unit) { mapView ->
                mapView.location.updateSettings {
                    enabled = true
                    locationPuck = createDefault2DPuck(withBearing = false)
                    puckBearing = PuckBearing.HEADING
                }
            }
        }
        for (p in places) {
            val color = com.local.salarytracker.ui.components.categoryTint(p.category, p.customCategory)
            // 7..16 dp, square-root scale so a few big payments do not hide the rest.
            val radius = 7.0 + 9.0 * sqrt(p.amountPaise.toDouble() / biggest)
            CircleAnnotation(
                point = Point.fromLngLat(p.lng, p.lat),
                onClick = { onSelect(p); true },
                circleAnnotationState = remember(p.remoteId, color, radius) {
                    CircleAnnotationState().apply {
                        circleColor = color
                        circleRadius = radius
                        circleOpacity = 0.9
                        circleStrokeColor = Color.White
                        circleStrokeWidth = 2.0
                    }
                },
            )
        }
    }
    if (canShowMe) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(end = 16.dp, bottom = 120.dp), contentAlignment = Alignment.BottomEnd) {
            RoundGlassButton(onClick = { viewport.transitionToFollowPuckState() }, contentDescription = "My location", state = null) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = AppColors.current.accent)
            }
        }
    }
}

@Composable
private fun PlaceCard(p: PlacedTxn, vm: AppViewModel, modifier: Modifier) {
    val c = AppColors.current
    val credit = p.type == TxnType.CREDIT
    GlassSurface(GlassMaterial.Thick, GlassCardShape, modifier.fillMaxWidth(), state = null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(p.category, 40.dp, credit = credit, custom = p.customCategory)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.merchant?.takeIf { it.isNotBlank() } ?: p.note?.takeIf { it.isNotBlank() } ?: com.local.salarytracker.ui.components.categoryName(p.category, p.customCategory),
                        style = MaterialTheme.typography.bodyLarge, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Instant.ofEpochMilli(p.timestamp).atZone(vm.zone).format(WHEN),
                        style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel,
                    )
                }
                Text(
                    (if (credit) "+" else "") + Money.format(p.amountPaise),
                    style = MoneyStyle.Row,
                    color = if (credit) c.credit else c.label,
                )
            }
            CapsuleButton("Open entry", onClick = { vm.openEntry(p.id) }, style = ButtonStyle.Tinted, small = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
