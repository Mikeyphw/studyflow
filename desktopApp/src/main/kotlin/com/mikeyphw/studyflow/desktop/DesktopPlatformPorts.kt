package com.mikeyphw.studyflow.desktop

/** Desktop-safe platform boundary. Android intents, WorkManager, notification channels and Android storage APIs stay out. */
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

data class DesktopNotificationCapabilities(
    val nativeNotificationsSupported: Boolean,
    val traySupported: Boolean,
    val actionButtonsSupported: Boolean,
    val clickRoutingSupported: Boolean,
)

data class DesktopNotification(
    val id: String,
    val title: String,
    val message: String,
    val defaultAction: DesktopAttentionSurfaceAction = DesktopAttentionSurfaceAction.OPEN_ATTENTION,
)

data class DesktopTrayDashboard(
    val headline: String,
    val nextStudy: String,
    val dueReviews: String,
    val activeSession: String?,
    val progress: String,
    val remindersPaused: Boolean,
)

enum class DesktopAttentionSurfaceAction {
    OPEN_ATTENTION,
    OPEN_TODAY,
    START_NEXT,
    REVIEW_DUE,
    TOGGLE_PAUSE,
    OPEN_NOTIFICATION_CENTER,
    SNOOZE_CURRENT,
    DISMISS_CURRENT,
}

interface DesktopNotificationPort {
    fun showInfo(title: String, message: String)
    fun capabilities(): DesktopNotificationCapabilities = DesktopNotificationCapabilities(false, false, false, false)
    fun show(notification: DesktopNotification) = showInfo(notification.title, notification.message)
    fun dismiss(notificationId: String) = Unit
    fun updateTray(dashboard: DesktopTrayDashboard?) = Unit
    fun setActionHandler(handler: (DesktopAttentionSurfaceAction, String?) -> Unit) = Unit
    fun close() = Unit
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
