package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SwapLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = NavyPrimary,
    secondary = EmeraldAccent,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = Color(0xFF0A4D32),
    tertiary = PendingAmber,
    onTertiary = Color(0xFF3F2800),
    tertiaryContainer = PendingAmberContainer,
    onTertiaryContainer = Color(0xFF6B3E00),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val SwapDarkColorScheme = darkColorScheme(
    primary = NavyDarkPrimary,
    onPrimary = Color(0xFF091B34),
    primaryContainer = NavyDarkContainer,
    onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = EmeraldDark,
    onSecondary = Color(0xFF032C1D),
    secondaryContainer = EmeraldDarkContainer,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = PendingAmber,
    onTertiary = Color(0xFF2F1D00),
    tertiaryContainer = Color(0xFF473004),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFECACA)
)

val SwapMoneyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SwapMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SwapDarkColorScheme else SwapLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SwapMoneyTypography,
        shapes = SwapMoneyShapes,
        content = content
    )
}
