#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    candidate = ROOT / path
    if not candidate.is_file():
        raise AssertionError(f"missing file: {path}")
    return candidate.read_text()


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise AssertionError(f"{label}: missing {token!r}")


def forbid(text: str, token: str, label: str) -> None:
    if token in text:
        raise AssertionError(f"{label}: forbidden {token!r}")


def main() -> None:
    build = read("build.gradle.kts")
    runner = read("scripts/run_optional_instrumentation_validation.py")
    android_test = read(
        "app/src/androidTest/java/com/mikeyphw/studyflow/OptionalSyncInstrumentationValidationTest.kt",
    )
    docs = read("docs/quality/CSOV15_OPTIONAL_INSTRUMENTATION_VALIDATION.md")
    validation = read("VALIDATION_REPORT.md")

    require(build, "verifyCsov15OptionalInstrumentationValidation", "build.gradle.kts")
    require(build, "recordOptionalInstrumentationValidation", "build.gradle.kts")
    require(build, "scripts/verify_csov15_optional_instrumentation_validation.py", "build.gradle.kts")
    require(build, "scripts/run_optional_instrumentation_validation.py", "build.gradle.kts")
    require(build, '"verifyCsov15OptionalInstrumentationValidation",', "checkAll CSOV wiring")
    forbid(build, '"recordOptionalInstrumentationValidation",\n        ":core:diagnostics:test"', "checkAll")

    for token in [
        "localhost:5555",
        "ANDROID_SERIAL",
        "STUDYFLOW_ADB_AUTOCONNECT_LOCALHOST",
        "STUDYFLOW_REQUIRE_CONNECTED_DEVICE",
        "STUDYFLOW_ADB_TRY_ROOT",
        "su", "-c", "id",
        "adb", "root",
        ":app:connectedDebugAndroidTest",
        "optional-instrumentation-validation.json",
        "SKIPPED",
        "PASS",
        "FAIL",
    ]:
        require(runner, token, "optional instrumentation runner")

    for token in [
        "OptionalSyncInstrumentationValidationTest",
        "createAndroidComposeRule<MainActivity>()",
        "SyncFoundationScreen",
        "StudySyncState.disabled",
        "StudyFlowSyncProviderIds.LOCAL_FOLDER",
        "sync_keep_offline",
        "sync_choose_folder",
        "sync_prepare_upload_checkpoint",
        "sync_check_remote_checkpoint",
        "sync_preview_restore",
        "assertCountEquals",
    ]:
        require(android_test, token, "optional sync instrumentation test")

    for token in [
        "localhost:5555",
        "root when available",
        "recordOptionalInstrumentationValidation",
        "connectedDebugAndroidTest",
        "SKIPPED when no device is connected",
    ]:
        require(docs, token, "CSOV15 docs")

    require(validation, "CSOV15 optional instrumentation validation", "VALIDATION_REPORT.md")
    print("CSOV15_OPTIONAL_INSTRUMENTATION_VALIDATION: PASS")


if __name__ == "__main__":
    main()
