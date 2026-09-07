from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

def node(source:str):
    p=subprocess.run(["node","--input-type=module","-e",textwrap.dedent(source)],cwd=WEB,text=True,capture_output=True)
    if p.returncode: raise AssertionError(p.stderr or p.stdout)

class Pc03PlanLifecycleHierarchyTest(unittest.TestCase):
    def test_v5_state_migrates_to_v6_with_parent_links_and_per_plan_cycles(self):
        node('''
        import assert from "node:assert/strict"; import {createInitialStudyState,migrateStudyState,validateStudyState} from "./runtime/domain.js";
        const current=createInitialStudyState({now:"2026-09-07T04:00:00.000Z"});const legacy=JSON.parse(JSON.stringify(current));legacy.schemaVersion=5;delete legacy.studyCycles;for(const topic of legacy.topics)delete topic.parentTopicId;const migrated=migrateStudyState(legacy,{now:"2026-09-07T05:00:00.000Z"});assert.equal(migrated.schemaVersion,7);assert.ok(migrated.studyCycles[migrated.activePlanId]);assert.deepEqual(migrated.studyCycles[migrated.activePlanId],migrated.studyCycle);assert.equal(migrated.topics.every(x=>x.parentTopicId===null),true);assert.equal(validateStudyState(migrated).length,0);
        ''')

    def test_create_rename_switch_archive_restore_and_focus_switch_guard(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>"2026-09-07T04:00:00.000Z"});const original=store.getState().activePlanId;const second=store.createPlan({name:"Second",mode:"READING_STUDY"},{activate:true});assert.equal(store.getState().activePlanId,second.id);store.renamePlan(second.id,"Reading plan");assert.equal(store.getState().plans.find(x=>x.id===second.id).name,"Reading plan");store.addActivity({title:"Focus",kind:"study",durationMinutes:25});store.startFocus();assert.throws(()=>store.switchPlan(original),/focus/i);store.cancelFocus();store.switchPlan(original);store.setPlanArchived(second.id,true);assert.equal(store.getState().plans.find(x=>x.id===second.id).status,"archived");store.setPlanArchived(second.id,false);assert.equal(store.getState().plans.find(x=>x.id===second.id).status,"active");
        ''')

    def test_each_plan_restores_its_own_study_cycle_cursor(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const original=store.getState().activePlanId;store.setStudyCycle(["subject-constitutional-law","subject-portuguese"]);store.advanceStudyCycle();assert.equal(store.getState().studyCycle.cursor,1);const second=store.createPlan({name:"Second"},{activate:true});const a=store.addSubject({name:"A"}),b=store.addSubject({name:"B"});store.setStudyCycle([a.id,b.id]);assert.equal(store.getState().studyCycle.cursor,0);store.switchPlan(original);assert.equal(store.getState().studyCycle.cursor,1);store.switchPlan(second.id);assert.equal(store.getState().studyCycle.cursor,0);assert.deepEqual(store.getState().studyCycles[original].subjectIds,["subject-constitutional-law","subject-portuguese"]);
        ''')

    def test_nested_topics_reject_cross_subject_parent_self_parent_and_cycles(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js"; import {validateStudyState} from "./runtime/domain.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const a=store.addSubject({name:"A"}),b=store.addSubject({name:"B"});const parent=store.addTopic({subjectId:a.id,name:"Parent"});const child=store.addTopic({subjectId:a.id,parentTopicId:parent.id,name:"Child"});assert.equal(child.parentTopicId,parent.id);assert.throws(()=>store.addTopic({subjectId:b.id,parentTopicId:parent.id,name:"Wrong"}));assert.throws(()=>store.updateTopic(parent.id,{parentTopicId:parent.id}));assert.throws(()=>store.updateTopic(parent.id,{parentTopicId:child.id}),/cycle/i);assert.equal(validateStudyState(store.getState()).length,0);
        ''')

    def test_topic_delete_requires_children_to_be_removed_and_reorder_is_sibling_local(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const s=store.addSubject({name:"Hierarchy"});const p1=store.addTopic({subjectId:s.id,name:"P1"}),p2=store.addTopic({subjectId:s.id,name:"P2"}),c1=store.addTopic({subjectId:s.id,parentTopicId:p1.id,name:"C1"}),c2=store.addTopic({subjectId:s.id,parentTopicId:p1.id,name:"C2"});assert.throws(()=>store.deleteTopic(p1.id),/child/i);store.moveTopic(c2.id,"up");const state=store.getState();assert.equal(state.topics.find(x=>x.id===c2.id).order,0);assert.equal(state.topics.find(x=>x.id===c1.id).order,1);assert.equal(state.topics.find(x=>x.id===p2.id).order,1);
        ''')

    def test_duplicate_plan_copies_structure_questions_goals_and_planned_work_not_history(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        let clock="2026-09-07T04:00:00.000Z";const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>clock});const subject=store.addSubject({name:"Clone me"});const parent=store.addTopic({subjectId:subject.id,name:"Parent"});store.addTopic({subjectId:subject.id,parentTopicId:parent.id,name:"Child"});store.addQuestion({subjectId:subject.id,topicId:parent.id,prompt:"Q?",answer:"A"});store.addGoal({name:"Goal",metric:"study-minutes",target:10});const activity=store.addActivity({subjectId:subject.id,topicId:parent.id,title:"Planned",kind:"study",durationMinutes:25});store.startFocus({activityId:activity.id});clock="2026-09-07T04:25:00.000Z";store.completeFocus();const source=store.getState().activePlanId;const copy=store.duplicatePlan(source,{name:"Copy"});const state=store.getState();assert.ok(state.subjects.some(x=>x.planId===copy.id&&x.name==="Clone me"));assert.ok(state.topics.some(x=>x.planId===copy.id&&x.name==="Child"));assert.ok(state.questions.some(x=>x.planId===copy.id&&x.prompt==="Q?"));assert.ok(state.goals.some(x=>x.planId===copy.id&&x.name==="Goal"));assert.equal(state.sessions.some(x=>x.planId===copy.id),false);assert.equal(state.evidence.some(x=>x.planId===copy.id),false);assert.equal(state.reviews.some(x=>x.planId===copy.id),false);assert.equal(state.errorEntries.some(x=>x.planId===copy.id),false);
        ''')

    def test_archive_active_plan_switches_to_another_and_last_active_plan_cannot_be_archived(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const first=store.getState().activePlanId;const second=store.createPlan({name:"Second"},{activate:true});store.setPlanArchived(second.id,true);assert.equal(store.getState().activePlanId,first);assert.equal(store.getState().plans.find(x=>x.id===second.id).status,"archived");assert.throws(()=>store.setPlanArchived(first,true),/only active plan/i);
        ''')

    def test_safe_delete_only_allows_empty_archived_inactive_plan(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const empty=store.createPlan({name:"Disposable"},{activate:false});assert.throws(()=>store.deletePlan(empty.id),/archive/i);store.setPlanArchived(empty.id,true);store.deletePlan(empty.id);assert.equal(store.getState().plans.some(x=>x.id===empty.id),false);const owned=store.createPlan({name:"Owned"},{activate:true});store.addSubject({name:"Content"});store.switchPlan("plan-local-default");store.setPlanArchived(owned.id,true);assert.throws(()=>store.deletePlan(owned.id),/owns content/i);
        ''')

    def test_plan_ui_exposes_lifecycle_import_and_nested_topic_controls(self):
        ui=(WEB/"src/react/App.tsx").read_text(encoding="utf-8")
        for token in ("Plan workspace","Create plan","Duplicate","Archived plans","Import Plan Content","Preview import","Apply import preview","Import as new plan","Parent topic","TopicTree"):
            self.assertIn(token,ui)

if __name__=="__main__": unittest.main()
