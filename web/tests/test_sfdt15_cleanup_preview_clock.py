"""Use the preview's recorded clock when recomputing review-only cleanup findings."""
from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class CleanupPreviewClock(unittest.TestCase):
 def test_review_only_and_fail_closed_guards(self):
  source=textwrap.dedent(r'''
    import assert from 'node:assert/strict';
    import {createStudyStore} from './runtime/state.js';
    import {buildContentCleanupPreview} from './runtime/intelligence.js';
    let now='2026-09-07T12:00:00Z';
    const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}};
    const store=createStudyStore({repository:repo,now:()=>now});
    store.createPlan({name:'Review-only',mode:'GENERAL_SKILL'},{activate:true});
    const material=store.addMaterial({title:'Archive',kind:'note'});
    const old=store.getState();
    store.replaceState({...old,materials:old.materials.map(m=>m.id===material.id?{...m,archived:true,updatedAt:'2026-05-01T00:00:00Z'}:m)});
    const preview=buildContentCleanupPreview(store.getState(),new Date(now));
    assert.equal(preview.summary['stale-resource'],1);
    const before=JSON.stringify(store.getState());
    now='2026-11-06T12:00:00Z'; // time drift must not invent a changed preview
    assert.throws(()=>store.applyContentCleanupPreview(preview),/review-only/);
    assert.equal(JSON.stringify(store.getState()),before);
    const forged={...preview,summary:{...preview.summary,'stale-resource':999}};
    assert.throws(()=>store.applyContentCleanupPreview(forged),/changed/);
    const invalid={...preview,generatedAt:'not-a-date'};
    assert.throws(()=>store.applyContentCleanupPreview(invalid),/timestamp invalid/);
    store.addMaterial({title:'new',kind:'note'});
    assert.throws(()=>store.applyContentCleanupPreview(preview),/stale/);
  ''')
  p=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,capture_output=True,text=True,check=False)
  self.assertEqual(p.returncode,0,p.stderr or p.stdout)
if __name__=='__main__':unittest.main(verbosity=2)
