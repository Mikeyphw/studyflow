#!/usr/bin/env python3
"""SFDT12-R3: committed web distribution must reproduce hosted RM21 bytes.

Static, read-only. Hosted native RM21 still owns real external execution and trust.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'web/dist'
EXPECTED = {
    'release-manifest.json': '129f483df9c5a802a68da01f7b048e9490163cf6aeb48f440fbb7563a038b80e',
    'runtime/semantic_golden.js': '949d7c73eaa71d4bb908e213b9f16b01276242b1a58e6b99c1a42252b7c2fdbc',
    'service-worker.js': '8205248cafc2c2e38ea6e01c2d5cf8267983b6f2015d0ba5283d18c8d80a4bd9',
}

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

class SFDT12R3OutputParity(unittest.TestCase):
    def test_exact_hosted_generated_bytes(self):
        for rel, digest in EXPECTED.items():
            with self.subTest(path=rel):
                p = DIST / rel
                self.assertTrue(p.is_file() and not p.is_symlink())
                self.assertEqual(sha(p), digest)

    def test_release_manifest_checks_every_member(self):
        doc = json.loads((DIST / 'release-manifest.json').read_text())
        self.assertEqual(doc['product'], 'StudyFlow Local')
        self.assertEqual(doc['version'], '1.0.0')
        self.assertEqual(doc['channel'], 'parity-closure')
        self.assertEqual(doc['runtimeDependency'], 'self-contained')
        self.assertGreater(len(doc['files']), 20)
        named = set()
        for item in doc['files']:
            rel = item['path']
            self.assertNotIn(rel, named)
            named.add(rel)
            self.assertNotIn('..', Path(rel).parts)
            self.assertFalse(Path(rel).is_absolute())
            file = DIST / rel
            self.assertTrue(file.is_file() and not file.is_symlink(), rel)
            self.assertEqual(file.stat().st_size, item['bytes'], rel)
            self.assertEqual(sha(file), item['sha256'], rel)
        self.assertIn('runtime/semantic_golden.js', named)
        self.assertNotIn('manifest.json', named)

    def test_service_worker_uses_addressed_release_cache(self):
        doc = json.loads((DIST / 'release-manifest.json').read_text())
        version = doc['cacheVersion']
        self.assertRegex(version, r'^studyflow-local-1\.0\.0-[a-f0-9]{16}$')
        sw = (DIST / 'service-worker.js').read_text()
        self.assertIn(f'const CACHE_VERSION = "{version}";', sw)
        self.assertNotIn('__STUDYFLOW_CACHE_VERSION__', sw)

    def test_rm21_manifest_is_additive_not_release_substitution(self):
        builder = (ROOT / 'web/scripts/build_dist.mjs').read_text()
        self.assertIn('const rm21OwnedFiles=Object.fromEntries([', builder)
        self.assertIn('writeFileSync(join(dist,"release-manifest.json")', builder)
        self.assertIn('writeFileSync(join(dist,"manifest.json")', builder)
        self.assertLess(builder.index('writeFileSync(join(dist,"release-manifest.json")'),
                        builder.index('writeFileSync(join(dist,"manifest.json")'))

if __name__ == '__main__':
    unittest.main(verbosity=2)
