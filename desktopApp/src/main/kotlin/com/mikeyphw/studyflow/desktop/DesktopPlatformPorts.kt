package com.mikeyphw.studyflow.desktop

/** Desktop-safe platform boundary for future overlays.
 *
 * Android intents, Android storage APIs, WorkManager jobs, and notification
 * channels must not cross into the desktop app. Later overlays should implement
 * these ports with desktop filesystem, tray, and notification adapters.
 */
interface DesktopPlatformPorts {
    val fileDialog: DesktopFileDialogPort
    val notifications: DesktopNotificationPort
    val filesystem: DesktopFilesystemPort
        get() = NoOpDesktopFilesystemPort
}

interface DesktopFileDialogPort {
    fun chooseImportPath(): String?

    fun chooseExportPath(suggestedFileName: String): String?
}

interface DesktopNotificationPort {
    fun showInfo(title: String, message: String)
}

interface DesktopFilesystemPort {
    fun openPath(path: String)

    fun openUrl(url: String)
}

object NoOpDesktopFilesystemPort : DesktopFilesystemPort {
    override fun openPath(path: String) = Unit

    override fun openUrl(url: String) = Unit
}

object NoOpDesktopPlatformPorts : DesktopPlatformPorts {
    override val fileDialog: DesktopFileDialogPort = object : DesktopFileDialogPort {
        override fun chooseImportPath(): String? = null

        override fun chooseExportPath(suggestedFileName: String): String? = null
    }

    override val notifications: DesktopNotificationPort = object : DesktopNotificationPort {
        override fun showInfo(title: String, message: String) = Unit
    }

    override val filesystem: DesktopFilesystemPort = NoOpDesktopFilesystemPort
}
