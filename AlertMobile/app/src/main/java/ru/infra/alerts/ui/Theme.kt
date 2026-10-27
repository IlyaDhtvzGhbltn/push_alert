package ru.infra.alerts.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.infra.alerts.Severity

private val LightColors = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF5DDDA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    background = Color(0xFF201A19),
    surface = Color(0xFF201A19),
    surfaceVariant = Color(0xFF534341),
)

val OkGreen = Color(0xFF2E7D32)

fun Severity.color(): Color = when (this) {
    Severity.CRITICAL -> Color(0xFFD32F2F)
    Severity.WARNING -> Color(0xFFF57C00)
    Severity.INFO -> Color(0xFF1976D2)
    Severity.RESOLVED -> Color(0xFF388E3C)
}

@Composable
fun RedAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
