package com.local.salarytracker.ui.theme

import androidx.compose.ui.graphics.Color

// Colour system v3 "Liquid Glass" (Apple system colours). See docs/DESIGN_GLASS.md.
// The only place (besides the category palette in CategoryIcon.kt) with raw hex values.

// Accent and semantic (light / dark)
internal val BlueLight = Color(0xFF007AFF)
internal val BlueDark = Color(0xFF0A84FF)
internal val GreenLight = Color(0xFF34C759)
internal val GreenDark = Color(0xFF30D158)
internal val OrangeLight = Color(0xFFFF9500)
internal val OrangeDark = Color(0xFFFF9F0A)
internal val RedLight = Color(0xFFFF3B30)
internal val RedDark = Color(0xFFFF453A)

// Text-safe variants of green / orange for small text on white (the system colours are for fills and icons).
internal val GreenTextLight = Color(0xFF248A3D)
internal val OrangeTextLight = Color(0xFFC93400)

// Labels
internal val LabelLight = Color(0xFF000000)
internal val LabelDark = Color(0xFFFFFFFF)
internal val SecondaryLabelLight = Color(0xFF6E6E73)
internal val SecondaryLabelDark = Color(0xFF9D9DA3)
internal val TertiaryLabelLight = Color(0x4D3C3C43)
internal val TertiaryLabelDark = Color(0x4DEBEBF5)

// Fills (systemFill 1..4 = #787880 at 20/16/12/8 % light, 36/32/24/18 % dark)
internal val Fill1Light = Color(0x33787880)
internal val Fill2Light = Color(0x29787880)
internal val Fill3Light = Color(0x1F787880)
internal val Fill4Light = Color(0x14787880)
internal val Fill1Dark = Color(0x5C787880)
internal val Fill2Dark = Color(0x52787880)
internal val Fill3Dark = Color(0x3D787880)
internal val Fill4Dark = Color(0x2E787880)

// Separators
internal val SeparatorLight = Color(0x4A3C3C43)
internal val SeparatorDark = Color(0x99545458)

// Grouped backgrounds: page / card or row / nested
internal val GroupedLight = Color(0xFFF2F2F7)
internal val GroupedDark = Color(0xFF000000)
internal val CardLight = Color(0xFFFFFFFF)
internal val CardDark = Color(0xFF1C1C1E)
internal val NestedLight = Color(0xFFF2F2F7)
internal val NestedDark = Color(0xFF2C2C2E)

// Solid versions of fills drawn over a card (for Material container slots that must be opaque)
internal val CardFill3Light = Color(0xFFEBEBEF)
internal val CardFill1Light = Color(0xFFE3E3E8)
internal val CardFill3Dark = Color(0xFF2C2C2E)
internal val CardFill1Dark = Color(0xFF3A3A3C)

// Accent containers (accent at 15 % over the card, flattened)
internal val BlueContainerLight = Color(0xFFD9EBFF)
internal val BlueContainerDark = Color(0xFF0F2A47)
internal val GreenContainerLight = Color(0xFFE1F6E6)
internal val GreenContainerDark = Color(0xFF0E3319)
internal val RedContainerLight = Color(0xFFFFE2E0)
internal val RedContainerDark = Color(0xFF4A1512)
