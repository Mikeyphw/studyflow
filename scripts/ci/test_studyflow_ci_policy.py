#!/usr/bin/env python3
"""Adversarial exact-range and classification tests; synthetic repos only."""
from __future__ import annotations
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import studyflow_ci_policy as p

class Classification(unittest.TestCase):
    def test_docs_only_is_source_only(self):
        r=p.classify(['docs/guide.md']);self.assertFalse(any(r[k] for k in ('needs_android','needs_web','needs_desktop','needs_tv')))
        self.assertEqual(r['groups'],['baseline']);self.assertIn(p.SELF_TEST,r['tests'])
    def test_database_compiles_android_and_preserves_settings(self):
        r=p.classify(['core/database/src/main/java/StudyPlanDao.kt'])
        self.assertTrue(r['needs_android']);self.assertIn('database',r['groups'])
        self.assertIn('scripts/test_sfdt22_hf27_contract.py',r['tests'])
    def test_android_test_file_is_hosted_compile(self):
        r=p.classify(['app/src/androidTest/java/Sfdt27RoomSettingsRegressionTest.kt'])
        self.assertTrue(r['needs_android']);self.assertIn('hosted',r['groups'])
    def test_workflow_changes_require_all_source_tests(self):
        r=p.classify(['.github/workflows/studyflow-targeted.yml'])
        self.assertTrue(r['ci_sensitive']);self.assertTrue(r['full_source'])
        self.assertIn('scripts/test_sfdt22_hf02_contract.py',r['tests'])
        self.assertNotIn('scripts/test_sfdt22_hf14_contract.py',r['tests'])
    def test_all_source_contracts_match_reviewed_historical_filenames(self):
        # First targeted push includes CI files, therefore this is the hot path.
        selected=p.classify(['.github/workflows/studyflow-targeted.yml'])['tests']
        expected={f'scripts/test_sfdt22_hf{n:02d}_contract.py'
                  for n in (1, *range(2, 14), *range(15, 26), 27)}
        expected.update({'scripts/test_sfdt22_hf26_fast_contract.py',
                         'scripts/test_sfdt22_emulator_contract.py'})
        self.assertEqual(set(p.CORE_SOURCE), expected)
        self.assertTrue(expected.issubset(selected))
        self.assertNotIn('scripts/test_sfdt22_hf26_contract.py', selected)
        self.assertEqual(len(p.CORE_SOURCE), len(expected))
    def test_app_build_gradle_requires_all_source_and_android_only(self):
        r=p.classify(['app/build.gradle.kts'])
        self.assertTrue(r['ci_sensitive'])
        self.assertTrue(r['full_source'])
        self.assertTrue(r['needs_android'])
        self.assertFalse(r['needs_tv'])
        self.assertFalse(r['needs_desktop'])
    def test_web_only(self):
        r=p.classify(['web/src/index.ts']);self.assertTrue(r['needs_web']);self.assertFalse(r['needs_android'])
    def test_desktop_only(self):
        r=p.classify(['desktopApp/src/main/kotlin/X.kt']);self.assertTrue(r['needs_desktop']);self.assertFalse(r['needs_android'])
    def test_tv_only(self):
        r=p.classify(['app-tv/src/main/java/X.kt']);self.assertTrue(r['needs_tv']);self.assertFalse(r['needs_android'])
    def test_shared_gradle_is_multi_target_and_sensitive(self):
        r=p.classify(['settings.gradle.kts']);self.assertTrue(r['needs_android']);self.assertTrue(r['needs_tv']);self.assertTrue(r['needs_desktop']);self.assertTrue(r['ci_sensitive'])
    def test_unknown_code_fails_closed(self):
        r=p.classify(['newcode/run.py']);self.assertTrue(r['needs_android']);self.assertTrue(r['full_source'])
    def test_bad_paths_and_empty_changes_refused(self):
        for paths in ([],['../outside.py'],['/private/file'],['a\\b']):
            with self.assertRaises(ValueError):p.classify(paths)
    def test_mandatory_tests_are_never_skipped_when_deleted(self):
        r=p.classify(['scripts/test_sfdt22_hf27_contract.py']);self.assertIn('scripts/test_sfdt22_hf27_contract.py',r['tests'])
    def test_source_file_creation_never_qualifies_release(self):
        s=(Path(__file__).parent/'studyflow_ci_policy.py').read_text()
        self.assertNotIn('git push',s)
        self.assertNotIn('gh pr merge',s)

class ExactGit(unittest.TestCase):
    def test_real_diff_both_sha_and_deletion(self):
        with tempfile.TemporaryDirectory(prefix='targeted-ci-test-') as d:
            root=Path(d)
            def run(*args):return subprocess.check_output(['git',*args],cwd=root,text=True).strip()
            run('init','-q','-b','main');run('config','user.name','CI Test');run('config','user.email','ci@example.invalid')
            (root/'deleted.md').write_text('x');run('add','.');run('commit','-q','-m','baseline');base=run('rev-parse','HEAD')
            (root/'deleted.md').unlink();(root/'feature').mkdir();(root/'feature'/'Screen.kt').write_text('test');run('add','-A');run('commit','-q','-m','next');head=run('rev-parse','HEAD')
            self.assertEqual(p.diff(base,head,cwd=root),['deleted.md','feature/Screen.kt'])
            for a,b in [('x',head),(base,'x'),(base,base)]:
                with self.assertRaises(ValueError):p.diff(a,b,cwd=root)

if __name__=='__main__':unittest.main(verbosity=2)
