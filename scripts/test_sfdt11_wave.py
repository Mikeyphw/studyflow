#!/usr/bin/env python3
"""SFDT11 wave coordinator tests. No live network or dispatch."""
from __future__ import annotations

from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
import sfdt11_wave as a

SRC='a'*40


def row(i,status='completed',conclusion='success'):
    return {'id':i,'status':status,'conclusion':conclusion,'head_sha':SRC,
            'head_branch':'main','event':'workflow_dispatch'}

class WaveTests(unittest.TestCase):
    def test_classification_never_invents_pending_scope(self):
        self.assertEqual(a.classify_jobs('fast',[],event='workflow_dispatch'),'unknown')
        self.assertEqual(a.classify_jobs('deep',[],event='workflow_dispatch'),'unknown')
        self.assertEqual(a.classify_jobs('fast',[{'name':'native-phone-release-jvm','conclusion':None}],event='workflow_dispatch'),'fast')
        self.assertEqual(a.classify_jobs('deep',[{'name':'native-quality-contracts','conclusion':'success'}],event='workflow_dispatch'),'deep')

    def test_mixed_matrix_refused(self):
        self.assertEqual(a.classify_jobs('fast',[{'name':'native-phone-release-jvm','conclusion':'success'},
                                                       {'name':'native-quality-contracts','conclusion':'success'}],
                                         event='workflow_dispatch'),'unexpected')

    def test_success_requires_terminal(self):
        self.assertTrue(a.passed(row(9)))
        self.assertFalse(a.passed(row(9,'in_progress',None)))
        self.assertFalse(a.passed(None))
        self.assertFalse(a.passed(row(9,'completed','failure')))

    def test_dispatch_allowlist_and_inputs(self):
        with patch.object(a,'command',return_value='') as cmd:
            a.dispatch(a.WF['android'],{'expected_sha':SRC})
            cmd.assert_called_once()
            self.assertIn('main',cmd.call_args[0][0])
            self.assertIn('expected_sha='+SRC,cmd.call_args[0][0])
        with self.assertRaises(a.WaveError):a.dispatch('unreviewed.yml',{'expected_sha':SRC})
        with self.assertRaises(a.WaveError):a.dispatch(a.WF['android'],{'bad;touch':'hello'})

    def test_start_dispatches_six_and_does_not_duplicate(self):
        with patch.object(a,'latest',return_value=None),patch.object(a,'dispatch') as d:
            a.begin(SRC)
            self.assertEqual(d.call_count,6)
        with patch.object(a,'latest',side_effect=lambda name,sha: row(45 if name=='deep' else 44)),patch.object(a,'dispatch') as d:
            a.begin(SRC)
            d.assert_not_called()
        with patch.object(a,'latest',return_value=row(44,'queued',None)),patch.object(a,'dispatch') as d:
            with self.assertRaises(a.WaveError):a.begin(SRC)
            d.assert_not_called()

    def test_compare_requires_native_and_legacy(self):
        with patch.object(a,'latest',side_effect=[row(99),None]):
            with self.assertRaises(a.WaveError):a.compare(SRC)
        with patch.object(a,'latest',side_effect=[row(99),row(100),None]),patch.object(a,'dispatch') as d:
            a.compare(SRC)
            self.assertEqual(d.call_args[0][1],{'expected_sha':SRC,
                                               'legacy_run_id':'99','shadow_run_id':'100'})

    def test_rollup_refuses_incomplete_or_duplicate(self):
        runs={k:row(i+10) for i,k in enumerate(('legacy','legacy_deep','source','expanded','fast','deep','android','compare'))}
        with patch.object(a,'wave_status',return_value={**runs,'fast':None}):
            with self.assertRaises(a.WaveError):a.rollup(SRC)
        runs['compare']=runs['source']
        with patch.object(a,'wave_status',return_value=runs):
            with self.assertRaises(a.WaveError):a.rollup(SRC)

    def test_rollup_dispatches_all_eight_ids(self):
        runs={k:row(i+10) for i,k in enumerate(('legacy','legacy_deep','source','expanded','fast','deep','android','compare'))}
        with patch.object(a,'wave_status',return_value=runs),patch.object(a,'dispatch') as d:
            a.rollup(SRC)
            params=d.call_args[0][1]
            self.assertEqual(len(params),9)
            self.assertEqual(params['expected_sha'],SRC)
            self.assertEqual(params['legacy_deep_run_id'],'11')


    def test_candidates_filter_wrong_source_branch_and_event(self):
        legit={**row(15), 'event':'push','path':'.github/workflows/studyflow-ci.yml'}
        wrong={**legit,'id':20,'head_sha':'b'*40}
        bad_event={**legit,'id':25,'event':'workflow_dispatch'}
        bad_branch={**legit,'id':30,'head_branch':'feature'}
        with patch.object(a,'api_json',return_value={'total_count':4,'workflow_runs':[
             wrong,bad_event,bad_branch,legit]}):
            self.assertEqual([x['id'] for x in a.candidates('legacy',SRC)],[15])

    def test_latest_coverage_scope_and_queued_ambiguity(self):
        fast={**row(12), 'event':'workflow_dispatch'}
        deep={**row(13), 'event':'workflow_dispatch'}
        def job_api(path):
            rid=int(path.split('runs/')[1].split('/')[0])
            name='native-web-full' if rid==12 else 'native-tv-contracts'
            return {'total_count':1,'jobs':[{'name':name,'conclusion':'success'}]}
        with patch.object(a,'candidates',return_value=[deep,fast]):
            self.assertEqual(a.latest('fast',SRC,api=job_api)['id'],12)
            self.assertEqual(a.latest('deep',SRC,api=job_api)['id'],13)
        waiting={**row(20,'queued',None)}
        with patch.object(a,'candidates',return_value=[waiting]):
            self.assertEqual(a.latest('fast',SRC,api=lambda _: {'total_count':0,'jobs':[]})['id'],20)

    def test_drive_rejects_moved_main_without_dispatching_comparison(self):
        with patch.object(a,'begin'),patch.object(a,'main_sha',return_value='b'*40),             patch.object(a,'compare') as compare:
            with self.assertRaises(a.WaveError):
                a.drive(SRC,poll_seconds=30,max_hours=1)
            compare.assert_not_called()

    def test_drive_requires_bounded_poll_and_wait(self):
        with self.assertRaises(a.WaveError):a.drive(SRC,poll_seconds=1,max_hours=1)
        with self.assertRaises(a.WaveError):a.drive(SRC,poll_seconds=45,max_hours=20)

if __name__=='__main__':unittest.main(verbosity=2)
