package com.mikeyphw.studyflow.tv.attention

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AttentionRoadmapPostsealTvContractTest {
    @Test
    fun `tv ingress and calendar preview use live delivery and canonical schedule time`() {
        val activity = readText(Path.of("src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt"))
        val adapter = readText(
            Path.of("src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt"),
        )

        assertContains(activity, "sflpPeer.setNotificationDeliveryHandler")
        assertContains(activity, "tvAttentionRuntime.ingest(delivery)")
        assertContains(activity, "item.scheduledStartEpochMillis ?: return@mapNotNull null")
        assertContains(activity, "durationMinutes = item.plannedDurationMinutes ?: 30")
        assertFalse(activity.contains("System.currentTimeMillis() + (index + 1) * 60L * 60L * 1_000L"))
        assertContains(adapter, "scheduledStartEpochMillis = item.scheduledStartEpochMillis")
        assertContains(adapter, "plannedDurationMinutes = item.plannedDurationMinutes")
    }

    private fun readText(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)
}
