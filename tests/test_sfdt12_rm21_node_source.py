#!/usr/bin/env python3
"""SFDT12 source-only contract: no native/release qualification implied."""
from __future__ import annotations
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]

class SFDT12ConsumerSource(unittest.TestCase):
    def test_actual_source_is_pinned(self):
        pkg=json.loads((ROOT/'web/package.json').read_text())
        lock=json.loads((ROOT/'web/package-lock.json').read_text())
        self.assertEqual(pkg['name'],'studyflow-local-web')
        self.assertEqual(pkg['packageManager'],'npm@10.9.4')
        self.assertEqual(lock['lockfileVersion'],3)
        self.assertEqual(lock['packages']['']['name'],pkg['name'])
        self.assertEqual(pkg['version'],'1.0.0')
        self.assertFalse((ROOT/'web/.npmrc').exists())
    def test_manifest_is_additive_to_release(self):
        src=(ROOT/'web/scripts/build_dist.mjs').read_text()
        self.assertIn('const rm21OwnedFiles=Object.fromEntries([',src)
        self.assertIn('...manifestFiles.map(entry=>[entry.path,entry.sha256])',src)
        self.assertIn('["release-manifest.json",sha(join(dist,"release-manifest.json"))]',src)
        self.assertIn('writeFileSync(join(dist,"manifest.json"),JSON.stringify({files:rm21OwnedFiles},null,2)',src)
        self.assertIn('writeFileSync(join(dist,"release-manifest.json")',src)
        self.assertLess(src.index('writeFileSync(join(dist,"release-manifest.json")'),src.index('const rm21OwnedFiles='))
    def test_existing_authority_unchanged(self):
        ci=(ROOT/'.github/workflows/studyflow-ci.yml').read_text()
        self.assertIn('name: StudyFlow CI',ci)
        settings=(ROOT/'settings.gradle.kts').read_text()
        self.assertIn('STUDYFLOW_GITHUB_ONLY_GRADLE',settings)
        flow=(ROOT/'.github/workflows/studyflow-sfdt12-rm21-node-acceptance.yml').read_text()
        self.assertIn('DEVTOOL_SHA: bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c',flow)
        self.assertIn('--external-repository Mikeyphw/studyflow',flow)
        self.assertIn('--node-project web',flow)
        self.assertIn('permissions:\n  contents: read',flow)

if __name__=='__main__':
    runner=unittest.main(verbosity=2,exit=False)
    if not runner.result.wasSuccessful():
        raise SystemExit(1)
    print('SFDT12_RM21_NODE_SOURCE_PASS')
