#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing required file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def load_json(relative: str) -> dict:
    try:
        value = json.loads(read(relative))
    except json.JSONDecodeError as exc:
        FAILURES.append(f"invalid JSON in {relative}: {exc}")
        return {}
    require(isinstance(value, dict), f"{relative} must contain a JSON object")
    return value if isinstance(value, dict) else {}


implementation = read("SFLP02_03_JOURNAL_RECONCILIATION.md")
architecture = read("docs/architecture/SFLP02_03_JOURNAL_RECONCILIATION.md")
roadmap = read("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md")
matrix = read("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md")
changelog = read("CHANGELOG.md")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
protocol = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocol.kt")
operation = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDomainOperation.kt")
journal = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOperationJournal.kt")
reconciliation = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowReconciliation.kt")
sync_protocol = read("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowSyncProtocol.kt")
journal_test = read("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOperationJournalTest.kt")
reconciliation_test = read(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowReconciliationEngineTest.kt",
)
sync_golden_test = read("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowSyncCodecGoldenTest.kt")
sync_wire_test = read("core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowSyncWireTest.kt")

for token in (
    "StudyFlowDomainOperation",
    "StudyFlowOperationKind",
    "originSequence",
    "entityRevision",
    "logicalTimestamp",
    "operationType",
    "occurredAtEpochMillis",
    "TOMBSTONE",
):
    require(token in operation, f"canonical operation model missing {token}")

for operation_type in (
    "goal.created",
    "goal.completed",
    "goal.paused",
    "question.attempt_recorded",
    "review.graded",
    "session.started",
    "session.paused",
    "session.completed",
    "material.added",
    "topic.priority_changed",
    "schedule.activity_moved",
):
    require(operation_type in operation, f"canonical roadmap operation type missing {operation_type}")

for token in (
    "StudyFlowOperationJournalStore",
    "InMemoryStudyFlowOperationJournalStore",
    "StudyFlowOperationJournal",
    "StudyFlowJournalFrontier",
    "StudyFlowOriginRevision",
    "StudyFlowJournalCheckpoint",
    "StudyFlowCheckpointId",
    "appendLocalMutation",
    "appendLocalTombstone",
    "appendAll",
    "missingFrom",
    "createCheckpoint",
    "replayAfter",
    "frontierFor",
    "validateBatchBeforeAppend",
):
    require(token in journal, f"journal implementation missing {token}")

require("localFrontier.sequenceFor" in journal, "missing-operation discovery does not stop at contiguous local truth")
require("Operation ID" in journal and "reused with different content" in journal, "operation-ID integrity check missing")
require("reused sequence" in journal, "origin sequence integrity check missing")
require("StudyFlowJournalAppendStatus.DUPLICATE" in journal, "idempotent duplicate suppression missing")
require("StudyFlowDomainOperationDeterministicComparator" in journal, "deterministic journal replay missing")

for token in (
    "StudyFlowOperationConflict",
    "StudyFlowConflictHandler",
    "StudyFlowKeepAllConflictHandler",
    "StudyFlowDeterministicWinnerConflictHandler",
    "StudyFlowConflictHandlerRegistry",
    "StudyFlowResolvedReplay",
    "StudyFlowSyncContinuationException",
    "StudyFlowSyncBatchIntegrityException",
    "StudyFlowReconciliationEngine",
    "nextBatch",
    "applyBatch",
    "resolvedReplay",
):
    require(token in reconciliation, f"reconciliation engine missing {token}")

require("restart from the receiver frontier" in reconciliation, "interrupted-sync recovery contract missing")
require("expectedThrough != declaredThrough" in reconciliation, "batch through-frontier integrity check missing")
require("localBefore.dominates(batch.baseFrontier)" in reconciliation, "batch continuation guard missing")
require("conflictHandlers.resolve" in reconciliation, "conflict-handler dispatch missing")

for token in (
    "StudyFlowSyncFrontierRequest",
    "StudyFlowSyncFrontierResponse",
    "StudyFlowSyncPullRequest",
    "StudyFlowSyncOperationsBatch",
    "sync.frontier",
    "sync.pull",
    "sync.operations",
    "StudyFlowSyncWire",
):
    require(token in sync_protocol, f"sync wire contract missing {token}")

require('val WorkspaceSync = capability("workspace.sync")' in protocol, "workspace.sync capability missing")
require("WorkspaceSync," in protocol, "workspace.sync is not part of the v1 capability vocabulary")

for test_token in (
    "localOperationsAdvanceOriginEntityAndLogicalRevisions",
    "duplicateOperationIsIdempotentButConflictingReuseIsRejected",
    "frontierOnlyAdvancesAcrossContiguousOriginSequences",
    "deterministicReplayDoesNotDependOnAppendArrivalOrder",
    "missingDiscoveryDoesNotAdvertiseOperationsBeyondAnOriginGap",
    "missingOperationDiscoveryUsesPeerFrontierAndBatchLimit",
    "appendAllPreflightsIntegritySoAFailedBatchDoesNotPartiallyMutateHistory",
    "checkpointCapturesFrontierWithoutCompactingAppendOnlyHistory",
    "tombstonesRemainFirstClassReplayableOperations",
):
    require(test_token in journal_test, f"journal tests missing {test_token}")

for test_token in (
    "peerFrontierDrivesBatchedMissingOperationTransfer",
    "interruptedTransferResumesFromReceiverFrontierAndDuplicateRetryIsSafe",
    "skippedBatchIsRejectedSoCallerCanRestartFromReceiverFrontier",
    "defaultConflictHandlerPicksSameWinnerRegardlessOfArrivalOrder",
    "appendOnlyConflictRuleCanKeepConcurrentEvents",
    "batchIntegrityRejectsDeclaredFrontierThatOperationsCannotReach",
):
    require(test_token in reconciliation_test, f"reconciliation tests missing {test_token}")

require("syncGoldenMessagesDecodeAndPayloadsRemainTyped" in sync_golden_test, "sync golden-vector JVM test missing")
require("frontierExchangeUsesTypedRequestAndCorrelatedResponse" in sync_wire_test, "frontier wire test missing")
require("pullAndOperationsResponseKeepWorkspaceAndCorrelationIdentity" in sync_wire_test, "pull wire test missing")

schema = load_json("protocol/sflp/v1/sflp-sync.schema.json")
require(schema.get("$schema", "").endswith("2020-12/schema"), "sync schema must use JSON Schema 2020-12")
defs = schema.get("$defs", {}) if isinstance(schema.get("$defs"), dict) else {}
for definition in (
    "originRevision",
    "frontier",
    "operation",
    "frontierRequest",
    "frontierResponse",
    "pullRequest",
    "operationsBatch",
):
    require(definition in defs, f"sync schema missing $defs.{definition}")

operation_required = set(defs.get("operation", {}).get("required", []))
for field in (
    "operationId",
    "workspaceId",
    "originDeviceId",
    "originSequence",
    "entityId",
    "entityRevision",
    "logicalTimestamp",
    "operationType",
    "kind",
    "occurredAtEpochMillis",
    "payload",
):
    require(field in operation_required, f"sync operation schema does not require {field}")

vectors = {
    "sync-frontier-request.json": ("request", "sync.frontier"),
    "sync-frontier-response.json": ("response", "sync.frontier"),
    "sync-pull-request.json": ("request", "sync.pull"),
    "sync-operations-response.json": ("response", "sync.operations"),
}
for filename, (expected_type, expected_name) in vectors.items():
    relative = f"protocol/sflp/v1/golden/{filename}"
    message = load_json(relative)
    require(message.get("protocol") == {"major": 1, "minor": 0}, f"{relative} is not SFLP 1.0")
    require(message.get("type") == expected_type, f"{relative} has wrong message type")
    require(message.get("name") == expected_name, f"{relative} has wrong message name")
    require(isinstance(message.get("payload"), dict), f"{relative} lacks object payload")
    if expected_type == "response":
        require(bool(message.get("correlationId")), f"{relative} response lacks correlationId")
    mirrored = ROOT / "core/link/src/test/resources/sflp/v1" / filename
    require(mirrored.is_file(), f"missing mirrored JVM golden vector {filename}")
    if mirrored.is_file():
        require((ROOT / relative).read_bytes() == mirrored.read_bytes(), f"golden mirror drifted: {filename}")

require("SFLP02 Domain operation journal — COMPLETE" in roadmap, "roadmap does not mark SFLP02 complete")
require("SFLP03 Reconciliation engine — COMPLETE" in roadmap, "roadmap does not mark SFLP03 complete")
require("canonical operation journal and reconciliation engine" in matrix, "capability matrix ignores SFLP02/SFLP03")
require("**not yet** provide a universal cross-client synchronization mesh" in matrix, "matrix overclaims live SFLP connectivity")
require("Storage durability is intentionally adapter-owned" in implementation, "journal persistence boundary missing")
require("SFLP04-SFLP12" in architecture, "architecture does not preserve later client/transport boundaries")
require("SFLP02 + SFLP03 — canonical operation journal and reconciliation" in changelog, "changelog lacks SFLP02/SFLP03")

require(
    'tasks.register<Exec>("verifySflp02Sflp03JournalReconciliation")' in root_gradle,
    "root Gradle SFLP02/SFLP03 verifier task missing",
)
require('dependsOn(":core:link:test")' in root_gradle, "combined verifier must depend on :core:link:test")
require(
    '"verifySflp02Sflp03JournalReconciliation"' in root_gradle,
    "checkAll does not retain the SFLP02/SFLP03 verifier",
)
require(
    devtool.count('"verifySflp02Sflp03JournalReconciliation"') >= 2,
    "Devtool Android/Desktop shared validation does not retain SFLP02/SFLP03",
)

line_length_paths = (
    "build.gradle.kts",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocol.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowDomainOperation.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOperationJournal.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowReconciliation.kt",
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowSyncProtocol.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOperationJournalTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowReconciliationEngineTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowSyncCodecGoldenTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowSyncWireTest.kt",
)
for relative in line_length_paths:
    for line_number, line in enumerate(read(relative).splitlines(), start=1):
        if len(line) > 120:
            FAILURES.append(f"{relative}:{line_number} exceeds 120 characters ({len(line)})")

if FAILURES:
    print("SFLP02_03_JOURNAL_RECONCILIATION: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)

print("SFLP02_03_JOURNAL_RECONCILIATION: PASS")
