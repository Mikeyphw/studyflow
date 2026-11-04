#!/usr/bin/env python3
"""Offline/adversarial same-SHA metadata checks, no GitHub or Gradle execution."""
from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sfdt06_compare_runs as M

SHA='a'*40

def fixture():
    shadow = {'id':4, 'run_attempt':1, 'head_sha':SHA,'path':M.SHADOW,
              'event':'workflow_dispatch','status':'completed','conclusion':'success',
              'repository':{'full_name':'Mikeyphw/studyflow'}}
    legacy={'id':5, 'run_attempt':2, 'head_sha':SHA,'path':M.LEGACY,
            'event':'push','status':'completed','conclusion':'success',
            'repository':{'full_name':'Mikeyphw/studyflow'}}
    sj={'total_count':1,'jobs':[{'name':'native-source-shadow','run_id':4,'status':'completed','conclusion':'success'}]}
    lj={'total_count':5,'jobs':[
        {'name':'Actions integration source contract','run_id':5,'status':'completed','conclusion':'success'},
        {'name':'web','run_id':5,'status':'completed','conclusion':'success'},
        {'name':'desktop','run_id':5,'status':'completed','conclusion':'success'},
        {'name':'android (app)','run_id':5,'status':'completed','conclusion':'success'},
        {'name':'android (app-tv)','run_id':5,'status':'completed','conclusion':'success'},
    ]}
    return shadow,legacy,sj,lj


class PairTests(unittest.TestCase):
    def test_green_still_cannot_claim_equivalence(self):
        row=M.compare(*fixture())
        self.assertEqual(row['status'],'SOURCE_SHADOW_AND_LEGACY_FAST_GREEN')
        self.assertTrue(row['same_source_sha'])
        self.assertFalse(row['cutover_authorized'])
        self.assertFalse(row['gradle_native_equivalent'])
        self.assertFalse(row['native_source_tasks_equivalent'])
        self.assertFalse(row['release_qualified'])
        self.assertFalse(row['deep_profiles_qualified'])

    def test_legacy_source_failure_is_visible(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        lj['jobs'][0]['conclusion']='failure'
        l['conclusion']='failure'
        row=M.compare(s,l,sj,lj)
        self.assertEqual(row['status'],'INCOMPLETE_OR_FAILED')
        self.assertFalse(row['legacy_scopes']['source']['passed'])

    def test_legacy_job_skipping_cannot_greenlight(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        lj['jobs'][3]['conclusion']='skipped'
        row=M.compare(s,l,sj,lj)
        self.assertFalse(row['legacy_fast_passed'])

    def test_shadow_failure_cannot_greenlight(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        sj['jobs'][0]['conclusion']='failure'
        row=M.compare(s,l,sj,lj)
        self.assertFalse(row['native_source_shadow_passed'])

    def test_job_from_other_run_refused(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        lj['jobs'][0]['run_id']=912
        with self.assertRaisesRegex(M.ComparisonError,'another run'):
            M.compare(s,l,sj,lj)

    def test_sha_mismatch_refused(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        l['head_sha']='b'*40
        with self.assertRaisesRegex(M.ComparisonError,'Different commits'):
            M.compare(s,l,sj,lj)

    def test_malformed_sha_refused(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        s['head_sha']='main'
        with self.assertRaisesRegex(M.ComparisonError,'source commit missing'):
            M.compare(s,l,sj,lj)

    def test_workflow_or_repo_mismatch_refused(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        s['path']='.github/workflows/release.yml'
        with self.assertRaisesRegex(M.ComparisonError,'workflow identity mismatch'):
            M.compare(s,l,sj,lj)
        s,l,sj,lj=copy.deepcopy(fixture())
        s['repository']['full_name']='Other/studyflow'
        with self.assertRaisesRegex(M.ComparisonError,'repository mismatch'):
            M.compare(s,l,sj,lj)

    def test_nonterminal_refused(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        s['status']='in_progress'
        with self.assertRaisesRegex(M.ComparisonError,'not terminal'):
            M.compare(s,l,sj,lj)

    def test_jobs_pagination_refused(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        lj['total_count']=120
        with self.assertRaisesRegex(M.ComparisonError,'pagination'):
            M.compare(s,l,sj,lj)

    def test_missing_android_cell_refused_if_all_missing(self):
        s,l,sj,lj=copy.deepcopy(fixture())
        lj['jobs']=[j for j in lj['jobs'] if not j['name'].startswith('android')]
        lj['total_count']=3
        with self.assertRaisesRegex(M.ComparisonError,'job inventory missing'):
            M.compare(s,l,sj,lj)

    def test_manually_fake_preflight_can_only_read(self):
        s,l,sj,lj=fixture()
        base='/repos/Mikeyphw/studyflow/actions/runs/'
        fake={base+'4':s,base+'5':l,base+'4/jobs?per_page=100':sj,base+'5/jobs?per_page=100':lj}
        out=[]
        def reader(url):
            out.append(url)
            return fake[url]
        self.assertEqual(M.main(['--shadow-run','4','--legacy-run','5'],fetch=reader),0)
        self.assertEqual(len(out),4)
        self.assertTrue(all(x.startswith('/repos/') for x in out))

    def test_invalid_argument_refused_before_network(self):
        def reader(url):
            raise AssertionError('must not access network')
        self.assertEqual(M.main(['--shadow-run','0','--legacy-run','5'],fetch=reader),2)
        self.assertEqual(M.main(['--repo','a/../b','--shadow-run','4','--legacy-run','5'],fetch=reader),2)

if __name__=='__main__':
    unittest.main(verbosity=2)
