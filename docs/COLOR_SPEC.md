# Colour system v2 — "Ink & Saffron"

Replaces the colour part of UI_SPEC.md section 1. Goal: a distinctive, premium look instead of a generic
green Material app. Deep indigo ink brand + warm saffron accent; green is no longer the brand.

## Why the old palette looked basic
- One green did everything (brand, buttons, positive, credit, Groceries, Cash) → no hierarchy.
- Green-tinted grey neutrals → muddy, especially in dark.
- Stock Material-600 category colours with uneven lightness (bright yellow next to dull brown).
- Hero card was a flat tonal block.

## Roles
| Role | Light | Dark | Use |
|---|---|---|---|
| Brand / primary | `#4338CA` (indigo ink) | `#A5B4FC` | buttons, selected nav, links, chart bars |
| onPrimary | `#FFFFFF` | `#1E1B4B` | |
| primaryContainer | `#E0E7FF` | `#2E2A6B` | selected chips, nav indicator, segmented selection |
| onPrimaryContainer | `#1E1B4B` | `#E0E7FF` | |
| Accent / secondary (saffron) | `#D97706` | `#FBBF24` | FAB, allowance/budget lines, highlights, "today" markers |
| secondaryContainer | `#FEF3C7` | `#3D2E0B` | FAB container (light) / tip icon bg |
| onSecondaryContainer | `#78350F` | `#FDE68A` | |
| Positive / credit (teal) | `#0D9488` | `#2DD4BF` | under budget, money received, ✓ chips, "Allowed" |
| Warning (marigold) | `#B45309` | `#FB923C` | ≥ 80 % of today used |
| Danger (coral) | `#DC2626` | `#F87171` | over budget, delete, danger zone |

## Neutrals (cool slate-ink, no green tint)
| Token | Light ("paper") | Dark ("ink") |
|---|---|---|
| background / surface | `#F6F5F2` | `#0B0D1A` |
| surfaceContainerLowest | `#FFFFFF` | `#080A14` |
| surfaceContainerLow (cards) | `#FFFFFF` | `#141729` |
| surfaceContainer | `#EFEEF0` | `#1B1F35` |
| surfaceContainerHigh | `#E8E7EC` | `#23273F` |
| surfaceContainerHighest | `#E1E0E7` | `#2C3150` |
| onSurface (text) | `#16182B` | `#ECEEF8` |
| onSurfaceVariant (secondary text) | `#5A5E73` | `#9EA3BD` |
| outline | `#8A8EA3` | `#6B7091` |
| outlineVariant (card border, dividers) | `#E4E3EA` | `#262A45` |
| inverseSurface / inverseOnSurface | `#24273D` / `#F2F2F7` | `#ECEEF8` / `#16182B` |
Snackbar uses inverseSurface; its action uses inversePrimary (light: `#A5B4FC`, dark: `#4338CA`).
Bottom nav bar: surfaceContainerLow with a 1dp top outlineVariant line (no tonal tint).
Top app bar on scroll: surfaceContainer.

## Signature surfaces
- **Today hero card**: vertical-diagonal gradient, light `#4338CA → #6D28D9`, dark `#312E81 → #4C1D95`,
  20dp radius, white text (`onHero = #FFFFFF`, secondary `#FFFFFF` at 75 %). Ring track white 18 %;
  ring arc: white while ≥ 20 % left, saffron `#FBBF24` when < 20 % left, coral `#FCA5A5` when over
  (full ring). Centre amount uses the same colour as the arc. "Spent ₹X of ₹Y" white 85 %.
- **Onboarding welcome mark & app icon**: same gradient circle with a saffron `#FBBF24` rupee.
  Launcher icon background `#4338CA`, foreground rupee `#FBBF24` (update ic_launcher_background.xml and the
  foreground vector). Small notification icon colour (`setColor`) `#4338CA`.
- **Charts**: bars = primary; today's bar = saffron outline/fill; over-allowance bars = danger; dashed
  allowance line = saffron; grid lines = outlineVariant; tooltip = inverseSurface.
- **Report KPIs**: "Left/Saved" in positive teal, "Over" in danger, progress bar primary on surfaceContainerHigh.
- **Status chips**: "Allowed" teal on teal 14 %; "Needs permission" warning on warning 14 %.
- **Danger zone**: danger text/icon on danger 12 % circle.
- **InfoBanner tones**: Info = surfaceContainerHigh + onSurfaceVariant; Positive = teal 12 % + teal;
  Warning = warning 12 % + warning; Danger = danger 12 % + danger.

## Category colours (balanced: similar lightness/saturation, all readable on both themes)
Icon tint = colour; icon circle = colour at 16 % (light) / 22 % (dark). Chart segments = colour.
| Category | Colour |
|---|---|
| Food | `#F97316` (tangerine) |
| Groceries | `#22A06B` (leaf) |
| Travel & Fuel | `#3B82F6` (sky) |
| Bills & Recharge | `#EAB308` (marigold) |
| Rent / EMI | `#A16207` (clay) |
| Shopping | `#C026D3` (orchid) |
| Health | `#F43F5E` (rose) |
| Movies & fun | `#EC4899` (pink) |
| Education | `#6366F1` (indigo-light) |
| People | `#0EA5E9` (cyan) |
| Cash / ATM | `#84CC16` (lime) |
| Transfer / Not spending | `#94A3B8` (slate) |
| Other | `#A8A29E` (stone) |
In dark theme, lift the icon tint toward white by ~15 % if contrast on the 22 % circle is below 3:1.

## Rules
- No hard-coded colours outside `ui/theme/` (except `Category.color`). Everything reads MaterialTheme /
  AppColors (extend AppColors with `accent`, `heroStart`, `heroEnd`, `onHero`).
- Text contrast ≥ 4.5:1 for body, ≥ 3:1 for large text and icons, both themes.
- Credit amounts: "+₹500" in positive teal. Excluded amounts: onSurfaceVariant, struck through.
