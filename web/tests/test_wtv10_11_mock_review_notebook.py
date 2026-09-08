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


class Wtv1011MockReviewNotebookTest(unittest.TestCase):
    def test_mock_runtime_timer_and_progress_are_deterministic(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const m=require('./runtime/tv_mock_exam.js');
            const minute=60*1000;
            assert.equal(m.tvMockRemainingMs(10,null,1000,null,0),10*minute);
            assert.equal(m.tvMockRemainingMs(10,1000,1000+3*minute,null,0),7*minute);
            assert.equal(m.tvMockRemainingMs(10,1000,1000+5*minute,1000+2*minute,0),8*minute);
            assert.equal(m.tvMockRemainingMs(10,1000,1000+5*minute,null,2*minute),7*minute);
            assert.equal(m.tvMockProgressPercent(10,10*minute),0);
            assert.equal(m.tvMockProgressPercent(10,0),100);
            assert.equal(m.tvMockAnsweredCount({a:'x',b:'',c:' y '}),2);
            assert.equal(m.tvMockQuestionProgressPercent(1,4),50);
            assert.equal(m.tvMockChoiceOptionId(2),'choice-2');
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        source = (WEB / "src/tv_mock_exam.ts").read_text()
        self.assertNotIn(".at(", source)
        self.assertNotIn("structuredClone", source)

    def test_review_runtime_options_and_progress(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const r=require('./runtime/tv_review_notebook.js');
            assert.deepEqual(r.TV_REVIEW_RATINGS,['again','hard','good','easy']);
            assert.equal(r.tvReviewRatingOptionId('hard'),'rating-hard');
            assert.equal(r.tvErrorCategoryOptionId('time-pressure'),'category-time-pressure');
            assert.equal(r.tvReviewProgressPercent(2,4),50);
            assert.equal(r.tvReviewIndexAfterCompletion(3,2),1);
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_mock_and_review_are_real_history_surfaces(self):
        self.assertIn('"mock"|"review"', HISTORY)
        self.assertIn('"mock","review"', HISTORY)
        self.assertIn('if(surface==="REVIEW")return{screen:"review"}', APP)
        self.assertIn('if(surface==="MOCK_EXAM")return{screen:"mock"}', APP)
        self.assertIn('screen==="mock"&&<TvMockExamStudio', APP)
        self.assertIn('screen==="review"&&<TvReviewNotebook', APP)
        self.assertIn('route.practiceTab==="tests"?"mock"', APP)
        self.assertIn('route.practiceTab==="reviews"?"review"', APP)

    def test_mock_exam_persists_canonical_result_and_guards_back(self):
        for token in (
            'store.recordMockExam(preview,answers)',
            'data-tv-control-action="MARK_FOR_REVIEW"',
            'data-tv-control-action="REVIEW_RESULTS"',
            'data-tv-control-action="SELECT_ANSWER"',
            'data-tv-control-action="SUBMIT"',
            'data-tv-control-action="PAUSE"',
            'data-tv-control-action="RESUME"',
            'studyflowTvExamActive',
            'studyflow:tv-exam-back',
            'beforeunload',
            'browser/remote Back is guarded while the exam is active',
            'screen==="mock"&&next!=="mock"&&document.documentElement.dataset.studyflowTvExamActive==="true"',
        ):
            self.assertIn(token, APP)

    def test_review_surface_uses_real_review_and_error_mutations(self):
        for token in (
            'TV_REVIEW_RATINGS.map',
            'tvReviewRatingOptionId(rating)',
            'store.completeReview(currentReview.id,{rating:rating as any})',
            'store.reclassifyError(currentError.id',
            'store.resolveError(currentError.id)',
            'data-tv-control-option="resolve-error"',
            'tvErrorCategoryOptionId(category)',
            'data-tv-review-primary="true"',
            'title(rating)',
        ):
            self.assertIn(token, APP)

    def test_phone_remote_supports_mock_and_review_specific_controls(self):
        for token in (
            "'Mock Exam':'MOCK_EXAM'",
            "'MARK_FOR_REVIEW'",
            "'REVIEW_RESULTS'",
            "currentSurface==='MOCK_EXAM'?'MOCK_EXAM':'QUESTION_SET'",
            "command('REVIEW','COMPLETE',{optionId:'rating-'+rating})",
            "command('REVIEW','MARK_FOR_REVIEW',{optionId:'category-'+category})",
            "command('REVIEW','COMPLETE',{optionId:'resolve-error'})",
        ):
            self.assertIn(token, SERVER)

    def test_offline_shell_and_roadmap_include_new_runtime(self):
        self.assertIn('./runtime/tv_mock_exam.js', SW)
        self.assertIn('./runtime/tv_review_notebook.js', SW)
        self.assertIn('WTV10 — dedicated TV Mock Exam Studio — delivered.', ROADMAP)
        self.assertIn('WTV11 — dedicated TV Review + Error Notebook — delivered.', ROADMAP)


if __name__ == "__main__":
    unittest.main()
