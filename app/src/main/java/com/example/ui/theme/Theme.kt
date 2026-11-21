package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, // F2: Unified Teal 400 (0xFF2DD4BF)
    onPrimary = Color(0xFF042F2E),
    primaryContainer = PrimaryContainerDark, // F1: High-contrast Teal 500 (5.88:1 vs SurfaceContainerDark)
    onPrimaryContainer = OnPrimaryContainerDark, // F1: High-contrast Teal 950 (5.81:1 vs PrimaryContainerDark)
    secondary = BrandGoldDarkTheme,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = BrandCyan,
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = MutedTextDark,
    surfaceContainerLowest = SurfaceContainerLowestDark, // F4: M3 Surface Containers
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    error = BadRedDark,
    onError = Color(0xFF0F172A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark // F5: Clean subtle border (Slate 700)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandTealLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight, // F1: Brand Teal 700 (5.00:1 vs SurfaceContainerLight)
    onPrimaryContainer = OnPrimaryContainerLight, // F1: High-contrast White (5.47:1 vs PrimaryContainerLight)
    secondary = BrandGoldLight,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = MutedTextLight,
    surfaceContainerLowest = SurfaceContainerLowestLight, // F4: M3 Surface Containers
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight // F5: Clean subtle border (Slate 200)
)

@Composable
fun MartinuFinancialsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Enforce our custom premium theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes, // F3: Standard M3 Shape Scale
            content = content
        )
    }
}
