package com.echogram.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.echogram.app.data.model.ThemeMode

private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramBlueDark = Color(0xFF38BDF8)
private val DeepNavy = Color(0xFF0E1621)
private val DeepNavySurface = Color(0xFF17212B)
private val DeepNavyHigh = Color(0xFF202B36)

private val DarkColors = darkColorScheme(
    primary = TelegramBlueDark,
    onPrimary = Color(0xFF00263A),
    primaryContainer = Color(0xFF0C3B5E),
    onPrimaryContainer = Color(0xFFC8E9FF),
    secondary = Color(0xFF7DD3FC),
    background = DeepNavy,
    onBackground = Color(0xFFF1F5F9),
    surface = DeepNavy,
    onSurface = Color(0xFFF1F5F9),
    surfaceContainerLow = Color(0xFF131D28),
    surfaceContainer = DeepNavySurface,
    surfaceContainerHigh = DeepNavyHigh,
    surfaceContainerHighest = Color(0xFF283544),
    onSurfaceVariant = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF2D3B4B)
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLow = Color(0xFF090C10),
    surfaceContainer = Color(0xFF11161D),
    surfaceContainerHigh = Color(0xFF1A222C),
    surfaceContainerHighest = Color(0xFF242E3B)
)

private val LightColors = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F0FF),
    onPrimaryContainer = Color(0xFF002C42),
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF0F172A),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF4F9),
    surfaceContainerHigh = Color(0xFFE4ECF4),
    surfaceContainerHighest = Color(0xFFD9E4F0),
    onSurfaceVariant = Color(0xFF475569),
    outlineVariant = Color(0xFFCBD5E1)
)

@Composable
fun EchoGramTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledColors
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
