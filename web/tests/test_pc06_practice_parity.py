from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC06PracticeParityTest(unittest.TestCase):
    def run_node(self, source:str)->None:
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_csv_import_preview_is_duplicate_safe_and_stale_guarded(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
            const src='prompt,answer,subject,topic,board,year,difficulty,favorite\n"Which HTML element represents the primary content of a page?",main,Portuguese,Text interpretation,,,easy,false\n"Imported one?",Yes,Portuguese,Text interpretation,CEBRASPE,2025,hard,true';
            const before=JSON.stringify(store.getState()); const p=store.previewQuestionImport(src,'csv'); assert.equal(JSON.stringify(store.getState()),before); assert.equal(p.summary.duplicates,1); assert.equal(p.summary.accepted,1); assert.equal(p.ready,true);
            store.addQuestion({prompt:'Mutation'}); assert.throws(()=>store.applyQuestionImportPreview(p),/stale/);
        '''))

    def test_markdown_and_tsv_imports_accept_supported_fields(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createInitialStudyState} from './runtime/domain.js'; import {buildQuestionImportPreview} from './runtime/question_import.js';
            const s=createInitialStudyState({now:'2026-09-07T12:00:00Z'});
            const md='# New question\nAnswer: B\nChoices: A | B\nSubject: Portuguese\nTopic: Text interpretation\nExamBoard: CEBRASPE\nDifficulty: medium'; const a=buildQuestionImportPreview(s,md,'markdown'); assert.equal(a.ready,true); assert.equal(a.accepted[0].answer,'B'); assert.equal(a.accepted[0].difficulty,'medium');
            const t='prompt\tanswer\tsubject\ttopic\nTSV item\tX\tPortuguese\tText interpretation'; const b=buildQuestionImportPreview(s,t,'tsv'); assert.equal(b.ready,true); assert.equal(b.accepted.length,1);
        '''))

    def test_missing_subject_or_topic_is_rejected_in_preview(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createInitialStudyState} from './runtime/domain.js'; import {buildQuestionImportPreview} from './runtime/question_import.js';
            const s=createInitialStudyState({now:'2026-09-07T12:00:00Z'}); const p=buildQuestionImportPreview(s,'prompt,answer,subject,topic\nQ,A,Missing,Nope','csv'); assert.equal(p.ready,false); assert.equal(p.summary.invalid,1); assert.ok(p.issues.some(x=>x.code==='subject'));
        '''))

    def test_weakness_drill_uses_only_matching_persisted_signals(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {buildWeaknessDrill} from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const seed=store.getState().questions[0];
            const related=store.addQuestion({prompt:'Related',answer:'x',subjectId:seed.subjectId,topicId:seed.topicId}); const quietSubject=store.addSubject({name:'No weakness evidence'}); const unrelated=store.addQuestion({prompt:'Unrelated',answer:'x',subjectId:quietSubject.id}); store.answerQuestion(seed.id,'wrong',{errorCategory:'concept-confusion'});
            const d=buildWeaknessDrill(store.getState(),{minQuestions:2}); assert.equal(d.ready,true); assert.ok(d.questionIds.includes(seed.id)); assert.ok(d.questionIds.includes(related.id)); assert.equal(d.questionIds.includes(unrelated.id),false);
        '''))

    def test_filters_saved_sets_quota_and_error_reclassification(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {filterPracticeQuestions} from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const seed=store.getState().questions[0]; const q=store.addQuestion({prompt:'Fav hard',answer:'A',difficulty:'hard',favorite:true,examBoard:'CEBRASPE',year:2025}); assert.equal(filterPracticeQuestions(store.getState(),{favorite:true,difficulty:'hard',board:'CEBRASPE'}).map(x=>x.id).includes(q.id),true); assert.equal(filterPracticeQuestions(store.getState(),{evidence:'unseen'}).map(x=>x.id).includes(q.id),true);
            store.setPracticeConfiguration({weeklyQuestionQuota:120}); const saved=store.savePracticeSet('Hard banca',{favorite:true,difficulty:'hard'},[q.id]); assert.equal(saved.questionIds.length,1); assert.equal(store.getState().plans[0].configuration.practice.weeklyQuestionQuota,120);
            const wrong=store.answerQuestion(seed.id,'wrong',{errorCategory:'other'}); const updated=store.reclassifyError(wrong.errorEntry.id,'interpretation'); assert.equal(updated.category,'interpretation'); assert.equal(updated.recurrenceCount,1);
        '''))

    def test_react_surface_contains_import_drill_filters_and_saved_sets(self):
        ui=(WEB/'src/react/App.tsx').read_text()
        for token in ('Question import','Preview import','Apply accepted questions','Weakness drill','Save current filter','Weekly question quota','Reclassify error'):
            self.assertIn(token,ui)

if __name__=='__main__': unittest.main()
