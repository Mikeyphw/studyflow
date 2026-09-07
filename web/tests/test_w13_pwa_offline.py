from pathlib import Path
from PIL import Image
import json,re,subprocess,unittest
ROOT=Path(__file__).resolve().parents[1]

class W13PwaOfflineTest(unittest.TestCase):
    def test_manifest_install_contract(self):
        manifest=json.loads((ROOT/'manifest.webmanifest').read_text())
        self.assertEqual(manifest['display'],'standalone'); self.assertEqual(manifest['start_url'],'./#today'); self.assertEqual(manifest['scope'],'./')
        sizes={item['sizes'] for item in manifest['icons']}; self.assertIn('192x192',sizes); self.assertIn('512x512',sizes); self.assertTrue(any('maskable' in item.get('purpose','') for item in manifest['icons']))
        self.assertIn('rel="manifest"',(ROOT/'index.html').read_text())
    def test_install_icons_have_declared_dimensions(self):
        for name,size in [('icon-192.png',(192,192)),('icon-512.png',(512,512)),('maskable-512.png',(512,512))]:
            with Image.open(ROOT/'icons'/name) as image: self.assertEqual(image.size,size)
    def test_service_worker_app_shell_is_self_contained(self):
        worker=(ROOT/'service-worker.js').read_text(); self.assertIn('CACHE_VERSION',worker); self.assertIn('cache.addAll(APP_SHELL)',worker); self.assertIn('request.mode === "navigate"',worker); self.assertNotIn('http://',worker); self.assertNotIn('https://',worker)
        quoted=re.findall(r'"(\./[^"?]+)"',worker)
        for rel in quoted:
            if rel=='./': continue
            self.assertTrue((ROOT/rel[2:]).exists(),rel)
        r=subprocess.run(['node','--check','service-worker.js'],cwd=ROOT,text=True,capture_output=True); self.assertEqual(r.returncode,0,r.stderr)
    def test_registration_handles_install_and_offline_status(self):
        pwa=(ROOT/'src/pwa.js').read_text(); app=(ROOT/'app.js').read_text()
        for token in ('serviceWorker.register','beforeinstallprompt','appinstalled','offline'): self.assertIn(token,pwa)
        self.assertIn('registerStudyFlowPwa',app); self.assertIn('id="install-app"',(ROOT/'index.html').read_text()); self.assertIn('id="pwa-status"',(ROOT/'index.html').read_text())

if __name__=='__main__': unittest.main()
