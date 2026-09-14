package com.mikeyphw.studyflow.desktop

import java.awt.Desktop
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.Path
import javax.swing.JFileChooser

object NativeDesktopPlatformPorts : DesktopPlatformPorts {
    override val fileDialog: DesktopFileDialogPort = SwingDesktopFileDialogPort
    override val notifications: DesktopNotificationPort = AwtDesktopNotificationPort
    override val filesystem: DesktopFilesystemPort = AwtDesktopFilesystemPort
}

private object SwingDesktopFileDialogPort : DesktopFileDialogPort {
    override fun chooseImportPath(): String? {
        if (GraphicsEnvironment.isHeadless()) return null
        val chooser = JFileChooser()
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath().toAbsolutePath().normalize().toString() else null
    }

    override fun chooseExportPath(suggestedFileName: String): String? {
        if (GraphicsEnvironment.isHeadless()) return null
        val chooser = JFileChooser().apply { selectedFile = java.io.File(suggestedFileName) }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath().toAbsolutePath().normalize().toString() else null
    }
}

private object AwtDesktopFilesystemPort : DesktopFilesystemPort {
    override fun openPath(path: String) {
        require(Desktop.isDesktopSupported()) { "Native Desktop integration is unavailable." }
        val target = Path.of(path).toAbsolutePath().normalize().toFile()
        require(target.exists()) { "The selected file no longer exists." }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.OPEN)) { "Desktop open-with is unavailable." }
        desktop.open(target)
    }

    override fun openUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" || uri.scheme == "http") { "Only HTTP(S) browser links are allowed." }
        require(Desktop.isDesktopSupported()) { "Native Desktop integration is unavailable." }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.BROWSE)) { "Desktop browser integration is unavailable." }
        desktop.browse(uri)
    }
}

private object AwtDesktopNotificationPort : DesktopNotificationPort {
    @Volatile private var handler: ((DesktopAttentionSurfaceAction, String?) -> Unit)? = null
    @Volatile private var currentNotificationId: String? = null
    @Volatile private var trayIconRef: TrayIcon? = null

    private val traySupported: Boolean
        get() = !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()

    @Synchronized
    private fun trayIcon(): TrayIcon? {
        if (!traySupported) return null
        val tray = SystemTray.getSystemTray()
        trayIconRef?.let { existing ->
            if (tray.trayIcons.any { it === existing }) return existing
            trayIconRef = null
        }
        return runCatching {
            val popup = PopupMenu()
            val openToday = MenuItem("Open Today").apply { addActionListener { fire(DesktopAttentionSurfaceAction.OPEN_TODAY) } }
            val startNext = MenuItem("Start next study block").apply { addActionListener { fire(DesktopAttentionSurfaceAction.START_NEXT) } }
            val reviewDue = MenuItem("Review due").apply { addActionListener { fire(DesktopAttentionSurfaceAction.REVIEW_DUE) } }
            val snooze = MenuItem("Snooze current reminder").apply { addActionListener { fire(DesktopAttentionSurfaceAction.SNOOZE_CURRENT, currentNotificationId) } }
            val dismiss = MenuItem("Dismiss current reminder").apply { addActionListener { fire(DesktopAttentionSurfaceAction.DISMISS_CURRENT, currentNotificationId) } }
            val pause = MenuItem("Pause / resume reminders").apply { addActionListener { fire(DesktopAttentionSurfaceAction.TOGGLE_PAUSE) } }
            val center = MenuItem("Open notifications").apply { addActionListener { fire(DesktopAttentionSurfaceAction.OPEN_NOTIFICATION_CENTER) } }
            listOf(openToday, startNext, reviewDue, snooze, dismiss, pause, center).forEach(popup::add)
            TrayIcon(createStudyFlowTrayImage(), StudyFlowDesktopBoundary.appName, popup).also { icon ->
                icon.isImageAutoSize = true
                icon.addActionListener { fire(DesktopAttentionSurfaceAction.OPEN_ATTENTION, currentNotificationId) }
                tray.add(icon)
                trayIconRef = icon
            }
        }.getOrNull()
    }

    override fun capabilities(): DesktopNotificationCapabilities = DesktopNotificationCapabilities(
        nativeNotificationsSupported = trayIcon() != null,
        traySupported = trayIcon() != null,
        actionButtonsSupported = false,
        clickRoutingSupported = trayIcon() != null,
    )

    override fun showInfo(title: String, message: String) {
        trayIcon()?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }

    override fun show(notification: DesktopNotification) {
        currentNotificationId = notification.id
        trayIcon()?.displayMessage(notification.title, notification.message, TrayIcon.MessageType.INFO)
    }

    override fun dismiss(notificationId: String) {
        if (currentNotificationId == notificationId) currentNotificationId = null
    }

    override fun updateTray(dashboard: DesktopTrayDashboard?) {
        val icon = trayIcon() ?: return
        icon.toolTip = dashboard?.let {
            buildString {
                append(it.headline)
                append(" — ")
                append(it.dueReviews)
                append(" — ")
                append(it.nextStudy)
                it.activeSession?.let { session -> append(" — ").append(session) }
                if (it.remindersPaused) append(" — reminders paused")
            }.take(120)
        } ?: StudyFlowDesktopBoundary.appName
    }

    override fun setActionHandler(handler: (DesktopAttentionSurfaceAction, String?) -> Unit) {
        this.handler = handler
        trayIcon()
    }

    @Synchronized
    override fun close() {
        trayIconRef?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        trayIconRef = null
        currentNotificationId = null
        handler = null
    }

    private fun fire(action: DesktopAttentionSurfaceAction, notificationId: String? = null) {
        val callback = handler ?: return
        runCatching { callback(action, notificationId) }.onFailure { error ->
            trayIconRef?.displayMessage(
                "StudyFlow action failed",
                error.message?.take(96) ?: "Desktop action could not be completed.",
                TrayIcon.MessageType.ERROR,
            )
        }
    }

    private fun createStudyFlowTrayImage(): BufferedImage = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).also { image ->
        val g: Graphics2D = image.createGraphics()
        try {
            g.fillRoundRect(3, 3, 26, 26, 8, 8)
            g.clearRect(9, 9, 14, 3)
            g.clearRect(9, 15, 14, 3)
            g.clearRect(9, 21, 9, 3)
        } finally {
            g.dispose()
        }
    }
}
