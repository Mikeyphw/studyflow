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

def string_names(rel):
    text = read(rel)
    root = ET.fromstring(text)
    return {node.attrib['name'] for node in root.findall('string') if 'name' in node.attrib}

if not phase_version_at_least(ROOT, 29):
    raise AssertionError("app version must be Phase 29 or later")

root_build = read("build.gradle.kts")
require(root_build, 'through Phase 29', 'repository contract description')
require(root_build, 'for phase in {0..29}; do', 'contract phase loop through Phase 29')

phase28 = read("scripts/verify_phase_28.py")
require(phase28, 'phase_version_at_least(ROOT, 28)', 'Phase 28 verifier phase-forward version check')
forbid(phase28, 'through Phase 28', 'stale exact Phase 28 build description')
forbid(phase28, 'for phase in {0..28}; do', 'stale exact Phase 28 loop check')

release_factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
match = re.search(r'private const val CurrentPhase = (\d+)', release_factory)
if not match or int(match.group(1)) < 29:
    raise AssertionError('release validation seal must move to Phase 29 or later')
require(release_factory, 'Phase 0 through 29 validators pass.', 'Phase 29 release-seal evidence')

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
require(settings, 'ReleaseCandidatePolishAuditor.evaluate(', 'settings release polish report')
require(settings, 'ReleaseCandidatePolishEvidence.verifiedForReleaseCandidateDocs()', 'settings explicit release-candidate evidence')
require(settings, 'release_candidate_polish_title', 'settings release candidate polish card')
require(settings, 'releaseCandidateReport.readyPercent', 'settings release polish percent')

en_keys = string_names("feature/settings/src/main/res/values/strings.xml")
pt_keys = string_names("feature/settings/src/main/res/values-pt-rBR/strings.xml")
for key in [
    'release_candidate_polish_title',
    'release_candidate_polish_value',
    'release_candidate_polish_message',
]:
    if key not in en_keys:
        raise AssertionError(f'missing English {key}')
    if key not in pt_keys:
        raise AssertionError(f'missing Portuguese {key}')
if not {'release_candidate_polish_title','release_candidate_polish_value','release_candidate_polish_message'} <= (en_keys & pt_keys):
    raise AssertionError('release-candidate resource parity failed')


reminder_planner = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt")
require(reminder_planner, 'const val PlannedActivityDeliveryGraceMillis: Long = 15 * 60 * 1000L', 'planned activity inexact-alarm grace constant')
require(reminder_planner, 'plannedActivityWithinDeliveryWindow(', 'planned activity delivery grace helper')
require(reminder_planner, 'scheduledStartEpochMillis >= nowEpochMillis - PlannedActivityDeliveryGraceMillis', 'late planned activity acceptance window')
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

print('PHASE_29_CONTRACT: PASSED (release candidate polish r2 + reminder delivery grace hotfix)')

if __name__ == '__main__':
    sys.exit(0)
