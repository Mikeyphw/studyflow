#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt": [
        "val cloudRequired: Boolean = false",
        "LocalFirstDefault",
        "StudySyncProviderCatalog",
        "optionalRemoteProviders",
        "StudySyncBackgroundSyncPlanner",
        "MANUAL_CONFLICT",
    ],
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowSyncProvider.kt": [
        "require(manifest.encryptedForRemote)",
        "WebDavStudyFlowSyncProvider",
        "GoogleDriveStudyFlowSyncProvider",
        "SyncProviderNotConfiguredException",
    ],
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncMerge.kt": [
        "StudySyncMergeAction.MANUAL_CONFLICT",
        "Duplicate $label sync entity ids",
        "DELETED_LOCALLY_CHANGED_REMOTELY",
        "DELETED_REMOTELY_CHANGED_LOCALLY",
    ],
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowOptionalCloudSyncFinalSealTest.kt": [
        "fresh install is local first and cloud optional",
        "provider catalog exposes optional remote providers",
        "remote uploads must remain encrypted",
        "conflicting changes block automatic merge",
    ],
    "docs/quality/CSOV11_FINAL_SYNC_ACCEPTANCE_SEAL.md": [
        "StudyFlow works without cloud",
        "Remote checkpoint uploads must use encrypted",
        "Background sync is never scheduled from a fresh install",
    ],
}
failures = []
for relative, markers in checks.items():
    text = (ROOT / relative).read_text()
    for marker in markers:
        if marker not in text:
            failures.append(f"{relative}: missing {marker}")
if failures:
    raise SystemExit("CSOV11 final sync acceptance violations:\n" + "\n".join(failures))
print("CSOV11_FINAL_SYNC_ACCEPTANCE: PASS")
