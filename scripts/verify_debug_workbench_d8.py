from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel):
    p = ROOT / rel
    if not p.exists():
        errors.append(f"Missing {rel}")
        return ""
    return p.read_text(encoding="utf-8")

def require(condition, message):
    if not condition:
        errors.append(message)

screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
seal = read("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchFinalSeal.kt")
seal_tests = read("core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchFinalSealTest.kt")
build = read("build.gradle.kts")
validate_repo = read("scripts/validate_repo.sh")
d7_seal = read("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchSeal.kt")
actions = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt")

probe_registry_test = read("app/src/test/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistryTest.kt")
workbench_actions_test = read("app/src/test/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActionsTest.kt")
d1_verifier = read("scripts/verify_debug_workbench_d1_r2.py")

require("fun `fatal errors are never converted into probe failures`(): Unit" in probe_registry_test,
        "D8 r2 hotfix missing block-body Unit fatal-error probe test")
require("fun `bundle cancellation is never converted into ordinary failure`(): Unit" in workbench_actions_test,
        "D8 r2 hotfix missing block-body Unit bundle-cancellation test")
require("private fun baseThreadName(name: String): String = name.substringBefore(\" @coroutine#\")" in workbench_actions_test,
        "D8 r4 hotfix missing coroutine debug thread-name normalizer")
require("store.copyThreads.map(::baseThreadName)" in workbench_actions_test
        and "baseThreadName(preparationThread)" in workbench_actions_test
        and "baseThreadName(launchThread)" in workbench_actions_test,
        "D8 r4 hotfix missing normalized coroutine thread assertions")
require('thread == "studyflow-debug-io"' not in workbench_actions_test
        and 'assertEquals("studyflow-bundle-io", preparationThread)' not in workbench_actions_test,
        "D8 r4 hotfix still contains brittle coroutine debug thread assertions")
require('if any(part in {"build", ".gradle"} for part in relative.parts):' in d1_verifier
        and 'continue' in d1_verifier,
        "D8 r2 hotfix missing generated diagnostics build-dir skip in D1 verifier")
require('FAILURES.append(f"forbidden generated diagnostics payload: {relative}")' in d1_verifier,
        "D8 r2 hotfix accidentally removed diagnostics payload guard")

require("name.contains(\"\\\\\")" in seal, "D8 final seal does not reject backslash path separators")

require("selfCheckHistory: RuntimeSelfCheckHistory = RuntimeSelfCheckHistory()" in screen,
        "DebugWorkbenchScreen does not expose self-check history parameter")
require("supportBundleSealReport: SupportBundleSealReport = SupportBundleSealReport.notRun()" in screen,
        "DebugWorkbenchScreen does not expose support-bundle seal parameter")
require("selfCheckHistory = selfCheckHistory" in app and "supportBundleSealReport = supportBundleSealReport" in app,
        "StudyFlowApp does not pass D7 state through DebugWorkbenchScreen")
require("private fun DebugOverview(" in screen
        and "selfCheckHistory: RuntimeSelfCheckHistory," in screen
        and "supportBundleSealReport: SupportBundleSealReport," in screen,
        "D8 r3 hotfix missing DebugOverview D7 state parameters")
require("DebugWorkbenchSection.OVERVIEW -> DebugOverview" in screen
        and "selfCheckHistory = selfCheckHistory" in screen
        and "supportBundleSealReport = supportBundleSealReport" in screen,
        "D8 r3 hotfix missing explicit D7 state forwarding into DebugOverview")

for token in [
    "enum class DebugWorkbenchMilestone",
    "D1_RECORDER_PRIVACY",
    "D2_SHELL_TIMELINE_REPORTS",
    "D3_TYPED_PROBE_REGISTRY",
    "D4_REMINDER_NOTIFICATION_LAB",
    "D5_DATABASE_PORTABILITY_LAB",
    "D6_STUDY_ENGINE_LAB",
    "D7_RUNTIME_SELF_CHECK_SUPPORT_SEAL",
    "D8_FINAL_RELEASE_SEAL",
    "object DebugWorkbenchFinalSealPolicy",
    "requiredValidators",
    "verify_debug_workbench_d8.py",
    "SUPPORT_BUNDLE_ALLOWLIST",
    "PROMISE_AUDIT",
]:
    require(token in seal, f"D8 final seal model missing {token}")

for phrase in [
    "final seal passes only",
    "final seal fails when a validator is missing",
    "rejects path-like validator names",
]:
    require(phrase in seal_tests, f"D8 final seal test missing {phrase}")

for phase in range(30):
    require(f"verify_phase_${{phase}}.py" in validate_repo or f"verify_phase_{phase}.py" in validate_repo,
            f"validate_repo.sh does not cover phase {phase}")

for validator in [
    "verify_debug_workbench_d1_r2.py",
    "verify_debug_workbench_d2.py",
    "verify_debug_workbench_d3.py",
    "verify_debug_workbench_d2_d3_corrective_seal.py",
    "verify_debug_workbench_d4.py",
    "verify_debug_workbench_d5.py",
    "verify_debug_workbench_d6.py",
    "verify_debug_workbench_d7.py",
    "verify_debug_workbench_d8.py",
]:
    require(validator in validate_repo, f"validate_repo.sh missing {validator}")
    require(validator in build, f"verifyRepositoryContracts missing {validator}")

for forbidden in [
    "READ_LOGS",
    "Logcat",
    "logcat",
    "ACTION_SEND_MULTIPLE",
    "studyflow.db",
    "shared_prefs",
    "datastore",
    "DataStore files",
    "annex files",
    "setExact(",
    "SCHEDULE_EXACT_ALARM",
    "USE_EXACT_ALARM",
    "Automatic upload",
    "automatic upload path",
]:
    require(forbidden not in actions, f"D8 action boundary contains forbidden export/logging hint: {forbidden}")

for forbidden_entry in ["studyflow.db", "shared_prefs", "datastore", "annex", "questions", "answers"]:
    require(forbidden_entry not in d7_seal.lower(), f"Support bundle seal allowlist contains forbidden entry hint {forbidden_entry}")

require((ROOT / "DEBUG_WORKBENCH_D8_IMPLEMENTATION.md").exists(), "D8 implementation doc missing")
require((ROOT / "docs/quality/DEBUG_WORKBENCH_D8_FINAL_RELEASE_SEAL.md").exists(), "D8 quality doc missing")
require((ROOT / "docs/validation/DEBUG_WORKBENCH_D8_VALIDATION.md").exists(), "D8 validation doc missing")

base = ROOT / "feature/settings/src/main/res"
try:
    values = ET.parse(base / "values" / "strings.xml").getroot()
    pt = ET.parse(base / "values-pt-rBR" / "strings.xml").getroot()
    values_names = {child.attrib.get("name") for child in values if child.tag == "string"}
    pt_names = {child.attrib.get("name") for child in pt if child.tag == "string"}
    require(values_names == pt_names, "English and pt-BR settings string resources are not key-aligned")
except Exception as exc:
    errors.append(f"XML parse or parity check failed: {exc}")

# D8 r5 lint seal: final focused validation must not regress on minSdk 23 API use.
registry = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistry.kt")
reminder_lab = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowReminderNotificationLab.kt")
require("notificationChannelImportance(channel)" in registry, "D8 r5 must route registry channel importance through the API-guarded helper")
require("notificationChannelImportance(channel)" in reminder_lab, "D8 r5 must route reminder lab channel importance through the API-guarded helper")
require("channel?.importance" not in registry, "Registry must not directly read NotificationChannel.importance")
require("channel?.importance" not in reminder_lab, "Reminder lab must not directly read NotificationChannel.importance")
require("channel.importance" not in reminder_lab, "Reminder lab notification post must not directly read NotificationChannel.importance")
database_mappers = [
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyLibraryEntityMapper.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyPracticeEntityMapper.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyProgressEntityMapper.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyRetentionEntityMapper.kt",
]
for mapper in database_mappers:
    mapper_text = read(mapper)
    require("import android.util.Base64" in mapper_text, f"{mapper} must use Android Base64 for minSdk 23")
    require("java.util.Base64" not in mapper_text, f"{mapper} must not use java.util.Base64")
    require("Base64.NO_WRAP" in mapper_text, f"{mapper} must preserve no-wrap Base64 semantics")


# D8 r6 lint seal: Android UI modules must not use java.time APIs without desugaring on minSdk 23.
android_time_files = [
    "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
    "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt",
]
for time_file in android_time_files:
    time_text = read(time_file)
    require("java.time" not in time_text, f"{time_file} must not use java.time APIs without desugaring on minSdk 23")
require("todayIsoDate()" in read("feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt"),
        "Progress screen must use minSdk-safe todayIsoDate helper")
require("startOfTodayEpochMillis()" in read("feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt"),
        "Schedule screen must use minSdk-safe startOfTodayEpochMillis helper")
sessions_text = read("feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt")
require("SimpleDateFormat" in sessions_text and "yyyy-MM-dd HH:mm" in sessions_text and "Date(epochMillis)" in sessions_text,
        "Sessions screen must use minSdk-safe SimpleDateFormat helper")


# Full-app final-user readiness seal checks.
practice_screen = read("feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt")
require("pluralStringResource(\n                                    R.plurals.question_count_value," in practice_screen,
        "Practice question count plurals must be wrapped for formatting seal")
phase29 = read("scripts/verify_phase_29.py")
require("plural_names" in phase29 and "missing English plural release_candidate_polish_message" in phase29,
        "Phase 29 verifier must validate release_candidate_polish_message as a plural resource")
planner = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySchedulePlanner.kt")
require("java.time" not in planner and "Calendar" in planner and "TimeZone" in planner,
        "StudySchedulePlanner must avoid java.time for minSdk 23 runtime compatibility")
practice_engine = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt")
require("java.util.Base64" not in practice_engine and "Base64UrlAlphabet" in practice_engine,
        "PracticeExecutionEngine must not rely on java.util.Base64 on Android minSdk 23")
settings_screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
require("diagnosticsAvailable: Boolean" in settings_screen and "if (diagnosticsAvailable)" in settings_screen,
        "Debug Workbench must not be shown unconditionally to release users")
require("syncConnectionStatusLabel" in settings_screen and "connectionStatus.name" not in settings_screen,
        "Settings sync card must use localized sync status labels")
sync_screen = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
require("syncConflictPolicyLabel" in sync_screen and ".name" not in sync_screen,
        "Sync Foundation screen must not show raw enum names")
require("currentSummary.changeCount" in sync_screen and "currentSummary.localChangeToken" not in sync_screen,
        "Sync Foundation screen must hide raw local change tokens")
require("FlowRow" in read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt"),
        "Debug Workbench section chips must wrap responsively")
require("FlowRow" in read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugLabsPanel.kt"),
        "Debug lab chips must wrap responsively")
seal = read("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchSeal.kt")
require("CHECKSUM_MISMATCH" in seal and "actualChecksums" in seal,
        "Support bundle seal must validate checksum contents")
actions = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt")
require("lastSealReport.set" in actions and "BUNDLE_MISSING" in actions and "actualPayloadChecksums" in actions,
        "Support bundle actions must clear stale seal state and verify payload checksums")
for enum_file in [
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt",
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt",
]:
    enum_text = read(enum_file)
    require("enumValueOrDefault" in enum_text, f"{enum_file} must safely restore saved enum state")

if errors:
    print("D8 verifier failed:")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("D8 verifier passed")
