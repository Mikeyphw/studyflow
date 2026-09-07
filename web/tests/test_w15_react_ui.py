from pathlib import Path
import json, subprocess, unittest

ROOT=Path(__file__).resolve().parents[1]

class W15ReactUiTest(unittest.TestCase):
    def test_react_is_pinned_and_ui_uses_external_store(self):
        pkg=json.loads((ROOT/'package.json').read_text())
        self.assertEqual(pkg['dependencies'],{'react':'19.2.8','react-dom':'19.2.8'})
        self.assertEqual(pkg['devDependencies'],{'typescript':'5.8.3'})
        ui=(ROOT/'src/react/App.tsx').read_text()
        main=(ROOT/'src/main.tsx').read_text()
        self.assertIn('useSyncExternalStore',ui)
        self.assertIn('createRoot',main)
        self.assertIn('createStudyStore',main)
        self.assertIn('createBrowserPersistence',main)
        self.assertLess(main.index('const store='),main.index('createRoot(node).render'))

    def test_react_preserves_primary_product_destinations(self):
        ui=(ROOT/'src/react/App.tsx').read_text()
        for token in ('id="today"','id="plan"','id="practice"','id="progress"','id="plan-schedule"','id="plan-content"','id="plan-library"','id="practice-reviews"','id="practice-questions"','id="practice-errors"','id="progress-summary"','id="progress-performance"','id="progress-goals"','id="progress-tools"'):
            self.assertIn(token,ui)

    def test_browser_bootloader_and_service_worker_have_no_remote_runtime(self):
        loader=(ROOT/'app.js').read_text(); worker=(ROOT/'service-worker.js').read_text()
        for text in (loader,worker):
            self.assertNotIn('https://',text); self.assertNotIn('http://',text)
        self.assertIn('react-dom/client',loader)
        self.assertIn('react-dom-client.production.js',worker)
        for path in ('app.js','service-worker.js','runtime/main.js','runtime/react/App.js'):
            result=subprocess.run(['node','--check',path],cwd=ROOT,text=True,capture_output=True)
            self.assertEqual(result.returncode,0,result.stderr)

if __name__=='__main__': unittest.main()
