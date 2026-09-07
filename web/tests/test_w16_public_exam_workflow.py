from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class W16PublicExamWorkflowTest(unittest.TestCase):
    def run_node(self, source:str)->None:
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_v4_state_migrates_to_v5_with_public_exam_configuration(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict';
            import { createInitialStudyState, migrateStudyState, validateStudyState } from './runtime/domain.js';
            const legacy=createInitialStudyState({now:'2026-09-01T00:00:00.000Z'}); legacy.schemaVersion=4; delete legacy.plans[0].configuration;
            const migrated=migrateStudyState(legacy,{now:'2026-09-07T00:00:00.000Z'});
            assert.equal(migrated.schemaVersion,6); assert.equal(migrated.plans[0].mode,'EXAM_PLAN'); assert.deepEqual(migrated.plans[0].configuration.publicExam.reviewIntervalsDays,[1,7,30]); assert.equal(validateStudyState(migrated).length,0);
        '''))

    def test_target_identity_and_final_stretch_are_truthful(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js'; import { deriveFinalStretchGuidance } from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-06T12:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
            assert.throws(()=>store.updatePublicExamTarget({organization:'Agency'}));
            store.updatePublicExamTarget({organization:'Agency',role:'Analyst',board:'CEBRASPE',examDate:'2026-09-12',dateStatus:'expected'});
            let plan=store.getState().plans[0]; assert.equal(deriveFinalStretchGuidance(plan,new Date(now())).band,'unknown');
            store.updatePublicExamTarget({dateStatus:'confirmed'}); plan=store.getState().plans[0]; const g=deriveFinalStretchGuidance(plan,new Date(now())); assert.equal(g.band,'7-day'); assert.equal(g.allocation.newContent,5);
        '''))

    def test_edital_preview_is_non_mutating_then_archives_removed_content_on_apply(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; let clock='2026-09-06T13:00:00.000Z'; const now=()=>clock; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const before=JSON.stringify(store.getState());
            const preview=store.previewExamNotice('Disciplina: Portuguese\n- Text interpretation\n- Grammar\nDisciplina: Administrative Law\n- Principles'); assert.equal(JSON.stringify(store.getState()),before); assert.equal(preview.subjects.length,2);
            store.applyExamNoticePreview(preview); const state=store.getState(); assert.equal(state.plans[0].configuration.publicExam.noticeRevisions.length,1); assert.ok(state.subjects.some(s=>s.name==='Administrative Law'&&!s.archived)); assert.ok(state.subjects.some(s=>s.name==='Constitutional Law'&&s.archived));
            assert.throws(()=>store.applyExamNoticePreview(preview));
        '''))

    def test_banca_metadata_mock_supply_scoring_and_cutoff_gap(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import { createBrowserRepository } from './runtime/storage.js'; import { createStudyStore } from './runtime/state.js'; import { filterExamQuestions, previewMockExam, derivePublicExamDashboard } from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-06T14:00:00.000Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
            const seed=store.getState().questions[0]; store.addQuestion({prompt:'Banca question',choices:['A','B'],answer:'B',subjectId:seed.subjectId,examBoard:'CEBRASPE',organization:'Agency',role:'Analyst',year:2025,points:1});
            assert.equal(filterExamQuestions(store.getState(),{board:'CEBRASPE',yearFrom:2025}).length,1);
            store.setMockBlueprint({totalQuestions:2,durationMinutes:30,scoring:'no-penalty'}); store.updatePublicExamTarget({estimatedCutoff:70});
            const preview=previewMockExam(store.getState()); assert.equal(preview.ready,true); const answers={}; for(const id of preview.questionIds){const q=store.getState().questions.find(x=>x.id===id);answers[id]=q.answer??'';} const evidence=store.recordMockExam(preview,answers); assert.equal(evidence.kind,'mock-exam-result'); const d=derivePublicExamDashboard(store.getState()); assert.equal(typeof d.cutoffGap,'number'); assert.equal(d.cutoffGap,30);
        '''))

    def test_react_ui_exposes_concurso_workflow(self):
        ui=(WEB/'src/react/App.tsx').read_text()
        for token in ('Concurso target','Concurso dashboard','Preview edital','Apply preview','Legislation','Mock Exam Studio','store.updatePublicExamTarget','store.previewExamNotice','store.recordMockExam','Banca'):
            self.assertIn(token,ui)

if __name__=='__main__': unittest.main()
