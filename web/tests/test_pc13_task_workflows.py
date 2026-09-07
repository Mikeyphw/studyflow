from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC13TaskWorkflowsParityTest(unittest.TestCase):
    def run_node(self,source:str)->None:
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_bb_preset_is_atomic_truthful_and_matches_reference_contract(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js';
            let saves=0,saved=null; const repo={load(){return {status:'empty',state:null}},save(s){saves++;saved=structuredClone(s);return {status:'saved'}},clear(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:repo,now}); const before=saves;
            store.completeOnboarding({locale:'en',mode:'EXAM_PLAN',planName:'BB',useSample:false,template:'bb-agente-comercial-pre-edital'}); assert.equal(saves,before+1,'preset provisioning must be one state save'); const state=store.getState(),plan=state.plans.find(p=>p.id===state.activePlanId),c=plan.configuration.publicExam;
            assert.deepEqual([c.target.organization,c.target.role,c.target.board],['Banco do Brasil','Escriturário — Agente Comercial','Fundação Cesgranrio']); assert.equal(c.target.dateStatus,'unknown'); assert.equal(c.target.examDate,null); assert.match(c.target.editalRef,/Edital-de-Abertura-de-Selecao-Externa-2022-01\.pdf/);
            const subjects=state.subjects.filter(s=>s.planId===plan.id); assert.equal(subjects.length,8); assert.equal(state.topics.filter(t=>t.planId===plan.id).length,0); assert.equal(subjects.reduce((n,s)=>n+s.weight,0),100); assert.deepEqual(subjects.map(s=>s.weight),[15,5,7.5,5,7.5,15,22.5,22.5]); assert.ok(subjects.every(s=>s.examIncidence==null));
            assert.deepEqual(c.reviewIntervalsDays,[1,7,30]); assert.equal(c.bbReferencePreset.weeklyScheduledMinutes,1140); assert.equal(c.bbReferencePreset.weeklyGoalHours,19); assert.equal(c.scheduleRhythm,undefined); assert.equal(c.stages.find(s=>s.type==='objective').referenceQuestionCount,70); assert.equal(c.stages.find(s=>s.type==='objective').weight,100); assert.equal(c.stages.find(s=>s.type==='essay').minimumScorePercent,70); assert.equal(c.bbReferencePreset.scheduleRhythm.reduce((n,b)=>n+b.durationMinutes,0),1140); assert.equal(plan.configuration.schedule.availability.reduce((n,b)=>n+(b.endMinute-b.startMinute),0),1140);
            const goals=state.goals.filter(g=>g.planId===plan.id); assert.deepEqual(goals.map(g=>[g.metric,g.target]),[['study-hours',19],['accuracy-percent',80],['writing-sessions',1]]); assert.equal(c.noticeRevisions[0].reference,'Seleção Externa 2022/001'); assert.equal(c.noticeRevisions[0].publishedAt,'2022-12-23'); assert.equal(saved.activePlanId,plan.id);
        '''))

    def test_writing_uses_canonical_session_and_generic_finish_cannot_bypass(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {decodeWritingSessionRecord,deriveWritingWeeklyProgress} from './runtime/task_workflows.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; let clock='2026-09-07T12:00:00Z'; const now=()=>clock; const store=createStudyStore({repository:repo,now}); store.provisionBbAgenteComercialPreset({activate:true}); const a=store.addActivity({title:'Essay practice',kind:'study',activityType:'WRITING',durationMinutes:60}); const session=store.startWritingSession({activityId:a.id,prompt:'Public service theme'}); assert.equal(session.status,'running'); assert.equal(store.getState().sessions.filter(s=>s.activityId===a.id).length,1); store.saveWritingDraft({prompt:'Public service theme',draftText:'First durable draft'}); let current=store.getState().sessions.find(s=>s.id===session.id); assert.equal(decodeWritingSessionRecord(current.note).draftText,'First durable draft'); assert.throws(()=>store.completeFocus());
            assert.throws(()=>store.completeWriting({prompt:'Public service theme',draftText:'Final draft',rubric:{themeAndTask:20,textType:18,cohesion:17,argumentation:19,standardLanguage:16},checklist:{taskReviewed:true,structureReviewed:true,cohesionReviewed:false,languageReviewed:true}})); clock='2026-09-07T12:30:00Z'; const done=store.completeWriting({prompt:'Public service theme',draftText:'Final draft',rubric:{themeAndTask:20,textType:18,cohesion:17,argumentation:19,standardLanguage:16},checklist:{taskReviewed:true,structureReviewed:true,cohesionReviewed:true,languageReviewed:true}}); assert.equal(done.id,session.id); assert.equal(done.status,'completed'); assert.equal(store.getState().sessions.filter(s=>s.activityId===a.id).length,1); assert.equal(store.getState().activities.find(x=>x.id===a.id).status,'completed'); const ev=store.getState().evidence.find(e=>e.sessionId===session.id&&e.kind==='writing-session-completed'); assert.equal(ev.value.scorePercent,90); assert.deepEqual(deriveWritingWeeklyProgress(store.getState(),now()),{completed:1,target:1,reached:true});
        '''))

    def test_activity_deep_links_are_explicit_type_driven_and_context_narrow(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {routeActivityDeepLink} from './runtime/task_workflows.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00Z'}); const subject=store.addSubject({name:'Banking'}),t1=store.addTopic({subjectId:subject.id,name:'Topic A'}),t2=store.addTopic({subjectId:subject.id,name:'Topic B'}); const theory=store.addActivity({title:'Mock exam misleading title',kind:'study',activityType:'THEORY',subjectId:subject.id,topicId:t1.id}); const mock=store.addActivity({title:'Read chapter misleading title',kind:'questions',activityType:'MOCK_EXAM',subjectId:subject.id,topicId:t1.id}); const q=store.addActivity({title:'Questions',kind:'questions',activityType:'QUESTIONS',subjectId:subject.id,topicId:t1.id}); const writing=store.addActivity({title:'Essay',kind:'study',activityType:'WRITING',subjectId:subject.id});
            assert.equal(routeActivityDeepLink(store.getState(),theory.id).focusMode,'focus'); assert.equal(routeActivityDeepLink(store.getState(),mock.id).practiceTab,'tests'); const qr=routeActivityDeepLink(store.getState(),q.id); assert.equal(qr.practiceTab,'questions'); assert.equal(qr.topicId,t1.id); assert.notEqual(qr.topicId,t2.id); assert.equal(routeActivityDeepLink(store.getState(),writing.id).focusMode,'writing');
        '''))

    def test_legacy_activity_migration_uses_kind_not_title_heuristics(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {migrateStudyState} from './runtime/domain.js';
            const base={schemaVersion:7,revision:0,activePlanId:'p',studyCycle:{subjectIds:[],cursor:0},studyCycles:{p:{subjectIds:[],cursor:0}},focus:{status:'idle',activityId:null,durationMinutes:25,elapsedMs:0,startedAt:null,lastResumedAt:null,note:null},plans:[{id:'p',name:'P',mode:'GENERAL_SKILL',status:'active',configuration:{},createdAt:'2026-09-07T00:00:00Z',updatedAt:'2026-09-07T00:00:00Z'}],subjects:[],topics:[],sessions:[],goals:[],materials:[],reviews:[],questions:[],evidence:[],errorEntries:[],preferences:{locale:'en',onboardingCompleted:true},activities:[{id:'a',planId:'p',subjectId:null,topicId:null,title:'Mock exam by title only',kind:'review',scheduledFor:null,durationMinutes:25,status:'planned',queue:'today',locked:false,source:'manual',createdAt:'2026-09-07T00:00:00Z',updatedAt:'2026-09-07T00:00:00Z'}]}; const s=migrateStudyState(base); assert.equal(s.activities[0].activityType,'REVIEW');
        '''))

    def test_source_ui_offline_and_localization_expose_pc13(self):
        ui=(WEB/'src/react/App.tsx').read_text(); sw=(WEB/'service-worker.js').read_text(); product=(WEB/'src/product.ts').read_text()
        for token in ('BB Agente Comercial — pre-edital','Writing Workspace','Finish Writing','Create BB Agente Comercial preset plan','Open {title(a.activityType)}'): self.assertIn(token,ui)
        self.assertIn('runtime/task_workflows.js',sw); self.assertIn('"Writing Workspace":"Espaço de Redação"',product); self.assertIn('"Finish Writing":"Concluir redação"',product)

if __name__=='__main__': unittest.main()
