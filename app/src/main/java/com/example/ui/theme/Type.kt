package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.example.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val displayFontName = GoogleFont("Outfit")
val bodyFontName = GoogleFont("Inter")

val DisplayFontFamily = FontFamily(
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = displayFontName, fontProvider = provider, weight = FontWeight.Bold)
)

val BodyFontFamily = FontFamily(
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = bodyFontName, fontProvider = provider, weight = FontWeight.Bold)
)

val TabularNumStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontFeatureSettings = "tnum"
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp, fontFeatureSettings = "tnum"),
    titleSmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp, fontFeatureSettings = "tnum"),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp, fontFeatureSettings = "tnum"),
    bodySmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontFeatureSettings = "tnum"),
    labelMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontFeatureSettings = "tnum"),
    labelSmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontFeatureSettings = "tnum")
)
