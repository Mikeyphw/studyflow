package com.mikeyphw.studyflow.desktop

import androidx.compose.runtime.Composable
import com.mikeyphw.studyflow.core.model.ThemeMode

fun resolveDesktopDarkTheme(themeMode: ThemeMode, systemDarkTheme: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM_DEFAULT -> systemDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun desktopThemeModeLabel(themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.SYSTEM_DEFAULT -> desktopText("System default")
    ThemeMode.LIGHT -> desktopText("Light")
    ThemeMode.DARK -> desktopText("Dark")
}
