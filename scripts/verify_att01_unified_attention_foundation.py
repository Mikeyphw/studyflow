#!/usr/bin/env python3
"""Focused source/contract verification for ATT01 unified attention foundation."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> str:
    value = text(path)
    for token in tokens:
        if token not in value:
            errors.append(f"{path}: missing contract token {token!r}")
    return value


attention = require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttention.kt",
    "value class StudyFlowAttentionOccurrenceId",
    "data class StudyFlowAttentionOccurrence(",
    "enum class StudyFlowAttentionDeliveryState",
    "interface StudyFlowAttentionLedgerStorage",
    "class StudyFlowAttentionLedger(",
    "fun admitInboundDelivery(",
    "fun migrateLegacyDeliveredAliases(",
    "fun diagnostics(): StudyFlowAttentionDiagnosticsSnapshot",
    "lastSequenceBySource",
    "legacyDeliveredOccurrenceIds",
)
if "private val delivered = linkedSetOf" in attention or "private val lastSequenceBySource = linkedMapOf" in attention:
    errors.append("shared attention ledger regressed to process-local dedupe state")

sync = require(
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt",
    '"notificationScheduleSync" to JsonPrimitive("sflp")' if False else '"notificationScheduleSync" to JsonPrimitive("sflp18")',
    '"attentionSchema" to JsonPrimitive("v1")',
    '"attentionScheduleChange"',
    "StudyFlowAttentionWireJson.encodeToJsonElement",
    "fun fromDomainOperation(operation: StudyFlowDomainOperation)",
    "attentionOccurrence: StudyFlowAttentionOccurrence? = null",
    "StudyFlowAttentionLedgerAdmission.DUPLICATE",
    "StudyFlowAttentionLedgerAdmission.STALE_SEQUENCE",
)
for required_field in (
    "reminderAtEpochMillis",
    "quietHoursStartMinute",
    "quietHoursEndMinute",
    "payload: JsonObject",
):
    if required_field not in sync:
        errors.append(f"SFLP schedule change lost field: {required_field}")
if "sflp18-attention-v1" in sync:
    errors.append("SFLP compatibility marker was replaced instead of preserved")

require(
    "app/src/main/java/com/mikeyphw/studyflow/link/AndroidStudyFlowAttentionLedgerStorage.kt",
    "SharedPreferences",
    "StudyFlowAttentionLedgerStorage",
)
app = require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "val attentionLedger: StudyFlowAttentionLedger",
    "AndroidStudyFlowAttentionLedgerStorage",
    "attention-ledger-legacy-migration",
    "migrateLegacyReminderStateToAttentionLedger",
    "StudyReminderOccurrenceStore",
    "migrateLegacyDeliveredAliases",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowAttentionLedgerStorage.kt",
    "StudyFlowAttentionLedgerStorage",
    "ATOMIC_MOVE",
)
require(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/link/StudyFlowTvAttentionLedgerStorage.kt",
    "SharedPreferences",
    "StudyFlowAttentionLedgerStorage",
)

web = require(
    "web/src/sflp_link.ts",
    "export class StudyFlowBrowserAttentionLedgerStorage",
    "localStorage",
    "lastSequenceBySource",
    "legacyDeliveredOccurrenceIds",
    "notificationScheduleSync: 'sflp18'",
    "attentionSchema: 'v1'",
    "attentionScheduleChange: { ...change }",
    "attentionDiagnostics()",
)
for runtime in ("web/runtime/sflp_link.js", "web/dist/runtime/sflp_link.js"):
    compiled = text(runtime)
    if "notificationScheduleSync: 'sflp18'" not in compiled or "attentionSchema: 'v1'" not in compiled:
        errors.append(f"{runtime}: compiled runtime does not contain ATT01 schedule semantics")
if (ROOT / "web/runtime/sflp_link.js").read_bytes() != (ROOT / "web/dist/runtime/sflp_link.js").read_bytes():
    errors.append("Web runtime and dist runtime diverge for sflp_link.js")

require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabs.kt",
    "attentionLedgerEntryCount",
    "attentionLedgerSourceSequenceCount",
    "attentionLedgerRecoveredFromInvalidState",
)
require(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/ReminderNotificationLabPanel.kt",
    "debug_reminder_lab_attention_title",
    "attentionLedgerLegacyAliasCount",
)

for path in (
    "protocol/sflp/v1/golden/schedule-notification-change-attention.json",
    "protocol/sflp/v1/golden/notification-delivery-attention.json",
    "core/link/src/test/resources/sflp/v1/schedule-notification-change-attention.json",
    "core/link/src/test/resources/sflp/v1/notification-delivery-attention.json",
):
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing ATT01 golden vector: {path}")
        continue
    try:
        value = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        errors.append(f"invalid JSON golden {path}: {exc}")
        continue
    if "schedule-notification-change" in path and "attentionOccurrence" not in value.get("payload", {}):
        errors.append(f"{path}: missing attentionOccurrence")

require(
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionLedgerTest.kt",
    "acceptedDeliveryAndSourceSequenceSurviveLedgerRecreation",
    "lifecycleRejectsInvalidTransitionsAndPersistsValidOnes",
    "legacyDeliveredAliasesPreventReplayWithoutInventingWorkspaceMappings",
    "invalidPersistedSnapshotFailsClosedToAnEmptyRecoverableLedger",
)
require(
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowAttentionLedgerStorageTest.kt",
    "fileStorageRestoresAttentionLedgerAcrossControllerProcessBoundaries",
)
require(
    "web/tests/test_att01_attention_runtime.py",
    "test_attention_ledger_survives_runtime_recreation_and_deduplicates",
    "test_schedule_change_operation_preserves_full_attention_semantics",
)

matrix = require(
    "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "SFLP notification envelope acceptance",
    "Durable ATT01 attention ledger",
    "Native reminder/background delivery",
)
if "Targeted notification delivery | Send/receive" in matrix:
    errors.append("capability matrix still conflates SFLP acceptance with native delivery")
require(
    "docs/architecture/SFLP18_19_NOTIFICATION_MEDIA_SYNC.md",
    "not the same as a native user-facing background notification surface",
    'notificationScheduleSync = "sflp18"',
    'attentionSchema = "v1"',
)
require(
    "ATT01_UNIFIED_ATTENTION_DOMAIN_SFLP_FOUNDATION.md",
    "Unified Attention Domain",
    "verifyAtt01UnifiedAttentionFoundation",
)
require(
    "docs/architecture/ATT01_UNIFIED_ATTENTION_DOMAIN.md",
    "durable receipt and lifecycle journal",
)

build = require(
    "build.gradle.kts",
    '"verifyAtt01AttentionContracts"',
    '"verifyAtt01WebAttentionRuntime"',
    '"verifyAtt01UnifiedAttentionFoundation"',
)
config = require(".devtool.toml", "verifyAttentionRoadmapPostsealCompletion")

if errors:
    print("ATT01 unified attention foundation verification FAILED", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("ATT01 unified attention foundation verification passed")
print("- shared attention domain + durable ledger present")
print("- SFLP18 legacy marker preserved and schedule semantics lossless")
print("- Android/Desktop/Web/Android TV persistence adapters present")
print("- Android legacy reminder migration + debug diagnostics present")
print("- cross-runtime goldens/tests/docs/Devtool validation wiring present")
