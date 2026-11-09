package com.hms.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF7A00)
val OrangeDeep = Color(0xFFE83A00)
val OrangeSoft = Color(0xFFFFB84D)
val Bg = Color(0xFF0B1220)
val Surface1 = Color(0xFF131C2E)
val Surface2 = Color(0xFF1C2842)
val TextMain = Color(0xFFEEF2F6)
val TextMuted = Color(0xFF9AA7BA)
val Danger = Color(0xFFFF5D5D)
val Ok = Color(0xFF2FBF86)

val BrandBrush = Brush.linearGradient(listOf(OrangeSoft, Orange, OrangeDeep))

@Composable
fun HmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Orange, onPrimary = Color.Black,
            secondary = OrangeSoft, background = Bg, surface = Surface1,
            onSurface = TextMain, onBackground = TextMain,
            surfaceVariant = Surface2, error = Danger
        ),
        content = content
    )
}
