from __future__ import annotations
import os, subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC17CorrectnessPromiseCleanupTest(unittest.TestCase):
    def run_node(self,source:str,*,tz:str|None=None)->None:
        env=os.environ.copy()
        if tz: env['TZ']=tz
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False,env=env)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_canonical_exam_date_resolver_preserves_confirmed_expected_and_legacy_truth(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict';
            import {resolvePublicExamDate,officialCountdownDate,daysUntilOfficialExam} from './runtime/promise_cleanup.js';
            const base={mode:'EXAM_PLAN',examDate:null,configuration:{publicExam:{target:{dateStatus:'unknown',examDate:null}}}};
            const confirmed=structuredClone(base); confirmed.configuration.publicExam.target={dateStatus:'confirmed',examDate:'2026-09-10'};
            assert.deepEqual(resolvePublicExamDate(confirmed),{date:'2026-09-10',source:'confirmed-target',official:true}); assert.equal(officialCountdownDate(confirmed),'2026-09-10');
            const expected=structuredClone(base); expected.configuration.publicExam.target={dateStatus:'expected',examDate:'2026-09-10'}; expected.examDate='2026-09-11';
            assert.deepEqual(resolvePublicExamDate(expected),{date:'2026-09-10',source:'expected-target',official:false}); assert.equal(officialCountdownDate(expected),null);
            const legacy=structuredClone(base); legacy.examDate='2026-09-10';
            assert.deepEqual(resolvePublicExamDate(legacy),{date:'2026-09-10',source:'legacy-plan',official:true});
            assert.equal(daysUntilOfficialExam(legacy,new Date('2026-09-08T01:30:00Z')),3);
        '''),tz='America/Sao_Paulo')

    def test_all_seven_strategies_persist_and_study_cycle_is_gated(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {STUDY_STRATEGY_TYPES,shouldOfferStudyCycle} from './runtime/promise_cleanup.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00-03:00'}); store.createPlan({name:'Strategies',mode:'GENERAL_SKILL'},{activate:true});
            assert.deepEqual(STUDY_STRATEGY_TYPES,['FIXED_WEEKLY','STUDY_CYCLE','GOAL_BASED','ADAPTIVE','FREE_GUIDED','INTENSIVE_SPRINT','COUNTDOWN']);
            for(const strategyType of STUDY_STRATEGY_TYPES){store.setScheduleConfiguration({strategyType}); const plan=store.getState().plans.find(p=>p.id===store.getState().activePlanId); assert.equal(plan.configuration.schedule.strategyType,strategyType); assert.equal(shouldOfferStudyCycle(plan),strategyType==='STUDY_CYCLE');}
        '''))

    def test_sample_exam_coverage_goal_uses_actual_active_topic_count(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {publicExamCoverageTopicCount} from './runtime/promise_cleanup.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00-03:00'}); const plan=store.provisionSamplePlan('public-exam-12-week',{activate:true,replaceSample:true}); const state=store.getState(); const actual=publicExamCoverageTopicCount(state,plan.id); assert.ok(actual>0); const goal=state.goals.find(g=>g.planId===plan.id&&g.metric==='topics-covered'); assert.ok(goal); assert.equal(goal.target,actual); assert.notEqual(goal.target,100);
            const free=store.provisionSamplePlan('free-study-habit',{activate:true,replaceSample:true}); const state2=store.getState(); assert.equal(state2.goals.some(g=>g.planId===free.id&&g.metric==='topics-covered'),false);
        '''))

    def test_manual_and_recurring_schedule_preserve_subject_topic_and_exact_local_time(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00-03:00'}); store.createPlan({name:'Ownership',mode:'GENERAL_SKILL'},{activate:true}); const s=store.addSubject({name:'Law'}),t=store.addTopic({subjectId:s.id,name:'Constitution'});
            const manual=store.addScheduledActivity({title:'Manual constitution',activityType:'READING',durationMinutes:35,subjectId:s.id,topicId:t.id,scheduledFor:'2026-09-08',scheduledStartMinute:1110}); assert.equal(manual.subjectId,s.id); assert.equal(manual.topicId,t.id); assert.equal(manual.scheduledFor,'2026-09-08'); assert.equal(manual.scheduledStartMinute,1110);
            store.setScheduleConfiguration({dailyCapacityMinutes:180,availability:[{id:'mon',weekday:1,startMinute:1080,endMinute:1260}],fixedBlocks:[],recurringBlocks:[{id:'weekly-law',title:'Weekly law',subjectId:s.id,topicId:t.id,weekday:1,startMinute:1140,durationMinutes:45,activityType:'THEORY',enabled:true}]});
            let preview=store.previewSchedule({at:'2026-09-07T12:00:00-03:00',days:1,maxBlocks:10}); const recurring=preview.operations.find(o=>o.action==='add'&&o.reason==='Recurring schedule block:weekly-law'); assert.ok(recurring); assert.equal(recurring.scheduledFor,'2026-09-07'); assert.equal(recurring.scheduledStartMinute,1140); assert.equal(recurring.activity.subjectId,s.id); assert.equal(recurring.activity.topicId,t.id);
            store.setScheduleConfiguration({fixedBlocks:[{id:'conflict',title:'Class',date:'2026-09-07',weekday:null,startMinute:1140,durationMinutes:45}]}); preview=store.previewSchedule({at:'2026-09-07T12:00:00-03:00',days:1,maxBlocks:10}); assert.equal(preview.operations.some(o=>o.reason==='Recurring schedule block:weekly-law'),false); assert.ok(preview.unscheduled.some(x=>x.title==='Weekly law'&&/persisted local date\/time/.test(x.reason)));
        '''))

    def test_web_shell_is_dark_only_with_no_light_variant(self):
        css=(WEB/'styles.css').read_text(encoding='utf-8')
        app=(WEB/'src/react/App.tsx').read_text(encoding='utf-8')
        self.assertIn('color-scheme: dark',css)
        self.assertIn('--bg: #0b0f14',css)
        self.assertIn('--surface: #121923',css)
        self.assertNotIn('color-scheme: light',css)
        self.assertNotIn('prefers-color-scheme: light',css)
        self.assertNotIn('light mode',app.lower())
        self.assertNotIn('theme-light',app.lower())

    def test_calendar_search_and_past_collapse_are_explicit(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {deriveScheduleCalendarSnapshot} from './runtime/parity_intelligence.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00-03:00'}); store.createPlan({name:'Calendar',mode:'GENERAL_SKILL'},{activate:true}); const a=store.addSubject({name:'Administrative Law'}),b=store.addSubject({name:'Portuguese'}); const ta=store.addTopic({subjectId:a.id,name:'Public acts'}),tb=store.addTopic({subjectId:b.id,name:'Syntax'}); store.addScheduledActivity({title:'Past acts',activityType:'THEORY',durationMinutes:30,subjectId:a.id,topicId:ta.id,scheduledFor:'2026-09-05',scheduledStartMinute:600}); store.addScheduledActivity({title:'Future language',activityType:'QUESTIONS',durationMinutes:40,subjectId:b.id,topicId:tb.id,scheduledFor:'2026-09-08',scheduledStartMinute:720});
            const at=new Date('2026-09-07T12:00:00-03:00'); const collapsed=deriveScheduleCalendarSnapshot(store.getState(),{view:'calendar',at}); assert.equal(collapsed.visibleCount,1); assert.equal(collapsed.hiddenPastCount,1); assert.equal(collapsed.includesPast,false); const expanded=deriveScheduleCalendarSnapshot(store.getState(),{view:'calendar',at,includePast:true}); assert.equal(expanded.visibleCount,2); assert.equal(expanded.includesPast,true); const subjectSearch=deriveScheduleCalendarSnapshot(store.getState(),{view:'calendar',at,query:'portuguese'}); assert.deepEqual(subjectSearch.activities.map(x=>x.title),['Future language']); const topicSearch=deriveScheduleCalendarSnapshot(store.getState(),{view:'calendar',at,query:'syntax'}); assert.equal(topicSearch.visibleCount,1);
        '''))

if __name__=='__main__': unittest.main()
