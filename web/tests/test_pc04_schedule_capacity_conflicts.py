from pathlib import Path
import subprocess, textwrap, unittest
ROOT=Path(__file__).resolve().parents[1]

class PC04ScheduleCapacityConflicts(unittest.TestCase):
    def run_node(self, source):
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=ROOT,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_capacity_keeps_overflow_explicitly_unscheduled(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict';
            import { createInitialStudyState } from './runtime/domain.js'; import { createStudyStore } from './runtime/state.js';
            const now=()=> '2026-09-07T12:00:00.000Z'; const base=createInitialStudyState({now:now()}); base.activities=[]; base.reviews=[];
            const repo={load:()=>({status:'loaded',state:base}),save:()=>({status:'saved'}),clear:()=>({status:'cleared'})}; const store=createStudyStore({repository:repo,now});
            store.setScheduleConfiguration({dailyCapacityMinutes:60,availability:[],fixedBlocks:[]}); store.addActivity({title:'A',kind:'study',durationMinutes:40,queue:'backlog',scheduledFor:null}); store.addActivity({title:'B',kind:'study',durationMinutes:40,queue:'backlog',scheduledFor:null});
            const before=JSON.stringify(store.getState()); const preview=store.previewSchedule({days:1,maxBlocks:10}); assert.equal(JSON.stringify(store.getState()),before); assert.equal(preview.summary.move,1); assert.ok(preview.summary.unscheduled>=1); assert.ok(preview.unscheduled.some(x=>x.title==='B'||x.title==='A')); assert.equal(preview.days[0].remainingMinutes,20);
        '''))

    def test_availability_fixed_block_and_lock_are_respected(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict'; import { createInitialStudyState } from './runtime/domain.js'; import { createStudyStore } from './runtime/state.js';
            const now=()=> '2026-09-07T12:00:00.000Z'; const base=createInitialStudyState({now:now()}); base.activities=[]; base.reviews=[]; const repo={load:()=>({status:'loaded',state:base}),save:()=>({status:'saved'}),clear:()=>({status:'cleared'})}; const store=createStudyStore({repository:repo,now});
            store.setScheduleConfiguration({dailyCapacityMinutes:120,availability:[{id:'monday',weekday:1,startMinute:1080,endMinute:1200}],fixedBlocks:[{id:'class',title:'Class',date:'2026-09-07',weekday:null,startMinute:1110,durationMinutes:30}]});
            const free=store.addActivity({title:'Fit me',kind:'study',durationMinutes:60,queue:'backlog',scheduledFor:null}); const locked=store.addActivity({title:'Locked missed',kind:'study',durationMinutes:30,scheduledFor:'2026-09-06',locked:true});
            const preview=store.previewSchedule({days:1,maxBlocks:10}); const move=preview.operations.find(x=>x.activityId===free.id); assert.ok(move); assert.equal(move.toStartMinute,1140); assert.ok(preview.conflicts.some(c=>c.kind==='locked-overdue'&&c.activityId===locked.id)); assert.equal(preview.operations.some(x=>x.activityId===locked.id),false);
        '''))

    def test_apply_preserves_completed_history_and_records_provenance(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict'; import { createInitialStudyState, createActivity } from './runtime/domain.js'; import { createStudyStore } from './runtime/state.js';
            const now=()=> '2026-09-07T12:00:00.000Z'; const base=createInitialStudyState({now:now()}); base.activities=[]; base.reviews=[]; base.activities.push(createActivity({id:'done',planId:base.activePlanId,title:'Historical',kind:'study',durationMinutes:25,status:'completed',scheduledFor:'2026-09-01',createdAt:now(),updatedAt:now()})); const repo={load:()=>({status:'loaded',state:base}),save:()=>({status:'saved'}),clear:()=>({status:'cleared'})}; const store=createStudyStore({repository:repo,now}); store.setScheduleConfiguration({dailyCapacityMinutes:90}); const a=store.addActivity({title:'Backlog',kind:'study',durationMinutes:40,scheduledFor:null}); const preview=store.previewSchedule({days:2,maxBlocks:3}); store.applySchedulePreview(preview); const after=store.getState(); assert.equal(after.activities.find(x=>x.id==='done').scheduledFor,'2026-09-01'); const moved=after.activities.find(x=>x.id===a.id); assert.ok(moved.scheduledFor); assert.equal(moved.scheduleProvenance.source,'adaptive'); assert.equal(moved.scheduleProvenance.baseRevision,preview.baseRevision); assert.ok(moved.scheduleProvenance.reason);
        '''))

    def test_locked_activity_cannot_be_moved_by_forged_preview(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict'; import { createInitialStudyState } from './runtime/domain.js'; import { createStudyStore } from './runtime/state.js';
            const now=()=> '2026-09-07T12:00:00.000Z'; const base=createInitialStudyState({now:now()}); base.activities=[]; const repo={load:()=>({status:'loaded',state:base}),save:()=>({status:'saved'}),clear:()=>({status:'cleared'})}; const store=createStudyStore({repository:repo,now}); const a=store.addActivity({title:'Locked',kind:'study',durationMinutes:25,scheduledFor:'2026-09-07'}); store.setActivityLocked(a.id,true); const revision=store.getState().revision; let ok=false; try{store.applySchedulePreview({baseRevision:revision,generatedAt:now(),operations:[{action:'move',activityId:a.id,from:'2026-09-07',to:'2026-09-08',reason:'forged'}],summary:{add:0,move:1,conflicts:0,overloadedDays:0,unscheduled:0},reasons:[],conflicts:[],days:[],unscheduled:[]})}catch(e){ok=String(e).includes('locked')} assert.equal(ok,true); assert.equal(store.getState().activities.find(x=>x.id===a.id).scheduledFor,'2026-09-07');
        '''))

if __name__=='__main__': unittest.main()
