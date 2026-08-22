#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

CSOV_SCRIPTS = [
    "verify_cloud_optional_sync_settings.py",
    "verify_csov02_sync_bundle.py",
    "verify_csov03_provider_abstraction.py",
    "verify_csov04_optional_sync_onboarding.py",
    "verify_csov05_manual_folder_sync.py",
    "verify_csov06_sync_ledger.py",
    "verify_csov07_merge_engine.py",
    "verify_csov08_conflict_review.py",
    "verify_csov09_background_sync_modes.py",
    "verify_csov10_real_providers.py",
    "verify_csov11_final_sync_seal.py",
    "verify_csov12_provider_setup_security.py",
    "verify_csov13_sync_runbook.py",
]

CSOV_TASKS = [
    "verifyCloudOptionalSyncSettings",
    "verifyCsov02SyncBundle",
    "verifyCsov03ProviderAbstraction",
    "verifyCsov04OptionalSyncOnboarding",
    "verifyCsov05ManualFolderSync",
    "verifyCsov06SyncLedger",
    "verifyCsov07MergeEngine",
    "verifyCsov08ConflictReview",
    "verifyCsov09BackgroundSyncModes",
    "verifyCsov10RealProviders",
    "verifyCsov11FinalSyncSeal",
    "verifyCsov12ProviderSetupSecurity",
    "verifyCsov13SyncRunbook",
    "verifyCsov14OptionalSyncDeliveryAudit",
]

QUALITY_DOCS = [
    "docs/quality/CSOV00_CSOV01_ACCEPTANCE.md",
    "docs/quality/CSOV02_SYNC_BUNDLE_ACCEPTANCE.md",
    "docs/quality/CSOV03_PROVIDER_ABSTRACTION_ACCEPTANCE.md",
    "docs/quality/CSOV04_OPTIONAL_SYNC_ONBOARDING.md",
    "docs/quality/CSOV05_MANUAL_FOLDER_SYNC.md",
    "docs/quality/CSOV06_SYNC_LEDGER.md",
    "docs/quality/CSOV07_CONSERVATIVE_MERGE_ENGINE.md",
    "docs/quality/CSOV08_CONFLICT_REVIEW.md",
    "docs/quality/CSOV09_BACKGROUND_SYNC_MODES.md",
    "docs/quality/CSOV10_REAL_PROVIDERS.md",
    "docs/quality/CSOV11_FINAL_SYNC_ACCEPTANCE_SEAL.md",
    "docs/quality/CSOV12_PROVIDER_SETUP_SECURITY.md",
    "docs/quality/CSOV13_SYNC_RUNBOOK.md",
    "docs/quality/CSOV14_OPTIONAL_SYNC_DELIVERY_AUDIT.md",
]

CURRENT_PRODUCT_SURFACES = [
    "README.md",
    "docs/product/PRODUCT_CONTRACT.md",
    "docs/product/FIRST_RUN_AND_UPGRADE_NOTES.md",
    "docs/product/SCOPE_BOUNDARIES.md",
    "docs/architecture/ADR-0034-local-first-optional-cloud-sync.md",
    "docs/roadmap/FINAL_ROADMAP_INDEX.md",
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
]

failures: list[str] = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        failures.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

build = read("build.gradle.kts")
check_all_start = build.find('tasks.register("checkAll")')
check_all_block = build[check_all_start:] if check_all_start >= 0 else ""

for script in CSOV_SCRIPTS:
    require((ROOT / "scripts" / script).is_file(), f"missing CSOV verifier script: {script}")

for task in CSOV_TASKS:
    require(f'tasks.register<Exec>("{task}")' in build, f"missing Gradle task registration: {task}")
    require(f'"{task}"' in check_all_block, f"checkAll does not depend on {task}")

for doc in QUALITY_DOCS:
    require((ROOT / doc).is_file(), f"missing CSOV quality document: {doc}")

for rel in CURRENT_PRODUCT_SURFACES:
    text = read(rel).lower()
    for forbidden in ["no cloud", "no-cloud", "cloud-free", "never syncs", "cloud required", "must sign in"]:
        require(forbidden not in text, f"{rel} still contains obsolete cloud wording: {forbidden}")

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt")
domain_provider = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowSyncProvider.kt")
runbook = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncRunbook.kt")
sync_ui = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
app_shell = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")

for token in [
    "StudyFlowSyncSettings.LocalFirstDefault",
    "cloudRequired: Boolean = false",
    "StudySyncProviderCatalog",
    "WebDavNextcloud",
    "StudySyncBackgroundMode.MANUAL_ONLY",
    "StudySyncConflictReviewPresenter",
]:
    require(token in model, f"model missing delivered sync token: {token}")

for token in [
    "interface StudyFlowSyncProvider",
    "WebDavStudyFlowSyncProvider",
    "uploadCheckpoint",
    "downloadCheckpoint",
    "deleteCheckpoint",
    "Authorization\" to \"Basic",
]:
    require(token in domain_provider, f"provider layer missing delivered sync token: {token}")

for token in [
    "BUILD_ENCRYPTED_BUNDLE",
    "UPLOAD_ENCRYPTED_BUNDLE",
    "DRY_RUN_RESTORE",
    "CONFLICT_REVIEW_REQUIRED",
    "appliesLocalData: Boolean = false",
]:
    require(token in runbook, f"runbook missing delivered sync token: {token}")

for token in [
    "OptionalSyncOnboardingCard",
    "ManualFolderSyncCard",
    "ConflictReviewCard",
    "BackgroundSyncModeCard",
    "syncFolderLauncher",
]:
    require(token in sync_ui + "\n" + app_shell, f"settings UI missing delivered sync surface: {token}")

if failures:
    print("CSOV14_OPTIONAL_SYNC_DELIVERY_AUDIT: FAILED")
    for failure in failures:
        print(f"- {failure}")
    sys.exit(1)

for script in CSOV_SCRIPTS:
    result = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / script)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if result.returncode != 0:
        print(result.stdout, end="")
        raise SystemExit(f"CSOV14_OPTIONAL_SYNC_DELIVERY_AUDIT: nested verifier failed: {script}")

print("CSOV14_OPTIONAL_SYNC_DELIVERY_AUDIT: PASS")
