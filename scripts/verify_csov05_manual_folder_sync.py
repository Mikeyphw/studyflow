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
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
strings = read("feature/settings/src/main/res/values/strings.xml")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/StudyFlowSyncSettingsTest.kt")

for marker in [
    "providerLocation: String? = null",
    "lastManualUploadAtEpochMillis: Long? = null",
    "lastRemoteCheckAtEpochMillis: Long? = null",
    "remoteCheckpointCount: Int = 0",
    "StudySyncManualAction",
    "StudySyncManualActionPlanner",
    "PREPARE_UPLOAD_CHECKPOINT",
    "CHECK_REMOTE_CHECKPOINTS",
]:
    if marker not in model:
        failures.append(f"StudySync.kt missing {marker}")

for marker in [
    "configureLocalFolderProvider(folderUri: String)",
    "recordManualUploadCheckpoint()",
    "recordManualRemoteCheck(remoteCheckpointCount: Int)",
]:
    if marker not in domain:
        failures.append(f"StudyCloudSync.kt missing {marker}")

for marker in [
    "ProviderLocationKey",
    "LastManualUploadAtKey",
    "LastRemoteCheckAtKey",
    "RemoteCheckpointCountKey",
    "StudyFlowSyncProviderIds.LOCAL_FOLDER",
]:
    if marker not in data:
        failures.append(f"LocalStudyCloudSyncRepository.kt missing {marker}")

for marker in [
    "ActivityResultContracts.OpenDocumentTree",
    "takePersistableUriPermission",
    "configureLocalFolderProvider(uri.toString())",
    "recordManualUploadCheckpoint",
    "recordManualRemoteCheck",
]:
    if marker not in app:
        failures.append(f"StudyFlowApp.kt missing {marker}")

for marker in [
    "ManualFolderSyncCard",
    "onUploadEncryptedCheckpoint",
    "onCheckRemoteCheckpoints",
    "sync_prepare_upload_checkpoint",
    "sync_check_remote_checkpoint",
]:
    if marker not in screen:
        failures.append(f"SyncFoundationScreen.kt missing {marker}")

for marker in [
    "Manual folder sync",
    "Prepare encrypted upload checkpoint",
    "Check for remote checkpoint",
    "Automatic sync remains off",
]:
    if marker not in strings:
        failures.append(f"settings strings missing {marker}")

for marker in [
    "local folder sync stores manual checkpoint state",
    "manual sync actions require a folder",
]:
    if marker not in test:
        failures.append(f"StudyFlowSyncSettingsTest.kt missing {marker}")

if failures:
    raise SystemExit("CSOV05 manual folder sync violations:\n" + "\n".join(failures))
print("CSOV05_MANUAL_FOLDER_SYNC: PASS")
