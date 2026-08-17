package com.mikeyphw.studyflow.desktop

/** Creates the desktop-only composition root.
 *
 * This is the first real desktop bootstrap. It wires domain use cases to small
 * JVM in-memory repositories so the Compose app can render real StudyFlow model
 * state before Android Room/DataStore adapters are made portable.
 */
object StudyFlowDesktopBootstrap {
    fun createAppState(
        platformPorts: DesktopPlatformPorts = NoOpDesktopPlatformPorts,
    ): DesktopAppState = DesktopAppState(
        services = createDesktopStudyFlowServices(platformPorts),
        shellState = createDesktopShellState(),
    )
}
