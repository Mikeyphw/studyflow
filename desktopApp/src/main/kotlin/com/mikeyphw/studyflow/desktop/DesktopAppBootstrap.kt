package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

/** Creates the desktop-only composition root.
 *
 * The ordinary factory remains side-effect free for tests and embedders. The
 * production factory adds the persistent SFLP peer and starts its listeners.
 */
object StudyFlowDesktopBootstrap {
    fun createAppState(
        platformPorts: DesktopPlatformPorts = NoOpDesktopPlatformPorts,
        linkService: DesktopStudyFlowLinkService? = null,
    ): DesktopAppState {
        val services = createDesktopStudyFlowServices(
            platformPorts = platformPorts,
            linkService = linkService,
        )
        services.defaultPlanId?.let { planId ->
            linkService?.registerWorkspace(StudyFlowWorkspaceId("plan-${planId.value}"))
        }
        return DesktopAppState(
            services = services,
            shellState = createDesktopShellState(),
        )
    }

    fun createProductionAppState(
        platformPorts: DesktopPlatformPorts = NoOpDesktopPlatformPorts,
        linkService: DesktopStudyFlowLinkService = DesktopStudyFlowLinkService(),
    ): DesktopAppState {
        linkService.start()
        return createAppState(platformPorts, linkService)
    }
}
