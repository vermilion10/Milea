package com.github.vermilion10.milea.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.github.vermilion10.milea.data.repository.ThemeMode

// Generated from seed #00696E with the Material tonal-spot scheme (2021 spec).
// Used whenever dynamic color is off or unavailable.
private val LightColors = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF0F6),
    onPrimaryContainer = Color(0xFF004F53),
    inversePrimary = Color(0xFF80D4D9),
    secondary = Color(0xFF4A6364),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF324B4D),
    tertiary = Color(0xFF4E5F7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF364764),
    background = Color(0xFFF4FAFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FAFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceTint = Color(0xFF00696E),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFECF2F2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4FAFA),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDDE4E4),
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD5DBDB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D9),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF9CF0F6),
    inversePrimary = Color(0xFF00696E),
    secondary = Color(0xFFB1CCCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB6C7E9),
    onTertiary = Color(0xFF20314C),
    tertiaryContainer = Color(0xFF364764),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceTint = Color(0xFF80D4D9),
    inverseSurface = Color(0xFFDDE4E4),
    inverseOnSurface = Color(0xFF2B3232),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3B),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303636),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceDim = Color(0xFF0E1415),
)

private val MileaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Default M3 type scale, with the display/headline roles used for big numbers
// (odometer, stat values) nudged to a medium weight so they read as figures
// rather than decoration.
private val MileaTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Medium),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Medium),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium)
    )
}

@Composable
fun MileaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MileaTypography,
        shapes = MileaShapes,
        content = content
    )
}
