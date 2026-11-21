package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LocalIsDarkTheme = compositionLocalOf { false }

// Modern Material 3 Vibrant Color Palette
val BrandCyan = Color(0xFF06B6D4) // Cyan 500
val BrandSky = Color(0xFF0EA5E9) // Sky 500

val AmberWarning = Color(0xFFF59E0B) // Amber 500

// Adaptive Brand Colors
val BrandTealLight = Color(0xFF0F766E) // Teal 700 (high contrast on light surface)
val BrandTealDark = Color(0xFF06B6D4) // Vibrant Cyan 500 (bright accent on dark surface)
val PrimaryDark = Color(0xFF2DD4BF) // Unified M3 Teal 400 for Dark Theme

val BrandTeal: Color
    @Composable get() = if (LocalIsDarkTheme.current) BrandTealDark else BrandTealLight

val OnBrandTeal: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF0F172A) else Color.White

fun brandSolidContentColor(background: Color): Color =
    if (background.luminance() > 0.32f) Color(0xFF0F172A) else Color.White

val BrandGoldLight = Color(0xFFB45309) // Rich Antique Gold (high contrast on white light mode)
val BrandGoldDarkTheme = Color(0xFFFACC15) // Radiant Warm Gold (high contrast on dark mode)

val BrandGold: Color
    @Composable get() = if (LocalIsDarkTheme.current) BrandGoldDarkTheme else BrandGoldLight

val BrandBlueDarkTheme = Color(0xFF38BDF8) // Sky 400
val BrandBlue: Color
    @Composable get() = if (LocalIsDarkTheme.current) BrandBlueDarkTheme else Color(0xFF2563EB)

val GoodGreenDark = Color(0xFF10B981) // Emerald 500
val GoodGreen: Color
    @Composable get() = if (LocalIsDarkTheme.current) GoodGreenDark else Color(0xFF047857) // Emerald 700 (>= 4.5:1 on white/slate-100)

val WarnAmberLight = Color(0xFFB45309) // Amber 700 (>= 4.5:1 on white)
val WarnAmberDark = Color(0xFFF59E0B) // Amber 500 (vibrant accent on dark)

val WarnAmber: Color
    @Composable get() = if (LocalIsDarkTheme.current) WarnAmberDark else WarnAmberLight

val BadRedDark = Color(0xFFF87171)
val BadRed: Color
    @Composable get() = if (LocalIsDarkTheme.current) BadRedDark else Color(0xFFDC2626)

// Surface & Neutral Grays - Light (Modern Neobank crisp canvas + pure white cards)
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF3F5F8) // Cool alabaster mist canvas
val SurfaceVariantLight = Color(0xFFEBF0F5) // Soft tonal container well
val OnSurfaceLight = Color(0xFF0F172A)
val MutedTextLight = Color(0xFF475569) // Slate 600 (>= 4.5:1 on SurfaceLight & SurfaceVariantLight)

// Material 3 Surface Containers & Containers - Light
val PrimaryContainerLight = Color(0xFF0F766E) // Teal 700 (5.00:1 contrast on SurfaceContainerLight)
val OnPrimaryContainerLight = Color(0xFFFFFFFF) // Pure White (5.47:1 contrast on PrimaryContainerLight)
val SecondaryContainerLight = Color(0xFFFEF3C7) // Amber 100
val OnSecondaryContainerLight = Color(0xFF78350F) // Amber 900
val TertiaryContainerLight = Color(0xFFE0F2FE) // Sky 100
val OnTertiaryContainerLight = Color(0xFF0C4A6E) // Sky 900
val SurfaceContainerLowestLight = Color(0xFFFFFFFF) // Pure White
val SurfaceContainerLowLight = Color(0xFFF6F8FB) // Soft tonal strip well
val SurfaceContainerLight = Color(0xFFEEF2F6) // NavigationBar & subtle tonal container
val SurfaceContainerHighLight = Color(0xFFE2E8F0) // Slate 200
val SurfaceContainerHighestLight = Color(0xFFCBD5E1) // Slate 300
val OutlineLight = Color(0xFF94A3B8) // Slate 400
val OutlineVariantLight = Color(0xFFE5E9F0) // Delicate hairline border

// Rich Deep Obsidian Palette - Dark (Modern Neobank dark mode)
val SurfaceDark = Color(0xFF131C2E) // Elevated card obsidian slate
val BackgroundDark = Color(0xFF0A0F1A) // Deepest midnight obsidian canvas
val SurfaceVariantDark = Color(0xFF1C283E) // Soft tonal dark container well
val OnSurfaceDark = Color(0xFFF8FAFC) // Slate 50
val MutedTextDark = Color(0xFFCBD5E1) // Slate 300 (>= 4.5:1 on SurfaceDark & SurfaceVariantDark)

// Material 3 Surface Containers & Containers - Dark
val PrimaryContainerDark = Color(0xFF14B8A6) // Teal 500 (5.88:1 contrast on SurfaceContainerDark)
val OnPrimaryContainerDark = Color(0xFF042F2E) // Teal 950 (5.81:1 contrast on PrimaryContainerDark)
val SecondaryContainerDark = Color(0xFF78350F) // Amber 900
val OnSecondaryContainerDark = Color(0xFFFEF3C7) // Amber 100
val TertiaryContainerDark = Color(0xFF0C4A6E) // Sky 900
val OnTertiaryContainerDark = Color(0xFFBAE6FD) // Sky 200
val SurfaceContainerLowestDark = Color(0xFF080C15) // Deepest Obsidian
val SurfaceContainerLowDark = Color(0xFF172236) // Dark tonal strip well
val SurfaceContainerDark = Color(0xFF162033) // NavigationBar & container
val SurfaceContainerHighDark = Color(0xFF23304A) // Elevated tonal dark control
val SurfaceContainerHighestDark = Color(0xFF334155) // Slate 700
val OutlineDark = Color(0xFF475569) // Slate 600
val OutlineVariantDark = Color(0xFF25324B) // Clean subtle obsidian border
