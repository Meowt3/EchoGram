package com.tgaudiobooks.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppIconPreset(
    val key: String,
    val label: String,
    val aliasClassName: String,
    val gradStart: Color,
    val gradEnd: Color,
    val fgColor: Color,
    val foldAlpha: Float
) {
    CLASSIC(
        key = "classic",
        label = "Классика",
        aliasClassName = "com.tgaudiobooks.app.MainActivityAliasClassic",
        gradStart = Color(0xFF37AEE2),
        gradEnd = Color(0xFF1D93D2),
        fgColor = Color.White,
        foldAlpha = 0.18f
    ),
    NIGHT(
        key = "night",
        label = "Ночь",
        aliasClassName = "com.tgaudiobooks.app.MainActivityAliasNight",
        gradStart = Color(0xFF1E2A3A),
        gradEnd = Color(0xFF0E1621),
        fgColor = Color(0xFF38BDF8),
        foldAlpha = 0.28f
    ),
    SUNSET(
        key = "sunset",
        label = "Закат",
        aliasClassName = "com.tgaudiobooks.app.MainActivityAliasSunset",
        gradStart = Color(0xFFFF7E5F),
        gradEnd = Color(0xFFE9386B),
        fgColor = Color.White,
        foldAlpha = 0.20f
    ),
    AQUA(
        key = "aqua",
        label = "Аква",
        aliasClassName = "com.tgaudiobooks.app.MainActivityAliasAqua",
        gradStart = Color(0xFF14B8A6),
        gradEnd = Color(0xFF0E7490),
        fgColor = Color.White,
        foldAlpha = 0.18f
    ),
    MONO(
        key = "mono",
        label = "Моно",
        aliasClassName = "com.tgaudiobooks.app.MainActivityAliasMono",
        gradStart = Color(0xFF27272A),
        gradEnd = Color(0xFF09090B),
        fgColor = Color(0xFFF4F4F5),
        foldAlpha = 0.24f
    );

    companion object {
        fun fromKey(key: String?): AppIconPreset =
            entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

object AppIconManager {
    private const val PREFS = "echogram_prefs"
    private const val KEY_ICON = "selected_app_icon"

    fun getSelectedIcon(context: Context): AppIconPreset {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppIconPreset.fromKey(prefs.getString(KEY_ICON, AppIconPreset.CLASSIC.key))
    }

    fun applyAppIcon(context: Context, preset: AppIconPreset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ICON, preset.key)
            .apply()

        val pm = context.packageManager
        val pkg = context.packageName
        runCatching {
            // Enable target alias first so launcher never sees 0 enabled entrypoints
            pm.setComponentEnabledSetting(
                ComponentName(pkg, preset.aliasClassName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            AppIconPreset.entries.forEach { other ->
                if (other != preset) {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, other.aliasClassName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}

@Composable
fun EchoGramBrandIcon(
    preset: AppIconPreset,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sx = w / 100f
        val sy = h / 100f
        val radius = 26f * sx

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(preset.gradStart, preset.gradEnd),
                start = Offset(12f * sx, 8f * sy),
                end = Offset(88f * sx, 92f * sy)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(radius, radius)
        )

        val arc1 = Path().apply {
            moveTo(29f * sx, 41f * sy)
            quadraticTo(32f * sx, 32f * sy, 41f * sx, 29f * sy)
        }
        drawPath(
            path = arc1,
            color = preset.fgColor.copy(alpha = 0.92f),
            style = Stroke(width = 3.8f * sx, cap = StrokeCap.Round)
        )

        val arc2 = Path().apply {
            moveTo(24f * sx, 35f * sy)
            quadraticTo(29f * sx, 24f * sy, 40f * sx, 21f * sy)
        }
        drawPath(
            path = arc2,
            color = preset.fgColor.copy(alpha = 0.65f),
            style = Stroke(width = 3.8f * sx, cap = StrokeCap.Round)
        )

        val plane = Path().apply {
            moveTo(26f * sx, 52f * sy)
            lineTo(75f * sx, 31f * sy)
            cubicTo(76.8f * sx, 30.2f * sy, 78.5f * sx, 31.8f * sy, 78f * sx, 33.7f * sy)
            lineTo(68.5f * sx, 71.5f * sy)
            cubicTo(68f * sx, 73.4f * sy, 65.7f * sx, 74.1f * sy, 64.2f * sx, 72.9f * sy)
            lineTo(51f * sx, 62.6f * sy)
            lineTo(43.6f * sx, 69.4f * sy)
            cubicTo(42.8f * sx, 70.1f * sy, 41.5f * sx, 69.6f * sy, 41.4f * sx, 68.5f * sy)
            lineTo(40.9f * sx, 58f * sy)
            lineTo(26.5f * sx, 54.4f * sy)
            cubicTo(24.8f * sx, 53.9f * sy, 24.6f * sx, 52.6f * sy, 26f * sx, 52f * sy)
            close()
        }
        drawPath(path = plane, color = preset.fgColor)

        val fold = Path().apply {
            moveTo(41.3f * sx, 57.5f * sy)
            lineTo(69.8f * sx, 37.8f * sy)
            lineTo(49.4f * sx, 60.3f * sy)
            close()
        }
        drawPath(path = fold, color = Color.Black.copy(alpha = preset.foldAlpha))
    }
}
