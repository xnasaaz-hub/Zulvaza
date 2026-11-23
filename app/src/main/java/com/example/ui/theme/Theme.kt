package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ZulvazaLightColorScheme = lightColorScheme(
    primary = ZulvazaOrange,
    onPrimary = Color.White,
    primaryContainer = ZulvazaOrangeSoft,
    onPrimaryContainer = ZulvazaOrangeDark,
    secondary = ZulvazaNavy,
    onSecondary = Color.White,
    secondaryContainer = ZulvazaNavySoft,
    onSecondaryContainer = ZulvazaNavy,
    tertiary = AccentTeal,
    onTertiary = Color.White,
    tertiaryContainer = AccentTealSoft,
    onTertiaryContainer = AccentTeal,
    background = LightBackground,
    onBackground = CharcoalText,
    surface = CardWhite,
    onSurface = CharcoalText,
    surfaceVariant = SurfaceWarm,
    onSurfaceVariant = SlateMuted,
    outline = SlateBorder,
    outlineVariant = SlateBorder,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedSoft,
    onErrorContainer = ErrorRed
)

private val ZulvazaDarkColorScheme = darkColorScheme(
    primary = ZulvazaOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF431607),
    onPrimaryContainer = Color(0xFFFFD8C8),
    secondary = Color(0xFF7B96FF),
    onSecondary = ZulvazaNavy,
    secondaryContainer = ZulvazaNavyLight,
    onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1E273B),
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF881337),
    onErrorContainer = Color(0xFFFFE4E6)
)

val ZulvazaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Composable
fun ZulvazaSellerTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = ZulvazaLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZulvazaTypography,
        shapes = ZulvazaShapes,
        content = content
    )
}
