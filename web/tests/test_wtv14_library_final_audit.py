from pathlib import Path
import json
import subprocess
import textwrap
import unittest

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent
APP = (WEB / "src/react/App.tsx").read_text()
HISTORY = (WEB / "src/tv_history.ts").read_text()
SERVER = (WEB / "scripts/serve_tv.mjs").read_text()
SW = (WEB / "service-worker.js").read_text()
CSS = (WEB / "styles.css").read_text()
ROADMAP = (ROOT / "docs/web/WEB_ROADMAP.md").read_text()
AUDIT = (ROOT / "docs/web/WTV14_LIBRARY_FINAL_AUDIT_SEAL.md").read_text()


class Wtv14LibraryFinalAuditTest(unittest.TestCase):
    def test_library_runtime_filter_navigation_text_and_pdf_helpers(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const l=require('./runtime/tv_library.js');
            const materials=[
              {id:'b',title:'B',attachmentId:'att-1',locator:'local-blob:att-1',mimeType:'text/plain',archived:false},
              {id:'a',title:'A web',attachmentId:null,locator:'https://example.com/ref',mimeType:'text/html',archived:false},
              {id:'c',title:'C old',attachmentId:null,locator:null,mimeType:null,archived:true},
            ];
            assert.deepEqual(l.tvLibraryMaterials(materials,'local').map(x=>x.id),['b']);
            assert.deepEqual(l.tvLibraryMaterials(materials,'reference').map(x=>x.id),['a']);
            assert.deepEqual(l.tvLibraryMaterials(materials,'all').map(x=>x.id),['a','b','c']);
            assert.equal(l.tvLibraryNextIndex(0,3,-1),0);
            assert.equal(l.tvLibraryNextIndex(1,3,1),2);
            assert.equal(l.tvLibraryNextIndex(2,3,1),2);
            const pages=l.paginateTvText('one '.repeat(1000),600);
            assert.ok(pages.length>1);
            assert.ok(pages.every(page=>page.length<=610));
            assert.equal(l.tvPdfPageUrl('blob:test#old',3),'blob:test#page=3&zoom=page-width');
            assert.equal(l.tvPdfPageUrl('blob:test',0),'blob:test#page=1&zoom=page-width');
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_library_is_first_class_history_remote_and_rail_surface(self):
        self.assertIn('"schedule"|"library"', HISTORY)
        self.assertIn('"schedule","library"', HISTORY)
        self.assertIn('if(surface==="LIBRARY")return{screen:"library"}', APP)
        self.assertIn('screen==="library"&&<TvLibrarySurface', APP)
        self.assertIn('["today","plan","practice","progress","schedule","library"]', APP)
        self.assertIn("['Library: All','LIBRARY','library-all','LIBRARY']", SERVER)
        self.assertIn("['Library: Local files','LIBRARY','library-local','LIBRARY']", SERVER)
        self.assertIn("['Library: Web references','LIBRARY','library-reference','LIBRARY']", SERVER)

    def test_library_surface_is_read_only_and_material_viewer_is_remote_first(self):
        start = APP.index('function TvLibrarySurface(')
        end = APP.index('\nfunction TvStudySession(', start)
        surface = APP[start:end]
        for forbidden in ('store.', 'type="file"', 'deleteMaterial(', 'addMaterial(', 'setMaterialReferenceProvenance('):
            self.assertNotIn(forbidden, surface)
        for token in (
            'data-tv-control-target="library-all"',
            'data-tv-control-target="library-local"',
            'data-tv-control-target="library-reference"',
            'data-tv-control-action="PREVIOUS"',
            'data-tv-control-action="NEXT"',
            'data-tv-control-action="START"',
            'Library editing',
        ):
            self.assertIn(token, surface)
        for token in (
            'paginateTvText(text??"")',
            'tvPdfPageUrl(url,pdfPage)',
            'Previous PDF page',
            'Next PDF page',
            'Zoom out',
            'Zoom in',
            'studyflow:tv-media-toggle',
        ):
            self.assertIn(token, APP)

    def test_remote_commands_scope_to_active_modal_and_search_material_uses_library_overlay(self):
        self.assertIn("const scope=document.querySelector<HTMLElement>('[role=\"dialog\"][aria-modal=\"true\"]')??document", APP)
        self.assertIn('navigateScreen("library",true);requestAnimationFrame(()=>openTvOverlay("material",item.id));return', APP)
        self.assertIn('overlay==="material"?entry.overlayId:null', APP)
        self.assertIn('if(entry.overlay){history.back();return}', APP)

    def test_wtv05_goal_controls_regression_is_corrected_without_mutating_progress(self):
        self.assertIn('function TvGoalQuickActions(', APP)
        self.assertIn('store.completeGoal(g.id)', APP)
        self.assertIn('store.toggleGoal(g.id)', APP)
        self.assertIn('{tv&&<TvGoalQuickActions state={state} store={store} run={run}/>}', APP)
        progress_start = APP.index('function TvProgressAnalytics(')
        progress_end = APP.index('\nfunction TvScheduleSurface(', progress_start)
        progress = APP[progress_start:progress_end]
        self.assertNotIn('store.', progress)
        self.assertIn('Goals, exports & maintenance', progress)
        self.assertIn('Existing goal completion and pause/resume stay available from Today.', progress)

    def test_wtv05_authoring_handoffs_are_sealed_after_full_audit(self):
        settings_start = APP.index('function TvSettingsDialog(')
        settings_end = APP.index('\nfunction TvOnboardingHandoff(', settings_start)
        tv_settings = APP[settings_start:settings_end]
        self.assertIn('allowCleanup={false}', tv_settings)
        self.assertNotIn('setScheduleConfiguration(', tv_settings)
        self.assertNotIn('setReviewIntervalsDays(', tv_settings)
        self.assertNotIn('onboardingCompleted:false', tv_settings)

        self.assertIn('allowCleanup&&Boolean(report?.orphanAttachmentIds.length)', APP)
        self.assertIn('if(tvBrowser){setToast("Writing Workspace continues on a phone or computer.");return}', APP)
        self.assertIn('focus.status!=="running"&&writing&&activeActivity&&!tv', APP)
        self.assertIn('function TvOnboardingHandoff(', APP)
        self.assertIn('tvBrowser?(tvSetupDismissed?null:<TvOnboardingHandoff', APP)
        self.assertIn('autoFocus={!isTvBrowserUi()}', APP)

    def test_wtv10_live_countdown_and_all_dedicated_surfaces_remain_wired(self):
        self.assertIn('setInterval(()=>tick(v=>v+1),1000)', APP)
        self.assertIn('tvMockRemainingMs(duration,startedAt,Date.now(),pausedAt,pausedAccumulated)', APP)
        for screen in ('session', 'questions', 'mock', 'review', 'progress', 'schedule', 'library'):
            self.assertIn(f'screen==="{screen}"', APP)
        for surface in ('STUDY_SESSION', 'QUESTION_SET', 'REVIEW', 'MOCK_EXAM', 'PROGRESS', 'SCHEDULE', 'LIBRARY'):
            self.assertIn(surface, APP)

    def test_final_compatibility_and_offline_shell_cover_every_tv_runtime(self):
        config = json.loads((WEB / "tsconfig.json").read_text())
        self.assertEqual(config["compilerOptions"]["target"], "ES2019")
        tv_tail = CSS[CSS.index('/* WTV04-WTV05'):]
        self.assertNotIn('clamp(', tv_tail)
        self.assertNotIn(' min(', tv_tail)
        runtime_files = sorted(path.name for path in (WEB / "runtime").glob("tv*.js"))
        self.assertIn("tv_library.js", runtime_files)
        for name in runtime_files:
            self.assertIn(f'./runtime/{name}', SW, name)
            compiled = (WEB / "runtime" / name).read_text()
            self.assertNotIn('?.', compiled, name)
            self.assertNotIn('??', compiled, name)

    def test_roadmap_audit_is_complete_and_records_corrections(self):
        for phase in range(15):
            self.assertIn(f'WTV{phase:02d}', ROADMAP)
            self.assertIn(f'WTV{phase:02d}', AUDIT)
        self.assertIn('WTV14 — TV Library + roadmap promise audit / final seal — delivered.', ROADMAP)
        self.assertIn('PASS AFTER CORRECTION', AUDIT)
        self.assertIn('No unresolved browser-TV roadmap promise remains after WTV14.', AUDIT)
        self.assertIn('WTV12 Progress remains read-only', AUDIT)

    def test_normal_web_primary_routes_remain_unchanged(self):
        self.assertIn('tvBrowser?["today","plan","practice","progress","schedule","library"]:["today","plan","practice","progress"]', APP)
        self.assertIn('if(!tvBrowser){const onHash=', APP)
        self.assertIn('mainRef.current?.focus({preventScroll:true})', APP)


if __name__ == "__main__":
    unittest.main()
