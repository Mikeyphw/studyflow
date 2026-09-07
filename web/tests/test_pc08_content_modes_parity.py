from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class PC08ContentModesParityTest(unittest.TestCase):
    def run_node(self,source:str):
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)
    def test_programming_attempts_are_manual_output_not_code_execution(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {assessProgrammingAttempt} from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); store.setPlanMode('PROGRAMMING_COURSE'); store.addProgrammingExercise({title:'Hello',prompt:'Print hi',expectedOutput:'hi',starterCode:'console.log("hi")',files:[{path:'main.ts',language:'TypeScript',content:'console.log("hi")'}]}); const ex=store.getState().plans.find(p=>p.id===store.getState().activePlanId).configuration.programming.exercises.at(-1); assert.equal(assessProgrammingAttempt(ex,'hi').matchesExpected,true); const ev=store.recordProgrammingExercise(ex.id,{observedOutput:'no'}); assert.equal(ev.value.executionMode,'manual-output'); assert.equal(ev.value.matchesExpected,false); assert.equal(store.getState().evidence.some(e=>e.kind==='programming-exercise'),true);
        '''))
    def test_solution_reveal_and_project_checkpoint_are_evidence_safe(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); store.setPlanMode('PROGRAMMING_COURSE'); store.addProgrammingExercise({title:'X',prompt:'Y',solutionNotes:'Use a loop'}); const plan=store.getState().plans.find(p=>p.id===store.getState().activePlanId),ex=plan.configuration.programming.exercises.at(-1); store.revealProgrammingSolution(ex.id); assert.equal(store.getState().evidence.at(-1).kind,'programming-solution-revealed');
        '''))
    def test_programming_and_language_import_preview_are_non_mutating_and_duplicate_safe(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); store.setPlanMode('PROGRAMMING_COURSE'); const before=JSON.stringify(store.getState()); const p=store.previewModeImport('title,prompt,expected_output\nFizzBuzz,Print values,1\\n2'); assert.equal(JSON.stringify(store.getState()),before); assert.equal(p.summary.add,1); store.applyModeImportPreview(p); assert.ok(store.getState().plans.find(x=>x.id===store.getState().activePlanId).configuration.programming.exercises.some(x=>x.title==='FizzBuzz')); const dup=store.previewModeImport('title,prompt\nFizzBuzz,Again'); assert.equal(dup.summary.duplicate,1);
            store.setPlanMode('LANGUAGE_DECK'); const l=store.previewModeImport('expression,meaning,example_sentence,common_mistakes\nbreak a leg,good luck,I said break a leg,break the leg'); assert.equal(l.summary.add,1); store.applyModeImportPreview(l); assert.ok(store.getState().plans.find(x=>x.id===store.getState().activePlanId).configuration.language.expressions.some(x=>x.expression==='break a leg'));
        '''))
    def test_all_six_language_directions_have_truthful_prompt_contracts(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {buildLanguageReviewPrompt} from './runtime/modes.js'; const x={id:'x',expression:'break a leg',meaning:'good luck',literalTranslation:null,region:null,register:'informal',pronunciationNote:null,commonMistakes:['break the leg'],exampleSentence:'Before the audition I said break a leg.'}; const dirs=['recognition','production','fill-blank','natural-usage','mistake-spotting','sentence-creation']; for(const d of dirs){const p=buildLanguageReviewPrompt(x,d);assert.equal(p.ready,true)} assert.equal(buildLanguageReviewPrompt(x,'sentence-creation').manualReview,true); const y={...x,exampleSentence:null,commonMistakes:[]}; assert.equal(buildLanguageReviewPrompt(y,'fill-blank').ready,false); assert.equal(buildLanguageReviewPrompt(y,'mistake-spotting').ready,false);
        '''))
    def test_language_reviews_do_not_create_duplicate_due_review_storms(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); store.setPlanMode('LANGUAGE_DECK'); store.addLanguageExpression({expression:'hola',meaning:'hello'}); const plan=store.getState().plans.find(p=>p.id===store.getState().activePlanId),x=plan.configuration.language.expressions.at(-1); store.recordLanguageReview(x.id,'recognition',4,{answer:'hello'}); store.recordLanguageReview(x.id,'production',4,{answer:'hola'}); const due=store.getState().reviews.filter(r=>r.status==='due'&&r.sourceType==='language-expression'&&r.sourceId===x.id); assert.equal(due.length,1);
        '''))
    def test_local_syntax_tokenizer_recognizes_keywords_without_remote_dependency(self):
        self.run_node("import assert from 'node:assert/strict'; import {tokenizeCode} from './runtime/modes.js'; const t=tokenizeCode('const answer = 42; // ok','TypeScript'); assert.ok(t.some(x=>x.kind==='keyword'&&x.text==='const')); assert.ok(t.some(x=>x.kind==='number')); assert.ok(t.some(x=>x.kind==='comment'));")
