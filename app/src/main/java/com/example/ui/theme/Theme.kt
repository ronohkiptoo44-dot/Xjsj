package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CustomDarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = BlackBackground,
    primaryContainer = MossGreen,
    onPrimaryContainer = BrightMint,
    secondary = PaleMint,
    onSecondary = BlackBackground,
    background = BlackBackground,
    onBackground = BrightMint,
    surface = DarkSurface,
    onSurface = PaleMint,
    error = ErrorRed,
    onError = BlackBackground
)

private val CustomLightColorScheme = lightColorScheme(
    primary = MossGreen,
    onPrimary = BrightMint,
    primaryContainer = CyberGreen,
    onPrimaryContainer = BlackBackground,
    secondary = PaleMint,
    onSecondary = BlackBackground,
    background = BrightMint,
    onBackground = BlackBackground,
    surface = PaleMint,
    onSurface = BlackBackground,
    error = ErrorRed,
    onError = BrightMint
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme or respect selection
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CustomDarkColorScheme else CustomLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
