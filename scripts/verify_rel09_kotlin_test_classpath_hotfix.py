#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {
    "core/link/build.gradle.kts": [
        'testImplementation(libs.junit4)',
        'testImplementation(kotlin("test-junit"))',
    ],
    "desktopApp/build.gradle.kts": [
        'testImplementation(libs.junit4)',
        'testImplementation(kotlin("test-junit"))',
    ],
}

failures = []
for rel, tokens in REQUIRED.items():
    path = ROOT / rel
    if not path.exists():
        failures.append(f"missing {rel}")
        continue
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            failures.append(f"{rel}: missing {token}")

for test_path in [
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupGoldenTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowControlInputDelegationTest.kt",
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowFinalReleaseGoldenTest.kt",
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopAccountBackupValidationControllerTest.kt",
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopControlInputDelegationControllerTest.kt",
]:
    path = ROOT / test_path
    if path.exists():
        text = path.read_text(encoding="utf-8")
        if "kotlin.test" not in text:
            failures.append(f"{test_path}: expected kotlin.test imports to remain covered by module test classpath")

if failures:
    raise SystemExit("REL09_KOTLIN_TEST_CLASSPATH_HOTFIX failed:\n" + "\n".join(failures))
print("REL09_KOTLIN_TEST_CLASSPATH_HOTFIX: PASS")
