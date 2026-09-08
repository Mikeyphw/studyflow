from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = (ROOT / "src/react/App.tsx").read_text()
CSS = (ROOT / "styles.css").read_text()
TV = (ROOT / "src/tv.ts").read_text()
ROADMAP = (ROOT.parent / "docs/web/WEB_ROADMAP.md").read_text()


class Wtv0405TenFootRemoteWorkflowsTest(unittest.TestCase):
    def test_tv_presentation_is_explicitly_gated(self):
        self.assertIn('html[data-studyflow-tv="true"] .tv-continue-card', CSS)
        self.assertIn('html[data-studyflow-tv="true"] .tv-question-stage', CSS)
        self.assertIn('@media (min-width: 1600px)', CSS)
        self.assertIn('html[data-studyflow-tv="true"] .app-main', CSS)
        self.assertNotIn('clamp(', CSS[CSS.index('/* WTV04-WTV05'):])

    def test_remote_first_branches_exist_without_forking_domain(self):
        self.assertIn('function TvPlan(', APP)
        self.assertIn('function TvPractice(', APP)
        self.assertIn('function TvSettingsDialog(', APP)
        self.assertIn('function TvContinueElsewhere(', APP)
        self.assertIn('if(isTvBrowserUi())return <TvPlan', APP)
        self.assertIn('if(isTvBrowserUi())return <TvPractice', APP)
        self.assertIn('tvReadOnly', APP)

    def test_tv_keeps_core_remote_actions(self):
        self.assertIn('store.completeReview(r.id,{rating:"again"', APP)
        self.assertIn('store.completeReview(r.id,{rating:"good"', APP)
        self.assertIn('store.completeReview(r.id,{rating:"easy"', APP)
        self.assertIn('store.resolveError(e.id)', APP)
        self.assertIn('Reveal answer', APP)
        self.assertIn('store.switchPlan(e.target.value)', APP)

    def test_typing_and_destructive_workflows_have_handoffs(self):
        for text in (
            'Quick Add',
            'Writing Workspace',
            'Plan editing',
            'Content authoring & import',
            'Graded answer entry & question import',
            'Portable backups & imports',
            'Coach exports & schedule mutations',
        ):
            self.assertIn(text, APP)
        self.assertIn('Continue this edit on a phone or computer', APP)
        self.assertIn('Destructive reset is unavailable in TV mode.', APP)

    def test_adapter_exposes_remote_first_contract(self):
        self.assertIn('root.dataset.studyflowTvInputMode="remote-first"', TV)
        self.assertIn('delete root.dataset.studyflowTvInputMode', TV)

    def test_roadmap_marks_batch_delivered(self):
        self.assertIn('WTV04 — 10-foot visual polish — delivered.', ROADMAP)
        self.assertIn('WTV05 — TV interaction simplification — delivered.', ROADMAP)


if __name__ == "__main__":
    unittest.main()
