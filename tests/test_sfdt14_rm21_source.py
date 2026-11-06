"""SFDT14 nonrelease, exact-SHA independent RM21 JVM source and inventory regressions."""
from __future__ import annotations
import os
from pathlib import Path
import sys
import tempfile
import unittest
from zipfile import ZipFile,ZipInfo

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt13_jvm_shadow as shadow
import sfdt14_jar_inventory as inventory

class SourcePolicy(unittest.TestCase):
    def test_authority_and_exact_source(self):
        s=shadow.source_check()
        self.assertEqual(s['devtool_sha'],shadow.DEVTOOL)
        self.assertFalse(s['rm21_qualified'])
        self.assertFalse(s['release_qualified'])
        workflow=(ROOT/'.github/workflows/studyflow-sfdt14-rm21-jvm-acceptance.yml').read_text()
        self.assertIn('DEVTOOL_SHA: '+shadow.DEVTOOL,workflow)
        self.assertIn('--jvm-module \':core:model\'',workflow)
        self.assertIn('--gradle-version \'9.7.1\'',workflow)
        self.assertIn('gradle/verification-metadata.xml',workflow)
        self.assertIn('independent_consumer_qualified',workflow)
        self.assertIn("check['classes']>0",workflow)
        self.assertIn('permissions:\n  contents: read',workflow)
        for unsafe in ('secrets.','id-token: write','contents: write','continue-on-error:',
                       'gh release','git push ','gh pr merge','--force'):
            self.assertNotIn(unsafe,workflow)

    def test_bounded_jar_inventory(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);folder=root/'libs';folder.mkdir()
            jar=folder/'model.jar'
            with ZipFile(jar,'w') as z:
                z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\n')
                z.writestr('pkg/Proof.class',b'not-trusted-as-valid-bytecode')
            report=inventory.inspect(folder)
            self.assertEqual(report['entries'],2)
            self.assertEqual(report['classes'],1)
            self.assertEqual(report['issues'],[])
            self.assertTrue(report['not_rm12_acceptance'])
            with ZipFile(jar,'a') as z:z.writestr('../escape',b'bad')
            self.assertIn('unsafe-entry-name',inventory.inspect(folder)['issues'])

    def test_symlink_and_duplicates_observed_not_promoted(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);folder=root/'libs';folder.mkdir()
            jar=folder/'model.jar'
            with ZipFile(jar,'w') as z:
                z.writestr('META-INF/MANIFEST.MF',b'Manifest-Version: 1.0\n')
                z.writestr('a/Foo.class',b'raw')
                z.writestr('A/foo.class',b'duplicate')
                e=ZipInfo('symlink.class');e.create_system=3;e.external_attr=0o120777<<16
                z.writestr(e,b'a/Foo.class')
            report=inventory.inspect(folder)
            self.assertIn('duplicate-case-insensitive',report['issues'])
            self.assertIn('symlink-entry',report['issues'])
            self.assertFalse(report['release_qualified'])

if __name__=='__main__':unittest.main(verbosity=2)
