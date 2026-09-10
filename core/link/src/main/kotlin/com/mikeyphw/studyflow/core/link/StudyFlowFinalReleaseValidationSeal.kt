package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@JvmInline
value class StudyFlowFinalReleaseSealId(val value: String) {
    init {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            "Final release seal ID must be a wire token"
        }
    }
}

@Serializable
enum class StudyFlowFinalReleaseSurface {
    @SerialName("core_link") CORE_LINK,
    @SerialName("android_phone") ANDROID_PHONE,
    @SerialName("android_tv") ANDROID_TV,
    @SerialName("desktop") DESKTOP,
    @SerialName("web") WEB,
    @SerialName("release_artifact") RELEASE_ARTIFACT,
}

@Serializable
data class StudyFlowFinalValidationEvidence(
    val taskName: String,
    val surface: StudyFlowFinalReleaseSurface,
    val required: Boolean = true,
) {
    init { require(taskName.isNotBlank()) { "Final validation task name must not be blank" } }
}

@Serializable
data class StudyFlowFinalReleaseValidationSeal(
    val sealId: StudyFlowFinalReleaseSealId,
    val workspaceId: StudyFlowWorkspaceId,
    val requiredTasks: List<StudyFlowFinalValidationEvidence>,
    val requiredArtifacts: List<String>,
    val createdAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(requiredTasks.isNotEmpty()) { "Final release seal must name required tasks" }
        require(requiredTasks.map { it.taskName }.distinct().size == requiredTasks.size) {
            "Final release tasks must be unique"
        }
        require(requiredArtifacts.distinct().size == requiredArtifacts.size) {
            "Final release artifacts must be unique"
        }
        require(createdAtEpochMillis >= 0L) { "Final release seal time must not be negative" }
        val names = requiredTaskNames().toSet()
        require(StudyFlowSflp23ReleaseGate.RequiredSflpTasks.all { it in names }) {
            "Final release seal must include the complete SFLP23 predecessor chain"
        }
    }

    fun requiredTaskNames(): List<String> = requiredTasks.filter { it.required }.map { it.taskName }

    fun coversAllPlatforms(): Boolean {
        val surfaces = requiredTasks.map { it.surface }.toSet()
        return StudyFlowSflp24FinalReleaseGate.RequiredSurfaces.all { it in surfaces }
    }

    fun containsFinalGate(): Boolean = StudyFlowSflp24FinalReleaseGate.FinalTaskName in requiredTaskNames()
}

object StudyFlowSflp24FinalReleaseGate {
    const val FinalTaskName = "verifySflp24FinalReleaseSeal"

    val RequiredSurfaces: Set<StudyFlowFinalReleaseSurface> = setOf(
        StudyFlowFinalReleaseSurface.CORE_LINK,
        StudyFlowFinalReleaseSurface.ANDROID_PHONE,
        StudyFlowFinalReleaseSurface.ANDROID_TV,
        StudyFlowFinalReleaseSurface.DESKTOP,
        StudyFlowFinalReleaseSurface.WEB,
        StudyFlowFinalReleaseSurface.RELEASE_ARTIFACT,
    )

    val RequiredTasks: List<String> = listOf(FinalTaskName) + StudyFlowSflp23ReleaseGate.RequiredSflpTasks

    fun defaultSeal(
        workspaceId: StudyFlowWorkspaceId = StudyFlowWorkspaceId("release-sflp-v1"),
        createdAtEpochMillis: Long,
    ): StudyFlowFinalReleaseValidationSeal = StudyFlowFinalReleaseValidationSeal(
        sealId = StudyFlowFinalReleaseSealId("sflp24-final-release-seal"),
        workspaceId = workspaceId,
        requiredTasks = RequiredTasks.map { taskName ->
            StudyFlowFinalValidationEvidence(
                taskName = taskName,
                surface = surfaceFor(taskName),
            )
        },
        requiredArtifacts = listOf(
            ".devtool-artifact.json",
            "protocol/sflp/v1/golden/final-release-validation-plan.json",
            "scripts/verify_sflp24_final_release_seal.py",
        ),
        createdAtEpochMillis = createdAtEpochMillis,
    )

    fun surfaceFor(taskName: String): StudyFlowFinalReleaseSurface = when {
        taskName.startsWith(":app-tv") -> StudyFlowFinalReleaseSurface.ANDROID_TV
        taskName.startsWith(":app") -> StudyFlowFinalReleaseSurface.ANDROID_PHONE
        taskName.startsWith(":desktop") -> StudyFlowFinalReleaseSurface.DESKTOP
        taskName.startsWith(":core:link") -> StudyFlowFinalReleaseSurface.CORE_LINK
        taskName.contains("Web", ignoreCase = true) -> StudyFlowFinalReleaseSurface.WEB
        else -> StudyFlowFinalReleaseSurface.RELEASE_ARTIFACT
    }
}
