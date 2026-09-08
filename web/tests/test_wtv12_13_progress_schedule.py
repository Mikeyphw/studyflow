from pathlib import Path
import subprocess
import textwrap
import unittest

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent
APP = (WEB / "src/react/App.tsx").read_text()
HISTORY = (WEB / "src/tv_history.ts").read_text()
SERVER = (WEB / "scripts/serve_tv.mjs").read_text()
SW = (WEB / "service-worker.js").read_text()
ROADMAP = (ROOT / "docs/web/WEB_ROADMAP.md").read_text()


class Wtv1213ProgressScheduleTest(unittest.TestCase):
    def test_progress_runtime_is_evidence_backed_and_truthful(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const p=require('./runtime/tv_progress.js');
            assert.equal(p.tvProgressRecommendedSurface({accuracyPercent:90,dueReviews:1,openErrors:0,coveredTopics:2,activeTopics:2}),'REVIEW');
            assert.equal(p.tvProgressRecommendedSurface({accuracyPercent:60,dueReviews:0,openErrors:0,coveredTopics:2,activeTopics:2}),'QUESTION_SET');
            assert.equal(p.tvProgressRecommendedSurface({accuracyPercent:null,dueReviews:0,openErrors:0,coveredTopics:1,activeTopics:2}),'STUDY_SESSION');
            assert.equal(p.tvProgressRecommendedSurface({accuracyPercent:null,dueReviews:0,openErrors:0,coveredTopics:0,activeTopics:0}),'QUESTION_SET');
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        source = (WEB / "src/tv_progress.ts").read_text()
        for token in ('kind==="question-attempt"', 'status==="completed"', 'status==="due"', 'status==="open"', 'deriveProgressMetrics'):
            self.assertIn(token, source)
        self.assertNotIn("Math.random", source)

    def test_schedule_runtime_maps_only_supported_tv_flows(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const s=require('./runtime/tv_schedule.js');
            assert.equal(s.tvScheduleActivityDestination('QUESTIONS'),'QUESTION_SET');
            assert.equal(s.tvScheduleActivityDestination('REVIEW'),'REVIEW');
            assert.equal(s.tvScheduleActivityDestination('FLASHCARDS'),'REVIEW');
            assert.equal(s.tvScheduleActivityDestination('MOCK_EXAM'),'MOCK_EXAM');
            assert.equal(s.tvScheduleActivityDestination('THEORY'),'STUDY_SESSION');
            assert.equal(s.tvScheduleActivityDestination('WRITING'),null);
            assert.equal(s.tvScheduleDateLabel('2026-09-08','2026-09-08'),'Today');
            assert.equal(s.tvScheduleDateLabel('2026-09-09','2026-09-08'),'Tomorrow');
            assert.equal(s.tvScheduleTimeLabel(1110),'18:30');
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_progress_and_schedule_are_dedicated_tv_surfaces(self):
        self.assertIn('screen==="progress"&&(tvBrowser?<TvProgressAnalytics', APP)
        self.assertIn('screen==="schedule"&&<TvScheduleSurface', APP)
        self.assertIn('if(surface==="PROGRESS")return{screen:"progress"}', APP)
        self.assertIn('if(surface==="SCHEDULE")return{screen:"schedule"}', APP)
        self.assertIn('"review"|"schedule"', HISTORY)
        self.assertIn('"review","schedule"', HISTORY)
        self.assertIn('["today","plan","practice","progress","schedule"]', APP)

    def test_progress_surface_is_navigation_only(self):
        start = APP.index('function TvProgressAnalytics(')
        end = APP.index('function TvScheduleSurface(', start)
        surface = APP[start:end]
        for forbidden in ('store.', 'completeGoal(', 'toggleGoal(', 'resetLocalState(', 'applyContentCleanupPreview(', 'applyStudySprintPreview('):
            self.assertNotIn(forbidden, surface)
        for target in ('progress-today', 'progress-questions', 'progress-reviews', 'progress-mock', 'progress-schedule', 'progress-weak-'):
            self.assertIn(target, surface)

    def test_schedule_surface_has_no_schedule_mutation_api(self):
        start = APP.index('function TvScheduleSurface(')
        end = APP.index('function Progress(', start)
        surface = APP[start:end]
        for forbidden in ('setScheduleConfiguration(', 'previewSchedule(', 'applySchedulePreview(', 'addScheduledActivity(', 'setActivityLocked(', 'delete'):
            self.assertNotIn(forbidden, surface)
        for token in ('data-tv-control-target="schedule-today"', 'data-tv-control-target="schedule-week"', 'data-tv-control-action="PREVIOUS"', 'data-tv-control-action="NEXT"', 'data-tv-control-action="START"', 'Continue writing on phone/computer'):
            self.assertIn(token, surface)

    def test_phone_remote_has_progress_and_schedule_shortcuts(self):
        for token in (
            "['Progress: Today','PROGRESS','progress-today','TODAY']",
            "['Progress: Questions','PROGRESS','progress-questions','QUESTION_SET']",
            "['Progress: Reviews','PROGRESS','progress-reviews','REVIEW']",
            "['Progress: Mock Exam','PROGRESS','progress-mock','MOCK_EXAM']",
            "['Progress: Schedule','PROGRESS','progress-schedule','SCHEDULE']",
            "['Schedule: Today','SCHEDULE','schedule-today','SCHEDULE']",
            "['Schedule: This week','SCHEDULE','schedule-week','SCHEDULE']",
        ):
            self.assertIn(token, SERVER)
        self.assertIn("currentSurface=item[3]", SERVER)

    def test_offline_shell_and_roadmap_include_new_runtime(self):
        self.assertIn('./runtime/tv_progress.js', SW)
        self.assertIn('./runtime/tv_schedule.js', SW)
        self.assertIn('WTV12 — dedicated TV Progress & analytics — delivered.', ROADMAP)
        self.assertIn('WTV13 — dedicated read-only TV Schedule — delivered.', ROADMAP)


if __name__ == "__main__":
    unittest.main()
