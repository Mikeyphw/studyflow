#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


if not phase_version_at_least(ROOT, 33):
    errors.append("app version must be Phase 33 or later")

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt")
domain = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSync.kt")
data = read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt")
settings = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
sync_screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
runtime = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/RuntimeSmokeWorkbench.kt")

for forbidden in [
    "CLOUD_PLACEHOLDER",
    "cloudProviderSelected",
    "readyForProviderSelection",
    "pendingUploadCount",
    "pendingDownloadCount",
    "identityRequired",
    "conflictPolicyRequired",
    "StudySyncConnectionStatus.PROVIDER_REQUIRED",
    "StudySyncConnectionStatus.BLOCKED",
]:
    require(forbidden not in model + domain + data + settings + sync_screen,
            f"sync checkpoint/settings model still has obsolete contract: {forbidden}")
for required in [
    "pendingLocalChangeCount",
    "checkpointReady",
    "localBackupAvailable",
    "CHECKPOINT_NEEDED",
    "CHECKPOINT_BLOCKED",
    "StudyLocalCheckpointReadinessAuditor",
    "localCheckpointReady",
    "PendingLocalChangeKey",
    "StudyFlowSyncSettings",
    "syncEnabled: Boolean = false",
    "providerId: String? = null",
    "automaticSyncEnabled: Boolean = false",
    "wifiOnly: Boolean = true",
    "chargingOnly: Boolean = false",
    "encryptRemoteBackups: Boolean = true",
    "StudyFlowSyncProviderIds.NONE",
    "NoSyncProvider",
]:
    require(required in model + domain + data + settings + sync_screen,
            f"local-first optional sync contract missing: {required}")

for rel in [
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    lower = text.lower()
    for forbidden in [
        "cloud required",
        "cloud is required",
        "must sign in",
        "required provider",
        "obligatory cloud",
        "automatic upload is enabled",
    ]:
        require(forbidden not in lower, f"{rel} makes cloud look obligatory: {forbidden}")
    for required in [
        "sync_device_value",
        "sync_checkpoint_controls_message",
        "sync_prepare_checkpoint",
        "sync_disable_checkpoint",
        "sync_connection_checkpoint_needed",
        "sync_connection_checkpoint_error",
        "sync_provider_value",
        "sync_automatic_value",
        "sync_encryption_value",
        "sync_provider_none",
    ]:
        require(f'name="{required}"' in text, f"{rel} missing local-first optional sync label: {required}")
    require("optional" in lower or "opcional" in lower,
            f"{rel} must describe sync as optional")
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"invalid XML in {rel}: {exc}")

require("Local checkpoint" in runtime and "Sync foundation" not in runtime,
        "runtime smoke workbench must keep local checkpoint language")
release = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
require("ReleaseReadinessPhase(33" in release and "Offline-first checkpoint completion" in release,
        "Release readiness seal must include Phase 33")
factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
require(
    "CurrentPhase = 37" in factory
    and "fun sourceOnlySeal(" in factory
    and "fun evidenceSeal(" in factory
    and "Required evidence was not supplied." in factory,
    "Release validation factory must advance to Phase 37 while preserving Phase 33",
)
validate_repo = read("scripts/validate_repo.sh")
require("python3 scripts/verify_phase_33.py" in validate_repo,
        "validate_repo must run Phase 33 verifier")

if errors:
    print("PHASE_33_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_33_CONTRACT: PASSED (local-first optional cloud sync settings, no obligatory cloud)")
