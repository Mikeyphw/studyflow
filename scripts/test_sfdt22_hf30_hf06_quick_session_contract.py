#!/usr/bin/env python3
"""HF30 HF06: source contracts for terminal quick-session transitions and real Room gate.

Offline checks never qualify an Android device or release artifacts.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = 'core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt'
UNIT = 'core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySessionFocusTest.kt'
ROOM = 'core/data/src/androidTest/kotlin/com/mikeyphw/studyflow/core/data/StudySessionAtomicStateTest.kt'
BASH = 'scripts/sfdt22_emulator_ci.sh'
FULL = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC = 'docs/actions/SFDT22_HF30_HF06_QUICK_SESSION_TRANSITIONS.md'
THIS = 'scripts/test_sfdt22_hf30_hf06_quick_session_contract.py'

class QuickSessionTerminalContracts(unittest.TestCase):
    def read(self, path):
        return (ROOT / path).read_text()

    def test_01_no_fictitious_planned_activity_for_terminal_quick_sessions(self):
        domain = self.read(DOMAIN)
        completion = domain.split('    suspend fun complete(', 1)[1].split('    suspend fun cancel(', 1)[0]
        cancellation = domain.split('    suspend fun cancel(', 1)[1].split('    suspend fun saveWritingDraft(', 1)[0]
        self.assertEqual(completion.count('if (current.plannedActivityId != null)'), 3)
        self.assertEqual(cancellation.count('if (current.plannedActivityId != null)'), 2)
        for block in (completion, cancellation):
            self.assertIn('PlannedActivityStatus.IN_PROGRESS else null', block)
        self.assertIn('PlannedActivityStatus.COMPLETED else null', completion)
        self.assertIn('finished.endedAtEpochMillis else null', completion)
        self.assertIn('PlannedActivityStatus.PLANNED else null', cancellation)

    def test_02_unit_fake_rejects_phantom_link_and_has_cancellation_test(self):
        s = self.read(UNIT)
        self.assertIn('if (session.plannedActivityId == null)', s)
        self.assertIn('require(linkedActivityExpectedStatus == null)', s)
        self.assertIn('require(linkedActivityStatus == null)', s)
        self.assertIn('require(linkedActivityCompletedAtEpochMillis == null)', s)
        self.assertIn('fun quickSessionCancelsWithoutInventingAPlannedActivity()', s)
        self.assertIn('fun completionPersistsRatingsAndFreezesDuration()', s)

    def test_03_real_room_tests_cover_three_flows_and_preexisting_cancel(self):
        s = self.read(ROOM)
        for name in (
            'quickSessionCompletesAndPersistsWithoutPlannedActivity',
            'quickSessionCancelsAndPersistsWithoutPlannedActivity',
            'linkedSessionCompletionStillUpdatesPlannedActivity',
            'cancellingSessionRestoresLinkedActivityToItsOriginalMissedState',
        ):
            self.assertIn('fun ' + name + '()', s)
        self.assertIn('Room.inMemoryDatabaseBuilder', s)
        self.assertIn('ManageStudySession(', s)
        self.assertIn('RoomStudySessionRepository(database)', s)
        self.assertIn('database.studyPlanningDao().findStudySession', s)
        self.assertNotIn('mockk(', s)

    def test_04_real_android_gate_and_strict_source_pins(self):
        bash = self.read(BASH)
        full = self.read(FULL)
        self.assertIn(':core:data:connectedDebugAndroidTest', bash)
        self.assertIn('android.testInstrumentationRunnerArguments.class=', bash)
        self.assertIn('StudySessionAtomicStateTest', bash)
        self.assertIn('SFDT22_HF06_REAL_ROOM_REGRESSION_PASS', bash)
        self.assertIn('script: bash scripts/sfdt22_emulator_ci.sh', full)
        self.assertIn('api-level: 36', full)
        self.assertIn('needs: [source, emulator]', full)
        self.assertIn('seven unique screenshots', full)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',
                      self.read('scripts/sfdt22_emulator_verify.py'))
        self.assertNotIn('continue-on-error:', full)
        for path in (DOMAIN, UNIT, ROOM, BASH, DOC, THIS):
            digest = hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
            self.assertIn(digest + '  ' + path, full, path)
        self.assertIn('python3 -B ' + THIS, full)

if __name__ == '__main__':
    unittest.main(verbosity=2)
