package com.tgaudiobooks.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.tgaudiobooks.app.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DefaultSeed = Color(0xFF315DA8)

fun buildDynamicColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    accentColor: Color? = null
): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
    val hue = hsl[0]
    val sat = hsl[1].coerceIn(0.34f, 0.82f)
    val neutralSat = if (darkTheme) {
        (sat * 0.16f).coerceIn(0.05f, 0.11f)
    } else {
        (sat * 0.18f).coerceIn(0.06f, 0.12f)
    }

    val tertiaryHue = if (accentColor != null) {
        val accHsl = FloatArray(3)
        ColorUtils.colorToHSL(accentColor.toArgb(), accHsl)
        accHsl[0]
    } else {
        (hue + 44f) % 360f
    }

    fun hslColor(h: Float, s: Float, l: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

    return if (darkTheme) {
        darkColorScheme(
            primary = hslColor(hue, (sat + 0.14f).coerceAtMost(0.90f), 0.76f),
            onPrimary = hslColor(hue, sat, 0.14f),
            primaryContainer = hslColor(hue, (sat * 0.78f).coerceAtLeast(0.32f), 0.25f),
            onPrimaryContainer = hslColor(hue, (sat + 0.14f).coerceAtMost(0.92f), 0.92f),
            secondary = hslColor(hue, (sat * 0.40f + 0.10f).coerceAtMost(0.70f), 0.76f),
            secondaryContainer = hslColor(hue, (sat * 0.36f + 0.05f).coerceAtMost(0.50f), 0.22f),
            onSecondaryContainer = hslColor(hue, (sat * 0.40f + 0.12f).coerceAtMost(0.84f), 0.90f),
            tertiary = hslColor(tertiaryHue, (sat + 0.08f).coerceAtMost(0.86f), 0.78f),
            tertiaryContainer = hslColor(tertiaryHue, (sat - 0.04f).coerceAtLeast(0.28f), 0.26f),
            surface = hslColor(hue, neutralSat, 0.055f),
            surfaceContainerLow = hslColor(hue, (neutralSat + 0.01f).coerceAtMost(0.14f), 0.09f),
            surfaceContainer = hslColor(hue, (neutralSat + 0.015f).coerceAtMost(0.15f), 0.12f),
            surfaceContainerHigh = hslColor(hue, (neutralSat + 0.02f).coerceAtMost(0.16f), 0.16f),
            surfaceContainerHighest = hslColor(hue, (neutralSat + 0.025f).coerceAtMost(0.18f), 0.21f),
            onSurface = hslColor(hue, 0.08f, 0.94f),
            onSurfaceVariant = hslColor(hue, 0.09f, 0.74f),
            outline = hslColor(hue, 0.10f, 0.52f),
            outlineVariant = hslColor(hue, 0.12f, 0.25f)
        )
    } else {
        lightColorScheme(
            primary = hslColor(hue, (sat + 0.08f).coerceAtMost(0.84f), 0.38f),
            onPrimary = Color.White,
            primaryContainer = hslColor(hue, (sat + 0.12f).coerceAtMost(0.86f), 0.87f),
            onPrimaryContainer = hslColor(hue, sat, 0.14f),
            secondary = hslColor(hue, (sat * 0.40f + 0.08f).coerceAtMost(0.68f), 0.39f),
            secondaryContainer = hslColor(hue, (sat * 0.38f + 0.10f).coerceAtMost(0.72f), 0.88f),
            onSecondaryContainer = hslColor(hue, (sat * 0.40f + 0.08f).coerceAtMost(0.72f), 0.16f),
            tertiary = hslColor(tertiaryHue, sat, 0.40f),
            tertiaryContainer = hslColor(tertiaryHue, (sat + 0.08f).coerceAtMost(0.84f), 0.89f),
            surface = hslColor(hue, neutralSat, 0.975f),
            surfaceContainerLow = hslColor(hue, (neutralSat + 0.01f).coerceAtMost(0.16f), 0.945f),
            surfaceContainer = hslColor(hue, (neutralSat + 0.015f).coerceAtMost(0.18f), 0.915f),
            surfaceContainerHigh = hslColor(hue, (neutralSat + 0.02f).coerceAtMost(0.20f), 0.885f),
            surfaceContainerHighest = hslColor(hue, (neutralSat + 0.025f).coerceAtMost(0.22f), 0.845f),
            onSurface = hslColor(hue, 0.12f, 0.12f),
            onSurfaceVariant = hslColor(hue, 0.12f, 0.33f),
            outline = hslColor(hue, 0.10f, 0.48f),
            outlineVariant = hslColor(hue, 0.12f, 0.78f)
        )
    }
}

suspend fun extractSeedAndAccentFromBitmap(bitmap: Bitmap?): Pair<Color, Color?> = withContext(Dispatchers.Default) {
    if (bitmap == null) return@withContext DefaultSeed to null
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val seedRgb = palette.getVibrantColor(
        palette.getDarkVibrantColor(
            palette.getLightVibrantColor(
                palette.getDominantColor(DefaultSeed.toArgb())
            )
        )
    )
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seedRgb, seedHsl)

    val candidateSwatches = listOfNotNull(
        palette.lightVibrantSwatch,
        palette.mutedSwatch,
        palette.darkMutedSwatch,
        palette.lightMutedSwatch,
        palette.darkVibrantSwatch
    )
    val accentRgb = candidateSwatches.firstOrNull { sw ->
        val h = FloatArray(3)
        ColorUtils.colorToHSL(sw.rgb, h)
        val rawDiff = kotlin.math.abs(h[0] - seedHsl[0])
        val hueDiff = minOf(rawDiff, 360f - rawDiff)
        hueDiff >= 24f && h[1] >= 0.18f
    }?.rgb

    Color(seedRgb) to accentRgb?.let { Color(it) }
}

suspend fun extractSeedColorFromBitmap(bitmap: Bitmap?): Color =
    extractSeedAndAccentFromBitmap(bitmap).first

private val EchoGramTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
)

@Composable
fun EchoGramTheme(
    themeMode: ThemeMode,
    coverBitmap: Bitmap?,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    var palettePair by remember { mutableStateOf<Pair<Color, Color?>>(DefaultSeed to null) }
    LaunchedEffect(coverBitmap) {
        palettePair = extractSeedAndAccentFromBitmap(coverBitmap)
    }

    val colorScheme = remember(palettePair, isDark) {
        buildDynamicColorScheme(palettePair.first, isDark, palettePair.second)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EchoGramTypography,
        content = content
    )
}
