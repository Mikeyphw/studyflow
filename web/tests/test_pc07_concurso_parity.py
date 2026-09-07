from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC07ConcursoParityTest(unittest.TestCase):
    def run_node(self, source:str)->None:
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_hierarchical_edital_diff_and_revision_provenance(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T13:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const before=JSON.stringify(store.getState());
            const p=store.previewExamNotice('Disciplina: Portuguese\n1 Text interpretation\n1.1 Inference\n1.1.1 Implicature'); assert.equal(JSON.stringify(store.getState()),before); assert.equal(p.subjects[0].topicPaths[2].join('>'),'Text interpretation>Inference>Implicature'); assert.ok(p.diff.archive.some(x=>x.includes('Constitutional Law'))); store.applyExamNoticePreview(p);
            const state=store.getState(); const revision=state.plans[0].configuration.publicExam.noticeRevisions.at(-1); assert.equal(revision.previousRevisionId,null); assert.ok(revision.syllabusFingerprint); const inference=state.topics.find(t=>t.name==='Inference'); const implicature=state.topics.find(t=>t.name==='Implicature'); assert.equal(implicature.parentTopicId,inference.id); assert.equal(state.plans[0].configuration.publicExam.studyPhase,'post-edital');
        '''))

    def test_study_phase_cycle_controls_and_starter_templates_are_explicit(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T13:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setPublicExamStudyPhase('pre-edital'); const first=store.getState().studyCycle.subjectIds[0]; store.moveCurrentCycleSubjectToEnd(); assert.equal(store.getState().studyCycle.subjectIds.at(-1),first); const cursor=store.getState().studyCycle.cursor; store.skipStudyCycleSubject(); assert.notEqual(store.getState().studyCycle.cursor,cursor);
            store.applyPublicExamStarterTemplate('questions'); const plan=store.getState().plans[0]; assert.equal(plan.configuration.practice.weeklyQuestionQuota,200); assert.deepEqual(plan.configuration.publicExam.reviewIntervalsDays,[1,5,20]); assert.equal(plan.configuration.publicExam.target.organization,null); assert.ok(store.getState().goals.some(g=>g.name==='Weekly questions'));
        '''))

    def test_legislation_verification_and_evidence_links_are_plan_scoped(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T13:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.addLegislation({title:'Constitution',sourceUpdatedAt:'2026-08-01T00:00:00Z',provisions:[{label:'Art. 5',text:'Rights'}]}); let plan=store.getState().plans[0]; const doc=plan.configuration.publicExam.legislation[0]; store.verifyLegislation(doc.id,'2026-09-07T13:00:00Z'); const q=store.getState().questions[0]; store.linkLegislationEvidence(doc.id,{provisionId:doc.provisions[0].id,questionId:q.id,note:'Question cites provision'}); plan=store.getState().plans[0]; const next=plan.configuration.publicExam.legislation[0]; assert.ok(next.verifiedAt); assert.equal(next.evidenceLinks.length,1); assert.equal(next.evidenceLinks[0].questionId,q.id); assert.throws(()=>store.linkLegislationEvidence(doc.id,{provisionId:'missing',questionId:q.id}));
        '''))

    def test_explainable_priority_and_edital_matrix_preserve_unknown_incidence(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {derivePublicExamNextActions,deriveEditalMatrix} from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T13:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const seed=store.getState().questions[0]; store.answerQuestion(seed.id,'wrong',{errorCategory:'content-gap'}); const actions=derivePublicExamNextActions(store.getState()); assert.ok(actions.length); assert.ok(actions[0].reasonCodes.length); const rows=deriveEditalMatrix(store.getState()); const row=rows.find(r=>r.subjectId===seed.subjectId); assert.equal(row.incidence,null); assert.equal(typeof row.priority,'number'); store.updateSubject(seed.subjectId,{examIncidence:80,weight:2}); const updated=deriveEditalMatrix(store.getState()).find(r=>r.subjectId===seed.subjectId); assert.equal(updated.incidence,.8);
        '''))

    def test_extended_banca_filter_and_rolling_dashboard(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {filterExamQuestions,derivePublicExamDashboard} from './runtime/modes.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; let clock='2026-09-07T13:00:00Z'; const now=()=>clock; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const q=store.addQuestion({prompt:'Meta',answer:'B',examBoard:'CEBRASPE',organization:'Agency',role:'Analyst',examStage:'Objective',authorship:'Official',year:2025,difficulty:'hard',favorite:true}); assert.equal(filterExamQuestions(store.getState(),{board:'CEBRASPE',stage:'Objective',authorship:'Official',difficulty:'hard',favorite:true}).length,1); store.answerQuestion(q.id,'B'); const d=derivePublicExamDashboard(store.getState()); assert.equal(d.accuracy7,1); assert.equal(d.accuracy30,1); assert.equal(d.accuracy90,1);
        '''))

    def test_react_surface_contains_full_concurso_closure(self):
        ui=(WEB/'src/react/App.tsx').read_text()
        for token in ('Study phase','Pre-edital','Local starter templates','Edital Matrix','Explainable next actions','Verified','Exam incidence'):
            self.assertIn(token,ui)

if __name__=='__main__': unittest.main()
