from __future__ import annotations
import subprocess, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class W08ScheduleIntelligenceTest(unittest.TestCase):
    def run_node(self, source:str):
        r=subprocess.run(["node","--input-type=module","-e",source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)
    def test_preview_is_pure_and_apply_is_explicit_and_revision_guarded(self):
        self.run_node(r'''
import assert from "node:assert/strict";
import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js";
const values=new Map(); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)}; const now=()=>"2026-09-07T10:00:00.000Z";
const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const before=JSON.stringify(store.getState()); const preview=store.previewSchedule({at:now(),maxBlocks:4});
assert.equal(JSON.stringify(store.getState()),before); assert.equal(preview.baseRevision,store.getState().revision); assert.ok(preview.operations.length>0); store.applySchedulePreview(preview); assert.ok(store.getState().revision>preview.baseRevision); assert.throws(()=>store.applySchedulePreview(preview),/stale/);
''')
    def test_overdue_planned_blocks_are_moved_without_rewriting_history(self):
        self.run_node(r'''
import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js";
const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=>"2026-09-07T12:00:00.000Z"; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
const a=store.addActivity({title:"Old planned block",kind:"study",durationMinutes:25,queue:"future",scheduledFor:"2026-09-01"}); const sessions=JSON.stringify(store.getState().sessions), evidence=JSON.stringify(store.getState().evidence); const p=store.previewSchedule({at:now(),maxBlocks:2}); const move=p.operations.find(op=>op.action==="move"&&op.activityId===a.id); assert.ok(move); store.applySchedulePreview(p); assert.equal(JSON.stringify(store.getState().sessions),sessions); assert.equal(JSON.stringify(store.getState().evidence),evidence);
''')
    def test_schedule_ui_is_preview_first(self):
        ui=(WEB/"src/react/App.tsx").read_text()
        for token in ("Adaptive schedule", "Preview", "Apply preview", "store.previewSchedule", "store.applySchedulePreview"):
            self.assertIn(token,ui)

if __name__=='__main__': unittest.main()
