#!/usr/bin/env python3
"""HF26 fail-closed staging contracts: exact source, read-only, no merge qualification."""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
STAGING='.github/workflows/studyflow-sfdt26-hotfix-push.yml'
FOCUSED='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
DOC='docs/actions/SFDT22_HF26_FAST_HOTFIX_LOOP.md'
THIS='scripts/test_sfdt22_hf26_fast_contract.py'

class FastLoop(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stage=(ROOT/STAGING).read_text()
        cls.focus=(ROOT/FOCUSED).read_text()
        cls.full=(ROOT/FULL).read_text()
        cls.legacy=(ROOT/'.github/workflows/studyflow-ci.yml').read_text()
    def test_01_staging_is_owner_scoped_and_not_a_pr_gate(self):
        s=self.stage
        self.assertIn("'sfdt22-hotfix/*-staging-*'",s)
        self.assertNotIn("'sfdt22-hotfix/**'",s)
        self.assertIn("github.repository == 'Mikeyphw/studyflow'",s)
        self.assertIn("startsWith(github.ref, 'refs/heads/sfdt22-hotfix/')",s)
        self.assertIn('uses: ./.github/workflows/studyflow-sfdt22-hotfix-session.yml',s)
        self.assertIn('expected_sha: ${{ github.sha }}',s)
        self.assertIn('contents: read',s)
        self.assertNotIn('pull_request:',s)
        self.assertNotIn('pull_request_target:',s)
        self.assertNotIn('workflow_dispatch:',s)
        self.assertNotIn('secrets: inherit',s)
        self.assertNotIn('contents: write',s)
        self.assertNotIn('id-token: write',s)
    def test_02_callee_is_reusable_but_direct_push_is_forbidden(self):
        f=self.focus
        self.assertIn('workflow_call:',f)
        self.assertIn('types: [labeled]',f)
        self.assertNotIn('pull_request_target:',f)
        self.assertNotIn('\n  push:',f)
        self.assertIn('expected_sha:',f)
        self.assertIn('test "$EXPECTED_SHA" = "$GITHUB_SHA"',f)
        self.assertEqual(f.count('if [ "$GITHUB_EVENT_NAME" = workflow_dispatch ] || [ "$GITHUB_EVENT_NAME" = push ]; then'),2)
        self.assertIn('case "$GITHUB_REF" in refs/heads/sfdt22-hotfix/*)',f)
        self.assertIn("github.event.label.name == 'sfdt22-hotfix-session'",f)
        self.assertIn('cache-read-only: true',f)
        self.assertEqual(f.count('--diagnostic-through practice'), 2)
        self.assertNotIn('--diagnostic-through session',f)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify-diagnostic --diagnostic-through practice',f)
        self.assertIn('--verify-diagnostic',f)
        self.assertIn('release_qualified":false',f)
        self.assertIn('publication_allowed":false',f)
        self.assertNotIn('continue-on-error:',f)
    def test_03_full_pr_gate_unchanged_in_authority(self):
        f=self.full
        for literal in ('pull_request:','push:','branches: [main]',
                        'needs: [source, emulator]',
                        'test "$SOURCE_RESULT" = success',
                        'test "$EMULATOR_RESULT" = success',
                        'api-level: 36','scripts/sfdt22_emulator_verify.py --verify ',
                        ':app:assembleDebugAndroidTest','seven unique screenshots',
                        'cache-read-only:', 'if: always()'):
            self.assertIn(literal,f)
        for bad in ('continue-on-error:', 'allow-failure', 'if: false'):
            self.assertNotIn(bad,f)
    def test_04_pinned_every_modified_nonqualifying_source(self):
        for p in (STAGING,FOCUSED,THIS,DOC):
            digest=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
            self.assertIn(f'{digest}  {p}',self.full,p)
        self.assertIn('python3 -B '+THIS,self.full)
    def test_05_legacy_ci_source_rollback_guard_unchanged(self):
        c=self.legacy
        self.assertIn('options: [all, android, desktop, web, deep]',c)
        for lane in ('web','desktop','android'):
            self.assertIn("    if: ${{ github.event_name == 'workflow_dispatch' && (inputs.lane == 'all' || inputs.lane == '"+lane+"') }}",c)
        self.assertIn("    if: ${{ github.event_name == 'workflow_dispatch' && inputs.lane == 'deep' }}",c)
        self.assertEqual(c.count('test "$EXPECTED_SHA" = "$GITHUB_SHA"'),1)
    def test_06_staging_dispatch_does_not_mark_four_journeys_as_release_pass(self):
        f=self.focus
        self.assertNotIn('PASS_HOSTED_REAL_STUDY_JOURNEYS',f)
        self.assertIn('NO full gate',f)
        self.assertIn('diagnostic_only":true',f)
        self.assertEqual(f.count('STUDYFLOW_RELEASE_STORE_PASSWORD'),1)
        self.assertIn('scripts/sfdt22_emulator_verify.py --run --diagnostic-through practice',f)
        self.assertIn('scripts/sfdt22_emulator_verify.py --verify-diagnostic --diagnostic-through practice',f)
    def test_07_policy_resists_mutations(self):
        s=self.stage
        for mutated in (s.replace("'sfdt22-hotfix/*-staging-*'","'**'"),
                        s.replace('expected_sha: ${{ github.sha }}','expected_sha: anything'),
                        s.replace("github.repository == 'Mikeyphw/studyflow'","true")):
            self.assertFalse(all(x in mutated for x in (
                "'sfdt22-hotfix/*-staging-*'","expected_sha: ${{ github.sha }}",
                "github.repository == 'Mikeyphw/studyflow'")))
    def test_08_archive_source_constraints(self):
        d=(ROOT/DOC).read_text()
        self.assertIn('GitHub-hosted',d)
        self.assertIn('$TMPDIR',d)
        self.assertIn('does not qualify',d)

if __name__=='__main__':
    unittest.main(verbosity=2)
