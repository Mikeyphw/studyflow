#!/usr/bin/env python3
"""Regression for OV01 Web root and SFDT03 bounded cross-root sources."""
from __future__ import annotations
import sys
import unittest
import tomllib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from test_studyflow_ov01_r4_contracts import web_target_path_safe, WEB_SHARED_SOURCE_INPUTS

ROOT=Path(__file__).resolve().parents[1]

class WebPolicyTests(unittest.TestCase):
    def test_reviewed_shared_inputs_accepted(self):
        for name in WEB_SHARED_SOURCE_INPUTS:
            self.assertTrue(web_target_path_safe(name,field='inputs'), name)
            self.assertFalse(web_target_path_safe(name,field='outputs'), name)

    def test_local_sources_preserved(self):
        for name in ('src/**','tests/**','scripts/**','package.json','package-lock.json',
                     'requirements-test.txt','tsconfig.json','sw.js','dist/**'):
            self.assertTrue(web_target_path_safe(name,field='inputs'),name)

    def test_traversal_and_doubly_rooted_refused(self):
        bad=('../../protocol/**','../gradle/**','../**','../.git/config',
             '../.github/workflows/*', '../protocol/../secret', 'web/src/**',
             '/etc/passwd', './src/**','src/../dist','scripts//test.py',
             'scripts\\run.sh','', 'foo/', 'foo/./bar')
        for name in bad:
            for field in ('inputs','outputs'):
                self.assertFalse(web_target_path_safe(name,field=field),(name,field))

    def test_current_web_config_and_sfdt03_ownership(self):
        cfg=tomllib.loads((ROOT/'.devtool.toml').read_text())
        web=cfg['targets']['studyflow_web']
        self.assertEqual(web['root'],'web')
        self.assertEqual(web.get('affected_ownership'),'inputs-only')
        self.assertTrue(WEB_SHARED_SOURCE_INPUTS.issubset(set(web['inputs'])))
        for field in ('inputs','outputs'):
            self.assertTrue(all(web_target_path_safe(v,field=field) for v in web[field]))

if __name__=='__main__':
    unittest.main(verbosity=2)
