#!/usr/bin/env python3
"""HF22 source-only regression contract: production checkpoints and non-qualifying hotfix lane."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / 'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
VERIFY = ROOT / 'scripts/sfdt22_emulator_verify.py'
CI = ROOT / '.github/workflows/studyflow-ci.yml'
HOTFIX = ROOT / '.github/workflows/studyflow-sfdt22-hotfix-session.yml'
WF = ROOT / '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
SFDT21 = ROOT / '.github/workflows/studyflow-sfdt21-hosted-ui.yml'
TRIAGE = ROOT / 'scripts/sfdt22_failure_triage.py'


class HF22SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kotlin = TEST.read_text()
        cls.verify = VERIFY.read_text()
        cls.workflow = WF.read_text()
        cls.ci = CI.read_text()
        cls.hotfix = HOTFIX.read_text()
        cls.sfdt21 = SFDT21.read_text()
        sys.path.insert(0, str(ROOT / 'scripts'))
        import sfdt22_emulator_verify as v
        cls.v = v

    def test_01_real_ui_unchanged_and_read_only_checkpoints(self):
        k = self.kotlin
        self.assertIn('createAndroidComposeRule<MainActivity>()', k)
        self.assertEqual(k.count('@Test fun '), 5)
        self.assertEqual(k.count('screenshot("'), 7)
        self.assertIn('SQLiteDatabase.OPEN_READONLY', k)
        self.assertIn('context.getDatabasePath("studyflow.db")', k)
        self.assertIn('WHERE plans.name = ?', k)
        self.assertIn('LEFT JOIN study_plan_settings', k)
        self.assertIn('arrayOf(planName)', k)
        # Historical SFDT22 tests disallow any injected domain or blocking coroutine.
        for forbidden in ('container.manageStudy', 'runBlocking {', 'setContent('):
            self.assertNotIn(forbidden, k)
        self.assertIn('rule.activity.getString(SessionsR.string.sessions_subtitle, planName)', k)
        self.assertIn('Context.MODE_APPEND', k)
        # Historical HF11/HF13 acceptance asserts these exact stages.
        self.assertIn('stage("subject_confirm") { awaitSubjectSaved() }', k)
        self.assertIn('stage("topic_capture") { screenshot("topic") }', k)
        self.assertIn('stage("session_start") { clickSessionAction("studyflow.session.quick.start") }', k)
        self.assertIn('SFDT22_BOUNDARY_SESSION_ROUTE_MISMATCHED', k)
        for phase in ('after_create', 'after_subject', 'after_topic', 'session_route', 'before_session_start'):
            self.assertIn('"' + phase + '"', k)
        self.assertNotIn('setContent {', k)
        self.assertNotIn('mockk(', k)
        self.assertNotIn('assumeTrue(', k)
        self.assertNotIn('run-as', k)  # Only host collects sanitized evidence.

    def test_02_strict_checkpoint_parser_and_failure_mapping(self):
        v = self.v
        valid = b'after_create|present\nafter_subject|present\nafter_topic|present\nsession_route|matched\nbefore_session_start|missing\n'
        parsed = v.parse_boundaries(valid)
        self.assertEqual(len(parsed), 5)
        self.assertEqual(parsed[-1], {'point': 'before_session_start', 'state': 'missing'})
        for bad in (b'after_create|present\nafter_create|present\n',
                    b'after_topic|present\n', b'after_create|present|data\n',
                    b'after_create|SECRET\n', b'after_create|present\n' + b'X' * 1024):
            with self.assertRaises((ValueError, UnicodeDecodeError)):
                v.parse_boundaries(bad)
        self.assertEqual(v.expected_boundaries(1), [{'point': 'after_create', 'state': 'present'}])
        self.assertEqual(len(v.expected_boundaries(3)), 5)
        self.assertEqual(len(v.expected_boundaries(5)), 5)
        raw = ('INSTRUMENTATION_STATUS: test=realUserCreatesAStudyPlan\n'
               'SFDT22_STEP_plan_confirm SFDT22_BOUNDARY_AFTER_CREATE_MISSING')
        failure = v.summarize_failure(raw, 'realUserCreatesAStudyPlan', 1)
        self.assertEqual((failure.stage, failure.reason), ('plan_confirm', 'boundary_after_create_missing'))
        self.assertEqual(v.summarize_failure('SFDT22_BOUNDARY_BEFORE_SESSION_START_MISSING',
                         'realUserStartsPausesResumesAndCompletesASession', 1).reason,
                         'boundary_before_session_start_missing')
        self.assertEqual(v.summarize_failure('SFDT22_BOUNDARY_SESSION_ROUTE_MISMATCHED',
                         'realUserStartsPausesResumesAndCompletesASession', 1).reason,
                         'boundary_session_route_mismatched')

    def test_03_full_emulator_gate_not_downgraded(self):
        w = self.workflow
        self.assertIn('pull_request:', w)
        self.assertIn('push:', w)
        self.assertIn('branches: [main]', w)
        self.assertIn('needs: [source, emulator]', w)
        self.assertIn('test "$SOURCE_RESULT" = success', w)
        self.assertIn('test "$EMULATOR_RESULT" = success', w)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify ', w)
        self.assertIn('if: always()', w)
        self.assertIn('seven unique screenshots', w)
        self.assertIn('cache-read-only:', w)
        self.assertNotIn('continue-on-error:', w)
        self.assertNotIn('cache-disabled: true', w)
        self.assertIn('force_stopped_app_and_verified_new_pid=True', self.verify)
        self.assertIn('require(data.get(\'boundaries\') == expected_boundaries(5)', self.verify)

    def test_04_manual_hotfix_is_opt_in_and_never_release_qualifies(self):
        c, h = self.ci, self.hotfix
        self.assertIn('options: [all, android, desktop, web, deep]', c)
        self.assertNotIn('sfdt22_hotfix:', c)
        for lane in ('web', 'desktop', 'android'):
            original = "    if: ${{ github.event_name == 'workflow_dispatch' && (inputs.lane == 'all' || inputs.lane == '"+lane+"') }}"
            self.assertIn(original, c)
        self.assertIn("    if: ${{ github.event_name == 'workflow_dispatch' && inputs.lane == 'deep' }}", c)
        self.assertEqual(c.count('test "$EXPECTED_SHA" = "$GITHUB_SHA"'), 1)
        self.assertIn('types: [labeled]', h)
        self.assertIn("github.event.label.name == 'sfdt22-hotfix-session'", h)
        self.assertIn("github.event.pull_request.head.repo.full_name == 'Mikeyphw/studyflow'", h)
        self.assertNotIn('pull_request_target:', h)
        self.assertNotIn('push:', h)
        self.assertIn('test "$(git rev-parse HEAD)" = "$GITHUB_SHA"', h)
        self.assertIn('test "$EXPECTED_SHA" = "$GITHUB_SHA"', h)
        self.assertEqual(h.count('--diagnostic-through practice'), 2)
        self.assertNotIn('--diagnostic-through session', h)
        self.assertIn('scripts/sfdt22_emulator_verify.py --run --diagnostic-through practice', h)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify-diagnostic --diagnostic-through practice', h)
        self.assertEqual(self.v.DIAGNOSTIC_THROUGH['practice'], 4)
        self.assertIn('--verify-diagnostic', h)
        self.assertIn('cache-read-only: true', h)
        self.assertIn('release_qualified":false', h)
        self.assertIn('publication_allowed":false', h)
        self.assertNotIn('continue-on-error:', h)
        self.assertNotIn('cache-disabled: true', self.sfdt21)
        self.assertIn('cache-read-only:', self.sfdt21)
        self.assertEqual(self.v.DIAGNOSTIC_THROUGH['session'], 3)
        self.assertNotEqual(self.v.DIAGNOSTIC_PASS, self.v.EXPECTED)
        self.assertIn("data.get('overall') == DIAGNOSTIC_PASS", self.verify)
        self.assertIn("require(not list(output.glob('*.png'))", self.verify)

    def test_06_read_only_sql_checkpoint_has_expected_semantics(self):
        import sqlite3
        db = sqlite3.connect(':memory:')
        db.executescript("""
            CREATE TABLE study_plans (id TEXT PRIMARY KEY, name TEXT NOT NULL);
            CREATE TABLE study_plan_settings (studyPlanId TEXT PRIMARY KEY);
            INSERT INTO study_plans VALUES ('p1','SFDT22 Study Journey');
        """)
        query = ("SELECT COUNT(*) AS matchedPlans, COUNT(settings.studyPlanId) AS storedSettings "
                 "FROM study_plans AS plans LEFT JOIN study_plan_settings AS settings "
                 "ON settings.studyPlanId = plans.id WHERE plans.name = ?")
        self.assertEqual(db.execute(query, ('SFDT22 Study Journey',)).fetchone(), (1, 0))
        db.execute("INSERT INTO study_plan_settings VALUES ('p1')")
        self.assertEqual(db.execute(query, ('SFDT22 Study Journey',)).fetchone(), (1, 1))
        db.execute("INSERT INTO study_plans VALUES ('p2','SFDT22 Study Journey')")
        self.assertEqual(db.execute(query, ('SFDT22 Study Journey',)).fetchone(), (2, 1))
        db.close()

    def test_05_triage_outputs_no_unknown_identifiers(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'SFDT22_EVIDENCE.json'
            p.write_text(json.dumps({
                'overall': 'FAILED_OR_NOT_RUN', 'failure_phase': 'session',
                'failure_step': 'session_start',
                'failure_reason_code': 'boundary_before_session_start_missing',
                'completed_before_failure': 2,
                'boundaries': [
                    {'point': 'after_create', 'state': 'present'},
                    {'point': 'after_subject', 'state': 'present'},
                    {'point': 'after_topic', 'state': 'present'},
                    {'point': 'session_route', 'state': 'matched'},
                    {'point': 'before_session_start', 'state': 'missing'},
                ],
                'unexpected_personal_data': 'PRIVATE_DO_NOT_PRINT',
            }))
            proc = subprocess.run([sys.executable, '-B', str(TRIAGE), str(p)],
                                  capture_output=True, text=True, check=False)
            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn('last_known_good=session_route', proc.stdout)
            self.assertNotIn('last_known_good=after_topic', proc.stdout)
            self.assertIn('first_known_bad=before_session_start', proc.stdout)
            self.assertNotIn('PRIVATE_DO_NOT_PRINT', proc.stdout)
            self.assertNotIn('unexpected_personal_data', proc.stdout)


if __name__ == '__main__':
    unittest.main(verbosity=2)
