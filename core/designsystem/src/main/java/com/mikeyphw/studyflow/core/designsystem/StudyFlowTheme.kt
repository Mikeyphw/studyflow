package com.mikeyphw.studyflow.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mikeyphw.studyflow.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5154D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E3FF),
    onPrimaryContainer = Color(0xFF20236E),
    secondary = Color(0xFF3976E8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7FF),
    onSecondaryContainer = Color(0xFF163D78),
    tertiary = Color(0xFF23856D),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF191B23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B23),
    surfaceVariant = Color(0xFFE7E9F0),
    onSurfaceVariant = Color(0xFF5C6270),
    outline = Color(0xFF777D8C),
    outlineVariant = Color(0xFFE1E4EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C8FF),
    onPrimary = Color(0xFF16234A),
    primaryContainer = Color(0xFF4B638F),
    onPrimaryContainer = Color(0xFFE9EEFF),
    secondary = Color(0xFFAFC7FF),
    onSecondary = Color(0xFF102544),
    secondaryContainer = Color(0xFF263B61),
    onSecondaryContainer = Color(0xFFE2EAFF),
    tertiary = Color(0xFF8CE3CA),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF101319),
    onBackground = Color(0xFFE7EAF3),
    surface = Color(0xFF151922),
    onSurface = Color(0xFFE7EAF3),
    surfaceVariant = Color(0xFF303641),
    onSurfaceVariant = Color(0xFFC5CAD7),
    outline = Color(0xFF8F96A5),
    outlineVariant = Color(0xFF3B424F),
)

@Composable
fun StudyFlowTheme(
    themeMode: ThemeMode,
    dynamicColorEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = StudyFlowTypography,
        content = content,
    )
}
