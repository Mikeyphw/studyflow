package com.mikeyphw.studyflow.desktop

import java.awt.Desktop
import java.awt.GraphicsEnvironment
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
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath().toAbsolutePath().normalize().toString()
        } else {
            null
        }
    }

    override fun chooseExportPath(suggestedFileName: String): String? {
        if (GraphicsEnvironment.isHeadless()) return null
        val chooser = JFileChooser().apply { selectedFile = java.io.File(suggestedFileName) }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath().toAbsolutePath().normalize().toString()
        } else {
            null
        }
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
    private val trayIcon: TrayIcon? by lazy {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return@lazy null
        runCatching {
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            TrayIcon(image, StudyFlowDesktopBoundary.appName).also { icon ->
                icon.isImageAutoSize = true
                SystemTray.getSystemTray().add(icon)
            }
        }.getOrNull()
    }

    override fun showInfo(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }
}
