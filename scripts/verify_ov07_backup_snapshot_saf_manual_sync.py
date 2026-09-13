#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    candidate = ROOT / path
    if not candidate.exists():
        errors.append(f"missing required file: {path}")
        return ""
    return candidate.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> None:
    data = text(path)
    for token in tokens:
        if token not in data:
            errors.append(f"{path}: missing contract token {token!r}")


def forbid(path: str, *tokens: str) -> None:
    data = text(path)
    for token in tokens:
        if token in data:
            errors.append(f"{path}: forbidden stale token {token!r}")

room_portability = "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt"
annex_repo = "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomAnnexFileStorageRepository.kt"
package_codec = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodec.kt"
desktop = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityController.kt"
plan_screen = "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt"
sync_bundle = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundle.kt"
sync_repo = "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt"
restore_stager = "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/ManualSyncRestoreStager.kt"
restore_db = "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseSnapshotRestorer.kt"
domain = "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSync.kt"
model = "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt"
sync_screen = "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt"
app = "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt"
codec_test = "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundleCodecTest.kt"
package_test = "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodecAnnexPayloadTest.kt"
restore_test = "core/data/src/androidTest/kotlin/com/mikeyphw/studyflow/core/data/ManualSyncDatabaseRestoreOv07Test.kt"
atomic_restore_test = "core/data/src/androidTest/kotlin/com/mikeyphw/studyflow/core/data/StudyPlanPortabilityAtomicRestoreTest.kt"
doc = "docs/quality/OV07_BACKUP_SNAPSHOT_SAF_MANUAL_SYNC_DURABILITY.md"
ledger = "docs/roadmap/FINAL_AUDIT_REMEDIATION_CLOSURE_LEDGER.md"

# S3-09: existing deterministic annex destinations are reversible until DB commit.
require(room_portability,
        "stageAnnexPayloads",
        "commitStagedAnnexPayloads",
        "replacedFinalFiles",
        "backup-${sanitizeSegment(entry.annexId)}.bin",
        "entry.destinationFile.renameTo(backup)")
require(atomic_restore_test,
        "repeatedAnnexBearingSnapshotRestoreAtomicallyReplacesExistingDestination",
        "snapshotDeletionRemovesPrivateBytesAndUsesSanitizedPlanPath")

# S3-10 / S3-11: Desktop backup cannot escape by symlink and cannot exceed its restore ceiling.
require(desktop,
        "LinkOption.NOFOLLOW_LINKS",
        "requireWorkspaceTreeHasNoSymlinks",
        "requireExternalPath",
        "BoundedBackupOutputStream",
        "MAX_BACKUP_PAYLOAD_BYTES",
        "Files.size(normalizedDestination) <= MAX_BACKUP_BYTES")

# S3-12 / DS2-41: transient SAF import grants and password buffers are explicitly released/wiped.
require(plan_screen,
        "mutableStateOf(CharArray(0))",
        "pendingExportPassword by remember { mutableStateOf<CharArray?>(null) }",
        "releaseImportGrant",
        "releasePersistableUriPermission",
        "clearSelectedImport",
        "password?.fill('\\u0000')",
        "pendingExportPassword?.fill('\\u0000')")
forbid(plan_screen,
       'var exportPassword by remember { mutableStateOf("") }',
       'var importPassword by remember { mutableStateOf("") }')

# S3-13.
require(package_codec,
        "val annexIds = manifest.annexPayloads.map { it.annexId }",
        "Study Plan package contains duplicate annex IDs.")
require(package_test, "writerRejectsDuplicateAnnexIdsEvenWhenEntryNamesDiffer")

# S3-14 / S3-15 / S12-08.
require(room_portability,
        ".delete-${UUID.randomUUID()}.pending",
        "supportDao.upsertSnapshots(listOf(snapshot))",
        "sanitizeSegment(planId.value)",
        "Unsafe snapshot destination.")
require(annex_repo,
        "Could not stage imported annex bytes for physical deletion.",
        "Annex physical deletion failed; the annex remains tracked.",
        "releasePersistedReadPermissionOrThrow",
        "Persisted linked-document permission could not be released.")

# DS2-05: real production apply path, staged annexes and exact logical DB replacement.
require(domain, "suspend fun applyLatestManualFolderRestore(password: CharArray)")
require(model, "RESTORE_APPLIED")
require(sync_repo,
        "override suspend fun applyLatestManualFolderRestore",
        "StudyFlowDatabaseSnapshotRestorer.inspect",
        "ManualSyncRestoreStager(appContext).prepare(staged)",
        "PhysicalStorageMutationCoordinator.mutex.withLock",
        "StudyFlowDatabaseSnapshotRestorer.restore(",
        "prepared.rollbackFiles()",
        "restoredRevision == manifest.checkpoint.graphRevision",
        "StudySyncLedgerEventType.RESTORE_APPLIED")
require(restore_stager,
        "sync-restored.bin",
        "PreparedManualSyncAttachments",
        "commitFiles()",
        "rollbackFiles()")
require(restore_db,
        "PRAGMA defer_foreign_keys = ON",
        "captureTriggers(db)",
        "DROP TRIGGER",
        "triggers.forEach { trigger -> db.execSQL(trigger.sql) }",
        "PRAGMA foreign_key_check",
        "StudyFlowIntegrity.logicalIntegrityViolationCount(db) == 0",
        "snapshot.tables.asReversed()")
require(app, "applyLatestManualFolderRestore(password)")
require(sync_screen, "SyncMutation.APPLY_RESTORE", "onApplyRemoteRestore(password)", "sync_apply_restore")
require(restore_test, "logicalSnapshotRestoreReplacesMutatedGraphAtomically")

# DS2-10: manual sync password is not retained as immutable String state and is cleared in finally.
require(sync_screen,
        "mutableStateOf(CharArray(0))",
        "syncPassphrase.copyOf()",
        "password.fill('\\u0000')",
        "clearSyncPassphrase()")
forbid(sync_screen, 'var syncPassphrase by remember { mutableStateOf("") }')

# DS2-11: preview uses authenticated private-file streaming, not readEncryptedArchive heap materialization.
require(sync_bundle,
        "fun decryptEncryptedArchive(",
        "fun readManifestStreaming(",
        "fun stageArchive(",
        "MaxMetadataBytes")
require(sync_repo,
        'File.createTempFile("studyflow-sync-preview-", ".zip", appContext.cacheDir)',
        "decryptEncryptedArchive(",
        "readManifestStreaming",
        "Could not delete decrypted manual-sync preview staging bytes.")
preview_region = text(sync_repo)
preview_start = preview_region.find("override suspend fun previewLatestManualFolderRestore")
apply_start = preview_region.find("override suspend fun applyLatestManualFolderRestore")
if preview_start >= 0 and apply_start > preview_start and "readEncryptedArchive(" in preview_region[preview_start:apply_start]:
    errors.append("manual restore preview still materializes the encrypted archive through readEncryptedArchive")
require(codec_test, "streamingPreviewAndStageAvoidWholeArchiveHeapMaterialization")

# DS2-12: only logical DB capture is transactional; annex collection runs outside with revision retry.
require(sync_repo,
        "captureStableManualCheckpoint()",
        "val attachments = attachmentCollector.collect()",
        "afterCollection == revision",
        "MaximumStableCaptureAttempts = 3")
repo_data = text(sync_repo)
if "StudyFlowDatabaseSnapshotWriter.capture(database),\n                attachmentCollector.collect()" in repo_data:
    errors.append("manual checkpoint still performs attachment collection inside the Room transaction")

require(doc, "Overlay 7 of 30", "RESTORE_APPLIED", "CharArray", "symlink")
ledger_data = text(ledger)
for finding in [
    "S3-09", "S3-10", "S3-11", "S3-12", "S3-13", "S3-14", "S3-15",
    "DS2-05", "DS2-10", "DS2-11", "DS2-12", "DS2-41", "S12-08",
]:
    if finding not in ledger_data:
        errors.append(f"closure ledger missing {finding}")

if errors:
    print("OV07 backup/snapshot/SAF/manual-sync verifier: FAIL", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("OV07 backup/snapshot/SAF/manual-sync verifier: PASS")
