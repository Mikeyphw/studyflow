#!/usr/bin/env python3
"""SFDT08: source-only migration adversarial checks (never invokes Gradle)."""
from __future__ import annotations
import json
from pathlib import Path
import shutil
import sys
import tempfile
import tomllib
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import sfdt08_contract as C
import sfdt08_native_coverage as N
import sfdt08_hosted_bridge as B
import sfdt08_compare_coverage as Compare

SOURCES=('.devtool.toml','gradle/wrapper/gradle-wrapper.jar',
         'scripts/sfdt08_hosted_bridge.py',
         'scripts/sfdt08_native_coverage.py',
         'scripts/sfdt08_contract.py',
         '.github/workflows/studyflow-ci.yml',
         '.github/workflows/studyflow-hosted-sign.yml',
         '.github/workflows/studyflow-devtool-native-coverage.yml')

class Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert C.audit(ROOT)['status']=='SOURCE_ONLY_PASS'
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='sfdt08-contract-')
        self.root=Path(self.tmp.name)
        for rel in SOURCES:
            dest=self.root/rel;dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/rel,dest)
    def tearDown(self):self.tmp.cleanup()
    def change(self,rel,old,new):
        file=self.root/rel
        txt=file.read_text()
        assert old in txt
        file.write_text(txt.replace(old,new,1))
    def test_positive_source_has_eight_lanes_and_no_cutover(self):
        row=C.audit(self.root)
        self.assertEqual(len(row['lanes']),8)
        self.assertFalse(row['cutover_authorized'])
        self.assertFalse(row['gradle_executed'])
    def test_script_tampering_refused(self):
        p=self.root/B.__file__.split('/scripts/')[-1] if False else self.root/'scripts/sfdt08_hosted_bridge.py'
        p.write_text(p.read_text()+'\nprint("tampered")\n')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_phone_release_assembly_refused(self):
        self.change('.devtool.toml',
            'gradle_tasks = [":app:testReleaseUnitTest"]',
            'gradle_tasks = [":app:assembleRelease"]')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_unreviewed_python_bridge_arg_refused(self):
        self.change('.devtool.toml',
                    '"scripts/sfdt08_hosted_bridge.py", "desktop-fast"',
                    '"scripts/sfdt08_hosted_bridge.py", "unreviewed"')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_junit_requirement_removal_refused(self):
        self.change('.devtool.toml',
                    'ci_junit_report_dir = "web/build/test-results/sfdt08-pytest"',
                    'ci_junit_report_dir = ""')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_old_ci_removal_refused(self):
        self.change('.github/workflows/studyflow-ci.yml','  web:\n','  web-renamed:\n')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_signer_boundary_removal_refused(self):
        self.change('.github/workflows/studyflow-hosted-sign.yml',
                    'environment: studyflow-signing','environment: public')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_secret_permission_injection_refused(self):
        wf=self.root/'.github/workflows/studyflow-devtool-native-coverage.yml'
        wf.write_text(wf.read_text()+'\nsecrets.SIGNING_KEY\n')
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_missing_fast_lane_refused(self):
        wf=self.root/'.github/workflows/studyflow-devtool-native-coverage.yml'
        wf.write_text(wf.read_text().replace('"lane":"web-full"','"lane":"web-skipped"'))
        with self.assertRaises(C.ContractError):C.audit(self.root)
    def test_bridge_local_gradle_refused(self):
        with self.assertRaisesRegex(ValueError,'GitHub Actions'):
            B.verify_host('desktop-fast')
    def test_native_identity_refuses_local(self):
        with self.assertRaises(ValueError):N.identity({},self.root)
    def test_fallback_must_not_claim_pass(self):
        p=self.root/'out.json'
        N.fallback('desktop-fast',p)
        data=json.loads(p.read_text())
        self.assertEqual(data['status'],'FAILED_OR_NOT_RUN')
        self.assertFalse(data['release_qualified'])
        with self.assertRaises(ValueError):N.fallback('desktop-fast',p)
    def test_junit_header_discrepancy_comparator_refused(self):
        raw=b'<testsuite tests="9"><testcase name="single"/></testsuite>'
        with self.assertRaises(Compare.ComparisonError):Compare.count_xml(raw)
    def test_comparator_rejects_empty_or_failed_tests(self):
        for xml in (b'<testsuite/>',b'<testsuite tests="1"><testcase name="a"><failure/></testcase></testsuite>'):
            if b'<failure' in xml:
                total,failed,skipped=Compare.count_xml(xml)
                self.assertEqual((total,failed,skipped),(1,1,0))
            else:
                with self.assertRaises(Compare.ComparisonError):Compare.count_xml(xml)

if __name__=='__main__':unittest.main(verbosity=2)
