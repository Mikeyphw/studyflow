from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC12ExamFoundationParityTest(unittest.TestCase):
    def run_node(self,source:str)->None:
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_pre_edital_cockpit_never_turns_expected_date_into_official_countdown(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict';
            import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {derivePreEditalCockpit} from './runtime/exam_foundation.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
            let plan=store.getState().plans[0], snap=derivePreEditalCockpit(plan,now()); assert.equal(snap.preEdital,true); assert.equal(snap.officialCountdownDays,null); assert.ok(snap.issues.includes('no-confirmed-exam-date')); assert.ok(snap.issues.includes('no-reference-notice'));
            store.updatePublicExamTarget({organization:'Agency',role:'Analyst',board:'Board',dateStatus:'expected',examDate:'2026-12-20'}); plan=store.getState().plans[0]; snap=derivePreEditalCockpit(plan,now()); assert.equal(snap.resolvedDate.source,'expected-target'); assert.equal(snap.resolvedDate.isConfirmed,false); assert.equal(snap.officialCountdownDays,null);
            store.updatePublicExamTarget({dateStatus:'confirmed',examDate:'2026-12-20'}); snap=derivePreEditalCockpit(store.getState().plans[0],now()); assert.equal(snap.preEdital,false); assert.deepEqual(snap.issues,[]);
        '''))

    def test_latest_edital_revision_carries_reference_publication_provenance_and_staleness(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {derivePreEditalCockpit} from './runtime/exam_foundation.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; let clock='2026-09-07T12:00:00Z'; const now=()=>clock; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
            let p=store.previewExamNotice('Disciplina: Portuguese\n- Text interpretation'); store.applyExamNoticePreview(p,{reference:'Edital 2022',publishedAt:'2022-12-23'}); let snap=derivePreEditalCockpit(store.getState().plans[0],now()); assert.equal(snap.referenceLabel,'Edital 2022'); assert.ok(snap.referenceAgeDays>365); assert.ok(snap.issues.includes('stale-reference-notice'));
            clock='2026-09-08T12:00:00Z'; p=store.previewExamNotice('Disciplina: Portuguese\n- Text interpretation\n- Grammar'); store.applyExamNoticePreview(p,{reference:'Revision 2026',publishedAt:null}); snap=derivePreEditalCockpit(store.getState().plans[0],now()); assert.equal(snap.referenceLabel,'Revision 2026'); assert.ok(snap.issues.includes('reference-publication-date-unknown')); assert.ok(!snap.issues.includes('stale-reference-notice'));
        '''))

    def test_phase_plan_validates_ranges_allocations_overlap_and_bb_reference_targets(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {deriveActiveStudyPhase,BB_STUDY_PHASE_SUGGESTIONS} from './runtime/exam_foundation.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now});
            assert.throws(()=>store.setStudyPhaseDefinitions([{id:'bad',kind:'foundation',label:'Bad',startsOn:'2026-09-01',endsOn:'2026-09-30',theoryRatioPercent:50,questionRatioPercent:30,reviewRatioPercent:10,questionGoal:null,mockExamGoal:null,allocationIsStudyFlowSuggested:false}]));
            store.applyBbStudyPhaseSuggestions(); const phases=store.getState().plans[0].configuration.publicExam.phaseDefinitions; assert.deepEqual(phases.map(x=>x.questionGoal),[300,500,700,1000]); assert.deepEqual(phases.map(x=>x.mockExamGoal),[3,5,4,4]); assert.equal(phases[0].startsOn,'2026-09-07'); assert.equal(phases.at(-1).endsOn,'2026-12-31'); assert.ok(phases.every(x=>x.allocationIsStudyFlowSuggested)); assert.equal(BB_STUDY_PHASE_SUGGESTIONS.length,4); assert.equal(deriveActiveStudyPhase(store.getState().plans[0],now()).kind,'foundation');
            const overlap={...phases[1],startsOn:'2026-09-20'}; assert.throws(()=>store.setStudyPhaseDefinitions([phases[0],overlap,...phases.slice(2)]));
        '''))

    def test_active_phase_allocation_shapes_generated_week_candidates(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {buildAdaptiveSchedulePreview} from './runtime/schedule.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.applyBbStudyPhaseSuggestions(); const state=structuredClone(store.getState()); state.activities=[]; state.reviews=[]; const preview=buildAdaptiveSchedulePreview(state,{at:now(),days:7,maxBlocks:10}); const phaseOps=preview.operations.filter(x=>String(x.reason).includes('Active phase Foundation')); assert.ok(phaseOps.length>=2); assert.ok(phaseOps.some(x=>x.activity?.kind==='study')); assert.ok(phaseOps.some(x=>x.activity?.kind==='questions')); assert.ok(phaseOps.every(x=>String(x.reason).includes('StudyFlow suggestion')));
        '''))

    def test_reference_tags_freshness_replacement_and_portability_preserve_history(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {deriveMaterialReferenceFreshness,decodeReferenceProvenance} from './runtime/exam_foundation.js'; import {createPortablePackage} from './runtime/portable.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const m=store.addMaterial({title:'Reference',kind:'link',locator:'https://example.com/2022.pdf'}); store.setMaterialReferenceProvenance(m.id,{origin:'EDITAL_2022',verifiedOn:'2026-06-01'}); let tracked=store.getState().materials.find(x=>x.id===m.id); assert.equal(deriveMaterialReferenceFreshness(tracked,'2026-08-30T00:00:00Z').status,'current'); assert.equal(deriveMaterialReferenceFreshness(tracked,'2026-09-01T00:00:00Z').status,'possibly-stale');
            const replacement=store.replaceMaterialReference(m.id,{locator:'https://example.com/current.pdf',verifiedOn:'2026-09-07'}); const state=store.getState(),old=state.materials.find(x=>x.id===m.id),next=state.materials.find(x=>x.id===replacement.id); assert.equal(old.archived,true); assert.equal(next.archived,false); assert.equal(decodeReferenceProvenance(next).supersedesMaterialId,m.id); assert.equal(decodeReferenceProvenance(next).verifiedOn,'2026-09-07'); assert.throws(()=>store.deleteMaterial(m.id)); const pkg=createPortablePackage(state); const carried=pkg.payload.materials.find(x=>x.id===next.id); assert.ok(carried.tags.some(x=>x.includes('reference-supersedes=')));
        '''))

    def test_react_and_offline_shell_expose_foundation_workflows(self):
        ui=(WEB/'src/react/App.tsx').read_text(); sw=(WEB/'service-worker.js').read_text()
        for token in ('Truthful pre-edital cockpit','Review assumptions in edital workflow','Study phase planner','Use BB Sep–Dec 2026 suggestions','Track as edital 2022 reference','Replace reference'):
            self.assertIn(token,ui)
        self.assertIn('runtime/exam_foundation.js',sw)

if __name__=='__main__': unittest.main()
