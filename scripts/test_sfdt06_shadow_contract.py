#!/usr/bin/env python3
"""SFDT-06 no-network/no-Gradle adversarial regression suite."""
from __future__ import annotations
import copy
from pathlib import Path
import json
import sys
import tempfile
import unittest
import hashlib
import types
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sfdt06_shadow_contract as S
import sfdt06_native_shadow as N


def sample(root: Path) -> None:
    for rel in [*{r for r, _ in S.STAGES.values()},
                'scripts/studyflow_ci_gradle_profile.py',
                S.SHADOW_WORKFLOW, S.LEGACY_WORKFLOW,
                '.github/workflows/studyflow-hosted-sign.yml']:
        (root/rel).parent.mkdir(parents=True, exist_ok=True)
    for path,_ in S.STAGES.values():
        (root/path).write_text('#!/usr/bin/env python3\nprint("SOURCE_PASS")\n')
    (root/'scripts/studyflow_ci_gradle_profile.py').write_text('''ROOT=None
PROFILES={'android-contracts':'studyflow_android','tv-contracts':'studyflow_tv','desktop-contracts':'studyflow_desktop','quality-contracts':'studyflow_quality','parity-contracts':'studyflow_parity'}
def profile_plan(profile):
    return {'target':PROFILES[profile], 'operation':'test', 'tasks':['verify_'+profile.replace('-','_')], 'qualified_release':False}
''')
    (root/'.github/workflows/studyflow-hosted-sign.yml').write_text('environment: studyflow-signing\n')
    (root/S.SHADOW_WORKFLOW).write_bytes((Path(__file__).resolve().parents[1]/S.SHADOW_WORKFLOW).read_bytes())
    (root/S.LEGACY_WORKFLOW).write_text('''jobs:
  source:
  web:
  desktop:
  android:
  deep-contracts:
    strategy:
      matrix:
        profile:
          - android-contracts
          - tv-contracts
          - desktop-contracts
          - quality-contracts
          - parity-contracts
''')
    scopes = ['studyflow_android', 'studyflow_web', 'studyflow_desktop', 'studyflow_tv',
              'studyflow_release', 'studyflow_tv_release', 'studyflow_quality', 'studyflow_parity']
    lines = ['[targets.'+name+']\nroot = "."\n' for name in scopes]
    for name,(path,args) in S.STAGES.items():
        digest=hashlib.sha256((root/path).read_bytes()).hexdigest()
        arr=json.dumps(['python3', path, *args])
        lines.append(f'[targets.studyflow_android.jobs.{name}]\ncommand = {arr}\n'
                     f'metadata = {{ read_only = true, ci_python_version = "3.12", ci_python_script_sha256 = "{digest}", ci_python_script_args = {json.dumps(args)} }}\n')
    lines.append('''[targets.studyflow_android.workflows]
ci-source = [
 { id = "a", ref = "job:ci-actions-contract" },
 { id = "b", ref = "job:ci-source-contract" },
 { id = "c", ref = "job:ci-sfdt-source-audit" },
 { id = "d", ref = "job:ci-github-gradle-policy" },
]
''')
    (root/'.devtool.toml').write_text('\n'.join(lines))


class SourceOnlyContractTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='sfdt06-test-')
        self.root = Path(self.tmp.name)
        sample(self.root)

    def tearDown(self):
        self.tmp.cleanup()

    def test_positive_reports_all_legacy_lanes_without_claiming_hosted(self):
        receipt=S.audit(self.root)
        self.assertEqual(len(receipt['profiles']), 5)
        self.assertEqual(len(receipt['scopes']), 7)
        self.assertEqual(len(receipt['source_nodes']), 4)
        self.assertEqual(receipt['legacy_hosted_results'],'NOT_OBSERVED')
        self.assertFalse(receipt['cutover_authorized'])
        self.assertFalse(receipt['gradle_executed'])
        self.assertEqual(receipt['scopes']['web']['shadow'],'NOT_ADOPTED')

    def test_source_script_drift_refused(self):
        (self.root/S.STAGES['ci-actions-contract'][0]).write_text('print("INJECT")\n')
        with self.assertRaisesRegex(S.ShadowContractError,'SHA/arguments mismatch'):
            S.audit(self.root)

    def test_removal_of_deep_profile_fails(self):
        path=self.root/S.LEGACY_WORKFLOW
        path.write_text(path.read_text().replace('          - parity-contracts\n',''))
        with self.assertRaisesRegex(S.ShadowContractError,'deep matrix omitted'):
            S.audit(self.root)

    def test_rewriting_shadow_to_push_refused(self):
        path=self.root/S.SHADOW_WORKFLOW
        path.write_text(path.read_text()+'\npush:\n')
        with self.assertRaisesRegex(S.ShadowContractError,'manual-only'):
            S.audit(self.root)

    def test_moving_signing_environment_refused(self):
        (self.root/'.github/workflows/studyflow-hosted-sign.yml').write_text('environment: public\n')
        with self.assertRaisesRegex(S.ShadowContractError,'signer boundary'):
            S.audit(self.root)

    def test_invalid_source_argv_refused(self):
        path=self.root/'.devtool.toml'
        path.write_text(path.read_text().replace('"scripts/devtool_actions_contract.py"','"./gradlew"',1))
        with self.assertRaisesRegex(S.ShadowContractError,'Source argv drift'):
            S.audit(self.root)

    def test_legacy_job_removal_refused(self):
        p=self.root/S.LEGACY_WORKFLOW
        p.write_text(p.read_text().replace('  android:\n', ''))
        with self.assertRaisesRegex(S.ShadowContractError,'fast/deep'):
            S.audit(self.root)

    def test_shadow_permission_inflation_refused(self):
        p=self.root/S.SHADOW_WORKFLOW
        p.write_text(p.read_text().replace('permissions:\n  contents: read\n', 'permissions:\n  contents: write\n'))
        with self.assertRaisesRegex(S.ShadowContractError,'source/DAG/receipt|permissions'):
            S.audit(self.root)

    def test_no_local_execution_without_github(self):
        with self.assertRaisesRegex(ValueError,'not authorized'):
            N._identity({},self.root)

    def test_native_graph_cannot_smuggle_gradle_or_signer(self):
        rows=[{'id':'node-'+str(i), 'job':n,'provider':'python-shell','outputs':[],
               'runner':'ubuntu-24.04','handoffs':[]} for i,n in enumerate(sorted(N.NODE_NAMES))]
        self.assertEqual(len(N._select({'jobs':rows})),4)
        rows[0]['provider']='android-gradle'
        with self.assertRaisesRegex(ValueError,'unsupported'):
            N._select({'jobs':rows})

    def test_native_graph_cannot_skip_a_node(self):
        rows=[{'id':'node-'+str(i), 'job':n,'provider':'python-shell','outputs':[],
               'runner':'ubuntu-24.04','handoffs':[]} for i,n in enumerate(sorted(N.NODE_NAMES))]
        with self.assertRaisesRegex(ValueError,'exactly four'):
            N._select({'jobs':rows[:3]})

    def _fake_native(self, *, bad=False):
        package=types.ModuleType('devtool')
        package.__path__=[]
        export=types.ModuleType('devtool.actions_graph_export')
        jobs=[{'id':'node-'+str(i), 'job':name,'provider':'python-shell','outputs':[],
               'runner':'ubuntu-24.04','handoffs':[]} for i,name in enumerate(sorted(N.NODE_NAMES))]
        graph={'jobs':jobs,'consumer_commit':'a'*40,'runtime_sha':'b'*40,'graph_sha256':'d'*64}
        export.compile_graph=lambda *args,**kw:graph
        def run_node(*args,**kwargs):
            nodeid=kwargs['job_id']
            return {'status':'LOCAL_JOB_SUCCEEDED',
                    'typed_node_evidence': {'source_commit': 'z'*40 if bad else 'a'*40,
                        'graph_sha256':'d'*64,'job_id':nodeid,'release_qualified':False,
                        'receipt_sha256':'f'*64}}
        export.run_node=run_node
        return {'devtool':package,'devtool.actions_graph_export':export}

    def test_native_attempt_success_is_still_not_legacy_equivalent(self):
        runner=self.root.parent/'runner-tmp'
        runner.mkdir(exist_ok=True)
        identity={'source_commit':'a'*40,'source_tree':'c'*40,'runtime_commit':'b'*40,
                  'run_id':5,'attempt':1,'release_qualified':False,'legacy_equivalent':False}
        out=runner/'native-source-shadow.json'
        with patch.dict(sys.modules,self._fake_native()), patch.object(N,'_identity',return_value=identity):
            row=N.execute(self.root,out,env={'RUNNER_TEMP':str(runner)})
        self.assertEqual(row['status'],'NATIVE_SOURCE_SHADOW_PASSED')
        self.assertEqual(len(row['nodes']),4)
        self.assertFalse(row['legacy_equivalent'])
        self.assertFalse(json.loads(out.read_text())['release_qualified'])
        out.unlink()
        runner.rmdir()

    def test_forged_native_receipt_cannot_mark_success(self):
        runner=self.root.parent/'runner-tmp'
        runner.mkdir(exist_ok=True)
        identity={'source_commit':'a'*40,'source_tree':'c'*40,'runtime_commit':'b'*40,
                  'run_id':5,'attempt':1,'release_qualified':False,'legacy_equivalent':False}
        out=runner/'native-source-shadow.json'
        with patch.dict(sys.modules,self._fake_native(bad=True)), patch.object(N,'_identity',return_value=identity):
            with self.assertRaisesRegex(ValueError,'missing its bound success receipt'):
                N.execute(self.root,out,env={'RUNNER_TEMP':str(runner)})
        receipt=json.loads(out.read_text())
        self.assertEqual(receipt['status'],'FAILED_INCOMPLETE')
        self.assertFalse(receipt['release_qualified'])
        self.assertFalse(receipt['legacy_equivalent'])
        out.unlink()
        runner.rmdir()

    def test_source_coverage_output_cannot_write_inside_checkout(self):
        previous=S.ROOT
        S.ROOT=self.root
        try:
            self.assertEqual(S.main(['--check', '--out', str(self.root/'coverage.json')]),2)
            self.assertFalse((self.root/'coverage.json').exists())
        finally:
            S.ROOT=previous

if __name__=='__main__':
    unittest.main(verbosity=2)
