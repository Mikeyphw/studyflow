package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ReleaseValidationGate
import com.mikeyphw.studyflow.core.model.ReleaseValidationGateKind
import com.mikeyphw.studyflow.core.model.ReleaseValidationGateStatus
import com.mikeyphw.studyflow.core.model.ReleaseValidationSeal

object ReleaseValidationSealFactory {
    private const val CurrentPhase = 27

    fun sourceOnlySeal(generatedAtEpochMillis: Long): ReleaseValidationSeal = ReleaseValidationSeal(
        currentPhase = CurrentPhase,
        validatedPhase = CurrentPhase,
        generatedAtEpochMillis = generatedAtEpochMillis,
        gates = listOf(
            pass(
                ReleaseValidationGateKind.ARTIFACT_MANIFEST,
                "Artifact manifest",
                "Inventory, hashes, and validator script are coherent.",
            ),
            pass(
                ReleaseValidationGateKind.SOURCE_CONTRACTS,
                "Source contracts",
                "Phase 0 through 27 validators pass.",
            ),
            blocked(
                ReleaseValidationGateKind.GRADLE_CONFIGURATION,
                "Gradle configuration",
                "Must run on the Android build host.",
            ),
            blocked(
                ReleaseValidationGateKind.MAIN_COMPILATION,
                "Main source compilation",
                "Must compile every affected Android and JVM module.",
            ),
            blocked(
                ReleaseValidationGateKind.TEST_COMPILATION,
                "Test source compilation",
                "Must compile changed and historical test source sets.",
            ),
            blocked(
                ReleaseValidationGateKind.UNIT_TESTS,
                "Unit tests",
                "Must run all tasks included by checkAll.",
            ),
            blocked(
                ReleaseValidationGateKind.ROOM_SCHEMA,
                "Room schema",
                "Must verify exported schemas and migrations with Room validation.",
            ),
            blocked(
                ReleaseValidationGateKind.RESOURCE_MERGE,
                "Resource merge",
                "Must process app and feature Android resources.",
            ),
            blocked(
                ReleaseValidationGateKind.LINT,
                "Lint",
                "Must complete lint with zero unexpected diagnostics.",
            ),
            blocked(
                ReleaseValidationGateKind.ASSEMBLE,
                "Assemble",
                "Must assemble the debug or release candidate APK.",
            ),
            blocked(
                ReleaseValidationGateKind.DEVICE_SMOKE,
                "Device smoke",
                "Must run runtime smoke probes on a device or emulator.",
            ),
            pass(
                ReleaseValidationGateKind.FINAL_STAGING,
                "Final staging",
                "Overlay payload paths can be staged without reserved metadata entries.",
            ),
        ),
    )

    fun completedSeal(
        generatedAtEpochMillis: Long,
        deviceSmokeEvidence: String,
    ): ReleaseValidationSeal {
        val sourceSeal = sourceOnlySeal(generatedAtEpochMillis)
        return sourceSeal.copy(
            gates = sourceSeal.gates.map { gate -> gate.toCompletedGate(deviceSmokeEvidence) },
        )
    }

    fun requiredKinds(): Set<ReleaseValidationGateKind> = ReleaseValidationGateKind.values().toSet()

    private fun ReleaseValidationGate.toCompletedGate(deviceSmokeEvidence: String): ReleaseValidationGate =
        when (kind) {
            ReleaseValidationGateKind.GRADLE_CONFIGURATION -> passed("Gradle configuration completed on target.")
            ReleaseValidationGateKind.MAIN_COMPILATION -> passed("Main sources compiled on target.")
            ReleaseValidationGateKind.TEST_COMPILATION -> passed("Test sources compiled on target.")
            ReleaseValidationGateKind.UNIT_TESTS -> passed("checkAll unit tests completed on target.")
            ReleaseValidationGateKind.ROOM_SCHEMA -> passed("Room schema and migration validation completed on target.")
            ReleaseValidationGateKind.RESOURCE_MERGE -> passed("Android resources processed on target.")
            ReleaseValidationGateKind.LINT -> passed("Lint completed on target.")
            ReleaseValidationGateKind.ASSEMBLE -> passed("APK assembled on target.")
            ReleaseValidationGateKind.DEVICE_SMOKE -> passed(deviceSmokeEvidence)
            else -> this
        }

    private fun pass(
        kind: ReleaseValidationGateKind,
        label: String,
        evidence: String,
    ): ReleaseValidationGate = ReleaseValidationGate(kind, ReleaseValidationGateStatus.PASS, label, evidence)

    private fun blocked(
        kind: ReleaseValidationGateKind,
        label: String,
        evidence: String,
    ): ReleaseValidationGate = ReleaseValidationGate(kind, ReleaseValidationGateStatus.BLOCKED, label, evidence)

    private fun ReleaseValidationGate.passed(evidence: String): ReleaseValidationGate =
        copy(status = ReleaseValidationGateStatus.PASS, evidence = evidence)
}
