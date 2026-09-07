from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class W17ContentModesTest(unittest.TestCase):
    def run_node(self, source:str)->None:
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_programming_mode_exercises_are_persistent_and_evidenced(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js'; import { deriveModeAnalytics } from './runtime/modes.js';
            const values=new Map(); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)}; const now=()=> '2026-09-06T15:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setPlanMode('PROGRAMMING_COURSE'); store.addProgrammingExercise({title:'FizzBuzz',prompt:'Implement FizzBuzz',starterCode:'function fizzBuzz() {}',expectedOutput:'1,2,Fizz'}); const id=store.getState().plans[0].configuration.programming.exercises[0].id; store.recordProgrammingExercise(id,{completed:true,note:'Passed manually'}); assert.equal(store.getState().plans[0].configuration.programming.exercises[0].status,'completed'); assert.ok(store.getState().evidence.some(e=>e.kind==='programming-exercise')); assert.ok(deriveModeAnalytics(store.getState()).metrics.some(m=>m.label==='Exercises completed'&&m.value===1)); const reload=createStudyStore({repository:createBrowserRepository({storage,now}),now}); assert.equal(reload.getState().plans[0].configuration.programming.exercises[0].title,'FizzBuzz');
        '''))

    def test_language_mode_has_six_review_directions_and_manual_sentence_creation(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-06T16:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setPlanMode('LANGUAGE_DECK'); store.addLanguageExpression({expression:'break the ice',meaning:'start a friendly conversation',region:'global',register:'neutral'}); const cfg=store.getState().plans[0].configuration.language; assert.equal(cfg.reviewDirections.length,6); const ev=store.recordLanguageReview(cfg.expressions[0].id,'sentence-creation',3,{answer:'I broke the ice at the meeting.'}); assert.equal(ev.value.manualReview,true); assert.ok(store.getState().reviews.some(r=>r.status==='due'));
        '''))

    def test_reading_and_general_skill_progress_create_explicit_evidence_only(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js'; import { deriveModeAnalytics } from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-06T17:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setPlanMode('READING_STUDY'); store.addReadingItem({title:'Clean Code',totalUnits:100,unit:'pages'}); let id=store.getState().plans[0].configuration.reading.items[0].id; store.updateReadingProgress(id,25); assert.ok(store.getState().evidence.some(e=>e.kind==='reading-progress')); assert.equal(deriveModeAnalytics(store.getState()).metrics.find(m=>m.label==='Reading progress').value,0.25); store.setPlanMode('GENERAL_SKILL'); store.addGeneralSkillDrill({title:'Typing sprint',metric:'wpm',target:70,unit:'wpm'}); id=store.getState().plans[0].configuration.general.drills[0].id; store.recordGeneralSkillDrill(id,72); assert.ok(store.getState().evidence.some(e=>e.kind==='skill-drill'));
        '''))

    def test_portable_package_preserves_mode_configuration(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js'; import { createPortablePackage, parsePortablePackage, serializePortablePackage } from './runtime/portable.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-06T18:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setPlanMode('LANGUAGE_DECK'); store.addLanguageExpression({expression:'saudade',meaning:'longing'}); const pkg=createPortablePackage(store.getState(),{now}); const parsed=parsePortablePackage(serializePortablePackage(pkg),{now}); assert.equal(parsed.payload.schemaVersion,6); assert.equal(parsed.payload.plans[0].mode,'LANGUAGE_DECK'); assert.equal(parsed.payload.plans[0].configuration.language.expressions[0].expression,'saudade');
        '''))

    def test_react_ui_exposes_all_modes_and_no_arbitrary_shell_execution(self):
        ui=(WEB/'src/react/App.tsx').read_text(); modes=(WEB/'src/modes.ts').read_text()
        for token in ('PROGRAMMING_COURSE','LANGUAGE_DECK','READING_STUDY','GENERAL_SKILL','Programming course','Language deck','Reading study','General skill','Mode-aware analytics','sentence creation'):
            self.assertIn(token,ui)
        self.assertIn('browser-only',modes); self.assertNotIn('child_process',modes); self.assertNotIn('exec(',modes); self.assertNotIn('spawn(',modes)

if __name__=='__main__': unittest.main()
