from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

def node(source:str):
    p=subprocess.run(["node","--input-type=module","-e",textwrap.dedent(source)],cwd=WEB,text=True,capture_output=True)
    if p.returncode: raise AssertionError(p.stderr or p.stdout)

class Pc01ReviewProgressionTest(unittest.TestCase):
    def test_default_1_7_30_progression_and_final_interval_repeat(self):
        node('''
        import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js";
        let clock="2026-09-07T09:00:00.000Z"; const now=()=>clock; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const q=store.getState().questions[0]; const wrong=store.answerQuestion(q.id,"definitely wrong"); assert.equal(wrong.review.intervalIndex,0); assert.equal(wrong.review.intervalDays,1); assert.equal(wrong.review.dueAt,"2026-09-08T09:00:00.000Z");
        clock="2026-09-08T09:00:00.000Z"; store.completeReview(wrong.review.id,{rating:3}); let due=store.getState().reviews.filter(r=>r.status==="due"&&r.sourceId===q.id); assert.equal(due.length,1); assert.equal(due[0].intervalIndex,1); assert.equal(due[0].intervalDays,7); assert.equal(due[0].dueAt,"2026-09-15T09:00:00.000Z");
        clock="2026-09-15T09:00:00.000Z"; store.completeReview(due[0].id,{rating:5}); due=store.getState().reviews.filter(r=>r.status==="due"&&r.sourceId===q.id); assert.equal(due[0].intervalIndex,2); assert.equal(due[0].intervalDays,30); assert.equal(due[0].dueAt,"2026-10-15T09:00:00.000Z");
        clock="2026-10-15T09:00:00.000Z"; store.completeReview(due[0].id,{rating:5}); due=store.getState().reviews.filter(r=>r.status==="due"&&r.sourceId===q.id); assert.equal(due[0].intervalIndex,2); assert.equal(due[0].intervalDays,30);
        ''')

    def test_failure_resets_to_first_interval(self):
        node('''
        import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js";
        let clock="2026-09-07T09:00:00.000Z"; const now=()=>clock; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const q=store.getState().questions[0]; let r=store.answerQuestion(q.id,"wrong").review; clock="2026-09-08T09:00:00.000Z"; store.completeReview(r.id,{rating:3}); r=store.getState().reviews.find(x=>x.status==="due"&&x.sourceId===q.id); assert.equal(r.intervalIndex,1); clock="2026-09-15T09:00:00.000Z"; store.completeReview(r.id,{rating:1}); r=store.getState().reviews.find(x=>x.status==="due"&&x.sourceId===q.id); assert.equal(r.intervalIndex,0); assert.equal(r.intervalDays,1); assert.equal(r.dueAt,"2026-09-16T09:00:00.000Z");
        ''')

    def test_custom_policy_is_authoritative_and_persists(self):
        node('''
        import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js";
        const values=new Map(); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)}; let clock="2026-09-07T09:00:00.000Z"; const now=()=>clock; let store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setReviewIntervalsDays([3,9,21]); const q=store.getState().questions[0]; let r=store.answerQuestion(q.id,"wrong").review; assert.equal(r.intervalDays,3); assert.equal(r.dueAt,"2026-09-10T09:00:00.000Z"); clock="2026-09-10T09:00:00.000Z"; store.completeReview(r.id,{rating:"good"}); r=store.getState().reviews.find(x=>x.status==="due"&&x.sourceId===q.id); assert.equal(r.intervalDays,9); assert.equal(r.dueAt,"2026-09-19T09:00:00.000Z"); store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); assert.deepEqual(store.getState().plans[0].configuration.publicExam.reviewIntervalsDays,[3,9,21]); assert.equal(store.getState().reviews.find(x=>x.id===r.id).intervalDays,9);
        ''')

    def test_review_completion_keeps_one_due_successor_and_moves_error_link(self):
        node('''
        import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js";
        let clock="2026-09-07T09:00:00.000Z"; const now=()=>clock; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const q=store.getState().questions[0]; const first=store.answerQuestion(q.id,"wrong",{errorCategory:"interpretation"}); const oldId=first.review.id; clock="2026-09-08T09:00:00.000Z"; store.completeReview(oldId,{rating:3}); const due=store.getState().reviews.filter(r=>r.status==="due"&&r.sourceId===q.id); assert.equal(due.length,1); assert.equal(store.getState().errorEntries.find(e=>e.id===first.errorEntry.id).reviewId,due[0].id); store.answerQuestion(q.id,"still wrong",{errorCategory:"interpretation"}); assert.equal(store.getState().reviews.filter(r=>r.status==="due"&&r.sourceId===q.id).length,1); const ev=store.getState().evidence.find(e=>e.kind==="review-completed"); assert.equal(ev.value.nextReviewId,due[0].id); assert.deepEqual(ev.value.policyIntervalsDays,[1,7,30]);
        ''')

    def test_legacy_v5_reviews_normalize_without_schema_bump(self):
        node('''
        import assert from "node:assert/strict"; import { createInitialStudyState,migrateStudyState,validateStudyState } from "./runtime/domain.js";
        const state=createInitialStudyState({now:"2026-09-07T09:00:00.000Z"}); const legacy={...state,reviews:state.reviews.map(({intervalIndex,intervalDays,repetitionCount,previousReviewId,completedAt,...r})=>r)}; const migrated=migrateStudyState(legacy,{now:"2026-09-07T10:00:00.000Z"}); assert.equal(migrated.schemaVersion,6); assert.equal(migrated.reviews[0].intervalIndex,0); assert.equal(migrated.reviews[0].intervalDays,null); assert.equal(migrated.reviews[0].repetitionCount,0); assert.equal(validateStudyState(migrated).length,0);
        ''')

    def test_invalid_review_policy_is_rejected(self):
        node('''
        import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js"; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>"2026-09-07T09:00:00.000Z"}); assert.throws(()=>store.setReviewIntervalsDays([7,1])); assert.throws(()=>store.setReviewIntervalsDays([])); assert.throws(()=>store.setReviewIntervalsDays([0,7])); store.setReviewIntervalsDays([1,1,7]); assert.deepEqual(store.getState().plans[0].configuration.publicExam.reviewIntervalsDays,[1,1,7]);
        ''')

if __name__=="__main__": unittest.main()
