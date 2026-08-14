#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def req(value: bool, message: str) -> None:
    if not value:
        errors.append(message)


model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/ReleaseValidation.kt")
factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
readiness = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
app_build = read("app/build.gradle.kts")
root_build = read("build.gradle.kts")
journey = read("app/src/androidTest/java/com/mikeyphw/studyflow/StudyFlowFullJourneyAcceptanceTest.kt")
artifact = read("scripts/verify_final_release_artifact.py")
journey_ev = read("scripts/record_full_journey_evidence.py")
device = read("scripts/record_final_device_acceptance.py")
verdict = read("scripts/verify_final_release_verdict.py")
validate_repo = read("scripts/validate_repo.sh")

for token in (
    "FAIL",
    "BUNDLE",
    "SIGNED_ARTIFACT_IDENTITY",
    "FULL_JOURNEY",
    "DEVICE_ACCEPTANCE",
    "ReleaseValidationProvenance.AUTHORITATIVE_VERDICT",
):
    req(token in model + factory, f"release evidence model missing {token}")
req("status != ReleaseValidationGateStatus.PASS" in model, "warning/failure gates must block Release")
req("completedSeal" not in factory, "generic completedSeal must remain removed")
req("Required evidence was not supplied." in factory, "missing gate evidence must block")
req("releaseValidation.releaseReady" in readiness, "product seal must consume authoritative Release evidence")
req("completedPhaseNumbers: Set<Int> = emptySet()" in readiness, "historical completion must be supplied as evidence")
req(
    not re.search(r"ReleaseReadinessPhase\([^\n]+complete\s*=\s*true", readiness),
    "historical release phases must not be hard-coded complete",
)

for token in (
    "STUDYFLOW_RELEASE_STORE_FILE",
    "STUDYFLOW_RELEASE_STORE_PASSWORD",
    "STUDYFLOW_RELEASE_KEY_ALIAS",
    "STUDYFLOW_RELEASE_KEY_PASSWORD",
    'testBuildType = "release"',
):
    req(token in app_build, f"release build missing {token}")
req(
    "releaseImplementation(libs.androidx.compose.ui.test.manifest)" not in app_build,
    "release APK must not include the Compose debug test manifest",
)

for token in (
    "verifyFinalReleaseHostGate",
    "verifyFinalReleaseArtifact",
    "recordConnectedReleaseTestGates",
    "recordFullJourneyEvidence",
    "verifyFinalReleaseAutomatedGate",
    "verifyFinalReleaseVerdict",
    ":app:assembleRelease",
    ":app:bundleRelease",
    ":core:database:connectedDebugAndroidTest",
    ":app:connectedReleaseAndroidTest",
):
    req(token in root_build, f"final validation wiring missing {token}")
req("app-release-unsigned.apk" not in root_build, "final host gate must never accept an unsigned Release APK")
req("missingSigningVariables" in root_build, "final host gate must fail closed when signing inputs are absent")
req('"core_database_migrations"' in root_build, "connected Room migration evidence gate is missing")
req("verifyFinalReleaseAutomatedGate" in validate_repo, "top-level repository validator must run the automated final gate")

for token in (
    "container.createStudyPlan",
    "generateActivities",
    "finishPracticeAttempt",
    "finishMockExamAttempt",
    "recordWrongAnswer",
    "StudyPlanImportStrategy.REPLACE_EXISTING",
    "StudyPlanImportStrategy.CREATE_AS_NEW",
    "MODE_RESTART_INITIAL",
    "MODE_POST_RESTART",
    "restoredJourneySurvivesProcessRestartAndCleansUp",
    "processInstanceToken",
    "assertNotEquals",
):
    req(token in journey, f"full restore/restart journey missing {token}")

for token in (
    "apkanalyzer",
    "apksigner",
    "--print-certs",
    "-Werr",
    "jarsigner",
    '"signatureVerified": True',
    "sha256_file(apk)",
    "sha256_file(aab)",
):
    req(token in artifact, f"exact signed artifact verifier missing {token}")

for token in (
    "connected-test-gates.json",
    "restart_initial",
    "post_restart",
    "am",
    "instrument",
    "processRestartVerified",
    "realRestoreVerified",
    "installed_apk_sha256",
    "release-journey-initial.txt",
    "release-journey-post-restart.txt",
):
    req(token in journey_ev, f"automated Android journey evidence missing {token}")

for token in (
    "REQUIRED_CONNECTED_GATES",
    "connected-test-gates.json",
    "processRestartVerified",
    "realRestoreVerified",
    "signatureVerified",
    "required device acceptance check has no observation evidence",
    "sha256_file(artifact_aab_path)",
):
    req(token in verdict, f"authoritative verdict missing fail-closed binding: {token}")
req('"releaseReady": not errors' in verdict and "raise SystemExit(1)" in verdict, "final verdict must fail closed")

for token in (
    "--evidence",
    "CHECK=OBSERVATION",
    '"evidence": evidence.get(check, "")',
    "missing_evidence",
):
    req(token in device, f"manual real-device attestation missing evidence requirement: {token}")
for token in (
    "fresh_install",
    "upgrade_install",
    "upgrade_data_survival",
    "process_restart",
    "notifications",
    "today_widget",
    "restore_create_as_new",
    "restore_replace_existing",
    "locale_en_ptbr",
    "theme_light_dark_system",
    "accessibility_talkback_focus_touch_target",
):
    req(token in device, f"device acceptance missing {token}")

if errors:
    print("REMEDIATION_OV_13: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("REMEDIATION_OV_13: PASSED (automated Release gate + fail-closed manual final verdict contracts)")
