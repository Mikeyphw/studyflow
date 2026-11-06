#!/usr/bin/env python3
"""SFDT12-R2: prior-source immutability is retained across an explicit npm pin."""
from __future__ import annotations
import hashlib,json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import sfdt11_contract as policy
ROOT=Path(__file__).resolve().parents[1]

class SFDT12R2Source(unittest.TestCase):
    def test_only_reviewed_npm_manager_pin_was_amended(self):
        self.assertEqual(policy.FROZEN_BLOBS['web/package.json'],
                         'e05e557473c267a52dd671a074b815b30512349f')
        policy.verify_web_manager_amendment((ROOT/'web/package.json').read_bytes())
        data=(ROOT/'web/package.json').read_bytes()
        old=data.replace(b'  "packageManager": "npm@10.9.4",\n',b'',1)
        self.assertEqual(hashlib.sha1(b'blob '+str(len(old)).encode()+b'\0'+old).hexdigest(),
                         '6ec3e0f9ff815d3649f48f3eb090d9fb97012718')
    def test_amendment_negative_mutations_fail_closed(self):
        src=(ROOT/'web/package.json').read_bytes()
        for bad in (src.replace(b'npm@10.9.4',b'npm@10.9.5'),
                    src.replace(b'"private": true',b'"private": false'),
                    src.replace(b'  "packageManager": "npm@10.9.4",\n',b'')):
            with self.subTest(bad=bad[:70]),self.assertRaises(policy.PolicyError):
                policy.verify_web_manager_amendment(bad)
    def test_existing_authorities_remain_separate(self):
        wf=(ROOT/'.github/workflows/studyflow-sfdt12-rm21-node-acceptance.yml').read_text()
        self.assertIn('DEVTOOL_SHA: bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c',wf)
        self.assertIn('RM21_TRACKED_DRIFT',wf)
        self.assertIn("'diff','--name-only'",wf)
        self.assertIn('if: failure()',wf)
        self.assertIn('Genuine external native npm build and RM11 byte receipt',wf)
        self.assertIn('name: StudyFlow CI', (ROOT/'.github/workflows/studyflow-ci.yml').read_text())
        self.assertIn('STUDYFLOW_GITHUB_ONLY_GRADLE',(ROOT/'settings.gradle.kts').read_text())

if __name__=='__main__':
    unittest.main(verbosity=2)
