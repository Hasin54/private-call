package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = TextOnIndigo,
    primaryContainer = IndigoLight,
    secondary = CyanAccent,
    onSecondary = Color(0xFF0F1115),
    background = SlateDarkBlue,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0x1CFFFFFF), // Extra translucent layer (white/11)
    onSurfaceVariant = TextPrimary,
    outline = SlateBorder,
    error = RedReject
)

private val LightColorScheme = darkColorScheme( // Enforce the gorgeous dark frosted-glass vibe as specified in user request
    primary = IndigoPrimary,
    onPrimary = TextOnIndigo,
    primaryContainer = IndigoLight,
    secondary = CyanAccent,
    onSecondary = Color(0xFF0F1115),
    background = SlateDarkBlue,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0x1CFFFFFF),
    onSurfaceVariant = TextPrimary,
    outline = SlateBorder,
    error = RedReject
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // We enforce our custom color schemes to keep consistent premium frosted styling
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
