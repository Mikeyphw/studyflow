package com.mikeyphw.studyflow.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main(args: Array<String>) = application {
    val appState = remember { StudyFlowDesktopBootstrap.createProductionAppState() }
    LaunchedEffect(appState) {
        args.firstOrNull { it.startsWith("studyflow://", ignoreCase = true) }?.let(appState::handleDeepLink)
    }
    Window(
        onCloseRequest = {
            appState.close()
            exitApplication()
        },
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
        title = StudyFlowDesktopBoundary.appName,
    ) {
        StudyFlowDesktopApp(appState)
    }
}
