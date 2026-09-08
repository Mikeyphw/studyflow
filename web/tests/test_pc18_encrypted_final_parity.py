from __future__ import annotations
import os, subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC18EncryptedFinalParityTest(unittest.TestCase):
    def run_node(self, source:str, *, tz:str|None=None)->None:
        env=os.environ.copy()
        if tz: env['TZ']=tz
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False,env=env)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_android_compatible_encrypted_vector_roundtrip_and_authentication(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {webcrypto} from 'node:crypto';
            import {encryptPortableText,decryptPortableText} from './runtime/encrypted_portable.js';
            const salt=Uint8Array.from({length:16},(_,i)=>i),nonce=Uint8Array.from({length:12},(_,i)=>i+16);
            const encrypted=await encryptPortableText('checksum-verified-study-plan','correct horse',{crypto:webcrypto,salt,nonce,iterations:210000});
            const hex=Buffer.from(encrypted).toString('hex');
            assert.equal(hex,'5354554459464c4f57454e43010003345010000102030405060708090a0b0c0d0e0f0c101112131415161718191a1b0f474d32305e9782e7cd359c87f9753c913ea9ef6aa36829646738889b5ee4c6cdd6c63fd531830b9ac8a3d5');
            assert.equal(await decryptPortableText(encrypted,'correct horse',{crypto:webcrypto}),'checksum-verified-study-plan');
            await assert.rejects(()=>decryptPortableText(encrypted,'wrong password',{crypto:webcrypto}),/incorrect|corrupted/i);
            const tampered=new Uint8Array(encrypted);tampered[tampered.length-1]^=1;await assert.rejects(()=>decryptPortableText(tampered,'correct horse',{crypto:webcrypto}),/incorrect|corrupted/i);
        '''))

    def test_today_prefers_local_current_day_then_earliest_future_only(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {recommendNextActivity} from './runtime/domain.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}};const store=createStudyStore({repository:repo,now:()=> '2026-09-08T01:30:00Z'});store.createPlan({name:'Today',mode:'GENERAL_SKILL'},{activate:true});const s=store.addSubject({name:'Law'});
            store.addScheduledActivity({title:'Stale missed',activityType:'THEORY',durationMinutes:20,subjectId:s.id,scheduledFor:'2026-09-06',scheduledStartMinute:600,status:'missed'});
            const today=store.addScheduledActivity({title:'Local today',activityType:'THEORY',durationMinutes:30,subjectId:s.id,scheduledFor:'2026-09-07',scheduledStartMinute:1320});
            store.addScheduledActivity({title:'Future later',activityType:'QUESTIONS',durationMinutes:30,subjectId:s.id,scheduledFor:'2026-09-09',scheduledStartMinute:600});
            let next=recommendNextActivity(store.getState(),new Date('2026-09-08T01:30:00Z'));assert.equal(next.activity.id,today.id);assert.equal(next.temporalScope,'today');
            store.startFocus({activityId:today.id,durationMinutes:30});store.completeFocus();const future=store.addScheduledActivity({title:'Future earliest',activityType:'REVIEW',durationMinutes:25,subjectId:s.id,scheduledFor:'2026-09-08',scheduledStartMinute:720});next=recommendNextActivity(store.getState(),new Date('2026-09-08T01:30:00Z'));assert.equal(next.activity.id,future.id);assert.equal(next.temporalScope,'future');assert.match(next.signals.join(' '),/Earliest future scheduled work/);
        '''),tz='America/Sao_Paulo')

    def test_mock_studio_filters_tags_disputed_and_explicit_passing_score(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {previewMockExam,scoreMockExam} from './runtime/modes.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}};const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00Z'});store.createPlan({name:'Mock',mode:'EXAM_PLAN'},{activate:true});const s=store.addSubject({name:'Law'});
            const good=store.addQuestion({prompt:'Good',answer:'A',subjectId:s.id,examBoard:'CEBRASPE',source:'Official 2025',tags:['official','priority'],disputed:false});store.addQuestion({prompt:'Wrong tag',answer:'A',subjectId:s.id,examBoard:'CEBRASPE',source:'Official 2025',tags:['other'],disputed:false});store.addQuestion({prompt:'Disputed',answer:'A',subjectId:s.id,examBoard:'CEBRASPE',source:'Official 2025',tags:['official','priority'],disputed:true});
            const blueprint={totalQuestions:1,durationMinutes:60,sections:[],examBoard:'CEBRASPE',sourceFilter:'Official',requiredTags:['official','priority'],includeDisputed:false,minimumOverallScorePercent:70,scoring:'no-penalty',negativePoints:0};const preview=previewMockExam(store.getState(),blueprint);assert.equal(preview.ready,true);assert.deepEqual(preview.questionIds,[good.id]);let score=scoreMockExam(preview,{[good.id]:'A'},store.getState());assert.equal(score.passed,true);assert.equal(score.minimumOverallScorePercent,70);
            const noCutoff=previewMockExam(store.getState(),{...blueprint,minimumOverallScorePercent:null});score=scoreMockExam(noCutoff,{[good.id]:'A'},store.getState());assert.equal(score.passed,null);
            const include=previewMockExam(store.getState(),{...blueprint,totalQuestions:2,includeDisputed:true});assert.equal(include.ready,true);assert.equal(include.questionIds.length,2);
        '''))

    def test_bb_reference_mock_is_exact_70_question_derived_and_bank_gated(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {buildBbReferenceMockBlueprint,previewBbReferenceMockExam} from './runtime/modes.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}};const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00-03:00'});store.completeOnboarding({locale:'pt-BR',mode:'EXAM_PLAN',planName:'BB',useSample:false,template:'bb-agente-comercial-pre-edital'});let bp=buildBbReferenceMockBlueprint(store.getState());assert.ok(bp);assert.equal(bp.totalQuestions,70);assert.equal(bp.durationMinutes,300);assert.equal(bp.examBoard,'Cesgranrio');assert.equal(bp.minimumOverallScorePercent,null);assert.deepEqual(bp.sections.map(x=>x.count),[11,4,5,3,5,10,16,16]);assert.equal(bp.sections.reduce((n,x)=>n+x.weight,0),100);
            let result=previewBbReferenceMockExam(store.getState());assert.equal(result.preview.ready,false);assert.equal(result.missingQuestionCount,70);assert.equal(result.essayMinimumScorePercent,70);
            bp.sections.forEach((section,index)=>{for(let i=0;i<section.count;i++)store.addQuestion({prompt:`Q${index}-${i}`,answer:'A',subjectId:section.subjectId,examBoard:i%2?'Fundação Cesgranrio':'Cesgranrio',source:'Reference bank',tags:['bb']});});result=previewBbReferenceMockExam(store.getState());assert.equal(result.preview.ready,true);assert.equal(result.preview.questionIds.length,70);assert.equal(result.missingQuestionCount,0);
        '''))

    def test_confirmed_exam_proximity_affects_schedule_but_expected_date_does_not(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}};const mk=()=>createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00-03:00'});
            let store=mk();store.createPlan({name:'Confirmed',mode:'EXAM_PLAN'},{activate:true});const s=store.addSubject({name:'Law'});store.addTopic({subjectId:s.id,name:'Rights',priority:'high'});store.updatePublicExamTarget({dateStatus:'confirmed',examDate:'2026-09-14'});let p=store.previewSchedule({at:'2026-09-07T12:00:00-03:00',days:7,maxBlocks:8});assert.ok(p.operations.some(x=>String(x.reason).includes('confirmed exam proximity 7 day')));
            store=mk();store.createPlan({name:'Expected',mode:'EXAM_PLAN'},{activate:true});const s2=store.addSubject({name:'Law'});store.addTopic({subjectId:s2.id,name:'Rights',priority:'high'});store.updatePublicExamTarget({dateStatus:'expected',examDate:'2026-09-14'});p=store.previewSchedule({at:'2026-09-07T12:00:00-03:00',days:7,maxBlocks:8});assert.equal(p.operations.some(x=>String(x.reason).includes('confirmed exam proximity')),false);
        '''))

    def test_question_import_and_manual_ui_expose_source_board_and_tags(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {parseQuestionImport} from './runtime/question_import.js';const p=parseQuestionImport(`prompt,answer,board,source,tags\nQ,A,CEBRASPE,Official 2025,official;priority`,'csv');assert.equal(p.drafts.length,1);assert.deepEqual(p.drafts[0].tags,['official','priority']);assert.equal(p.drafts[0].examBoard,'CEBRASPE');assert.equal(p.drafts[0].source,'Official 2025');
        '''))
        ui=(WEB/'src/react/App.tsx').read_text(encoding='utf-8')
        for token in ('Manual question board','Manual question source','Manual question tags','Mock tag filter','Export encrypted v2','Decrypt and preview'):
            self.assertIn(token,ui)

if __name__=='__main__': unittest.main()
