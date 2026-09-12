from pathlib import Path
import json, os, subprocess, unittest

ROOT=Path(__file__).resolve().parents[1]

class W14TypeScriptMigrationTest(unittest.TestCase):
    def test_strict_typescript_contract_and_runtime_parity(self):
        cfg=json.loads((ROOT/'tsconfig.json').read_text())
        self.assertTrue(cfg['compilerOptions']['strict'])
        self.assertEqual(cfg['compilerOptions']['module'],'CommonJS')
        types=(ROOT/'src/types.ts').read_text()
        for token in ('interface StudyState','interface StudyStore','interface StudyRepository','interface AttachmentRepository','interface SchedulePreview','interface PortableImportPreview'):
            self.assertIn(token,types)
        if os.environ.get('STUDYFLOW_EXTERNAL_RUNTIME_PARITY') == '1':
            # The Gradle Attention seal runs compile_runtime.mjs once as a dedicated gate.
            # Avoid launching a second tsc from the historical W14 regression suite.
            self.assertTrue((ROOT/'runtime'/'main.js').is_file())
        else:
            result=subprocess.run(['node','scripts/compile_runtime.mjs'],cwd=ROOT,text=True,capture_output=True)
            self.assertEqual(result.returncode,0,result.stderr or result.stdout)
            self.assertIn('TypeScript runtime parity OK',result.stdout)

    def test_legacy_javascript_sources_are_tombstones_only(self):
        for name in ('domain.js','indexeddb.js','storage.js','state.js','portable.js','pwa.js','events.js','render.js'):
            text=(ROOT/'src'/name).read_text()
            self.assertIn('migration tombstone',text)
            self.assertLess(len(text),500)
        loader=(ROOT/'app.js').read_text()
        self.assertNotIn('/src/',loader)
        self.assertIn('runtime/main.js',loader)

    def test_domain_remains_framework_and_browser_independent(self):
        domain=(ROOT/'src/domain.ts').read_text().lower()
        for token in ('react','document.','window.','localstorage','indexeddb','fetch('):
            self.assertNotIn(token,domain)

if __name__=='__main__': unittest.main()
