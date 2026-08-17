package com.mikeyphw.studyflow.desktop

/**
 * Shared-module contract for the desktop app.
 *
 * The first desktop overlays are allowed to reuse JVM-safe business and model
 * code only. Android UI modules, Android persistence modules, and feature
 * screens remain outside the desktop dependency graph until an explicit shared
 * boundary is introduced for them.
 */
object StudyFlowDesktopBoundary {
    const val appName: String = "StudyFlow"

    val allowedSharedModules: List<String> = listOf(
        ":core:model",
        ":core:domain",
        ":core:portability",
    )

    val forbiddenProjectDependencyPrefixes: List<String> = listOf(
        ":app",
        ":feature:",
        ":core:data",
        ":core:database",
        ":core:designsystem",
        ":core:diagnostics",
        ":core:i18n",
        ":core:navigation",
        ":core:testing",
    )

    fun isAllowedSharedDependency(projectPath: String): Boolean = projectPath in allowedSharedModules

    fun isForbiddenProjectDependency(projectPath: String): Boolean =
        forbiddenProjectDependencyPrefixes.any { prefix ->
            projectPath == prefix || projectPath.startsWith(prefix)
        }
}

data class DesktopShellState(
    val appName: String,
    val status: String,
    val allowedSharedModules: List<String>,
    val boundarySummary: String,
)

fun createDesktopShellState(): DesktopShellState = DesktopShellState(
    appName = StudyFlowDesktopBoundary.appName,
    status = "Desktop shell ready",
    allowedSharedModules = StudyFlowDesktopBoundary.allowedSharedModules,
    boundarySummary = "Using JVM-safe StudyFlow model, domain, and portability modules only.",
)
