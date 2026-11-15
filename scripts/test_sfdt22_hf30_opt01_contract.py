#!/usr/bin/env python3
"""OPT01 branch de-duplication with adversarial branch and qualification tests.

This is an offline source contract only. It never qualifies Android or release.
"""
from __future__ import annotations
from fnmatch import fnmatchcase
from pathlib import Path
import hashlib
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
TARGET='.github/workflows/studyflow-targeted.yml'
STAGE='.github/workflows/studyflow-sfdt26-hotfix-push.yml'
FULL='.github/workflows/studyflow-sfdt22-hosted-journeys.yml'
FOCUS='.github/workflows/studyflow-sfdt22-hotfix-session.yml'
FAST='scripts/test_sfdt22_hf26_fast_contract.py'
DOC='docs/actions/STUDYFLOW_OPT01_WORKFLOW_TRIGGER_DEDUP.md'
THIS='scripts/test_sfdt22_hf30_opt01_contract.py'
BRANCH_PATTERN='sfdt22-hotfix/*-staging-*'

def read(p): return (ROOT/p).read_text(encoding='utf-8')

def push_branches(source):
    match=re.search(r'(?m)^  push:\n    branches:\n((?:      - [^\n]+\n)+)',source)
    if match is None: raise AssertionError('Expected exactly one explicit push-branches list')
    branches=[]
    for line in match.group(1).splitlines():
        val=line.partition('- ')[2].strip().strip("\"'")
        branches.append(val)
    return branches

class Opt01DedupContracts(unittest.TestCase):
    def test_01_exact_trigger_routing(self):
        stage=read(STAGE)
        targeted=read(TARGET)
        self.assertEqual(push_branches(stage),[BRANCH_PATTERN])
        self.assertEqual(push_branches(targeted),['main',BRANCH_PATTERN])
        self.assertIn('on:\n  pull_request:\n  push:',targeted)
        self.assertIn('  workflow_dispatch:',targeted)
        self.assertNotIn('  pull_request:',stage)
        self.assertNotIn('  workflow_dispatch:',stage)
        self.assertNotIn('pull_request_target:',stage+targeted)
        for feature in ('sfdt22-hotfix/hf30-hf04-pr-20261110',
                        'sfdt22-hotfix/hf30-feature-20261112',
                        'sfdt22-hotfix/debug-probe', 'topic/other'):
            self.assertFalse(any(fnmatchcase(feature,p) for p in push_branches(stage)),feature)
            self.assertFalse(any(fnmatchcase(feature,p) for p in push_branches(targeted)),feature)
        for staging in ('sfdt22-hotfix/hf25-staging-20261109',
                        'sfdt22-hotfix/hf40-staging-20261112'):
            self.assertTrue(all(any(fnmatchcase(staging,p) for p in push_branches(s))
                                for s in (stage,targeted)),staging)
        self.assertTrue(fnmatchcase('main','main'))
        self.assertFalse(any(fnmatchcase('main',p) for p in push_branches(stage)))

    def test_02_full_pr_and_separate_manual_diagnosis_still_authoritative(self):
        full,focused,stage=read(FULL),read(FOCUS),read(STAGE)
        for word in ('on:\n  pull_request:', 'push:\n    branches: [main]',
                     'needs: [source, emulator]', 'test "$EMULATOR_RESULT" = success',
                     'test "$SOURCE_RESULT" = success', 'api-level: 36',
                     'seven unique screenshots', 'Independently verify five test receipts',
                     'scripts/sfdt22_emulator_verify.py --verify '):
            self.assertIn(word,full,word)
        self.assertEqual(focused.count('--diagnostic-through practice'),2)
        self.assertIn('workflow_dispatch:',focused)
        self.assertIn('types: [labeled]',focused)
        self.assertIn('diagnostic_only":true',focused)
        self.assertIn('release_qualified":false',focused)
        self.assertIn('publication_allowed":false',focused)
        self.assertIn('expected_sha: ${{ github.sha }}',stage)
        self.assertIn('contents: read',stage)
        for name,source in (('full',full),('focused',focused),('stage',stage)):
            self.assertNotIn('continue-on-error:',source,name)
            self.assertNotIn('pull_request_target:',source,name)
        self.assertNotIn('secrets: inherit',stage)

    def test_03_legacy_source_contract_migrated_not_silenced(self):
        fast=read(FAST)
        self.assertIn('test_01_staging_is_owner_scoped_and_not_a_pr_gate',fast)
        self.assertIn('test_07_policy_resists_mutations',fast)
        self.assertIn('test_03_full_pr_gate_unchanged_in_authority',fast)
        self.assertIn("'sfdt22-hotfix/*-staging-*'",fast)
        self.assertIn('assertNotIn',fast)
        self.assertNotIn("s.replace("'sfdt22-hotfix/**'"",fast)
        self.assertIn("self.assertEqual(f.count('--diagnostic-through practice'), 2)",fast)

    def test_04_full_sha_pins_reflect_all_new_and_modified_source(self):
        full=read(FULL)
        for name in (TARGET,STAGE,FAST,THIS,DOC,
                     'scripts/sfdt22_hf30_hf07_diagnostics.py',
                     'scripts/test_sfdt22_hf30_hf07_diagnostics_contract.py'):
            pin=hashlib.sha256((ROOT/name).read_bytes()).hexdigest()+'  '+name
            self.assertIn(pin,full,name)
        self.assertIn('python3 -B '+THIS,full)
        self.assertIn('python3 -B scripts/test_sfdt22_hf26_fast_contract.py',full)

    def test_05_github_required_gate_identity_unmodified(self):
        target=read(TARGET)
        for name in ('Targeted required-result gate', 'needs: [scope, source, android, tv, desktop, web]',
                     "needs.scope.outputs.needs_android",'STUDYFLOW_TARGETED_PASS_NONQUALIFYING'):
            self.assertIn(name,target)
        self.assertNotIn('continue-on-error:',target)
        self.assertNotIn('pull_request_target:',target)

    def test_06_documented_nonqualifying_behavior(self):
        doc=read(DOC)
        self.assertIn('PR #16',doc)
        self.assertIn('SFDT22',doc)
        self.assertIn('SFDT21',doc)
        self.assertIn('not release qualification',doc)
        self.assertIn('HF07',doc)

if __name__=='__main__':unittest.main(verbosity=2)
