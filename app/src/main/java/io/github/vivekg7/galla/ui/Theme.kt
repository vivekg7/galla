package io.github.vivekg7.galla.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2F7A55)
private val GreenLight = Color(0xFF7FCFA4)
private val Turmeric = Color(0xFFC98A1B)

private val Light =
    lightColorScheme(
        primary = Green,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD5EEDF),
        onPrimaryContainer = Color(0xFF0C2E1D),
        secondary = Turmeric,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFBE8C4),
        onSecondaryContainer = Color(0xFF3A2700),
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFF1F4F2),
        surfaceContainer = Color(0xFFF1F4F2),
        surfaceContainerHigh = Color(0xFFEAEFEC),
        error = Color(0xFFB3261E),
    )

private val Dark =
    darkColorScheme(
        primary = GreenLight,
        onPrimary = Color(0xFF00391F),
        primaryContainer = Color(0xFF1C5A3C),
        onPrimaryContainer = Color(0xFFD5EEDF),
        secondary = Color(0xFFF0C062),
        secondaryContainer = Color(0xFF5A4000),
        background = Color(0xFF121413),
        surface = Color(0xFF121413),
    )

@Composable
fun GallaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
