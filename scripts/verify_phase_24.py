#!/usr/bin/env python3
from pathlib import Path
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(path: str, *needles: str) -> None:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"{path} missing: {missing}")

if not phase_version_at_least(ROOT, 24):
    raise SystemExit("app version is below Phase 24")
require("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt",
        "StudySyncProviderKind", "LOCAL_ONLY", "StudySyncState", "StudySyncChangeSummary")
require("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSync.kt",
        "StudyCloudSyncRepository", "ManageStudyCloudSync", "StudyCloudSyncReadinessAuditor")
require("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt",
        "preferencesDataStore", "study_sync_foundation", "inspectLocalChanges", "prepareLocalFoundation")
require("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
        "SyncFoundationScreen", "sync_provider_gate_message")
require("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt",
        "syncState: StudySyncState", "onOpenSyncCenter", "sync_settings_message")
require("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
        "SyncRoute", "SyncFoundationScreen", "manageStudyCloudSync")
require("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt", "data object SyncRoute")
require("docs/architecture/ADR-0028-optional-cloud-sync-foundation.md", "No network permission", "No account identity")
require("docs/quality/PHASE_24_ACCEPTANCE.md", "local-only scope")
require("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSyncReadinessAuditorTest.kt",
        "readyOnlyWhenFoundationTokenMatchesLocalData")

repo_text = "\n".join(
    p.read_text(encoding="utf-8", errors="ignore")
    for p in [
        ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt",
        ROOT / "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt",
    ]
)
for forbidden in ["google-services.json", "firebase", "OAuth", "http://", "https://"]:
    if forbidden in repo_text:
        raise SystemExit(f"Phase 24 local sync foundation must not contain provider/network token: {forbidden}")

print("PHASE_24_CONTRACT: PASSED (local sync foundation, provider gate, no cloud claims)")
