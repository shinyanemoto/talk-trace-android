package com.shinyanemoto.talktrace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SlateBlue,
    secondary = SoftBlue,
    surface = WarmSurface,
    surfaceVariant = SoftAccent,
)

private val DarkColors = darkColorScheme(
    primary = SoftAccent,
    secondary = SoftBlue,
)

@Composable
fun TalkTraceTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
