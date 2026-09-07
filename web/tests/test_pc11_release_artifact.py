from __future__ import annotations
import hashlib, json, os, subprocess, tempfile, unittest, zipfile
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class PC11ReleaseArtifactTest(unittest.TestCase):
    def vendor_fixture(self,root:Path):
        specs={
            'react':('19.2.8',['cjs/react.production.js']),
            'react-dom':('19.2.8',['cjs/react-dom.production.js','cjs/react-dom-client.production.js']),
            'scheduler':('0.27.0',['cjs/scheduler.production.js']),
        }
        for name,(version,files) in specs.items():
            pkg=root/'node_modules'/name; pkg.mkdir(parents=True)
            (pkg/'package.json').write_text(json.dumps({'name':name,'version':version}),encoding='utf-8')
            (pkg/'LICENSE').write_text('MIT License\nfixture',encoding='utf-8')
            for rel in files:
                p=pkg/rel; p.parent.mkdir(parents=True,exist_ok=True)
                body=f'"use strict"; module.exports={{fixture:{name!r}}};\n'
                if rel.endswith('react-dom-client.production.js'):
                    body += 'const reactDiagnostic="https://react.dev/errors/418";\n'
                    body += 'const svgNamespace="http://www.w3.org/2000/svg";\n'
                p.write_text(body,encoding='utf-8')
    def run_cmd(self,args,env):
        r=subprocess.run(args,cwd=WEB,text=True,capture_output=True,env=env)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)
        return r
    def test_dist_is_self_contained_and_release_manifest_is_hash_verified(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td); vendor=base/'vendor'; dist=base/'dist'; self.vendor_fixture(vendor)
            env=os.environ.copy(); env['STUDYFLOW_VENDOR_ROOT']=str(vendor); env['STUDYFLOW_DIST_DIR']=str(dist)
            self.run_cmd(['node','scripts/build_dist.mjs'],env); self.run_cmd(['node','scripts/check_release.mjs'],env)
            self.assertTrue((dist/'vendor/react.production.js').is_file()); self.assertTrue((dist/'runtime/product.js').is_file())
            executable='\n'.join(p.read_text(encoding='utf-8') for p in dist.rglob('*') if p.is_file() and p.suffix in {'.js','.html','.webmanifest','.css'})
            self.assertNotIn('node_modules/',executable)
            self.assertIn('https://react.dev/errors/418',(dist/'vendor/react-dom-client.production.js').read_text(encoding='utf-8'))
            manifest=json.loads((dist/'release-manifest.json').read_text()); self.assertEqual(manifest['runtimeDependency'],'self-contained'); self.assertGreater(len(manifest['files']),20)
            for item in manifest['files']:
                data=(dist/item['path']).read_bytes(); self.assertEqual(hashlib.sha256(data).hexdigest(),item['sha256']); self.assertEqual(len(data),item['bytes'])
    def test_release_checker_allows_inert_vendor_urls_but_rejects_network_calls(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td); vendor=base/'vendor'; dist=base/'dist'; self.vendor_fixture(vendor)
            env=os.environ.copy(); env.update(STUDYFLOW_VENDOR_ROOT=str(vendor),STUDYFLOW_DIST_DIR=str(dist))
            self.run_cmd(['node','scripts/build_dist.mjs'],env)
            self.run_cmd(['node','scripts/check_release.mjs'],env)
            target=dist/'runtime/product.js'; target.write_text(target.read_text(encoding='utf-8')+'\nfetch("https://example.invalid/runtime");\n',encoding='utf-8')
            r=subprocess.run(['node','scripts/check_release.mjs'],cwd=WEB,text=True,capture_output=True,env=env)
            self.assertNotEqual(r.returncode,0); self.assertIn('remote network reference',r.stderr+r.stdout); self.assertIn('runtime/product.js',r.stderr+r.stdout)
    def test_release_checker_rejects_remote_css_assets(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td); vendor=base/'vendor'; dist=base/'dist'; self.vendor_fixture(vendor)
            env=os.environ.copy(); env.update(STUDYFLOW_VENDOR_ROOT=str(vendor),STUDYFLOW_DIST_DIR=str(dist))
            self.run_cmd(['node','scripts/build_dist.mjs'],env)
            css=dist/'styles.css'; css.write_text(css.read_text(encoding='utf-8')+'\n.bad{background:url("https://example.invalid/a.png")}\n',encoding='utf-8')
            r=subprocess.run(['node','scripts/check_release.mjs'],cwd=WEB,text=True,capture_output=True,env=env)
            self.assertNotEqual(r.returncode,0); self.assertIn('remote network reference',r.stderr+r.stdout); self.assertIn('styles.css',r.stderr+r.stdout)
    def test_release_zip_is_deterministic_and_has_sha256_sidecar(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td); vendor=base/'vendor'; dist=base/'dist'; release=base/'release'; self.vendor_fixture(vendor)
            env=os.environ.copy(); env.update(STUDYFLOW_VENDOR_ROOT=str(vendor),STUDYFLOW_DIST_DIR=str(dist),STUDYFLOW_RELEASE_DIR=str(release))
            self.run_cmd(['node','scripts/build_dist.mjs'],env); self.run_cmd(['python3','scripts/package_release.py'],env); first=(release/'studyflow-local-1.0.0.zip').read_bytes(); self.run_cmd(['python3','scripts/package_release.py'],env); second=(release/'studyflow-local-1.0.0.zip').read_bytes(); self.assertEqual(first,second)
            side=(release/'studyflow-local-1.0.0.zip.sha256').read_text(); self.assertIn(hashlib.sha256(first).hexdigest(),side)
            with zipfile.ZipFile(release/'studyflow-local-1.0.0.zip') as z:
                self.assertIn('index.html',z.namelist()); self.assertIn('vendor/react-dom-client.production.js',z.namelist()); self.assertIn('release-manifest.json',z.namelist()); self.assertTrue(all(i.date_time==(1980,1,1,0,0,0) for i in z.infolist()))
    def test_dist_builder_rejects_wrong_dependency_version(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td); vendor=base/'vendor'; dist=base/'dist'; self.vendor_fixture(vendor); p=vendor/'node_modules/react/package.json'; d=json.loads(p.read_text()); d['version']='0.0.0'; p.write_text(json.dumps(d))
            env=os.environ.copy(); env.update(STUDYFLOW_VENDOR_ROOT=str(vendor),STUDYFLOW_DIST_DIR=str(dist)); r=subprocess.run(['node','scripts/build_dist.mjs'],cwd=WEB,text=True,capture_output=True,env=env); self.assertNotEqual(r.returncode,0); self.assertIn('mismatch',r.stderr+r.stdout)
