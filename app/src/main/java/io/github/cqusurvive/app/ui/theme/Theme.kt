package io.github.cqusurvive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CquBlue = Color(0xFF00639A)
private val LightColors = lightColorScheme(
    primary = CquBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF50606E),
    background = Color(0xFFF8F9FD),
    surface = Color(0xFFF8F9FD),
    surfaceVariant = Color(0xFFE6E9EF),
)
private val DarkColors = darkColorScheme(primary = Color(0xFF91CCFF))

@Composable
fun CquSurviveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
