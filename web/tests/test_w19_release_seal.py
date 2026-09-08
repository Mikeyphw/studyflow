from pathlib import Path
import json, os, subprocess, unittest

ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parent

class W19ReleaseSealTest(unittest.TestCase):
    def test_release_identity_is_consistent(self):
        package=json.loads((ROOT/'package.json').read_text()); lock=json.loads((ROOT/'package-lock.json').read_text())
        self.assertEqual(package['version'],'1.0.0')
        self.assertEqual(lock['version'],'1.0.0')
        self.assertEqual(lock['packages']['']['version'],'1.0.0')
        self.assertIn('release:check',package['scripts'])
        release=(ROOT/'src/release.ts').read_text()
        for token in ('version:"1.0.0"','STATE_SCHEMA_VERSION','STUDYFLOW_DB_VERSION','PORTABLE_FORMAT_VERSION','evaluateReleaseReadiness'):
            self.assertIn(token,release)
        self.assertIn('__STUDYFLOW_CACHE_VERSION__',(ROOT/'service-worker.js').read_text())
        build=(ROOT/'scripts/build_dist.mjs').read_text()
        self.assertIn('studyflow-local-${pkg.version}-${createHash("sha256").update(cacheSeed)',build)

    def test_release_readiness_is_truthful_about_browser_storage(self):
        source='''
        import assert from 'node:assert/strict';
        import { createInitialStudyState } from './runtime/domain.js';
        import { evaluateReleaseReadiness, STUDYFLOW_LOCAL_RELEASE } from './runtime/release.js';
        const state=createInitialStudyState({now:'2026-09-07T04:00:00.000Z'});
        assert.equal(STUDYFLOW_LOCAL_RELEASE.version,'1.0.0');
        assert.equal(evaluateReleaseReadiness(state,'indexeddb').status,'ready');
        const fallback=evaluateReleaseReadiness(state,'localstorage');
        assert.equal(fallback.status,'degraded');
        assert.match(fallback.reasons[0],/IndexedDB is unavailable/);
        const invalid=structuredClone(state); invalid.activePlanId='missing';
        assert.equal(evaluateReleaseReadiness(invalid,'indexeddb').status,'blocked');
        '''
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=ROOT,text=True,capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_release_shell_check_passes_without_network_dependencies(self):
        env=dict(os.environ); env['STUDYFLOW_RELEASE_SKIP_NODE_MODULES']='1'
        result=subprocess.run(['node','scripts/check_release.mjs'],cwd=ROOT,text=True,capture_output=True,env=env)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)
        self.assertIn('StudyFlow Local 1.0.0 release shell OK',result.stdout)

    def test_release_docs_and_roadmap_are_sealed(self):
        roadmap=(REPO/'docs/web/WEB_ROADMAP.md').read_text()
        self.assertIn('W18 | Accessibility/testing/offline/storage hardening — delivered',roadmap)
        self.assertIn('W19 | StudyFlow Local 1.0 release seal — delivered',roadmap)
        doc=(REPO/'docs/web/W18_W19_HARDENING_RELEASE_SEAL.md').read_text()
        for token in ('StudyFlow Local 1.0.0','no required backend','unknown evidence remains distinct','npm run release:check'):
            self.assertIn(token,doc)

    def test_release_runtime_has_no_remote_application_dependency(self):
        files=['app.js','service-worker.js','src/main.tsx','src/react/App.tsx','src/storage.ts','src/indexeddb.ts','src/pwa.ts']
        joined='\n'.join((ROOT/name).read_text() for name in files).lower()
        self.assertNotIn('http://',joined); self.assertNotIn('https://',joined)
        for token in ('firebase','supabase','google-analytics','segment.io'):
            self.assertNotIn(token,joined)

if __name__=='__main__': unittest.main()
