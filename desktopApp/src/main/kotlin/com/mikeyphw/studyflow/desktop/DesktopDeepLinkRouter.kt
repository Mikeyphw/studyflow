package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.net.URI

object DesktopDeepLinkRouter {
    fun route(rawUri: String, appState: DesktopAppState): Boolean {
        val uri = runCatching { URI(rawUri.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("studyflow", ignoreCase = true)) return false
        val parts = buildList {
            uri.host?.takeIf(String::isNotBlank)?.let(::add)
            addAll(uri.path.orEmpty().split('/').filter(String::isNotBlank))
        }
        return when (parts.firstOrNull()?.lowercase()) {
            "route" -> routeScreen(parts.getOrNull(1), appState)
            "plan" -> parts.getOrNull(1)?.let { id ->
                runCatching {
                    appState.selectPlan(StudyPlanId(id))
                    appState.selectRoute(DesktopRoute.PLAN)
                }.isSuccess
            } ?: false
            "library" -> parts.getOrNull(1)?.let { id ->
                appState.selectMaterial(StudyMaterialId(id))
                appState.selectRoute(DesktopRoute.LIBRARY)
                true
            } ?: false
            else -> false
        }
    }

    private fun routeScreen(rawRoute: String?, appState: DesktopAppState): Boolean {
        val route = rawRoute?.let { candidate ->
            DesktopRoute.entries.firstOrNull { route ->
                route.name.equals(candidate.replace('-', '_'), ignoreCase = true) ||
                    route.title.replace(" ", "-").equals(candidate, ignoreCase = true)
            }
        } ?: return false
        appState.selectRoute(route)
        return true
    }
}
