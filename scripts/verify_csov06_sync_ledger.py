#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        failures.append(f"missing file: {rel}")
        return ""
    return path.read_text()

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt")
domain = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSync.kt")
data = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/StudyFlowSyncSettingsTest.kt")
ledger_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncLedgerTest.kt")

for marker in [
    "StudySyncDevice",
    "StudySyncCheckpointRecord",
    "StudySyncLedgerEventType",
    "StudySyncLedgerSnapshot",
    "StudySyncDeviceIdentity",
    "StudySyncLedgerFactory",
    "restorePreviewedAtEpochMillis",
    "conflictCount",
]:
    if marker not in model:
        failures.append(f"StudySync.kt missing {marker}")

for marker in [
    "inspectSyncLedger()",
    "previewLatestManualFolderRestore(password: CharArray)",
    "StudySyncLedgerSnapshot",
]:
    if marker not in domain:
        failures.append(f"StudyCloudSync.kt missing {marker}")

for marker in [
    "DeviceIdKey",
    "LastCheckpointIdKey",
    "SyncLedgerEventCountKey",
    "recordCheckpointLedger",
    "appendLedgerEvent",
    "decodeLedger",
    "StudySyncLedgerEventType.MANUAL_UPLOAD_RECORDED",
    "StudySyncLedgerEventType.RESTORE_PREVIEWED",
]:
    if marker not in data:
        failures.append(f"LocalStudyCloudSyncRepository.kt missing {marker}")

for marker in [
    "sync ledger records device checkpoint upload and restore preview facts",
    "StudySyncLedgerSnapshot",
    "restorePreviewed",
]:
    if marker not in test:
        failures.append(f"StudyFlowSyncSettingsTest.kt missing {marker}")

if "ledger keeps durable device and checkpoint context" not in ledger_test:
    failures.append("StudySyncLedgerTest.kt missing ledger test")

if failures:
    raise SystemExit("CSOV06 sync ledger violations:\n" + "\n".join(failures))
print("CSOV06_SYNC_LEDGER: PASS")
