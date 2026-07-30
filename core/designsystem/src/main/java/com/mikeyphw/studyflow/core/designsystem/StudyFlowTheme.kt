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
    primary = Color(0xFF4557C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF08125C),
    secondary = Color(0xFF4A6354),
    secondaryContainer = Color(0xFFCCE9D5),
    tertiary = Color(0xFF715573),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE4E1EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF142577),
    primaryContainer = Color(0xFF2C3E9F),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB0CCBA),
    secondaryContainer = Color(0xFF324B3C),
    tertiary = Color(0xFFDFBBDF),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF46464F),
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
