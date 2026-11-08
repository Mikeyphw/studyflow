#!/usr/bin/env python3
"""SFDT22 HF01: negative-first offline regressions for CI failure repair."""
from __future__ import annotations

import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
source = ROOT/'scripts/sfdt22_hf01_phone_gradle.py'
spec = importlib.util.spec_from_file_location('sfdt22_hf01_phone_gradle',source)
assert spec and spec.loader
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class Contract(unittest.TestCase):
    sha = 'a'*40
    def setup_env(self, base: Path):
        root = base/'repo';root.mkdir(); (root/'.git').mkdir()
        home=base/'user-home';home.mkdir()
        env={'GITHUB_ACTIONS':'true','CI':'true','RUNNER_OS':'Linux',
             'RUNNER_ARCH':'X64','GITHUB_REPOSITORY':m.REPO,
             'GITHUB_EVENT_NAME':'pull_request','STUDYFLOW_LANE':'phone',
             'GITHUB_SHA':self.sha,'GRADLE_USER_HOME':str(home)}
        return root,home,env
    def test_01_real_compose_collection_extensions_imported(self):
        src=(ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt').read_text()
        for func in ('onFirst','onLast'):
            self.assertIn('import androidx.compose.ui.test.'+func,src)
            self.assertIn('.'+func+'()',src)
        self.assertEqual(src.count('@Test fun '),5)
    def test_02_hosted_source_sha_follows_actual_kotlin(self):
        f=ROOT/'app/src/androidTest/java/com/mikeyphw/studyflow/Sfdt22HostedStudyJourneyTest.kt'
        wf=(ROOT/'.github/workflows/studyflow-sfdt22-hosted-journeys.yml').read_text()
        self.assertIn(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+str(f.relative_to(ROOT)),wf)
        self.assertIn('python3 -B scripts/test_sfdt22_hf01_contract.py',wf)
        self.assertNotIn('continue-on-error:',wf)
    def test_03_native_devtool_contract_and_guard_unchanged(self):
        wf=(ROOT/'.github/workflows/studyflow-devtool-primary.yml').read_text()
        native=(ROOT/'scripts/studyflow_devtool_primary.py').read_text()
        self.assertIn('scripts/sfdt22_hf01_phone_gradle.py --apply',wf)
        self.assertLess(wf.index('scripts/sfdt22_hf01_phone_gradle.py --apply'),
                        wf.index('name: Execute Devtool-owned CI DAG'))
        self.assertIn("if: ${{ matrix.lane == 'phone' }}",wf)
        self.assertIn('--guarded',native)
        self.assertIn('quiet_limit=10*60, wall_limit=45*60',native)
        self.assertIn("(':app:assembleDebug', ':app:lintDebug')",native)
        self.assertIn("(':app:testReleaseUnitTest',)",native)
        self.assertIn('SFDT20 per-lane source and artifact provenance',wf)
        self.assertNotIn('continue-on-error:',wf)
        self.assertNotIn('secrets.',wf)
    def test_04_hosted_home_applies_no_task_or_release_changes(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            self.assertEqual(m.configure(env,root,self.sha)['status'],
                             'SFDT22_HF01_HOSTED_PHONE_GRADLE_PASS')
            actual=(home/'gradle.properties').read_text()
            for key,value in m.VALUES.items():self.assertIn(key+'='+value,actual)
            self.assertFalse((root/'gradle.properties').exists())
            m.configure(env,root,self.sha)
            self.assertEqual((home/'gradle.properties').read_text(),actual)
    def test_05_environment_gate_is_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            for key,bad in (('STUDYFLOW_LANE','tv'),('RUNNER_OS','Android'),
                            ('CI','false'),('GITHUB_REPOSITORY','other/repo'),
                            ('GITHUB_EVENT_NAME','bogus'),('GITHUB_SHA','b'*40),
                            ('STUDYFLOW_RELEASE_STORE_PASSWORD','exposed')):
                with self.subTest(key=key),self.assertRaises(m.Refused):
                    m.configure({**env,key:bad},root,self.sha)
            self.assertFalse((home/'gradle.properties').exists())
    def test_06_conflicting_property_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            (home/'gradle.properties').write_text('org.gradle.parallel=true\n')
            with self.assertRaisesRegex(m.Refused,'Conflicting'):
                m.configure(env,root,self.sha)
            self.assertEqual((home/'gradle.properties').read_text(), 'org.gradle.parallel=true\n')
    def test_07_existing_valid_properties_preserved(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            (home/'gradle.properties').write_text('org.gradle.jvmargs=-Xmx2048m\n')
            m.configure(env,root,self.sha)
            self.assertTrue((home/'gradle.properties').read_text().startswith('org.gradle.jvmargs=-Xmx2048m\n'))
    def test_08_symlink_forbidden(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            other=Path(td)/'outside';other.write_text('outside')
            (home/'gradle.properties').symlink_to(other)
            with self.assertRaisesRegex(m.Refused,'Symlinked'):
                m.configure(env,root,self.sha)
            self.assertEqual(other.read_text(),'outside')
    def test_09_repo_destination_forbidden(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            env['GRADLE_USER_HOME']=str(root)
            with self.assertRaises(m.Refused):m.configure(env,root,self.sha)
    def test_10_missing_repo_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root,home,env=self.setup_env(Path(td))
            (root/'.git').rmdir()
            with self.assertRaises(m.Refused):m.configure(env,root,self.sha)

if __name__=='__main__':
    unittest.main(verbosity=2)
