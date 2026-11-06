#!/usr/bin/env python3
"""SFDT18 source-only negative tests; never call GitHub or Gradle."""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.sfdt18_evidence_coordinator import (
    POLICY, REPO, LANES, SCHEMA, Refused, choose_legacy_run,
    checked_inputs, check_run, fresh_dispatch_ids, require_unique_dispatch,
    read_state, reviewed_path, validate_workflow_catalog, write_state,
    EVIDENCE_WORKFLOW, validate_recovery_request,
    validate_recovery_candidate, _complete_recovery_candidate, recover, adopt,
)
from unittest.mock import patch

SHA = "a" * 40
IDS = {lane: index+1000 for index, lane in enumerate(LANES)}


def run(lane: str) -> dict:
    wid, _, name = POLICY[lane]
    return {"id": IDS[lane], "workflow_id": wid,
            "path": reviewed_path(lane), "name": name,
            "event": "push" if lane == "legacy" else "workflow_dispatch",
            "head_branch": "main", "head_sha": SHA, "run_attempt": 1,
            "status": "completed", "conclusion": "success",
            "repository": {"full_name": REPO}}


def catalog() -> list[dict]:
    rows = []
    seen = set()
    for wid, filename, name in POLICY.values():
        if wid in seen:
            continue
        seen.add(wid)
        rows.append({"id": wid, "path": ".github/workflows/" + filename,
                     "name": name, "state": "active"})
    rows.append({"id": 8888888, "path": ".github/workflows/" + EVIDENCE_WORKFLOW[0],
                 "name": EVIDENCE_WORKFLOW[1], "state": "active"})
    return rows


def sample_runs() -> dict:
    return {lane: run(lane) for lane in LANES}


class CoordinatorTests(unittest.TestCase):
    def test_full_catalog_review(self):
        self.assertEqual(validate_workflow_catalog(catalog()), 8888888)

    def test_missing_workflow_definition_refused(self):
        with self.assertRaisesRegex(Refused, 'reviewed workflow'):
            validate_workflow_catalog(catalog()[1:])

    def test_disabled_workflow_refused(self):
        rows = catalog(); rows[0]['state'] = 'disabled_manually'
        with self.assertRaisesRegex(Refused, 'active-state'):
            validate_workflow_catalog(rows)

    def test_changed_id_workflow_refused(self):
        rows = catalog(); rows[0]['id'] = 1
        with self.assertRaises(Refused): validate_workflow_catalog(rows)

    def test_unreviewed_evidence_workflow_refused(self):
        rows = catalog(); rows[-1]['path'] = '.github/workflows/evil.yml'
        with self.assertRaisesRegex(Refused, 'SFDT17'):
            validate_workflow_catalog(rows)

    def test_legacy_exact_push_success(self):
        self.assertEqual(choose_legacy_run([run('legacy')], SHA), IDS['legacy'])

    def test_legacy_skipped_or_wrong_event_refused(self):
        for key, val in [('conclusion', 'skipped'), ('event','workflow_dispatch'),
                         ('head_branch', 'develop'), ('workflow_id', 99),
                         ('head_sha', 'b'*40), ('path', '.github/workflows/unrelated.yml')]:
            item = run('legacy'); item[key] = val
            with self.subTest(key=key), self.assertRaises(Refused):
                choose_legacy_run([item], SHA)

    def test_legacy_prefers_latest_success(self):
        a = run('legacy'); b = dict(a); b['id'] = 9999
        self.assertEqual(choose_legacy_run([a,b], SHA), 9999)

    def test_live_path_from_github_is_relative(self):
        self.assertEqual(reviewed_path('legacy'), '.github/workflows/studyflow-ci.yml')
        self.assertEqual(check_run(run('legacy'),lane='legacy',sha=SHA,run_id=IDS['legacy']),1)

    def test_qualified_legacy_path_is_rejected(self):
        r=run('legacy'); r['path']=f'{REPO}/.github/workflows/studyflow-ci.yml@refs/heads/main'
        with self.assertRaises(Refused):
            check_run(r, lane='legacy', sha=SHA,run_id=IDS['legacy'])

    def test_wrong_workflow_id_path_name_branch_source_event_repository(self):
        for key,val in [('workflow_id', 99), ('path', 'other.yml'), ('name', 'other'),
                        ('head_branch', 'develop'), ('head_sha', 'c'*40),
                        ('event', 'push'), ('repository', {'full_name':'other/repo'}),
                        ('run_attempt',0)]:
            r=run('native_fast');r[key]=val
            with self.subTest(key=key), self.assertRaises(Refused):
                check_run(r,lane='native_fast',sha=SHA,run_id=IDS['native_fast'])

    def test_wrong_run_id_refused(self):
        with self.assertRaises(Refused):
            check_run(run('native_deep'),lane='native_deep',sha=SHA,run_id=12345)

    def test_fresh_run_cursor_filters_old_or_other_sha(self):
        old=run('native_fast');old['id']=20
        new=run('native_fast');new['id']=30
        incorrect=run('native_fast');incorrect['id']=40;incorrect['head_sha']='b'*40
        self.assertEqual(fresh_dispatch_ids([old,new,incorrect],25,SHA,POLICY['native_fast'][0]),[30])
        self.assertIsNone(require_unique_dispatch([old],25,SHA,POLICY['native_fast'][0]))

    def test_ambiguous_dispatch_refused(self):
        a=run('native_fast');a['id']=30
        b=run('native_fast');b['id']=31
        with self.assertRaisesRegex(Refused, 'Ambiguous'):
            require_unique_dispatch([a,b],25,SHA,POLICY['native_fast'][0])

    def test_checked_inputs_pin_identity_while_not_promoting_ci(self):
        identities,inputs=checked_inputs(SHA,IDS,sample_runs())
        self.assertEqual(identities['native_fast']['workflow_id'],406507103)
        self.assertEqual(identities['native_fast']['workflow_path'],reviewed_path('native_fast'))
        self.assertEqual(inputs['native_deep_run'],str(IDS['native_deep']))
        self.assertNotIn('cutover_authorized',inputs)

    def test_missing_or_duplicate_run_refused(self):
        dupe=dict(IDS);dupe['native_deep']=dupe['native_fast']
        with self.assertRaisesRegex(Refused,'duplicate'):
            checked_inputs(SHA,dupe,sample_runs())
        missing=dict(IDS);missing.pop('native_deep')
        with self.assertRaises(Refused):checked_inputs(SHA,missing,sample_runs())

    def test_incomplete_or_failed_run_cannot_dispatch_verifier(self):
        for conclusion,status in [('failure','completed'),(None,'in_progress'),('cancelled','completed')]:
            data=sample_runs();data['native_deep']['conclusion']=conclusion
            data['native_deep']['status']=status
            with self.subTest(conclusion=conclusion),self.assertRaises(Refused):
                checked_inputs(SHA,IDS,data)

    def test_state_round_trip_permissions_and_schema(self):
        with tempfile.TemporaryDirectory() as d:
            file=Path(d)/'state.json'
            obj={'schema':SCHEMA,'repo':REPO,'sha':SHA,'runs':dict(IDS),'phase':'READY'}
            write_state(file,obj)
            self.assertEqual(read_state(file,SHA),obj)
            self.assertEqual(file.stat().st_mode & 0o777,0o600)

    def test_state_wrong_sha_refused(self):
        with tempfile.TemporaryDirectory() as d:
            file=Path(d)/'state.json'
            write_state(file,{'schema':SCHEMA,'repo':REPO,'sha':SHA,'runs':{},'phase':'READY'})
            with self.assertRaisesRegex(Refused,'Wrong/corrupt'):
                read_state(file,'b'*40)

    def test_state_missing_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(Refused):read_state(Path(d)/'missing.json',SHA)



def failed_state() -> tuple[dict, dict]:
    state={"schema":SCHEMA, "repo":REPO, "sha":SHA,
           "phase":"READY", "runs":dict(IDS)}
    live=sample_runs()
    live["expanded_shadow"]["conclusion"]="cancelled"
    return state, live


def recovery_artifacts(run_id: int, attempt: int=1) -> list[dict]:
    from scripts.sfdt17_hosted_equivalence import artifact_contracts
    ids={lane:run_id if lane=='expanded_shadow' else 1 for lane in LANES}
    ats={lane:attempt if lane=='expanded_shadow' else 1 for lane in LANES}
    patterns=artifact_contracts(ids,ats)['expanded_shadow']
    return [{"id":20000+i,"name":name,"size_in_bytes": 2000000 if role.endswith('_apk') else 5000,
             "expired":False,"digest":"sha256:"+'b'*64,
             "workflow_run":{"id":run_id,"head_sha":SHA}}
            for i,(name,role) in enumerate(patterns.items())]


class SFDT18HF01RecoveryTests(unittest.TestCase):
    def test_failed_lane_preserves_other_four(self):
        state,live=failed_state()
        self.assertEqual(validate_recovery_request(state,live,SHA),IDS['expanded_shadow'])
        self.assertEqual(len(state['runs']),5)

    def test_successful_lane_cannot_be_recovered(self):
        state,live=failed_state();live['expanded_shadow']['conclusion']='success'
        with self.assertRaisesRegex(Refused,'failed or been cancelled'):
            validate_recovery_request(state,live,SHA)

    def test_other_lane_failure_refuses(self):
        state,live=failed_state();live['native_deep']['conclusion']='failure'
        with self.assertRaisesRegex(Refused,'Other four'):
            validate_recovery_request(state,live,SHA)

    def test_changed_source_refused(self):
        state,live=failed_state()
        with self.assertRaisesRegex(Refused,'source/state'):
            validate_recovery_request(state,live,'b'*40)

    def test_recovery_refuses_pending_evidence_or_state_mutation(self):
        state,live=failed_state()
        for phase in ('DISPATCHING','EVIDENCE_DISPATCHED','RECOVERY_PENDING'):
            x=dict(state);x['phase']=phase
            with self.subTest(phase=phase),self.assertRaises(Refused):
                validate_recovery_request(x,live,SHA)
        x=dict(state);x['evidence_run']=123
        with self.assertRaises(Refused):validate_recovery_request(x,live,SHA)

    def test_candidate_accepts_real_complete_4_job_matrix_and_zip_names(self):
        rid=9000; r=run('expanded_shadow');r['id']=rid
        jobs=[{'name': 'native-'+name, 'status':'completed','conclusion':'success',
               'run_id':rid,'head_sha':SHA}
              for name in ('phone-debug','tv-debug','web-pytest','quality-source')]
        validate_recovery_candidate(r,sha=SHA,run_id=rid,
                                    jobs=jobs,artifacts=recovery_artifacts(rid))

    def test_candidate_refuses_fallback_422_bytes(self):
        rid=9000; r=run('expanded_shadow');r['id']=rid
        jobs=[{'name':'native-'+name,'status':'completed','conclusion':'success'}
              for name in ('phone-debug','tv-debug','web-pytest','quality-source')]
        arts=recovery_artifacts(rid);arts[0]['size_in_bytes']=422
        with self.assertRaisesRegex(Refused,'too small'):
            validate_recovery_candidate(r,sha=SHA,run_id=rid,jobs=jobs,artifacts=arts)

    def test_candidate_refuses_skipped_job(self):
        rid=9000; r=run('expanded_shadow');r['id']=rid
        jobs=[{'name':'native-'+name,'status':'completed','conclusion':'success'}
              for name in ('phone-debug','tv-debug','web-pytest','quality-source')]
        jobs[0]['conclusion']='skipped'
        with self.assertRaisesRegex(Refused,'skipped'):
            validate_recovery_candidate(r,sha=SHA,run_id=rid,jobs=jobs,
                                        artifacts=recovery_artifacts(rid))

    def test_candidate_refuses_wrong_sha_or_workflow(self):
        rid=9000; r=run('expanded_shadow');r['id']=rid;r['head_sha']='b'*40
        with self.assertRaises(Refused):
            validate_recovery_candidate(r,sha=SHA,run_id=rid,jobs=[],artifacts=[])

    def test_candidate_refuses_missing_artifact(self):
        rid=9000; r=run('expanded_shadow');r['id']=rid
        with self.assertRaises(Refused):
            validate_recovery_candidate(r,sha=SHA,run_id=rid,
                 jobs=[{'name':'native-'+name,'status':'completed','conclusion':'success'}
              for name in ('phone-debug','tv-debug','web-pytest','quality-source')],
                 artifacts=recovery_artifacts(rid)[:2])

    def test_adoption_pure_transaction_preserves_old_history(self):
        state,_=failed_state();state['phase']='RECOVERY_PENDING'
        state['recovery']={'lane':'expanded_shadow','prior_id':IDS['expanded_shadow'],
                            'cursor':IDS['expanded_shadow'],'candidate_id':9000}
        r=run('expanded_shadow');r['id']=9000
        jobs=[{'name':'native-'+name,'status':'completed','conclusion':'success'}
              for name in ('phone-debug','tv-debug','web-pytest','quality-source')]
        result=_complete_recovery_candidate(state,r,jobs,recovery_artifacts(9000))
        self.assertEqual(state['runs']['expanded_shadow'],IDS['expanded_shadow'])
        self.assertEqual(result['runs']['expanded_shadow'],9000)
        self.assertEqual(result['runs']['native_fast'],IDS['native_fast'])
        self.assertEqual(result['recoveries'][0]['prior_id'],IDS['expanded_shadow'])
        self.assertEqual(result['phase'],'READY')
        self.assertNotIn('recovery',result)

    def test_failed_candidate_cannot_be_adopted(self):
        state,_=failed_state();state['phase']='RECOVERY_PENDING'
        state['recovery']={'lane':'expanded_shadow','prior_id':IDS['expanded_shadow'],
                           'candidate_id':9000}
        r=run('expanded_shadow');r['id']=9000;r['conclusion']='cancelled'
        with self.assertRaises(Refused):
            _complete_recovery_candidate(state,r,[],[])
        self.assertEqual(state['runs']['expanded_shadow'],IDS['expanded_shadow'])

    def test_recover_dispatch_preserves_previous_run_ids(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); state,live=failed_state()
            write_state(root/(SHA+'.json'),state)
            with patch('scripts.sfdt18_evidence_coordinator.main_sha',return_value=SHA), \
                 patch('scripts.sfdt18_evidence_coordinator._live_states',return_value=live), \
                 patch('scripts.sfdt18_evidence_coordinator.workflow_catalog',return_value=(777,[])), \
                 patch('scripts.sfdt18_evidence_coordinator._latest_run_id',return_value=8000), \
                 patch('scripts.sfdt18_evidence_coordinator._wait_new_run',return_value=9000), \
                 patch('scripts.sfdt18_evidence_coordinator.gh') as mockgh:
                recover(state_dir=root)
            state=read_state(root/(SHA+'.json'),SHA)
            self.assertEqual(state['phase'],'RECOVERY_PENDING')
            self.assertEqual(state['runs']['expanded_shadow'],IDS['expanded_shadow'])
            self.assertEqual(state['recovery']['candidate_id'],9000)
            self.assertIn('expected_sha='+SHA,mockgh.call_args.args)

    def test_adopt_fetches_attempt_scoped_jobs_and_updates_state(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); state,live=failed_state();state['phase']='RECOVERY_PENDING'
            state['recovery']={'lane':'expanded_shadow','prior_id':IDS['expanded_shadow'],
                                'cursor':8000,'candidate_id':9000}
            write_state(root/(SHA+'.json'),state)
            candidate=run('expanded_shadow');candidate['id']=9000
            def fake_api(endpoint):
                if endpoint.endswith('/runs/9000'):
                    return candidate
                if endpoint.endswith('/attempts/1/jobs?per_page=100'):
                    return {'total_count':4,'jobs':[{'name':'native-'+name,'status':'completed','conclusion':'success'}
                       for name in ('phone-debug','tv-debug','web-pytest','quality-source')]}
                if endpoint.endswith('/artifacts?per_page=100'):
                    return {'total_count':4,'artifacts':recovery_artifacts(9000)}
                raise AssertionError(endpoint)
            with patch('scripts.sfdt18_evidence_coordinator.main_sha',return_value=SHA), \
                 patch('scripts.sfdt18_evidence_coordinator._live_states',return_value=live), \
                 patch('scripts.sfdt18_evidence_coordinator.gh_json',side_effect=fake_api):
                adopt(state_dir=root)
            now=read_state(root/(SHA+'.json'),SHA)
            self.assertEqual(now['phase'],'READY')
            self.assertEqual(now['runs']['expanded_shadow'],9000)
            self.assertEqual(now['runs']['native_deep'],IDS['native_deep'])

if __name__ == '__main__':
    unittest.main()
