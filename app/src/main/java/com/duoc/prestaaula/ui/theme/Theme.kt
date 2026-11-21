package com.duoc.prestaaula.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8E4EC6),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFE1E6EF),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF0B4684),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFD9B8FF),
    background = Color(0xFF111418),
    surface = Color(0xFF111418),
    surfaceVariant = Color(0xFF42474E),
    error = Color(0xFFFFB4AB)
)

/** Colores semánticos de estado usados en chips y mensajes. */
object StatusColors {
    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFE65100)
    val Danger = Color(0xFFC62828)
    val Info = Color(0xFF1565C0)
    val Neutral = Color(0xFF616161)
}

@Composable
fun PrestaAulaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
