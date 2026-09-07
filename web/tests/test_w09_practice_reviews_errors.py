from __future__ import annotations
import subprocess, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class W09PracticeReviewsErrorsTest(unittest.TestCase):
    def run_node(self, source:str):
        r=subprocess.run(["node","--input-type=module","-e",source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)
    def test_wrong_answer_creates_evidence_review_and_error_without_duplicate_review_storm(self):
        self.run_node(r'''
import assert from "node:assert/strict"; import { createBrowserRepository } from "./src/storage.js"; import { createStudyStore } from "./src/state.js";
const storage={getItem(){return null},setItem(){},removeItem(){}}; let clock="2026-09-07T13:00:00.000Z"; const now=()=>clock; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const q=store.getState().questions[0];
const first=store.answerQuestion(q.id,"header",{errorCategory:"concept-confusion"}); assert.equal(first.result.correct,false); assert.equal(store.getState().evidence.at(-1).kind,"question-attempt"); assert.ok(first.review); assert.ok(first.errorEntry); const dueForQuestion=()=>store.getState().reviews.filter(r=>r.status==="due"&&r.sourceType==="question"&&r.sourceId===q.id).length; assert.equal(dueForQuestion(),1);
clock="2026-09-07T13:05:00.000Z"; const second=store.answerQuestion(q.id,"aside",{errorCategory:"concept-confusion"}); assert.equal(dueForQuestion(),1); assert.equal(second.errorEntry.recurrenceCount,2);
''')
    def test_correct_and_ungraded_attempts_do_not_invent_error_evidence(self):
        self.run_node(r'''
import assert from "node:assert/strict"; import { createBrowserRepository } from "./src/storage.js"; import { createStudyStore } from "./src/state.js";
const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=>"2026-09-07T14:00:00.000Z"; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const q=store.getState().questions[0]; const before=store.getState().errorEntries.length; const ok=store.answerQuestion(q.id,"main"); assert.equal(ok.result.correct,true); assert.equal(store.getState().errorEntries.length,before); const u=store.addQuestion({subjectId:q.subjectId,prompt:"Essay response",choices:[],answer:null}); const result=store.answerQuestion(u.id,"my answer"); assert.equal(result.result.gradable,false); assert.equal(result.result.correct,null); assert.equal(store.getState().errorEntries.length,before);
''')
    def test_review_completion_and_error_resolution_are_persisted(self):
        self.run_node(r'''
import assert from "node:assert/strict"; import { createBrowserRepository } from "./src/storage.js"; import { createStudyStore } from "./src/state.js";
const values=new Map(); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)}; const now=()=>"2026-09-07T15:00:00.000Z"; const repo=createBrowserRepository({storage,now}); const store=createStudyStore({repository:repo,now}); const q=store.getState().questions[0]; const wrong=store.answerQuestion(q.id,"header",{errorCategory:"interpretation"}); store.completeReview(wrong.review.id,{rating:"again"}); store.resolveError(wrong.errorEntry.id); const reload=createStudyStore({repository:createBrowserRepository({storage,now}),now}); assert.equal(reload.getState().reviews.find(r=>r.id===wrong.review.id).status,"completed"); assert.equal(reload.getState().errorEntries.find(e=>e.id===wrong.errorEntry.id).status,"resolved"); assert.ok(reload.getState().evidence.some(e=>e.kind==="review-completed"));
''')
    def test_v2_storage_migrates_to_v3_with_error_notebook_collection(self):
        self.run_node(r'''
import assert from "node:assert/strict"; import { createInitialStudyState } from "./src/domain.js"; import { createBrowserRepository, STORAGE_KEY } from "./src/storage.js"; import { createStudyStore } from "./src/state.js";
const legacy=createInitialStudyState({now:"2026-09-07T09:00:00.000Z"}); legacy.schemaVersion=2; delete legacy.errorEntries; const values=new Map([["studyflow.local.state.v2",JSON.stringify(legacy)]]); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)}; const now=()=>"2026-09-07T09:05:00.000Z"; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); assert.equal(store.getState().schemaVersion,3); assert.deepEqual(store.getState().errorEntries,[]); assert.ok(values.has(STORAGE_KEY));
''')
    def test_practice_ui_is_domain_backed(self):
        html=(WEB/'index.html').read_text(); state=(WEB/'src/state.js').read_text();
        for t in ('id="practice-question-select"','id="practice-review-list"','id="practice-error-list"','id="question-dialog"'): self.assertIn(t,html)
        for t in ('answerQuestion','completeReview','resolveError','addQuestion'): self.assertIn(t,state)
if __name__=='__main__': unittest.main()
