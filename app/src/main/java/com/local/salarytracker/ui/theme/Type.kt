package com.local.salarytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Roboto (system) type scale from the spec: titles 20 semibold, body 16, labels 13 medium, captions 12. */
internal val AppTypography = Typography().let { d ->
    d.copy(
        headlineSmall = d.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = d.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyLarge = d.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = d.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = d.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = d.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = d.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = d.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/** Money text styles. All use tabular figures so digits line up. */
object MoneyStyle {
    private const val TNUM = "tnum"

    /** 44sp bold: the one big number on a screen. */
    val Hero = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)

    /** 24sp semibold: numbers in cards and tiles. */
    val Card = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)

    /** 16sp semibold: amounts in list rows. */
    val Row = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)

    /** 14sp medium: small amounts in captions and legends. */
    val Small = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM)

    /** 40sp semibold: the amount box in the Add / Edit sheet. */
    val Input = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
}
