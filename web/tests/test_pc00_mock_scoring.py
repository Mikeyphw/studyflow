from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

def node(source:str):
    p=subprocess.run(["node","--input-type=module","-e",textwrap.dedent(source)],cwd=WEB,text=True,capture_output=True)
    if p.returncode: raise AssertionError(p.stderr or p.stdout)

class Pc00MockScoringTest(unittest.TestCase):
    def test_question_points_are_not_equal_weighted(self):
        node('''
        import assert from "node:assert/strict"; import { createInitialStudyState,createQuestion } from "./runtime/domain.js"; import { scoreMockExam } from "./runtime/modes.js";
        const state=createInitialStudyState({now:"2026-09-07T10:00:00.000Z"}); const plan=state.activePlanId;
        state.questions=[createQuestion({id:"q-heavy",planId:plan,prompt:"Heavy",answer:"yes",points:10}),createQuestion({id:"q-light",planId:plan,prompt:"Light",answer:"yes",points:1})];
        const preview={baseRevision:state.revision,generatedAt:"2026-09-07T10:00:00.000Z",blueprint:{scoring:"weighted",negativePoints:0,totalQuestions:2,durationMinutes:60,sections:[]},questionIds:["q-heavy","q-light"],deficits:[],ready:true};
        const score=scoreMockExam(preview,{"q-heavy":"yes","q-light":"no"},state); assert.equal(score.rawScore,10); assert.equal(score.maximumScore,11); assert.equal(score.percent,90.91); assert.equal(score.questions[0].maximumPoints,10); assert.equal(score.questions[1].maximumPoints,1);
        ''')

    def test_section_weights_and_question_points_combine_deterministically(self):
        node('''
        import assert from "node:assert/strict"; import { createInitialStudyState,createQuestion } from "./runtime/domain.js"; import { scoreMockExam } from "./runtime/modes.js";
        const state=createInitialStudyState({now:"2026-09-07T10:00:00.000Z"}); const plan=state.activePlanId;
        state.questions=[createQuestion({id:"a1",planId:plan,subjectId:"subject-constitutional",prompt:"A1",answer:"yes",points:3}),createQuestion({id:"a2",planId:plan,subjectId:"subject-constitutional",prompt:"A2",answer:"yes",points:1}),createQuestion({id:"b1",planId:plan,subjectId:"subject-portuguese",prompt:"B1",answer:"yes",points:1})];
        const preview={baseRevision:state.revision,generatedAt:"x",blueprint:{scoring:"weighted",negativePoints:0,totalQuestions:3,durationMinutes:60,sections:[{id:"heavy",subjectId:"subject-constitutional",count:2,weight:4},{id:"light",subjectId:"subject-portuguese",count:1,weight:1}]},questionIds:["a1","a2","b1"],deficits:[],ready:true};
        const score=scoreMockExam(preview,{a1:"yes",a2:"no",b1:"no"},state); assert.equal(score.maximumScore,5); assert.equal(score.rawScore,3); assert.equal(score.percent,60); assert.equal(score.sections[0].maximumPoints,4); assert.equal(score.sections[0].percent,75); assert.equal(score.sections[1].percent,0);
        ''')

    def test_penalty_modes_are_value_aware_and_blanks_neutral(self):
        node('''
        import assert from "node:assert/strict"; import { createInitialStudyState,createQuestion } from "./runtime/domain.js"; import { scoreMockExam } from "./runtime/modes.js";
        const state=createInitialStudyState({now:"2026-09-07T10:00:00.000Z"}); const plan=state.activePlanId; state.questions=[createQuestion({id:"h",planId:plan,prompt:"H",answer:"yes",points:4}),createQuestion({id:"l",planId:plan,prompt:"L",answer:"yes",points:1})];
        const base={baseRevision:0,generatedAt:"x",questionIds:["h","l"],deficits:[],ready:true}; const bp={negativePoints:.5,totalQuestions:2,durationMinutes:60,sections:[]};
        let s=scoreMockExam({...base,blueprint:{...bp,scoring:"fixed-negative"}},{h:"yes",l:"no"},state); assert.equal(s.rawScore,3.5); assert.equal(s.percent,70);
        s=scoreMockExam({...base,blueprint:{...bp,scoring:"right-minus-wrong"}},{h:"yes",l:"no"},state); assert.equal(s.rawScore,3); assert.equal(s.percent,60);
        s=scoreMockExam({...base,blueprint:{...bp,scoring:"fixed-negative"}},{h:"yes"},state); assert.equal(s.rawScore,4); assert.equal(s.percent,80); assert.equal(s.unanswered,1);
        ''')

    def test_recorded_mock_keeps_breakdown_and_cutoff_uses_correct_percent(self):
        node('''
        import assert from "node:assert/strict"; import { createBrowserRepository } from "./runtime/storage.js"; import { createStudyStore } from "./runtime/state.js"; import { previewMockExam,derivePublicExamDashboard } from "./runtime/modes.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=>"2026-09-07T10:00:00.000Z"; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.updatePublicExamTarget({organization:"Org",role:"Role",board:"Board",estimatedCutoff:80});
        const existing=store.getState().questions[0]; store.replaceState({...store.getState(),questions:[{...existing,id:"p10",points:10},{...existing,id:"p1",points:1}]}); store.setMockBlueprint({scoring:"weighted",totalQuestions:2,sections:[]}); const preview=previewMockExam(store.getState()); const first=store.getState().questions.find(q=>q.id===preview.questionIds[0]); const ev=store.recordMockExam(preview,{[preview.questionIds[0]]:first.answer}); const v=ev.value; assert.equal(v.percent,90.91); assert.equal(v.maximumScore,11); assert.equal(v.questions.length,2); assert.equal(v.sections.length,1); assert.equal(derivePublicExamDashboard(store.getState()).cutoffGap,10.91);
        ''')

    def test_mock_preview_excludes_ungraded_questions(self):
        node('''
        import assert from "node:assert/strict"; import { createInitialStudyState,createQuestion } from "./runtime/domain.js"; import { previewMockExam } from "./runtime/modes.js";
        const state=createInitialStudyState({now:"2026-09-07T10:00:00.000Z"}); state.questions=[createQuestion({id:"graded",planId:state.activePlanId,prompt:"G",answer:"yes"}),createQuestion({id:"ungraded",planId:state.activePlanId,prompt:"U"})]; state.plans[0].configuration.publicExam.mockBlueprint={scoring:"weighted",negativePoints:0,totalQuestions:2,durationMinutes:60,sections:[]}; const p=previewMockExam(state); assert.equal(p.ready,false); assert.deepEqual(p.questionIds,["graded"]); assert.match(p.deficits[0],/need 2, have 1/);
        ''')

if __name__=="__main__": unittest.main()
