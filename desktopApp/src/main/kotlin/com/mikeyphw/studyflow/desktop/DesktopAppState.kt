package com.mikeyphw.studyflow.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.ThemeMode

class DesktopAppState(
    val services: DesktopStudyFlowServices,
    val shellState: DesktopShellState,
) {
    var selectedRoute: DesktopRoute by mutableStateOf(DesktopRoute.DASHBOARD)
        private set

    var selectedMaterialId: StudyMaterialId? by mutableStateOf(
        services.defaultPlanId?.let { planId ->
            services.libraryRepository.currentMaterials(planId).firstOrNull()?.id
        },
    )
        private set

    var searchQuery: String by mutableStateOf("compose")
        private set

    var linkQrInput: String by mutableStateOf("")
        private set

    var linkManualEndpoint: String by mutableStateOf("")
        private set

    var linkActionMessage: String by mutableStateOf("")
        private set

    private var linkRevision: Int by mutableStateOf(0)

    fun selectRoute(route: DesktopRoute) {
        selectedRoute = route
    }

    fun selectMaterial(materialId: StudyMaterialId) {
        selectedMaterialId = materialId
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun cycleThemeMode() {
        val next = when (settingsState().themeMode) {
            ThemeMode.SYSTEM_DEFAULT -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM_DEFAULT
        }
        services.setTheme(next)
    }

    fun cycleLanguage() {
        val next = when (settingsState().language) {
            AppLanguage.SYSTEM_DEFAULT -> AppLanguage.ENGLISH
            AppLanguage.ENGLISH -> AppLanguage.PORTUGUESE_BRAZIL
            AppLanguage.PORTUGUESE_BRAZIL -> AppLanguage.SYSTEM_DEFAULT
        }
        services.setLanguage(next)
    }

    fun dashboardState(): DesktopDashboardState = services.createDashboardState()

    fun libraryState(): DesktopLibraryScreenState = services.createLibraryState()

    fun plannerState(): DesktopPlannerScreenState = services.createPlannerState()

    fun practiceState(): DesktopPracticeScreenState = services.createPracticeState()

    fun analyticsState(): DesktopAnalyticsScreenState = services.createAnalyticsState()

    fun searchState(): DesktopSearchScreenState = services.createSearchState(searchQuery)

    fun settingsState(): DesktopSettingsScreenState = services.createSettingsState()

    fun updateLinkQrInput(value: String) {
        linkQrInput = value
    }

    fun updateLinkManualEndpoint(value: String) {
        linkManualEndpoint = value
    }

    fun createLinkPairingOffer() {
        val service = services.linkService ?: return
        linkActionMessage = runCatching {
            val offer = service.createPairingOffer()
            "Pairing offer ready until ${offer.expiresAtEpochMillis}"
        }.getOrElse { error -> "Pairing offer failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun pairLinkFromQr() {
        val service = services.linkService ?: return
        linkActionMessage = runCatching {
            val peer = service.pairFromQr(linkQrInput.trim())
            "Paired with ${peer.peer.displayName}"
        }.getOrElse { error -> "Pairing failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun rememberLinkEndpoint() {
        val service = services.linkService ?: return
        linkActionMessage = runCatching {
            service.rememberManualEndpoint(linkManualEndpoint.trim())
            "Endpoint remembered"
        }.getOrElse { error -> "Endpoint failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun syncLinkPeers() {
        val service = services.linkService ?: return
        val workspace = linkWorkspaceId() ?: return
        linkActionMessage = runCatching {
            val results = service.syncAll(workspace)
            "Sync complete for ${results.size} peer(s)"
        }.getOrElse { error -> "Sync failed: ${error.message.orEmpty()}" }
        linkRevision += 1
    }

    fun revokeLinkPeer(deviceId: String) {
        services.linkService?.revoke(StudyFlowDeviceId(deviceId))
        linkActionMessage = "Peer revoked"
        linkRevision += 1
    }

    fun forgetLinkPeer(deviceId: String) {
        services.linkService?.forget(StudyFlowDeviceId(deviceId))
        linkActionMessage = "Peer forgotten"
        linkRevision += 1
    }

    fun linkState(): DesktopStudyFlowLinkState? {
        linkRevision // Compose dependency for explicit UI actions.
        return services.linkService?.snapshot(linkWorkspaceId())
    }

    fun close() {
        services.linkService?.close()
    }

    private fun linkWorkspaceId(): StudyFlowWorkspaceId? = services.defaultPlanId?.let { planId ->
        StudyFlowWorkspaceId("plan-${planId.value}")
    }
}

enum class DesktopRoute(val title: String) {
    DASHBOARD("Dashboard"),
    LIBRARY("Library"),
    PLANNER("Planner"),
    PRACTICE("Practice"),
    ANALYTICS("Analytics"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

enum class DesktopScreenStatus {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
}
