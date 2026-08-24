package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GeometricPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = GeometricPrimaryContainer,
    secondary = GeometricSecondaryDark,
    onSecondary = Color(0xFF24323B),
    secondaryContainer = Color(0xFF3A4B54),
    onSecondaryContainer = GeometricSecondaryContainer,
    tertiary = GeometricTertiaryDark,
    onTertiary = Color(0xFF043913),
    tertiaryContainer = Color(0xFF1B5125),
    onTertiaryContainer = GeometricTertiaryContainer,
    background = GeometricBackgroundDark,
    onBackground = GeometricTextPrimaryDark,
    surface = GeometricSurfaceDark,
    onSurface = GeometricTextPrimaryDark,
    surfaceVariant = GeometricSurfaceVariantDark,
    onSurfaceVariant = GeometricTextSecondaryDark,
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onError = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val LightColorScheme = lightColorScheme(
    primary = GeometricPrimary,
    onPrimary = GeometricOnPrimary,
    primaryContainer = GeometricPrimaryContainer,
    onPrimaryContainer = GeometricOnPrimaryContainer,
    secondary = GeometricSecondary,
    onSecondary = Color.White,
    secondaryContainer = GeometricSecondaryContainer,
    onSecondaryContainer = GeometricOnSecondaryContainer,
    tertiary = GeometricTertiary,
    onTertiary = Color.White,
    tertiaryContainer = GeometricTertiaryContainer,
    onTertiaryContainer = GeometricOnTertiaryContainer,
    background = GeometricBackgroundLight,
    onBackground = GeometricTextPrimaryLight,
    surface = GeometricSurfaceLight,
    onSurface = GeometricTextPrimaryLight,
    surfaceVariant = GeometricSurfaceVariantLight,
    onSurfaceVariant = GeometricTextSecondaryLight,
    outline = Color(0xFFCAC4D0),
    error = ErrorRed,
    errorContainer = ErrorRedLight,
    onError = Color.White,
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

