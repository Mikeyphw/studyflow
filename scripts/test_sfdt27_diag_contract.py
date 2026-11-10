#!/usr/bin/env python3
"""DIAG-01 source contract and adversarial event parser tests (no emulator/Gradle)."""
from __future__ import annotations
import ast
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt22_emulator_verify as verify

BASE_EVENT='1|android_test|TRACE_RESET|none|unknown|none|unknown|unknown|none'

class TraceContract(unittest.TestCase):
    def parse(self, rows):
        return verify.sfdt27_diag_parse(('V2\n'+'\n'.join(rows)+'\n').encode('ascii'))

    def test_valid_bounded_allowlisted(self):
        a=self.parse([BASE_EVENT,'2|ui|TOPIC_UI_CLICK|upsert_topic|not_started|ui|unknown|unknown|none'])
        self.assertEqual([x['seq'] for x in a],[1,2])
        self.assertEqual(a[1]['checkpoint'],'TOPIC_UI_CLICK')

    def test_duplicate_gap_reorder(self):
        for rows in ([BASE_EVENT,BASE_EVENT],
                     [BASE_EVENT,'3|ui|TOPIC_UI_CLICK|upsert_topic|not_started|ui|unknown|unknown|none'],
                     [BASE_EVENT,'0|ui|TOPIC_UI_CLICK|upsert_topic|not_started|ui|unknown|unknown|none']):
            with self.assertRaises(ValueError):self.parse(rows)

    def test_unknown_keys_and_overlong_strings(self):
        for raw in [b'V2\n'+BASE_EVENT.encode()+b'|surprise\n',
                    b'V2\n1|ui|PRIVACY_LEAK|none|unknown|none|unknown|unknown|none\n',
                    b'V2\n1|ui|TRACE_RESET|none|unknown|none|unknown|unknown|raw user data\n',
                    b'V2\n'+BASE_EVENT.encode()+b'\n'+b'X'*17_000]:
            with self.assertRaises((ValueError, UnicodeError)):
                verify.sfdt27_diag_parse(raw)

    def test_invalid_unicode_truncation_crashes(self):
        for raw in [b'V2\n'+BASE_EVENT.encode()+b'\n\xff',
                    b'V2\n'+BASE_EVENT.encode(),b'',b'V2\n',b'V2\r\n'+BASE_EVENT.encode()+b'\n']:
            with self.assertRaises((ValueError,UnicodeError)):
                verify.sfdt27_diag_parse(raw)

    def test_event_count_limited(self):
        rows=[str(i)+'|ui|TOPIC_UI_CLICK|upsert_topic|not_started|ui|unknown|unknown|none'
              for i in range(1,66)]
        with self.assertRaises(ValueError):self.parse(rows)

    def test_canary_strings_never_export(self):
        canaries=['Mikey person@gmail.com','test-secret-123','DROP TABLE study_plans',
                  "x' OR 1=1 --",'planId=a8ef6']
        for canary in canaries:
            with self.assertRaises((ValueError,UnicodeError)):
                self.parse([BASE_EVENT.replace('unknown',canary,1)])

    def test_preserves_five_real_journeys(self):
        ui=(ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
        self.assertEqual(ui.count('@Test fun '),5)
        self.assertIn('createAndroidComposeRule<MainActivity>()',ui)
        self.assertIn('Sfdt27DiagnosticTrace.resetForHostedJourney()',ui)
        self.assertNotIn('setContent {',ui)
        self.assertNotIn('assumeTrue(',ui)

    def test_settings_preservation_not_removed(self):
        cr=(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt').read_text()
        pr=(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt').read_text()
        self.assertIn('insertMissingPlanSettings(settingsBeforeTopic)',cr)
        self.assertIn('insertMissingPlanSettings(settingsBefore)',cr)
        self.assertIn('PLAN_SETTINGS_CHANGED_DURING_TOPIC_SAVE',cr)
        self.assertIn('PLAN_SETTINGS_CHANGED_DURING_CONTENT_PROGRESS',cr)
        self.assertIn('studyPlanDao.updateExisting(entity) == 1',pr)
        self.assertIn('TOPIC_TRANSACTION_COMMIT',cr)
        self.assertIn('TOPIC_TRANSACTION_ROLLBACK',cr)

    def test_release_off_and_fixed_codes(self):
        sink=(ROOT/'core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/Sfdt27AndroidTraceSink.kt').read_text()
        trace=(ROOT/'core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/Sfdt27DiagnosticTrace.kt').read_text()
        self.assertIn('ApplicationInfo.FLAG_DEBUGGABLE',sink)
        self.assertIn('AtomicFile(',sink)
        for x in ('MAX_EVENTS = 64','MAX_BYTES = 16_384','@Synchronized','installDebugSink'):
            self.assertIn(x,trace)
        for x in ('stackTraceToString','Log.e(','println(','error.message','cause.toString('):
            self.assertNotIn(x,sink+trace)

    def test_hosted_gates_and_target_ci_unchanged(self):
        full=(ROOT/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
        focused=(ROOT/'.github/workflows/studyflow-sfdt22-hotfix-session.yml').read_text()
        targeted=(ROOT/'.github/workflows/studyflow-targeted.yml').read_text()
        self.assertIn('seven unique screenshots',full)
        self.assertIn('needs: [source, emulator]',full)
        self.assertIn('api-level: 36',full)
        self.assertIn('test_sfdt27_diag_contract.py',focused)
        ver=(ROOT/'scripts/sfdt22_emulator_verify.py').read_text()
        self.assertIn('if diagnostic_through and index == 1:',ver)
        self.assertIn('sfdt27_diag_attach(evidence)',ver)
        self.assertIn("if 'sfdt27_diag_capture' not in evidence:",ver)
        self.assertIn('test_sfdt27_diag_contract.py',full)
        self.assertIn('Targeted required-result gate',targeted)
        self.assertNotIn('continue-on-error:',targeted+focused)

if __name__ == '__main__':
    unittest.main(verbosity=2)
