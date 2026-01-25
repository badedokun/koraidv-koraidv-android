package com.koraidv.sdk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.koraidv.sdk.KoraTheme

/**
 * Convert KoraTheme to Compose color
 */
private fun Long.toComposeColor(): Color = Color(this)

/**
 * Create Material3 color scheme from KoraTheme
 */
private fun createColorScheme(theme: KoraTheme): ColorScheme {
    return lightColorScheme(
        primary = theme.primaryColor.toComposeColor(),
        onPrimary = Color.White,
        primaryContainer = theme.primaryColor.toComposeColor().copy(alpha = 0.1f),
        onPrimaryContainer = theme.primaryColor.toComposeColor(),
        secondary = theme.primaryColor.toComposeColor().copy(alpha = 0.8f),
        onSecondary = Color.White,
        background = theme.backgroundColor.toComposeColor(),
        onBackground = theme.textColor.toComposeColor(),
        surface = theme.surfaceColor.toComposeColor(),
        onSurface = theme.textColor.toComposeColor(),
        surfaceVariant = theme.surfaceColor.toComposeColor(),
        onSurfaceVariant = theme.secondaryTextColor.toComposeColor(),
        error = theme.errorColor.toComposeColor(),
        onError = Color.White
    )
}

/**
 * KoraIDV theme wrapper
 */
@Composable
fun KoraIDVTheme(
    theme: KoraTheme = KoraTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = createColorScheme(theme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Default Typography
 */
val Typography = Typography()
