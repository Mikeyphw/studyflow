package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.nio.file.Path

/** Creates the desktop-only composition root.
 *
 * The ordinary factory remains side-effect free for tests and embedders. The
 * production factory adds durable Desktop workspace state plus the persistent
 * SFLP peer and starts its listeners.
 */
object StudyFlowDesktopBootstrap {
    fun createAppState(
        platformPorts: DesktopPlatformPorts = NoOpDesktopPlatformPorts,
        linkService: DesktopStudyFlowLinkService? = null,
        workspacePersistence: DesktopWorkspacePersistence? = null,
    ): DesktopAppState {
        val services = createDesktopStudyFlowServices(
            platformPorts = platformPorts,
            linkService = linkService,
            workspacePersistence = workspacePersistence,
        )
        services.defaultPlanId?.let { planId ->
            linkService?.registerWorkspace(StudyFlowWorkspaceId("plan-${planId.value}"))
        }
        return DesktopAppState(
            services = services,
            shellState = createDesktopShellState(),
        ).also { appState ->
            services.attentionRuntime?.bind(appState)
            services.attentionRuntime?.start()
        }
    }

    fun createProductionAppState(
        platformPorts: DesktopPlatformPorts = NativeDesktopPlatformPorts,
        linkService: DesktopStudyFlowLinkService = DesktopStudyFlowLinkService(),
        workspaceRoot: Path = defaultDesktopWorkspaceRoot(),
    ): DesktopAppState {
        val persistence = DesktopWorkspacePersistence.open(
            root = workspaceRoot,
            seed = DesktopWorkspaceSnapshot.seed(DesktopStudyFlowSampleData.create()),
        )
        linkService.start()
        return createAppState(platformPorts, linkService, persistence)
    }
}
