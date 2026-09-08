from pathlib import Path
import subprocess
import textwrap
import unittest

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent
APP = (WEB / "src/react/App.tsx").read_text()
TV = (WEB / "src/tv.ts").read_text()
HISTORY = (WEB / "src/tv_history.ts").read_text()
SERVER = (WEB / "scripts/serve_tv.mjs").read_text()
SW = (WEB / "service-worker.js").read_text()
ROADMAP = (ROOT / "docs/web/WEB_ROADMAP.md").read_text()


class Wtv0809SessionQuestionSetTest(unittest.TestCase):
    def test_session_runtime_is_deterministic_and_es2019_safe(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const s=require('./runtime/tv_session.js');
            assert.equal(s.tvStudySessionPhase('idle',false),'ready');
            assert.equal(s.tvStudySessionPhase('running',false),'running');
            assert.equal(s.tvStudySessionPhase('paused',false),'paused');
            assert.equal(s.tvStudySessionPhase('idle',true),'completed');
            assert.deepEqual(s.buildTvStudySessionSegments(10),[{id:'core',label:'Focused study',minutes:10}]);
            assert.equal(s.buildTvStudySessionSegments(25).reduce((n,x)=>n+x.minutes,0),25);
            assert.equal(s.buildTvStudySessionSegments(60).reduce((n,x)=>n+x.minutes,0),60);
            assert.equal(s.tvStudySessionProgressPercent(25,25*60*1000),0);
            assert.equal(s.tvStudySessionProgressPercent(25,0),100);
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        source = (WEB / "src/tv_session.ts").read_text()
        self.assertNotIn(".at(", source)
        self.assertNotIn("structuredClone", source)

    def test_question_runtime_choice_contract(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const q=require('./runtime/tv_question_set.js');
            assert.equal(q.TV_QUESTION_SET_LIMIT,40);
            assert.equal(q.tvQuestionChoiceOptionId(0),'choice-0');
            assert.equal(q.tvQuestionChoiceIndex('choice-3'),3);
            assert.equal(q.tvQuestionChoiceIndex('bad'),null);
            assert.equal(q.tvQuestionChoiceLabel(0),'A');
            assert.equal(q.tvQuestionProgressPercent(0,4),25);
            assert.equal(q.tvQuestionProgressPercent(3,4),100);
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_history_and_surface_routing_are_real_tv_routes(self):
        self.assertIn('"session"|"questions"', HISTORY)
        self.assertIn('"session","questions"', HISTORY)
        self.assertIn('if(surface==="STUDY_SESSION")return{screen:"session"}', APP)
        self.assertIn('if(surface==="QUESTION_SET")return{screen:"questions"}', APP)
        self.assertIn('screen==="session"&&<TvStudySession', APP)
        self.assertIn('screen==="questions"&&<TvQuestionSet', APP)
        self.assertIn('route.practiceTab==="questions"?"questions":route.practiceTab==="tests"?"mock":route.practiceTab==="reviews"?"review":"practice"', APP)

    def test_study_session_uses_canonical_focus_and_media_controls(self):
        for token in (
            'data-tv-control-action="START"',
            'data-tv-control-action="PAUSE"',
            'data-tv-control-action="RESUME"',
            'data-tv-control-action="NEXT"',
            'data-tv-control-action="COMPLETE"',
            'store.startFocus',
            'store.pauseFocus',
            'store.completeFocus',
            'studyflow:tv-media-toggle',
            'Segment changes are presentation-only',
        ):
            self.assertIn(token, APP)
        for token in ('MediaPlayPause', 'code===179||code===415||code===19', 'installMediaToggleNavigation'):
            self.assertIn(token, TV)

    def test_question_set_submits_real_question_attempts(self):
        for token in (
            'className={`tv-choice${selection===choice?" is-selected":""}`}',
            'data-tv-control-action="SELECT_ANSWER"',
            'data-tv-control-action="SUBMIT"',
            'data-tv-control-action="SHOW_EXPLANATION"',
            'store.answerQuestion(current.id,selection)',
            'Question attempt recorded from TV.',
            'Your submitted choice was incorrect and StudyFlow scheduled the canonical review/error evidence.',
            'Typed-answer question',
        ):
            self.assertIn(token, APP)

    def test_phone_remote_tracks_surface_and_offers_choice_shortcuts(self):
        for token in (
            "let currentSurface='TODAY'",
            "currentSurface=surface",
            "command(currentSurface,action)",
            "'SUBMIT'",
            "command(currentSurface==='MOCK_EXAM'?'MOCK_EXAM':'QUESTION_SET','SELECT_ANSWER',{optionId:'choice-'+index})",
            "Choose '+String.fromCharCode(65+index)",
        ):
            self.assertIn(token, SERVER)

    def test_offline_shell_and_roadmap_include_new_runtime(self):
        self.assertIn('./runtime/tv_session.js', SW)
        self.assertIn('./runtime/tv_question_set.js', SW)
        self.assertIn('WTV08 — dedicated TV Study Session — delivered.', ROADMAP)
        self.assertIn('WTV09 — dedicated TV Question Set — delivered.', ROADMAP)


if __name__ == "__main__":
    unittest.main()
