#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
failures = []


def require(path, *markers):
    file = ROOT / path
    if not file.is_file():
        failures.append(f"missing Phase 23 file: {path}")
        return
    text = file.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            failures.append(f"{path} missing marker: {marker}")


require("app/build.gradle.kts", 'project(":feature:privacy")')
if not phase_version_at_least(ROOT, 23):
    failures.append("app version is not Phase 23 or later")
require(
    "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanEncryptedPackageCodec.kt",
    "AES/GCM/NoPadding",
    "HmacSHA256",
    "210_000",
    "updateAAD",
    "MinimumPasswordLength",
    "CipherOutputStream",
    "maximumEncryptedBytes",
    "parseEnvelopeHeader",
)
require(
    "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanEncryptedPackageCodecTest.kt",
    "streamingEncryptionAuthenticatesTheCompleteWrittenPackage",
    "wrongPasswordFailsWithoutReturningPlaintext",
    "tamperingFailsAuthentication",
    "streamingDecryptionAuthenticatesBeforeTheTemporaryOutputIsConsumed",
    "streamingDecryptionEnforcesTheEncryptedSizeLimit",
)
require(
    "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPrivacyPolicy.kt",
    "excludedTableNames",
    "HistoryTableNames",
    "validatePayload",
    "PrivateExportFileName",
    "PrivateApplicationVersion",
    "pseudonymizeIdentifier",
    "isPrivateIdentifier",
)
require(
    "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPrivacyPolicyTest.kt",
    "noteAndHistoryRedactionDeclareEveryExcludedTable",
    "privacyValidatorAcceptsACompletelyRedactedPayload",
    "privacyValidatorRejectsProgressHiddenInARedactedPackage",
    "privateIdentifiersAreStableInsideOneExportButUnlinkableAcrossExports",
    "metadataPrivateAnnexPayloadPackageUsesPrivateAnnexIdAndEntryPath",
    "metadataPrivateAnnexPayloadValidatorRejectsRawAnnexIds",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPlanPortabilityRepository.kt",
    "inspectBackup",
    "dryRunImport",
    "password: CharArray?",
)
require(
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt",
    "StudyPlanBackupIntegrityReport",
    "databaseRowsChanged = 0",
    "decodeArchive",
    "readPrefix",
    "MaxAnnexPayloadBytes",
    "studyflow-authenticated-restore-",
    "MaxEncryptedPackageBytes = 516L",
    "sourceAnnexesByExportedId",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt",
    "metadataPrivacyMode",
    "StudyPlanPrivacyPolicy.excludedTableNames",
    "StudyPlanPrivacyPolicy.HistoryTableNames",
    "privatePlanId",
    "privatizeIdentifiers",
    "pseudonymizeIdentifier",
    "isPrivateIdentifier",
    "non-private $column identifier",
    "StudyPlanPrivacyPolicy.HistoryTableNames",
)
require(
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt",
    "androidx.compose.ui.text.input.PasswordVisualTransformation",
    "inspectBackup",
    "dryRunImport",
    "metadataPrivacy",
    "redactNotes",
    "StudyPlanPrivacyPolicy.PrivateExportFileName",
)
require(
    "feature/privacy/src/main/java/com/mikeyphw/studyflow/feature/privacy/PrivacyCenterScreen.kt",
    "retention_policy_title",
    "recovery",
)
require("PHASE_23_IMPLEMENTATION.md", "does not claim or implement cloud sync")
require(
    "docs/quality/PHASE_22_23_PROMISE_AUDIT.md",
    "Audit 1: implementation completeness",
    "Audit 2: independent release recheck",
    "Validation boundary",
)
require(
    "docs/quality/PHASE_23_ACCEPTANCE.md",
    "databaseRowsChanged = 0",
    "fresh per-export scope",
    "not persisted or device-bound",
)
require(
    ".devtool.toml",
    '":core:portability:compileKotlin"',
    '":core:portability:compileTestKotlin"',
    '":core:portability:test"',
    '":feature:privacy:compileDebugKotlin"',
    '":feature:privacy:lintDebug"',
)


plans_source = (
    ROOT / "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt"
).read_text(encoding="utf-8")
if "androidx.compose.material3.PasswordVisualTransformation" in plans_source:
    failures.append("PlanPortabilityScreen uses the wrong PasswordVisualTransformation package")

repository_path = ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt"
repository_text = repository_path.read_text(encoding="utf-8")

prepare_start = repository_text.find("private suspend fun prepareArchive(")
prepare_end = repository_text.find("private suspend fun restoreAnnexPayloads(", prepare_start)
if prepare_start < 0 or prepare_end < 0:
    failures.append("could not isolate prepareArchive for annex privacy validation")
else:
    prepare_text = repository_text[prepare_start:prepare_end]
    if "annexId = annex.id" in prepare_text:
        failures.append("prepareArchive exposes the raw source annex ID in the package manifest")
    if "annexId = annexId" not in prepare_text:
        failures.append("prepareArchive does not declare the exported annex ID in package metadata")

dry_start = repository_text.find("override suspend fun dryRunImport(")
dry_end = repository_text.find("override suspend fun importPlan(", dry_start)
if dry_start < 0 or dry_end < 0:
    failures.append("could not isolate dryRunImport for zero-write validation")
elif "graphStore.importPayload" in repository_text[dry_start:dry_end]:
    failures.append("dryRunImport invokes graphStore.importPayload")

for path in ("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml"):
    try:
        root = ET.parse(ROOT / path).getroot()
        allowed_domains = {
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        }
        for element in root.iter():
            domain = element.attrib.get("domain")
            if domain is not None and domain not in allowed_domains:
                failures.append(f"invalid backup domain {domain}: {path}")
    except Exception as error:
        failures.append(f"invalid backup XML {path}: {error}")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for marker in ('android:fullBackupContent="@xml/backup_rules"', 'android:dataExtractionRules="@xml/data_extraction_rules"'):
    if marker not in manifest:
        failures.append(f"AndroidManifest.xml missing backup marker: {marker}")

for forbidden in ("EncryptedSharedPreferences", "EncryptedFile", "security-crypto"):
    for path in ROOT.rglob("*"):
        if path.is_file() and path.suffix in {".kt", ".kts", ".md"}:
            if forbidden in path.read_text(encoding="utf-8", errors="ignore"):
                failures.append(f"forbidden Phase 23 API {forbidden}: {path.relative_to(ROOT)}")

claim_paths = [
    ROOT / "README.md",
    ROOT / "PHASE_23_IMPLEMENTATION.md",
    ROOT / "feature/privacy/src/main/java/com/mikeyphw/studyflow/feature/privacy/PrivacyCenterScreen.kt",
]
for path in claim_paths:
    text = path.read_text(encoding="utf-8", errors="ignore").lower()
    for claim in ("secure cloud sync is available", "cloud sync enabled", "cloud backup is encrypted"):
        if claim in text:
            failures.append(f"unsupported Phase 23 cloud claim {claim}: {path.relative_to(ROOT)}")

if failures:
    print("PHASE_23_CONTRACT: FAILED")
    for failure in failures:
        print(f"- {failure}")
    sys.exit(1)
print("PHASE_23_CONTRACT: PASSED (encryption, privacy, annex payload IDs, integrity, dry-run, retention)")
