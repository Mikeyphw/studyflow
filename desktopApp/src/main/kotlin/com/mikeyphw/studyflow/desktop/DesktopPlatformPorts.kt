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
}

interface DesktopFileDialogPort {
    fun chooseImportPath(): String?

    fun chooseExportPath(suggestedFileName: String): String?
}

interface DesktopNotificationPort {
    fun showInfo(title: String, message: String)
}

object NoOpDesktopPlatformPorts : DesktopPlatformPorts {
    override val fileDialog: DesktopFileDialogPort = object : DesktopFileDialogPort {
        override fun chooseImportPath(): String? = null

        override fun chooseExportPath(suggestedFileName: String): String? = null
    }

    override val notifications: DesktopNotificationPort = object : DesktopNotificationPort {
        override fun showInfo(title: String, message: String) = Unit
    }
}
