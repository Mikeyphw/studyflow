#!/usr/bin/env python3
"""SFDT20 fail-closed source and evidence tests: no network or Gradle."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import sfdt20_provenance as p
import sfdt20_main_audit as a


class ProvenanceTests(unittest.TestCase):
    SHA = 'a'*40

    def fixture(self, repo: Path, lane: str):
        env = {'GITHUB_ACTIONS': 'true', 'CI': 'true', 'RUNNER_OS': 'Linux',
               'RUNNER_ARCH': 'X64', 'GITHUB_REPOSITORY': p.REPO,
               'GITHUB_SHA': self.SHA, 'GITHUB_RUN_ID': '12345',
               'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_EVENT_NAME': 'pull_request',
               'DEVTOOL_SOURCE_SHA': p.DEVTOOL_SHA}
        conf = p.LANES[lane]
        xml = repo / conf['junit'] / ('web-pytest-junit.xml' if lane=='web' else 'TEST-case.xml')
        xml.parent.mkdir(parents=True)
        xml.write_text('<testsuite><testcase name="actual"/></testsuite>')
        receipt={'schema':'studyflow.devtool.primary.v1','lane':lane,
                 'status':'NATIVE_CI_LANE_PASSED','ci_lane_passed':True,
                 'cutover_authorized':False,'signing_qualified':False,
                 'release_qualified':False,'device_qualified':False,
                 'identity': {'studyflow_sha':self.SHA, 'devtool_sha':p.DEVTOOL_SHA,
                              'event':'pull_request','run_id':12345,'run_attempt':1},
                 'nodes': []}
        for job, provider in conf['jobs']:
            row={'job':job,'provider':provider,'graph_sha256':'b'*64,
                 'native_receipt_sha256':'c'*64}
            if job != 'sfdt07-phone-debug':
                row['junit']={'executed':1,'failed':0,
                              'proof':'local-XML' if lane=='tv' else 'devtool-local-junit'}
            if (lane=='tv' or job=='sfdt07-phone-debug'):
                path=repo / conf['apk']
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_bytes(b'x'*1800)
                row['apk']=p.evidence_file(repo,conf['apk'],low=1024)
            if provider in ('jvm','node'):
                path=repo / conf['output']
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_bytes(b'<testsuite><testcase name="x"/></testsuite>')
                row['native_output']=p.evidence_file(repo,conf['output'])
            receipt['nodes'].append(row)
        if 'lint' in conf:
            lint=repo/conf['lint']/'lint-results-debug.html'
            lint.parent.mkdir(parents=True,exist_ok=True)
            lint.write_text('<html>actual lint report</html>')
        return receipt,env

    def test_every_lane_generates_attested_artifacts(self):
        for lane in p.LANES:
            with self.subTest(lane=lane), tempfile.TemporaryDirectory() as td:
                root=Path(td)
                receipt,env=self.fixture(root,lane)
                record=p.manifest(root,receipt,lane,env)
                self.assertEqual(record['identity']['source_sha'],self.SHA)
                self.assertEqual(record['lane'],lane)
                self.assertTrue(record['qualified'])
                self.assertEqual(record['junit']['summary']['executed'],1)
                self.assertEqual(len(record['nodes']),len(p.LANES[lane]['jobs']))
                for item in record['junit']['files']:
                    self.assertEqual(len(item['sha256']),64)
                self.assertFalse(record['release_qualified'])

    def test_mutated_apk_or_native_output_is_refused(self):
        for lane in p.LANES:
            with self.subTest(lane=lane), tempfile.TemporaryDirectory() as td:
                root=Path(td)
                receipt,env=self.fixture(root,lane)
                path=p.LANES[lane].get('apk') or p.LANES[lane]['output']
                (root/path).write_bytes(b'y'*2000)
                with self.assertRaisesRegex(p.Refused,'mismatch'):
                    p.manifest(root,receipt,lane,env)

    def test_wrong_source_runtime_run_and_failed_receipts_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            receipt,env=self.fixture(root,'web')
            for key,change in [('GITHUB_SHA','d'*40),('GITHUB_RUN_ID','12346'),
                               ('DEVTOOL_SOURCE_SHA','f'*40),('GITHUB_REPOSITORY','someone/else')]:
                with self.subTest(key=key), self.assertRaises(p.Refused):
                    p.manifest(root,receipt,'web',{**env,key:change})
            for key,val in [('status','FAILED_INCOMPLETE'),('ci_lane_passed',False),
                            ('release_qualified',True),('signing_qualified',True)]:
                with self.subTest(key=key), self.assertRaises(p.Refused):
                    p.manifest(root,{**receipt,key:val},'web',env)

    def test_missing_or_failing_junit_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            receipt,env=self.fixture(root,'desktop')
            report=root/p.LANES['desktop']['junit']/'TEST-case.xml'
            report.write_text('<testsuite><testcase><failure/></testcase></testsuite>')
            with self.assertRaisesRegex(p.Refused,'Failed or empty JUnit'):
                p.manifest(root,receipt,'desktop',env)
            report.unlink()
            with self.assertRaisesRegex(p.Refused,'Missing or unbounded JUnit'):
                p.manifest(root,receipt,'desktop',env)

    def test_wrong_junit_count_or_node_type_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            receipt,env=self.fixture(root,'web')
            receipt['nodes'][0]['junit']['executed']=2
            with self.assertRaisesRegex(p.Refused,'counts differ'):
                p.manifest(root,receipt,'web',env)
            receipt['nodes'][0]['junit']['executed']=1
            receipt['nodes'][0]['provider']='python-shell'
            with self.assertRaisesRegex(p.Refused,'job or source-bound'):
                p.manifest(root,receipt,'web',env)

    def test_symlinked_evidence_is_refused(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            receipt,env=self.fixture(root,'desktop')
            report=root/p.LANES['desktop']['junit']/'TEST-case.xml'
            actual=root/'outside.xml';actual.write_bytes(report.read_bytes())
            report.unlink();report.symlink_to(actual)
            with self.assertRaisesRegex(p.Refused,'symlinked evidence'):
                p.manifest(root,receipt,'desktop',env)

    def test_no_overwrite_and_output_runner_location(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);temp=root/'tmp';temp.mkdir()
            receipt,env=self.fixture(root,'web')
            obj=p.manifest(root,receipt,'web',env)
            out=temp/'provenance.json'
            with out.open('x') as f:json.dump(obj,f)
            with self.assertRaises(FileExistsError):
                with out.open('x') as f:json.dump(obj,f)

    def test_workflow_wires_provenance_before_upload(self):
        root=Path(__file__).resolve().parents[1]
        wf=root/'.github/workflows/studyflow-devtool-primary.yml'
        if not wf.exists():self.skipTest('Bundle payload has no workflow; check in repository')
        text=wf.read_text()
        digest=hashlib.sha256((root/'scripts/sfdt20_provenance.py').read_bytes()).hexdigest()
        self.assertIn(digest+'  scripts/sfdt20_provenance.py',text)
        self.assertIn('python3 -B scripts/test_sfdt20_provenance.py',text)
        self.assertTrue(text.index('Require fresh hosted test and Android lint report bytes')<
                        text.index('Verify SFDT20 per-lane source and artifact provenance')<
                        text.index('Upload Devtool CI receipts and JVM diagnostics'))
        self.assertIn('studyflow-devtool-primary/sfdt20-provenance.json',text)
        self.assertIn('name: Devtool CI gate',text)
        self.assertNotIn('continue-on-error:',text)
        prewarm=(root/'scripts/sfdt19_desktop_prewarm.init.gradle').read_text()
        self.assertIn("':core:portability'",prewarm)
        self.assertIn("':core:model'",prewarm)
        self.assertIn('module.configurations.findByName',prewarm)
        self.assertIn('component instanceof ModuleComponentIdentifier',prewarm)
        self.assertIn('kotlin-serialization-compiler-plugin-embeddable-2.4.10',prewarm)
        self.assertIn('dependsOn approvedPaths.findAll',prewarm)


class MainAuditTests(unittest.TestCase):
    def audit_inputs(self):
        sha='a'*40
        runs={'workflow_runs':[{'id':77,'run_attempt':1,'name':a.CI_NAME,'event':'push',
                                 'head_sha':sha,'head_branch':'main','status':'completed',
                                 'conclusion':'success','html_url':'https://github.com/x/actions/runs/77'}]}
        secondary={}
        for n,(workflow,job) in enumerate(a.SECONDARY.items(),start=78):
            runs['workflow_runs'].append({'id':n,'run_attempt':1,'name':workflow,
                 'event':'push','head_sha':sha,'head_branch':'main',
                 'status':'completed','conclusion':'success'})
            secondary[workflow]={'jobs':[{'name':job,'status':'completed','conclusion':'success'}]}
        jobs={'jobs':[{'name':n,'status':'completed','conclusion':'success'} for n in a.REQUIRED_JOBS]}
        return sha,runs,jobs,secondary

    def test_passes_main_push_with_required_gate(self):
        sha,runs,jobs,secondary=self.audit_inputs()
        proof={'required_status_checks':{'contexts':list(a.REQUIRED_CONTEXTS)}}
        out=a.audit(runs,jobs,sha,proof,[],secondary_jobs=secondary)
        self.assertEqual(out['postmerge_main_ci'],'PASS')
        self.assertTrue(out['main_requires_devtool_ci_gate'])
        self.assertTrue(out['main_requires_sfdt11'])
        self.assertTrue(out['main_requires_legacy_source'])
        self.assertEqual(out['run_id'],77)
        rule=[{'type':'required_status_checks','parameters':{'required_status_checks':
               [{'context':c} for c in a.REQUIRED_CONTEXTS]}}]
        self.assertEqual(a.audit(runs,jobs,sha,None,rule,secondary_jobs=secondary)['protection_state'],'PASS')
        self.assertEqual(len(a.audit(runs,jobs,sha,None,None,secondary_jobs=secondary)['missing_required_contexts']),3)

    def test_refuses_failed_or_non_main_ci(self):
        sha,runs,jobs,secondary=self.audit_inputs()
        for key,value in [('event','pull_request'),('head_sha','b'*40),('conclusion','failure'),
                          ('head_branch','feature')]:
            modified={'workflow_runs':[{**runs['workflow_runs'][0],key:value},*runs['workflow_runs'][1:]]}
            with self.subTest(key=key), self.assertRaises(a.Refused):
                a.audit(modified,jobs,sha,None,None,secondary_jobs=secondary)
        with self.assertRaises(a.Refused):
            a.audit(runs,{'jobs':[{'name':'Devtool CI gate','conclusion':'success','status':'completed'}]},
                    sha,None,None,secondary_jobs=secondary)
        missing=dict(secondary); missing.pop(next(iter(secondary)))
        with self.assertRaises(a.Refused):
            a.audit(runs,jobs,sha,None,None,secondary_jobs=missing)


if __name__ == '__main__':
    unittest.main(verbosity=2)
