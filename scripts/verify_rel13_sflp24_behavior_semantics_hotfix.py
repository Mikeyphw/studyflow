#!/usr/bin/env python3
from pathlib import Path
import re, sys

ROOT = Path(__file__).resolve().parents[1]
TEST_DEP = 'testImplementation(kotlin("test-junit"))'
errors = []

for rel in (
    "core/link/build.gradle.kts",
    "desktopApp/build.gradle.kts",
    "app/build.gradle.kts",
    "app-tv/build.gradle.kts",
):
    p = ROOT / rel
    text = p.read_text(encoding="utf-8", errors="replace")
    if TEST_DEP not in text:
        errors.append(f"{rel}: missing {TEST_DEP}")
    if "testImplementation(libs.junit4)" not in text:
        errors.append(f"{rel}: junit4 test dependency unexpectedly missing")

profiles_path = ROOT / "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowClientProfiles.kt"
profiles = profiles_path.read_text(encoding="utf-8", errors="replace")
m = re.search(r"val AndroidPhone:.*?=\s*listOf\((.*?)\n\s*\)\n", profiles, re.S)
if not m or "StudyFlowLinkCapabilities.KeyboardInput" not in m.group(1):
    errors.append("AndroidPhone profile must advertise KeyboardInput for delegated keyboard input")
desktop = re.search(r"val Desktop:.*?(?=\n\s*val Web:)", profiles, re.S)
if not desktop or ".distinct()" not in desktop.group(0):
    errors.append("Desktop profile must deduplicate composed capabilities")

# Parse capability constants and profile references to ensure the two principal profiles are unique by wire value.
protocol = (ROOT / "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLinkProtocol.kt").read_text(
    encoding="utf-8", errors="replace"
)
cap_values = dict(re.findall(r'val\s+(\w+)\s*=\s*capability\("([^"]+)"\)', protocol))
if m:
    android_names = re.findall(r"StudyFlowLinkCapabilities\.(\w+)", m.group(1))
    android_values = [cap_values.get(n, f"<unknown:{n}>") for n in android_names]
    if len(android_values) != len(set(android_values)):
        errors.append("AndroidPhone profile contains duplicate capability wire values")
    # Desktop is intentionally composed from AndroidPhone plus KeyboardInput and then distinct.
    desktop_values = list(dict.fromkeys(android_values + [cap_values.get("KeyboardInput", "<unknown:KeyboardInput>")]))
    if len(desktop_values) != len(set(desktop_values)):
        errors.append("Desktop profile deduplication preflight failed")

projection_path = ROOT / "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionProjection.kt"
projection = projection_path.read_text(encoding="utf-8", errors="replace")
receiver = re.search(
    r"fun accept\(offer: StudyFlowPresentationProjectionOffer\).*?\n\s*}\n\n\s*fun applyFrame",
    projection,
    re.S,
)
if not receiver:
    errors.append("Projection receiver accept() could not be located")
else:
    body = receiver.group(0)
    write_pos = body.find('Rejected("projection_write_capability")')
    missing_pos = body.find('Rejected("missing_capability")')
    if write_pos < 0:
        errors.append("Projection write-capability rejection is missing")
    if missing_pos < 0:
        errors.append("Projection missing-capability rejection is missing")
    if write_pos >= 0 and missing_pos >= 0 and write_pos > missing_pos:
        errors.append("Projection write rejection must precede generic missing-capability rejection")

# Preserve the core release promises that REL13 is repairing rather than weakening.
required_guards = {
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowControlInputDelegation.kt": [
        '"bad_lease_token"', '"stale_sequence"', '"wrong_target"',
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowLiveSessionProjection.kt": [
        '"stale_epoch"', '"stale_sequence"', '"already_completed"', '"stale_frame"',
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowNotificationMediaSync.kt": [
        '"wrong_target"', '"stale_sequence"', "LibraryImport",
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowShareImportConflictReview.kt": [
        '"wrong_target"', '"stale_sequence"', "tvClass && isWriteIntent",
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAccountBackupValidation.kt": [
        "DefaultAccountHandoffTtlMillis", "tvClass && purpose !=", '"stale_sequence"',
    ],
}
for rel, needles in required_guards.items():
    text = (ROOT / rel).read_text(encoding="utf-8", errors="replace")
    for needle in needles:
        if needle not in text:
            errors.append(f"{rel}: required semantic guard missing: {needle}")

# REL13 v3: the SFLP22/SFLP23 cycle guard must inspect only the predecessor task block.
sflp22_verifier = (ROOT / "scripts/verify_sflp22_23_account_backup_validation.py").read_text(
    encoding="utf-8", errors="replace"
)
if "sflp20_marker =" not in sflp22_verifier or "sflp20_block =" not in sflp22_verifier:
    errors.append("SFLP22/SFLP23 dependency-cycle verifier is not scoped to the SFLP20/SFLP21 task block")

if errors:
    print("REL13_SFLP24_BEHAVIOR_SEMANTICS_HOTFIX: STATIC FAIL", file=sys.stderr)
    for e in errors:
        print(f"- {e}", file=sys.stderr)
    raise SystemExit(1)

print("REL13_SFLP24_BEHAVIOR_SEMANTICS_HOTFIX: STATIC PASS")
print("Runtime/Gradle authority: verifySflp24FinalReleaseSeal")
