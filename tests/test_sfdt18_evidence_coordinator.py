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
    EVIDENCE_WORKFLOW,
)

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


if __name__ == '__main__':
    unittest.main()
