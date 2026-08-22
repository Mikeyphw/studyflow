#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt": [
        "StudySyncProviderCatalog",
        "WebDavNextcloud",
        "GoogleDrive",
        "StudySyncProviderCapability.REMOTE_UPLOAD",
        "optionalRemoteProviders",
    ],
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowSyncProvider.kt": [
        "WebDavSyncProviderConfig",
        "WebDavSyncTransport",
        "HttpUrlConnectionWebDavSyncTransport",
        "WebDavStudyFlowSyncProvider",
        "studyflow-sync-index.tsv",
        "GoogleDriveStudyFlowSyncProvider",
        "SyncProviderNotConfiguredException(id)",
    ],
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/WebDavStudyFlowSyncProviderTest.kt": [
        "uploads encrypted checkpoint",
        "downloads and deletes indexed checkpoints",
        "requires https by default",
        "google drive provider is optional shell",
    ],
    "docs/quality/CSOV10_REAL_PROVIDERS.md": [
        "Fresh installs remain local-first",
        "WebDAV / Nextcloud",
        "Google Drive is represented as an optional provider shell",
    ],
}
failures = []
for relative, markers in checks.items():
    text = (ROOT / relative).read_text()
    for marker in markers:
        if marker not in text:
            failures.append(f"{relative}: missing {marker}")
if failures:
    raise SystemExit("CSOV10 real provider violations:\n" + "\n".join(failures))
print("CSOV10_REAL_PROVIDERS: PASS")
