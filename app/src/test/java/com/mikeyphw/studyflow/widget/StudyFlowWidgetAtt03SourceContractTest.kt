package com.mikeyphw.studyflow.widget

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWidgetAtt03SourceContractTest {
    @Test
    fun widgetSuiteOwnsConfigurationFreshnessModesActionsAndPreview() {
        val root = repositoryRoot()
        val provider = read(root, "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt")
        val coordinator = read(root, "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt")
        val freshness = read(root, "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetFreshnessScheduler.kt")
        val config = read(root, "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetConfigurationStore.kt")
        val info = read(root, "app/src/main/res/xml/studyflow_today_widget_info.xml")

        StudyFlowWidgetMode.entries.forEach { mode -> assertTrue(mode.name in provider || mode.name in config) }
        assertTrue("StudyFlowWidgetActionType.START_NEXT" in provider)
        assertTrue("StudyFlowWidgetActionType.TOGGLE_SESSION" in provider)
        assertTrue("AlarmManager.RTC_WAKEUP" in freshness)
        assertTrue("setWindow" in freshness)
        assertTrue("previewLayout" in info)
        assertTrue("reconfigurable|configuration_optional" in info)
        assertTrue("StudyFlowWidgetConfigureActivity" in info)
        assertFalse("POST_NOTIFICATIONS" in coordinator)
        assertFalse("notificationPermissionGranted" in coordinator)
    }

    private fun read(root: Path, relative: String): String = Files.readString(root.resolve(relative))

    private fun repositoryRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        repeat(6) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) return current
            current = current.parent ?: return@repeat
        }
        error("StudyFlow repository root not found")
    }
}
