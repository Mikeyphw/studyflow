#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required file: {rel}")
    data = path.read_bytes()
    if b"\x00" in data:
        raise AssertionError(f"NUL byte found in text source: {rel}")
    return data.decode()

def require(text, needle, label):
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")

def forbid(text, needle, label):
    if needle in text:
        raise AssertionError(f"forbidden {label}: {needle}")

def resource_names(rel, tag):
    text = read(rel)
    root = ET.fromstring(text)
    return {node.attrib['name'] for node in root.findall(tag) if 'name' in node.attrib}

def string_names(rel):
    return resource_names(rel, 'string')

def plural_names(rel):
    return resource_names(rel, 'plurals')

if not phase_version_at_least(ROOT, 29):
    raise AssertionError("app version must be Phase 29 or later")

root_build = read("build.gradle.kts")
match = re.search(r'through Phase (\d+)', root_build)
if not match or int(match.group(1)) < 29:
    raise AssertionError('repository contract description must cover Phase 29 or later')
match = re.search(r'for phase in \{0\.\.(\d+)\}; do', root_build)
if not match or int(match.group(1)) < 29:
    raise AssertionError('contract phase loop must cover Phase 29 or later')

phase28 = read("scripts/verify_phase_28.py")
require(phase28, 'phase_version_at_least(ROOT, 28)', 'Phase 28 verifier phase-forward version check')
forbid(phase28, 'through Phase 28', 'stale exact Phase 28 build description')
forbid(phase28, 'for phase in {0..28}; do', 'stale exact Phase 28 loop check')

release_factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
match = re.search(r'private const val CurrentPhase = (\d+)', release_factory)
if not match or int(match.group(1)) < 29:
    raise AssertionError('release validation seal must move to Phase 29 or later')
match = re.search(r'Phase 0 through (\d+) validators pass\.', release_factory)
if not match or int(match.group(1)) < 29:
    raise AssertionError('release-seal evidence must cover Phase 29 or later')

polish = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseCandidatePolish.kt")
for area in [
    'SETTINGS_POLISH', 'EMPTY_ERROR_LOADING_STATES', 'ACCESSIBILITY_LABELS', 'RESOURCE_PARITY',
    'TRUST_COPY', 'FIRST_RUN_UPGRADE_NOTES', 'FINAL_ROADMAP_INDEX'
]:
    require(polish, area, f'release candidate polish area {area}')
require(polish, 'data class ReleaseCandidatePolishEvidence', 'evidence-backed polish input')
require(polish, 'ReleaseCandidatePolishEvidence()', 'default missing-evidence gate')
require(polish, 'verifiedForReleaseCandidateDocs', 'explicit verified-evidence constructor')
require(polish, 'blockingCount == 0', 'release polish blocking gate')
require(polish, 'ReleaseCandidatePolishArea.entries.toSet()', 'all-area coverage')
require(polish, 'avoids fake cloud-provider, account-identity, or exact-alarm claims', 'trust copy audit evidence')
forbid(polish, 'complete = true', 'unconditional green polish finding')

tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseCandidatePolishAuditorTest.kt")
require(tests, 'releaseCandidatePolishCoversEveryPromisedAreaWhenEvidenceIsPresent', 'release polish positive test')
require(tests, 'releaseCandidatePolishBlocksWhenEvidenceIsMissing', 'release polish missing-evidence test')
require(tests, 'releaseCandidatePolishBlocksPartialEvidence', 'release polish partial-evidence test')
require(tests, 'assertEquals(100, report.readyPercent)', 'polish readiness assertion')
require(tests, 'assertFalse(report.releaseReady)', 'blocking assertion')

settings = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
for marker in [
    'ReleaseCandidatePolishAuditor.evaluate(',
    'ReleaseCandidatePolishEvidence.verifiedForReleaseCandidateDocs()',
    'release_candidate_polish_title',
    'releaseCandidateReport.readyPercent',
]:
    if marker in settings:
        raise AssertionError(f'settings screen still exposes release-candidate polish marker: {marker}')

en_keys = string_names("feature/settings/src/main/res/values/strings.xml")
pt_keys = string_names("feature/settings/src/main/res/values-pt-rBR/strings.xml")
en_plurals = plural_names("feature/settings/src/main/res/values/strings.xml")
pt_plurals = plural_names("feature/settings/src/main/res/values-pt-rBR/strings.xml")
if en_keys != pt_keys:
    raise AssertionError('settings string resource parity failed')
if en_plurals != pt_plurals:
    raise AssertionError('settings plural resource parity failed')


reminder_planner = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt")
require(reminder_planner, 'const val PlannedActivityDeliveryGraceMillis: Long = 15 * 60 * 1000L', 'planned activity inexact-alarm grace constant')
require(reminder_planner, 'plannedActivityWithinDeliveryWindow(', 'planned activity delivery grace helper')
require(reminder_planner, 'scheduledStartEpochMillis >= nowEpochMillis - PlannedActivityLateDeliveryGraceMillis', 'late planned activity acceptance window')
require(reminder_planner, 'activity.status == PlannedActivityStatus.PLANNED', 'completed/missed planned activity suppression for planned reminders')

reminder_delivery = read("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt")
require(reminder_delivery, 'StudyReminderPlanner.PlannedActivityDeliveryGraceMillis', 'delivery coordinator uses domain grace as alarm window')
forbid(reminder_delivery, 'private const val AlarmWindowMillis = 15 * 60 * 1000L', 'duplicated alarm window constant')

reminder_hotfix_tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerPhase29HotfixTest.kt")
for hotfix_test in [
    'plannedActivityStillDeliversWhenInexactAlarmFiresInsideWindow',
    'stalePlannedActivityAlarmOutsideWindowIsSuppressed',
    'quietHoursStillSuppressLatePlannedActivityDelivery',
    'completedActivitiesRemainSuppressedEvenInsideDeliveryWindow',
]:
    require(reminder_hotfix_tests, hotfix_test, f'planned activity delivery grace test {hotfix_test}')


# Phase 29B reminder/time/backup hardening checks.
require(reminder_planner, 'import java.util.Calendar', 'local Calendar import for day boundaries')
require(reminder_planner, 'PlannedActivityLateDeliveryGraceMillis: Long = 2 * 60 * 60 * 1000L', 'late planned-activity delivery grace')
require(reminder_planner, 'startOfNextLocalDay(', 'local next-day calculation')
require(reminder_planner, 'sameLocalDay(', 'local today calculation')
require(reminder_planner, 'Calendar.DAY_OF_YEAR', 'local day comparison')
forbid(reminder_planner, 'DayMillis', 'UTC epoch-day math')
require(reminder_delivery, 'openAppPendingIntent(planId, candidate.type)', 'notification content intent carries reminder type')
require(reminder_delivery, 'putExtra(StudyReminderExtraType, type.name)', 'notification deep-link reminder type extra')
require(reminder_delivery, 'notificationId(planId, type)', 'per-reminder notification content PendingIntent request code')
main_activity = read("app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt")
require(main_activity, 'data class StudyReminderLaunch', 'notification launch bridge model')
require(main_activity, 'onNewIntent(intent: Intent)', 'notification tap new-intent handling')
require(main_activity, 'toStudyReminderLaunch()', 'notification intent extraction')
app_source = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
require(app_source, 'reminderLaunch: StudyReminderLaunch?', 'StudyFlowApp reminder launch parameter')
require(app_source, 'ReminderNotificationType.DUE_REVIEWS -> navController.navigate(ReviewsRoute', 'due-review notification route')
require(app_source, 'ReminderNotificationType.PLANNED_ACTIVITY,', 'planned notification route')
require(app_source, 'ReminderNotificationType.MISSED_ACTIVITY -> navController.navigate(ScheduleRoute', 'missed notification route')
require(app_source, 'ReminderNotificationType.DAILY_SUMMARY -> navController.navigate(DashboardRoute', 'daily summary notification route')
require(app_source, 'onNotificationPermissionResult = { granted ->', 'notification permission reconcile callback wiring')
require(app_source, 'container.reminderDeliveryCoordinator.reconcilePlan(currentPlan.id)', 'permission grant reminder reconciliation')
reminder_screen = read("feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
require(reminder_screen, 'onNotificationPermissionResult: (Boolean) -> Unit = {}', 'reminders permission result callback API')
require(reminder_screen, 'onNotificationPermissionResult(granted)', 'permission launcher callback dispatch')
receivers = read("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceivers.kt")
require(receivers, 'ReconcileReminderScheduleActions', 'shared reconcile actions set')
for action in ['ACTION_BOOT_COMPLETED', 'ACTION_TIME_CHANGED', 'ACTION_TIMEZONE_CHANGED', 'ACTION_DATE_CHANGED', 'ACTION_MY_PACKAGE_REPLACED']:
    require(receivers, action, f'receiver reconcile action {action}')
manifest = read("app/src/main/AndroidManifest.xml")
for action_name in ['BOOT_COMPLETED', 'TIME_SET', 'TIMEZONE_CHANGED', 'DATE_CHANGED', 'MY_PACKAGE_REPLACED']:
    require(manifest, f'android.intent.action.{action_name}', f'manifest reminder recovery action {action_name}')
data_rules = read("app/src/main/res/xml/data_extraction_rules.xml")
require(data_rules, 'disableIfNoEncryptionCapabilities="true"', 'encrypted cloud backup capability requirement')
phase29b_tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerPhase29BHardeningTest.kt")
for test_name in [
    'dailySummaryUsesNextLocalMidnightInsteadOfUtcBucket',
    'latePlannedActivitySurvivesDozeDelayInsideLateDeliveryGrace',
    'veryOldPlannedActivityStillSuppressedOutsideLateDeliveryGrace',
]:
    require(phase29b_tests, test_name, f'Phase 29B hardening test {test_name}')
require(read("docs/quality/PHASE_29B_REMINDER_TIME_BACKUP_HARDENING.md"), 'local-time day math', 'Phase 29B hardening docs')

docs = read("docs/quality/PHASE_29_RELEASE_CANDIDATE_POLISH.md")
require(docs, 'settings polish', 'settings polish docs')
require(docs, 'empty/error/loading states pass', 'empty/error/loading docs')
require(docs, 'accessibility labels', 'accessibility docs')
require(docs, 'Portuguese/English resource completeness', 'resource parity docs')
require(docs, 'privacy/sync/reminder copy review', 'trust copy docs')
require(docs, 'first-run and upgrade notes', 'first run docs')
require(docs, 'final roadmap index', 'roadmap index docs')
require(read("docs/product/FIRST_RUN_AND_UPGRADE_NOTES.md"), 'Local-first by default', 'first-run notes')
require(read("docs/roadmap/FINAL_ROADMAP_INDEX.md"), 'Phase 29', 'final roadmap index through Phase 29')

print('PHASE_29_CONTRACT: PASSED (release candidate polish r2 + reminder delivery grace + Phase 29B hardening)')

if __name__ == '__main__':
    sys.exit(0)
