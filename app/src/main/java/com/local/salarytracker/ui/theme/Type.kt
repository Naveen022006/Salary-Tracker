package com.local.salarytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.local.salarytracker.R

// Inter (bundled, subset variable font: weight 400-700, optical size 14-32) as the SF Pro stand-in.
// Text sizes use optical size 14 ("Inter Text"), big titles and big money use 32 ("Inter Display"), like SF Text / Display.

private fun inter(weight: Int, opsz: Float) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

val InterText = FontFamily(inter(400, 14f), inter(500, 14f), inter(600, 14f), inter(700, 14f))
val InterDisplay = FontFamily(inter(400, 32f), inter(500, 32f), inter(600, 32f), inter(700, 32f))

/** Apple-style tracking given in points at a size -> em. */
private fun track(points: Float, size: Float): TextUnit = (points / size).em

private fun style(family: FontFamily, size: Float, line: Float, weight: Int, trackingPt: Float) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = track(trackingPt, size),
)

/** Apple type scale on the Material3 slots (see docs/DESIGN_GLASS.md, section D). */
internal val AppTypography = Typography(
    displaySmall = style(InterDisplay, 34f, 41f, 700, 0.4f),   // Large Title
    headlineLarge = style(InterDisplay, 28f, 34f, 700, 0.3f),  // Title 1
    headlineMedium = style(InterDisplay, 28f, 34f, 700, 0.3f),
    headlineSmall = style(InterDisplay, 22f, 28f, 700, -0.26f), // Title 2
    titleLarge = style(InterDisplay, 20f, 25f, 600, -0.45f),   // Title 3
    titleMedium = style(InterText, 17f, 22f, 600, -0.43f),     // Headline
    titleSmall = style(InterText, 15f, 20f, 600, -0.24f),
    bodyLarge = style(InterText, 17f, 22f, 400, -0.43f),       // Body
    bodyMedium = style(InterText, 15f, 20f, 400, -0.24f),      // Subhead
    bodySmall = style(InterText, 13f, 18f, 400, -0.08f),       // Footnote
    labelLarge = style(InterText, 17f, 22f, 600, -0.43f),      // Buttons
    labelMedium = style(InterText, 13f, 18f, 500, 0.3f),       // Section headers (upper case where used)
    labelSmall = style(InterText, 11f, 13f, 500, 0.07f),       // Tab labels, Caption 2
)

/** Money text styles. All use tabular figures so digits line up. */
object MoneyStyle {
    private const val TNUM = "tnum"
    private fun money(family: FontFamily, size: Float, line: Float, weight: Int, trackingPt: Float) =
        style(family, size, line, weight, trackingPt).copy(fontFeatureSettings = TNUM)

    /** 40sp bold: the one big number on a screen. */
    val Hero = money(InterDisplay, 40f, 44f, 700, -0.8f)

    /** 28sp semibold: numbers in cards and tiles. */
    val Card = money(InterDisplay, 28f, 34f, 600, -0.4f)

    /** 17sp semibold: amounts in list rows. */
    val Row = money(InterText, 17f, 22f, 600, -0.3f)

    /** 13sp medium: small amounts in captions and legends. */
    val Small = money(InterText, 13f, 18f, 500, 0f)

    /** 44sp medium: the amount box in the Add / Edit sheet. */
    val Input = money(InterDisplay, 44f, 52f, 500, -1.0f)
}
