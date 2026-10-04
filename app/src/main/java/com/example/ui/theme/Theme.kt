package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AstralViolet,
    onPrimary = Color.Black,
    primaryContainer = AstralVioletDark,
    onPrimaryContainer = Color.White,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = NeonCyanDark,
    onSecondaryContainer = Color.White,
    tertiary = VoidCrimson,
    onTertiary = Color.White,
    background = VoidBlack,
    onBackground = Color(0xFFECEFF1),
    surface = VoidDark,
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = VoidSurface,
    onSurfaceVariant = Color(0xFFCFD8DC),
    outline = VoidOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Void Realms is strictly an immersive dark fantasy sci-fi experience
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
