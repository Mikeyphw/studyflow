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
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7E9F0),
    outlineVariant = Color(0xFFE1E4EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9295FF),
    onPrimary = Color(0xFF171A68),
    primaryContainer = Color(0xFF303487),
    onPrimaryContainer = Color(0xFFE4E4FF),
    secondary = Color(0xFF8DB4FF),
    onSecondary = Color(0xFF082E63),
    secondaryContainer = Color(0xFF173F75),
    onSecondaryContainer = Color(0xFFD8E5FF),
    tertiary = Color(0xFF72D7BC),
    background = Color(0xFF0E1015),
    surface = Color(0xFF151820),
    surfaceVariant = Color(0xFF292E3A),
    outlineVariant = Color(0xFF292E3A),
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
