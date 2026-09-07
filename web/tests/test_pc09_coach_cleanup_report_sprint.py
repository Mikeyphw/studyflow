from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class PC09CoachCleanupReportSprintTest(unittest.TestCase):
    def run_node(self,source:str):
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)
    def test_readiness_and_weekly_coach_preserve_unknowns_and_explain_actions(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {deriveReadinessExplanation,deriveWeeklyCoach} from './runtime/intelligence.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); const r=deriveReadinessExplanation(store.getState()); assert.ok(r.signals.some(x=>x.status==='unknown')); const c=deriveWeeklyCoach(store.getState()); assert.ok(c.items.length); assert.ok(c.items.every(x=>x.reason));
        '''))
    def test_question_quota_plan_uses_persisted_attempts_not_fabricated_progress(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {deriveQuestionQuotaPlan} from './runtime/intelligence.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); store.setPracticeConfiguration({weeklyQuestionQuota:10}); let q=deriveQuestionQuotaPlan(store.getState(),new Date(now())); assert.equal(q.completed,0); assert.equal(q.remaining,10); store.answerQuestion(store.getState().questions[0].id,'wrong'); q=deriveQuestionQuotaPlan(store.getState(),new Date(now())); assert.equal(q.completed,1); assert.equal(q.remaining,9);
        '''))
    def test_cleanup_preview_is_non_mutating_and_only_safe_actions_apply(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {buildContentCleanupPreview} from './runtime/intelligence.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); const subject=store.addSubject({name:'Orphan subject'}); const topic=store.addTopic({subjectId:subject.id,name:'Unused'}); store.addQuestion({prompt:'Duplicate?',answer:'A'}); store.addQuestion({prompt:'  duplicate? ',answer:'B'}); const before=JSON.stringify(store.getState()); const p=buildContentCleanupPreview(store.getState()); assert.equal(JSON.stringify(store.getState()),before); assert.ok(p.issues.some(x=>x.kind==='duplicate-question'&&!x.applicable)); assert.ok(p.issues.some(x=>x.entityId===topic.id&&x.action==='archive-topic')); store.applyContentCleanupPreview(p); assert.equal(store.getState().topics.find(x=>x.id===topic.id).status,'archived'); assert.equal(store.getState().questions.filter(x=>x.prompt.toLowerCase().includes('duplicate')).length,2);
        '''))
    def test_exam_report_is_structured_local_html_and_escapes_user_content(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {buildExamReportSnapshot,renderExamReportHtml} from './runtime/intelligence.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); store.renamePlan(store.getState().activePlanId,'<Exam & plan>'); const report=buildExamReportSnapshot(store.getState()); const html=renderExamReportHtml(report); assert.match(html,/<!doctype html>/); assert.ok(html.includes('&lt;Exam &amp; plan&gt;')); assert.ok(!html.includes('<Exam & plan>'));
        '''))
    def test_exam_runbook_refuses_false_urgency_without_confirmed_date(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {buildExamRunbook} from './runtime/intelligence.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); let r=buildExamRunbook(store.getState(),new Date(now())); assert.equal(r.status,'no-date'); assert.equal(r.daysRemaining,null); store.updatePublicExamTarget({organization:'Agency',role:'Analyst',board:'Board',dateStatus:'confirmed',examDate:'2026-09-12'}); r=buildExamRunbook(store.getState(),new Date(now())); assert.equal(r.status,'ready'); assert.ok(r.daysRemaining<=5); assert.ok(r.items.some(x=>x.priority==='urgent'));
        '''))
    def test_seven_day_sprint_is_preview_first_and_reuses_schedule_safety(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {buildStudySprintPreview} from './runtime/intelligence.js'; const storage={getItem(){return null},setItem(){},removeItem(){}}; const now=()=> '2026-09-07T12:00:00Z'; const store=createStudyStore({repository:createBrowserRepository({storage,now}),now}); const before=JSON.stringify(store.getState()); const p=buildStudySprintPreview(store.getState(),new Date(now())); assert.equal(JSON.stringify(store.getState()),before); assert.equal(p.days.length,7); assert.equal(p.schedule.baseRevision,store.getState().revision); store.applySchedulePreview(p.schedule); assert.ok(store.getState().revision>p.baseRevision);
        '''))
