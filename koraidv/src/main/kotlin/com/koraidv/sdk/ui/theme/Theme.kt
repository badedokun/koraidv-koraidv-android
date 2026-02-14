package com.koraidv.sdk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koraidv.sdk.KoraTheme

/**
 * Convert KoraTheme hex to Compose color
 */
private fun Long.toComposeColor(): Color = Color(this)

/**
 * Create Material3 color scheme from KoraTheme with updated teal palette
 */
private fun createColorScheme(theme: KoraTheme): ColorScheme {
    val primary = theme.primaryColor.toComposeColor()
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.1f),
        onPrimaryContainer = primary,
        secondary = primary.copy(alpha = 0.8f),
        onSecondary = Color.White,
        tertiary = Color(0xFF0284C7),       // info blue
        onTertiary = Color.White,
        background = theme.backgroundColor.toComposeColor(),
        onBackground = theme.textColor.toComposeColor(),
        surface = Color(0xFFF8FAFB),
        onSurface = theme.textColor.toComposeColor(),
        surfaceVariant = Color(0xFFF8FAFB),
        onSurfaceVariant = theme.secondaryTextColor.toComposeColor(),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEF2F2),
        onErrorContainer = Color(0xFFDC2626),
        outline = Color(0xFFE5E7EB),
        outlineVariant = Color(0xFFE5E7EB)
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
        typography = KoraTypography,
        shapes = KoraShapes,
        content = content
    )
}

/**
 * Custom Typography with proper weights
 */
val KoraTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W600
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W600
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W500
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.W500,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W600
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.W500
    )
)

/**
 * Custom shapes with 16dp default corner radius
 */
val KoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)
