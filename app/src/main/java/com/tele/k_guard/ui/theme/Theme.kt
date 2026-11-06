package com.tele.k_guard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = KGuardBlueLight,
    onPrimary = Color(0xFF003261),
    primaryContainer = KGuardBlueDark,
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = KGuardPinkLight,
    onSecondary = Color(0xFF5B0029),
    secondaryContainer = KGuardPinkDark,
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = KGuardAmberLight,
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = KGuardAmberDark,
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = KGuardMidnightNavy,
    onBackground = KGuardOnDarkText,
    surface = KGuardSurfaceNavy,
    onSurface = KGuardOnDarkText,
    surfaceVariant = KGuardSurfaceVariantNavy,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = KGuardBlue,
    onPrimary = Color.White,
    primaryContainer = KGuardBlueContainer,
    onPrimaryContainer = KGuardOnBlueContainer,
    secondary = KGuardPink,
    onSecondary = Color.White,
    secondaryContainer = KGuardPinkContainer,
    onSecondaryContainer = KGuardOnPinkContainer,
    tertiary = KGuardAmber,
    onTertiary = Color.White,
    tertiaryContainer = KGuardAmberContainer,
    onTertiaryContainer = KGuardOnAmberContainer,
    background = KGuardCanvasLight,
    onBackground = KGuardOnLightText,
    surface = Color.White,
    onSurface = KGuardOnLightText,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

enum class ThemeModeOption(val key: String, val label: String) {
    SYSTEM("SYSTEM", "System Default"),
    LIGHT("LIGHT", "Light Mode"),
    DARK("DARK", "Dark Mode")
}

@Composable
fun KGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default dynamicColor to false so K-Guard logo brand colors are used consistently across devices
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
