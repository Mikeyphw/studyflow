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
storage = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/SafManualFolderSyncStorage.kt")
snapshot = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseSnapshotWriter.kt")
attachments = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/ManualSyncAttachmentCollector.kt")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
strings = read("feature/settings/src/main/res/values/strings.xml")
strings_pt = read("feature/settings/src/main/res/values-pt-rBR/strings.xml")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/StudyFlowSyncSettingsTest.kt")
app_contract = read("app/src/test/java/com/mikeyphw/studyflow/ManualFolderSyncIntegrationContractTest.kt")

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
    "uploadManualFolderCheckpoint(password: CharArray)",
    "scanManualFolderCheckpoints()",
    "previewLatestManualFolderRestore(password: CharArray)",
    "StudyManualFolderCheckpointDetails",
    "StudyManualFolderScan",
]:
    if marker not in domain:
        failures.append(f"StudyCloudSync.kt missing {marker}")

for marker in [
    "SafManualFolderSyncStorage",
    "StudyFlowDatabaseSnapshotWriter.capture(database)",
    "ManualSyncAttachmentCollector",
    "database.withTransaction",
    "writeEncryptedArchive(archive, output, password)",
    "listCheckpointDocuments(folderUri)",
    "readEncryptedArchive(",
    "LastManualUploadAtKey",
    "LastRemoteCheckAtKey",
    "RemoteCheckpointCountKey",
    "StudyFlowSyncProviderIds.LOCAL_FOLDER",
    "MANUAL_UPLOAD_RECORDED",
    "REMOTE_CHECK_COMPLETED",
    "RESTORE_PREVIEWED",
]:
    if marker not in data:
        failures.append(f"LocalStudyCloudSyncRepository.kt missing {marker}")

for marker in [
    "DocumentsContract.buildChildDocumentsUriUsingTree",
    "DocumentsContract.buildDocumentUriUsingTree",
    "DocumentsContract.createDocument",
    "resolver.openOutputStream",
    "resolver.openInputStream",
    "DocumentsContract.renameDocument",
]:
    if marker not in storage:
        failures.append(f"SafManualFolderSyncStorage.kt missing {marker}")

for marker in [
    "sqlite_master",
    "StudyFlowDatabase.SCHEMA_VERSION",
    "BoundedSnapshotOutputStream",
]:
    if marker not in snapshot:
        failures.append(f"StudyFlowDatabaseSnapshotWriter.kt missing {marker}")

for marker in [
    "AnnexStorageMode.IMPORTED_COPY",
    "AnnexStorageMode.LINKED_DOCUMENT",
    "checksumSha256",
    "openInputStream",
]:
    if marker not in attachments:
        failures.append(f"ManualSyncAttachmentCollector.kt missing {marker}")

for marker in [
    "ActivityResultContracts.OpenDocumentTree",
    "takePersistableUriPermission",
    "configureLocalFolderProvider(uri.toString())",
    "uploadManualFolderCheckpoint(password)",
    "scanManualFolderCheckpoints()",
    "previewLatestManualFolderRestore(password)",
    "inspectSyncLedger()",
]:
    if marker not in app:
        failures.append(f"StudyFlowApp.kt missing {marker}")

# The old implementation fabricated remote state from local timestamps; that must never return.
for forbidden in [
    "recordManualUploadCheckpoint",
    "recordManualRemoteCheck",
    "lastManualUploadAtEpochMillis == null) 0 else 1",
]:
    if forbidden in app or forbidden in domain or forbidden in data:
        failures.append(f"manual folder sync still contains fake operation marker: {forbidden}")

for marker in [
    "ManualFolderSyncCard",
    "PasswordVisualTransformation",
    "StudyPlanEncryptedPackageCodec.MinimumPasswordLength",
    "onUploadEncryptedCheckpoint",
    "onCheckRemoteCheckpoints",
    "onPreviewRemoteRestore",
    "onViewSyncHistory",
    "ManualFolderScanResultCard",
    "ManualFolderRestorePreviewCard",
    "SyncHistoryResultCard",
]:
    if marker not in screen:
        failures.append(f"SyncFoundationScreen.kt missing {marker}")

for marker in [
    "Manual folder sync",
    "Write encrypted checkpoint",
    "Refresh folder checkpoints",
    "StudyFlow does not save this passphrase",
    "automatic sync remains off",
    "Authenticated restore preview",
]:
    if marker not in strings:
        failures.append(f"English settings strings missing {marker}")

for marker in [
    "Sincronização manual por pasta",
    "Gravar checkpoint criptografado",
    "Atualizar checkpoints da pasta",
    "A senha não é salva pelo StudyFlow",
    "sincronização automática continua desligada",
    "Prévia autenticada da restauração",
]:
    if marker not in strings_pt:
        failures.append(f"pt-BR settings strings missing {marker}")

for marker in [
    "local folder sync stores manual checkpoint state",
    "manual sync actions require a folder",
]:
    if marker not in test:
        failures.append(f"StudyFlowSyncSettingsTest.kt missing {marker}")

if "manual folder sync is backed by real SAF archive io" not in app_contract:
    failures.append("ManualFolderSyncIntegrationContractTest.kt missing real-I/O regression test")

if failures:
    raise SystemExit("CSOV05 manual folder sync violations:\n" + "\n".join(failures))
print("CSOV05_MANUAL_FOLDER_SYNC: PASS")
