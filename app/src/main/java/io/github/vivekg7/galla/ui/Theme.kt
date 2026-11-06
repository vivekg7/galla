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

// Every surface and outline tone is set: one left out keeps Material's lavender
// default (a plain Card is drawn in surfaceContainerHighest).
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
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F9F8),
        surfaceContainer = Color(0xFFF1F4F2),
        surfaceContainerHigh = Color(0xFFEAEFEC),
        surfaceContainerHighest = Color(0xFFE3EAE6),
        onSurfaceVariant = Color(0xFF414944),
        outline = Color(0xFF717973),
        outlineVariant = Color(0xFFC1C9C3),
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
        surfaceVariant = Color(0xFF2E3330),
        surfaceContainerLowest = Color(0xFF0D0F0E),
        surfaceContainerLow = Color(0xFF1A1C1B),
        surfaceContainer = Color(0xFF1E211F),
        surfaceContainerHigh = Color(0xFF282B2A),
        surfaceContainerHighest = Color(0xFF333735),
        onSurfaceVariant = Color(0xFFC1C9C3),
        outline = Color(0xFF8B938D),
        outlineVariant = Color(0xFF414944),
    )

@Composable
fun GallaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
