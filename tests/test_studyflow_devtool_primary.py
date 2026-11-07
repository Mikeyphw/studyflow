#!/usr/bin/env python3
"""Offline, negative-first tests for the one-workflow Devtool CI candidate."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('studyflow_devtool_primary', HERE/'scripts/studyflow_devtool_primary.py')
assert spec is not None and spec.loader is not None
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class ContractTests(unittest.TestCase):
    def test_lane_inventory_is_explicit(self):
        self.assertEqual(set(m.NODES), {'phone','tv','desktop','web'})
        self.assertEqual(len(m.NODES['phone']), 2)
        self.assertEqual(len(m.NODES['tv']), 1)
        self.assertEqual(m.NODES['phone'][0][4], (':app:assembleDebug', ':app:lintDebug'))
        self.assertEqual(m.NODES['phone'][1][4], (':app:testReleaseUnitTest',))
        self.assertEqual(m.NODES['tv'][0][4],
                         (':app-tv:assembleDebug',':app-tv:testDebugUnitTest',':app-tv:lintDebug'))

    def test_desktop_web_native_providers_are_first_class(self):
        self.assertEqual(m.NODES['web'][0][3], 'node')
        self.assertEqual(m.NODES['desktop'][0][3], 'jvm')
        self.assertEqual(m.NODES['web'][0][0], 'studyflow_android')
        self.assertEqual(m.owned_outputs(m.NODES['web'][0]), ['web/dist/test-results.xml'])
        self.assertEqual(m.owned_outputs(m.NODES['desktop'][0]), ['dist/jvm-tests.xml'])
        self.assertEqual(m.NODES['tv'][0][3], 'android-gradle')
        self.assertEqual(m.NODES['phone'][0][3], 'android-gradle')
        self.assertEqual(m.NODES['phone'][1][3], 'android-gradle')

    def test_reviewed_devtool_pin_and_signing_separation(self):
        self.assertEqual(m.DEVTOOL_SHA, 'bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c')
        workflow = (HERE/'.github/workflows/studyflow-devtool-primary.yml').read_text()
        self.assertIn('DEVTOOL_SOURCE_SHA: '+m.DEVTOOL_SHA, workflow)
        self.assertIn('permissions:\n  contents: read\n', workflow)
        self.assertNotIn('secrets.', workflow)
        self.assertNotIn('id-token: write', workflow)
        self.assertNotIn('actions: write', workflow)
        self.assertNotIn('environment: studyflow-signing', workflow)
        self.assertNotIn('continue-on-error:', workflow)
        self.assertIn('pull_request:', workflow)
        self.assertIn('    branches: [main]', workflow)
        self.assertIn('name: Devtool CI gate', workflow)
        self.assertIn('SFDT18', (HERE/'docs/actions/STUDYFLOW_DEVTOOL_CI_MIGRATION.md').read_text())

    def test_no_local_gradle_in_tests_or_executor(self):
        script = (HERE/'scripts/studyflow_devtool_primary.py').read_text()
        self.assertIn('from devtool.actions_graph_export import compile_graph, run_node', script)
        self.assertNotIn('subprocess.run([\'./gradlew', script)
        self.assertNotIn('gh workflow run', script)

    def test_expected_graph_pass(self):
        one=m.NODES['phone'][0]
        graph=self.graph(one)
        row=m.check_node(graph,one,{'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA})
        self.assertEqual(row['job'], 'sfdt07-phone-debug')

    def test_graph_rejects_changed_task_and_host(self):
        node=m.NODES['phone'][0]
        ident={'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA}
        for key,value in [('tasks',[':app:assembleRelease']),('java_version','17'),('host_profile','termux')]:
            with self.subTest(key=key):
                graph=self.graph(node)
                graph['jobs'][0]['android_review'][key]=value
                with self.assertRaises(m.Refused):m.check_node(graph,node,ident)
        graph=self.graph(node);graph['jobs'][0]['provider']='python-shell'
        with self.assertRaises(m.Refused):m.check_node(graph,node,ident)
        graph=self.graph(node);graph['jobs'][0]['outputs']=[]
        with self.assertRaises(m.Refused):m.check_node(graph,node,ident)
        graph=self.graph(node);graph['consumer_commit']='b'*40
        with self.assertRaises(m.Refused):m.check_node(graph,node,ident)

    def test_result_rejects_fake_receipt(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'app/build/outputs/apk/debug').mkdir(parents=True)
            (root/'app/build/outputs/apk/debug/app-debug.apk').write_bytes(b'0'*1100)
            node=m.NODES['phone'][0]; graph=self.graph(node)
            row=graph['jobs'][0]; ident={'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA}
            good=self.good_result(graph)
            item=m.check_result(good,graph,row,ident,None,root,apk=node[5])
            self.assertEqual(item['apk']['size'], 1100)
            for path,value in [('status','LOCAL_JOB_FAILED'),('typed_node_evidence',{})]:
                with self.subTest(path=path):
                    bad=dict(good);bad[path]=value
                    with self.assertRaises(m.Refused):m.check_result(bad,graph,row,ident,None,root,apk=node[5])
            (root/node[5]).write_bytes(b'failed')
            with self.assertRaises(m.Refused):m.check_result(good,graph,row,ident,None,root,apk=node[5])

    def test_execute_phone_both_native_jobs_and_required_junit(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'repo';root.mkdir()
            temp=Path(d)/'temp';temp.mkdir()
            p=root/'app/build/outputs/apk/debug/app-debug.apk'
            p.parent.mkdir(parents=True)
            p.write_bytes(b'\x00'*1100)
            ident={'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA,'run_id':123,'run_attempt':1}
            mapping={spec[1]:spec for spec in m.NODES['phone']}
            calls=[]
            def compiler(root,*,target,workflow,devtool_sha):
                calls.append(workflow)
                return self.graph(mapping[workflow])
            def runner(root,*,target,workflow,devtool_sha,graph_sha,job_id,inputs,outputs):
                self.assertEqual(outputs, temp/'rm06-output')
                # Reproduce Devtool's real FileExistsError in GitHub run
                # 42334086888 if the first node's typed receipt still exists.
                evidence = temp/'rm06-evidence'/'node-01'/'node-evidence.json'
                evidence.parent.mkdir(parents=True, exist_ok=True)
                with evidence.open('x', encoding='utf-8') as handle:
                    handle.write(workflow)
                outputs.mkdir()
                (outputs/'native-evidence.txt').write_text(workflow)
                result=self.good_result(self.graph(mapping[workflow]))
                if workflow=='ci-sfdt08-phone-release-jvm':
                    result['typed_node_evidence']['typed_evidence']={'gates':{'test':{'state':'PASS'}}}
                    result['local_junit_verification']={'local_verified':True,
                                                        'tests':{'total':4,'failed':0}}
                return result
            with patch.object(m,'hosted_identity',return_value=ident):
                result=m.execute('phone',temp/'receipt.json',root=root,env={'RUNNER_TEMP':str(temp)},
                                 compiler=compiler,runner=runner)
            self.assertEqual(result['status'],'NATIVE_CI_LANE_PASSED')
            self.assertEqual(len(result['nodes']),2)
            self.assertEqual(calls,['ci-sfdt07-phone-debug','ci-sfdt08-phone-release-jvm'])
            self.assertEqual(result['nodes'][1]['junit']['executed'],4)
            self.assertFalse((temp/'rm06-output').exists())
            self.assertFalse((temp/'rm06-evidence').exists())
            for index,workflow in enumerate(calls):
                self.assertEqual((temp/'studyflow-primary-execution'/'phone'/str(index)/'outputs'/'native-evidence.txt').read_text(),workflow)
                self.assertEqual((temp/'studyflow-primary-execution'/'phone'/str(index)/'evidence'/'node-01'/'node-evidence.json').read_text(),workflow)

    def test_preexisting_native_evidence_fails_before_execution(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'repo';root.mkdir()
            temp=Path(d)/'temp';temp.mkdir()
            (temp/'rm06-evidence').mkdir()
            node=m.NODES['web'][0]
            ident={'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA,
                   'run_id':123,'run_attempt':1}
            def runner(*args, **kwargs):
                self.fail('Native Devtool should not execute over existing evidence')
            with patch.object(m,'hosted_identity',return_value=ident):
                with self.assertRaisesRegex(m.Refused, 'staging contains stale bytes'):
                    m.execute('web',temp/'receipt.json',root=root,
                              env={'RUNNER_TEMP':str(temp)},
                              compiler=lambda *args,**kw:self.graph(node),runner=runner)
            receipt=json.loads((temp/'receipt.json').read_text())
            self.assertFalse(receipt['ci_lane_passed'])
            self.assertEqual(receipt['status'],'FAILED_INCOMPLETE')

    def test_native_evidence_staging_symlink_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'repo';root.mkdir()
            temp=Path(d)/'temp';temp.mkdir()
            (temp/'outside').mkdir()
            (temp/'rm06-evidence').symlink_to(temp/'outside',target_is_directory=True)
            ident={'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA,
                   'run_id':123,'run_attempt':1}
            with patch.object(m,'hosted_identity',return_value=ident):
                with self.assertRaisesRegex(m.Refused, 'staging contains stale bytes'):
                    m.execute('web',temp/'receipt.json',root=root,
                              env={'RUNNER_TEMP':str(temp)},
                              compiler=lambda *a,**k:self.graph(m.NODES['web'][0]),
                              runner=lambda *a,**k:self.fail('Must refuse symlink'))

    def test_junit_typed_gate_must_pass_for_release(self):
        with tempfile.TemporaryDirectory() as d:
            node=m.NODES['phone'][1];graph=self.graph(node)
            result=self.good_result(graph)
            result['local_junit_verification']={'local_verified':True,'tests':{'total':5,'failed':0}}
            ident={'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA}
            with self.assertRaises(m.Refused):
                m.check_result(result,graph,graph['jobs'][0],ident,node[-1],Path(d),apk=None)
            result['typed_node_evidence']['typed_evidence']={'gates':{'test':{'state':'PASS'}}}
            item=m.check_result(result,graph,graph['jobs'][0],ident,node[-1],Path(d),apk=None)
            self.assertEqual(item['junit']['executed'],5)

    def test_tv_junit_fallback_parses_real_cases(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);p=root/'app-tv/build/test-results/testDebugUnitTest';p.mkdir(parents=True)
            (p/'TEST-fixture.xml').write_text('<testsuite><testcase name="a"/></testsuite>')
            self.assertEqual(m.inspect_junit(root,'app-tv/build/test-results/testDebugUnitTest')['executed'],1)
            (p/'TEST-fixture.xml').write_text('<testsuite><testcase><failure/></testcase></testsuite>')
            with self.assertRaises(m.Refused):m.inspect_junit(root,'app-tv/build/test-results/testDebugUnitTest')

    def test_fallback_never_success_and_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as d:
            dst=Path(d)/'receipt.json'
            m.fallback('phone',dst)
            result=json.loads(dst.read_text())
            self.assertEqual(result['status'],'FAILED_OR_NOT_RUN')
            self.assertFalse(result['ci_lane_passed'])
            with self.assertRaises(m.Refused):m.fallback('phone',dst)
            with self.assertRaises(m.Refused):m.fallback('wrong',Path(d)/'x.json')

    def test_host_rejects_local_execution_and_signing(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'repo';root.mkdir()
            temp=Path(d)/'temp';temp.mkdir()
            cfg={'GITHUB_ACTIONS':'true','CI':'true','RUNNER_OS':'Linux','RUNNER_ARCH':'X64',
                 'GITHUB_REPOSITORY':m.REPO,'GITHUB_EVENT_NAME':'pull_request',
                 'GITHUB_WORKSPACE':str(root),'GITHUB_SHA':'a'*40,
                 'RUNNER_TEMP':str(temp),'DEVTOOL_SOURCE_SHA':m.DEVTOOL_SHA,
                 'GITHUB_RUN_ID':'123','GITHUB_RUN_ATTEMPT':'1'}
            with patch.object(m,'git',side_effect=lambda path,*args:
                              ('a'*40 if args[-1]=='HEAD' else
                               m.DEVTOOL_SHA if args[-1]=='HEAD' else '')):
                # The mocked Git helper deliberately mismatches Devtool pin.
                with self.assertRaises(m.Refused):m.hosted_identity(cfg,root)
            cfg['STUDYFLOW_RELEASE_KEY_PASSWORD']='unsafe'
            with patch.object(m,'git',side_effect=lambda path,*args: m.DEVTOOL_SHA if path==temp/'studyflow-primary-devtool-source' else ('a'*40 if args[-1]=='HEAD' else '')):
                with self.assertRaises(m.Refused):m.hosted_identity(cfg,root)
                cfg.pop('STUDYFLOW_RELEASE_KEY_PASSWORD')
                out=m.hosted_identity(cfg,root)
                self.assertEqual(out['studyflow_sha'],'a'*40)
                cfg['GITHUB_ACTIONS']='false'
                with self.assertRaises(m.Refused):m.hosted_identity(cfg,root)

    def test_workflow_reuses_existing_native_dags_without_config_edits(self):
        code=(HERE/'scripts/studyflow_devtool_primary.py').read_text()
        for workflow in ('ci-sfdt07-phone-debug','ci-sfdt08-phone-release-jvm',
                         'ci-sfdt07-tv-debug','ci-sfdt19-native-desktop-test','ci-sfdt19-native-web-test'):
            self.assertIn(workflow,code)
        rollback=(HERE/'.devtool.toml').read_text()
        self.assertIn('ci-sfdt08-desktop-fast', rollback)
        self.assertIn('ci-sfdt08-web-full', rollback)
        source=(HERE/'.github/workflows/studyflow-devtool-primary.yml').read_text()
        self.assertIn('sfdt11_contract.py --source-check',source)
        self.assertIn('sfdt18_phone_debug_guard',code)
        self.assertIn('test "$NATIVE_RESULT" = success',source)

    def test_junit_graph_path_must_match_reviewed_directory(self):
        identity = {'studyflow_sha': 'a'*40, 'devtool_sha': m.DEVTOOL_SHA}
        for lane in ('phone', 'desktop', 'web'):
            node = m.NODES[lane][-1]
            if node[-1] is None:
                continue
            graph = self.graph(node)
            graph['jobs'][0]['junit_report_dir'] = 'other/reports'
            with self.subTest(lane=lane), self.assertRaises(m.Refused):
                m.check_node(graph, node, identity)

    def test_verify_reports_positive_each_lane_and_fail_closed(self):
        import os
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)/'repo'
            root.mkdir()
            temp = Path(d)/'tmp'
            temp.mkdir()
            for lane, rel in m.REPORT_DIRS.items():
                out = temp/(lane+'.json')
                out.write_text(json.dumps({'lane':lane,'status':'NATIVE_CI_LANE_PASSED',
                                           'ci_lane_passed':True,'cutover_authorized':False}))
                report_dir = root/rel
                report_dir.mkdir(parents=True,exist_ok=True)
                name = 'web-pytest-junit.xml' if lane=='web' else 'TEST-case.xml'
                report = report_dir/name
                report.write_text('<testsuite><testcase name="ok"/></testsuite>')
                if lane in m.LINT_DIRS:
                    lintdir = root/m.LINT_DIRS[lane]
                    lintdir.mkdir(parents=True,exist_ok=True)
                    (lintdir/'lint-results-debug.html').write_text('<html>ok</html>')
                with patch.dict(os.environ, {'RUNNER_TEMP':str(temp)}):
                    self.assertEqual(m.verify_reports(lane,out,root=root)['junit']['executed'],1)
                    report.unlink()
                    with self.assertRaises(m.Refused):
                        m.verify_reports(lane,out,root=root)
                    report.write_text('<testsuite><testcase name="ok"/></testsuite>')
                    out.write_text(json.dumps({'lane':lane,'status':'FAILED_OR_NOT_RUN',
                                               'ci_lane_passed':False,'cutover_authorized':False}))
                    with self.assertRaises(m.Refused):
                        m.verify_reports(lane,out,root=root)

    def test_report_upload_validation_is_required_before_artifact(self):
        workflow = (HERE/'.github/workflows/studyflow-devtool-primary.yml').read_text()
        assert workflow.index('Require fresh hosted test and Android lint report bytes') < workflow.index('Upload Devtool CI receipts')
        self.assertIn('--verify-reports', workflow)
        self.assertIn('STUDYFLOW_LANE', workflow)
        self.assertIn('if-no-files-found: error', workflow)

    def test_lint_report_missing_is_refused(self):
        import os
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)/'repo';root.mkdir()
            temp=Path(d)/'temp';temp.mkdir()
            junit=root/m.REPORT_DIRS['phone'];junit.mkdir(parents=True)
            (junit/'TEST-case.xml').write_text('<testsuite><testcase name="ok"/></testsuite>')
            out=temp/'receipt.json';out.write_text(json.dumps({
                'lane':'phone','status':'NATIVE_CI_LANE_PASSED','ci_lane_passed':True,
                'cutover_authorized':False}))
            with patch.dict(os.environ,{'RUNNER_TEMP':str(temp)}):
                with self.assertRaises(m.Refused):
                    m.verify_reports('phone',out,root=root)

    def test_native_polyglot_source_contracts_are_not_label_only(self):
        from pathlib import Path
        config=(HERE/'.devtool.toml').read_text()
        self.assertIn('command = ["npm", "run", "test"]',config)
        self.assertIn('ci_node_script_policy = "reviewed-composite"',config)
        self.assertIn('command = ["gradle", "--offline", "--no-daemon", "--console=plain", ":desktopApp:test"]',config)
        for lane, key, value in [('desktop','jvm_selected',':app'),('web','node_kind','build')]:
            with self.subTest(lane=lane):
                spec=m.NODES[lane][0]
                graph=self.graph(spec)
                graph['jobs'][0][key]=value
                with self.assertRaises(m.Refused):
                    m.check_node(graph,spec,{'studyflow_sha':'a'*40,'devtool_sha':m.DEVTOOL_SHA})

    def test_native_web_root_target_is_not_nested(self):
        import tomllib
        cfg=tomllib.loads((HERE/'.devtool.toml').read_text())
        native=cfg['targets']['studyflow_android']['jobs']['sfdt19-native-web-test']
        self.assertEqual(cfg['targets']['studyflow_android']['root'], '.')
        self.assertEqual(native['root'], '.')
        self.assertEqual(native['metadata']['ci_toolchain'], 'node')
        self.assertEqual(native['metadata']['ci_node_project_dir'], 'web')
        self.assertFalse(any('*' in item for item in native['inputs']))
        self.assertFalse(any('*' in item for item in
            cfg['targets']['studyflow_desktop']['jobs']['sfdt19-native-desktop-test']['inputs']))
        self.assertIn('ci-sfdt19-native-web-test',cfg['targets']['studyflow_android']['workflows'])
        self.assertNotIn('sfdt19-native-web-test', cfg['targets']['studyflow_web'].get('jobs',{}))
        self.assertIn('sfdt08-web-full', cfg['targets']['studyflow_android']['jobs'])

    def test_hf03_desktop_prewarm_downloads_external_artifact_bytes(self):
        # Source policy: dependency report metadata is not sufficient for RM12
        # offline compilation, which requires actual Compose/Kotlin class jars.
        import hashlib
        workflow = (HERE/'.github/workflows/studyflow-devtool-primary.yml').read_text()
        prewarm = HERE/'scripts/sfdt19_desktop_prewarm.init.gradle'
        source = prewarm.read_text(encoding='utf-8')
        digest = hashlib.sha256(prewarm.read_bytes()).hexdigest()
        self.assertEqual(digest, '261ae3c20741ac4fe4cbdbbbe5ff40b75bfd95d9cb7120f36cd186c0fb643e13')
        self.assertIn(digest + '  scripts/sfdt19_desktop_prewarm.init.gradle', workflow)
        self.assertIn('--no-configuration-cache --max-workers=2', workflow)
        self.assertIn(':desktopApp:sfdt19MaterializeDesktopExternalArtifacts', workflow)
        self.assertIn('component instanceof ModuleComponentIdentifier', source)
        self.assertIn('configuration.incoming.artifactView', source)
        for config in ('compileClasspath', 'runtimeClasspath', 'testCompileClasspath', 'testRuntimeClasspath'):
            self.assertIn("'"+config+"'", source)
        self.assertNotIn(':desktopApp:dependencies --configuration', workflow)
        preparation=workflow.split('name: Provision reviewed Desktop external artifact bytes',1)[1].split('name: Acquire immutable Devtool runtime',1)[0]
        self.assertFalse(any(':desktopApp:test' in line for line in preparation.splitlines()
                             if line.lstrip().startswith('./gradlew ')))
        self.assertNotIn('gradle --offline', preparation)
        self.assertIn('test ! -e desktopApp/build/test-results/test', preparation)
        self.assertIn('test ! -e dist/jvm-tests.xml', preparation)
        self.assertIn("('studyflow_desktop', 'ci-sfdt19-native-desktop-test'", (HERE/'scripts/studyflow_devtool_primary.py').read_text())
        self.assertIn('sfdt08-desktop-fast', (HERE/'.devtool.toml').read_text())

    @staticmethod
    def graph(node):
        target,workflow,job,provider,tasks,apk,_=node
        return {'consumer_commit':'a'*40,'runtime_sha':m.DEVTOOL_SHA,
                'graph_sha256':'f'*64,
                'jobs':[{'id':'node-01','job':job,'provider':provider,'runner':'ubuntu-24.04',
                         'handoffs':[],'needs':[], 'outputs':m.owned_outputs(node),
                         'junit_report_dir': node[-1] if job != 'sfdt07-tv-debug' else '',
                         'jvm_manager':'gradle', 'jvm_selected':':desktopApp',
                         'jvm_operation':'test', 'jvm_tool_version':'9.7.1',
                         'node_manager':'npm', 'node_kind':'test',
                         'node_project_dir':'web', 'node_script_policy':'reviewed-composite',
                         'android_review':{'semantic_request':True,'tasks':list(tasks),
                                           'java_version':'21','ndk_version':'29.0.14206865',
                                           'host_profile':'studyflow-ubuntu24'}}]}

    @staticmethod
    def good_result(graph):
        return {'status':'LOCAL_JOB_SUCCEEDED','typed_node_evidence':{
            'source_commit':'a'*40,'graph_sha256':graph['graph_sha256'],
            'job_id':'node-01','authenticated_remote':False,'release_qualified':False,
            'receipt_sha256':'e'*64}}


if __name__=='__main__':
    unittest.main(verbosity=2)
