#!/usr/bin/env python3
"""HF30 HF02 — inherited HF22/HF26 contracts must recognize the exact four-journey lane.

Source-only, fail-closed. This is not Android or release qualification.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FOCUSED = '.github/workflows/studyflow-sfdt22-hotfix-session.yml'
FULL = '.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
VERIFY = 'scripts/sfdt22_emulator_verify.py'
HF22 = 'scripts/test_sfdt22_hf22_contract.py'
HF26 = 'scripts/test_sfdt22_hf26_fast_contract.py'
THIS = 'scripts/test_sfdt22_hf30_hf02_contract.py'
DOC = 'docs/actions/SFDT22_HF30_HF02_HISTORICAL_GATE_MIGRATION.md'

class HistoricalGateMigration(unittest.TestCase):
    def get(self,path:str)->str:
        return (ROOT/path).read_text(encoding='utf-8')

    def test_01_focused_gate_runs_real_practice_without_promoting_release(self):
        s=self.get(FOCUSED)
        self.assertEqual(s.count('--diagnostic-through practice'),2)
        self.assertNotIn('--diagnostic-through session',s)
        self.assertIn('script: python3 -B scripts/sfdt22_emulator_verify.py --run --diagnostic-through practice',s)
        self.assertIn('python3 -B scripts/sfdt22_emulator_verify.py --verify-diagnostic --diagnostic-through practice',s)
        self.assertIn('diagnostic_only":true',s)
        self.assertIn('release_qualified":false',s)
        self.assertIn('publication_allowed":false',s)
        self.assertIn('test "$EXPECTED_SHA" = "$GITHUB_SHA"',s)
        self.assertIn("github.event.label.name == 'sfdt22-hotfix-session'",s)
        self.assertNotIn('pull_request_target:',s)
        self.assertNotIn('\n  push:',s)
        self.assertNotIn('continue-on-error:',s)
        self.assertIn('cache-read-only: true',s)

    def test_02_historical_contracts_are_not_stale_or_weakened(self):
        a=self.get(HF22);b=self.get(HF26)
        for s in (a,b):
            self.assertIn("assertNotIn('--diagnostic-through session'",s)
            self.assertIn("assertIn('scripts/sfdt22_emulator_verify.py --run --diagnostic-through practice'",s)
            self.assertIn("assertIn('scripts/sfdt22_emulator_verify.py --verify-diagnostic --diagnostic-through practice'",s)
            self.assertNotIn("assertIn('--diagnostic-through session'",s)
            self.assertIn('continue-on-error:',s)
            self.assertIn('release_qualified',s)
            self.assertIn('publication_allowed',s)
            self.assertIn('EXPECTED_SHA',s)
        self.assertIn("self.assertEqual(h.count('--diagnostic-through practice'), 2)",a)
        self.assertIn("self.assertEqual(f.count('--diagnostic-through practice'), 2)",b)
        self.assertIn("DIAGNOSTIC_THROUGH['practice'], 4",a)
        self.assertIn('test_06_staging_dispatch_does_not_mark_four_journeys_as_release_pass',b)

    def test_03_hosted_receipt_semantics_are_unchanged(self):
        s=self.get(VERIFY)
        self.assertIn("DIAGNOSTIC_THROUGH = {'plan': 1, 'subjects': 2, 'session': 3, 'practice': 4}",s)
        self.assertIn("DIAGNOSTIC_PASS = 'DIAGNOSTIC_PASS_NOT_RELEASE_QUALIFIED'",s)
        self.assertIn("'realUserSeesPersistedProgressAfterColdRestart'",s)
        self.assertIn("SCREENS = ('plan', 'subject', 'topic', 'session', 'question', 'review', 'restart')",s)
        self.assertIn('real_process_restart()',s)
        self.assertIn('force_stopped_app_and_verified_new_pid=True',s)

    def test_04_full_gate_has_verified_sha_and_execution_for_every_migration(self):
        s=self.get(FULL)
        self.assertIn('needs: [source, emulator]',s)
        self.assertIn('Independently verify five test receipts',s)
        self.assertIn('seven unique screenshots',s)
        self.assertIn('test "$SOURCE_RESULT" = success',s)
        self.assertIn('test "$EMULATOR_RESULT" = success',s)
        self.assertNotIn('continue-on-error:',s)
        for path,n in ((HF22,1),(HF26,1),(FOCUSED,2),(THIS,1),(DOC,1)):
            expected=hashlib.sha256((ROOT/path).read_bytes()).hexdigest()
            lines=re.findall(r'(?m)^\s*echo "([0-9a-f]{64})  '+re.escape(path)+r'" \| sha256sum --check --status$',s)
            self.assertEqual(lines,[expected]*n,path)
        for path in (HF22,HF26,THIS):
            self.assertEqual(s.count('python3 -B '+path),1,path)

    def test_05_preserves_five_ui_journeys_and_original_room_sflp_security(self):
        s=self.get(FULL)
        for required in (
            'scripts/test_sfdt22_hf28_practice_contract.py',
            'scripts/test_sfdt22_hf29_subject_fab_contract.py',
            'scripts/test_sfdt22_hf30_session_navigation_contract.py',
            'scripts/test_sfdt27_fix03_hf03_contract.py',
            'api-level: 36',
            'script: bash scripts/sfdt22_emulator_ci.sh',
            'scripts/sfdt22_emulator_verify.py --verify '
        ):
            self.assertIn(required,s)

if __name__=='__main__':
    unittest.main(verbosity=2)
