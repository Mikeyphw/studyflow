from pathlib import Path
import json, subprocess, textwrap, unittest

WEB=Path(__file__).resolve().parents[1]
ROOT=WEB.parent

class TvBrowserFoundationTest(unittest.TestCase):
    def test_tv_mode_detection_runtime(self):
        script=textwrap.dedent(r'''
            const assert=require('node:assert/strict');
            const {resolveTvBrowserMode,spatialScore}=require('./runtime/tv.js');
            assert.deepEqual(resolveTvBrowserMode('?tv=1','Mozilla/5.0'),{enabled:true,source:'query',forced:true});
            assert.deepEqual(resolveTvBrowserMode('?tv=0','Mozilla/5.0 Tizen 6.0'),{enabled:false,source:'query',forced:true});
            assert.deepEqual(resolveTvBrowserMode('','Mozilla/5.0 Tizen 6.0'),{enabled:true,source:'user-agent',forced:false});
            assert.deepEqual(resolveTvBrowserMode('','Mozilla/5.0 Chrome/120'),{enabled:false,source:'none',forced:false});
            const source={left:100,right:200,top:100,bottom:200,width:100,height:100};
            const right={left:250,right:350,top:100,bottom:200,width:100,height:100};
            const down={left:100,right:200,top:260,bottom:360,width:100,height:100};
            assert.ok(spatialScore(source,right,'right') < spatialScore(source,down,'right') || spatialScore(source,down,'right')===null);
            assert.equal(spatialScore(source,right,'left'),null);
        ''')
        result=subprocess.run(['node','-e',script],cwd=WEB,text=True,capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_lan_server_and_entrypoint_contract(self):
        package=json.loads((WEB/'package.json').read_text())
        self.assertIn('scripts/serve_tv.mjs',package['scripts']['serve:tv'])
        server=(WEB/'scripts/serve_tv.mjs').read_text()
        for token in ('"0.0.0.0"','requestUrl.pathname==="/tv"','Location:"/?tv=1"','networkInterfaces()','STUDYFLOW_TV_PORT'):
            self.assertIn(token,server)
        self.assertIn('--bind 127.0.0.1',package['scripts']['dev'])

    def test_es2019_and_clone_fallback_contract(self):
        config=json.loads((WEB/'tsconfig.json').read_text())
        self.assertEqual(config['compilerOptions']['target'],'ES2019')
        indexed=(WEB/'src/indexeddb.ts').read_text()
        self.assertIn('typeof nativeClone==="function"',indexed)
        self.assertIn('JSON.parse(JSON.stringify(value))',indexed)

    def test_dpad_bootstrap_css_and_offline_cache(self):
        main=(WEB/'src/main.tsx').read_text(); tv=(WEB/'src/tv.ts').read_text(); css=(WEB/'styles.css').read_text(); sw=(WEB/'service-worker.js').read_text()
        self.assertIn('installTvBrowserAdapter();',main)
        for token in ('moveSpatialFocus','activeModal','controlOwnsArrow','[role="tablist"]','event.stopPropagation()','supportsFlexGap'):
            self.assertIn(token,tv)
        for token in ('html[data-studyflow-tv="true"]','.bottom-nav','outline: 5px solid','--content-max: 1560px','data-studyflow-tv-flex-gap="false"'):
            self.assertIn(token,css)
        self.assertIn('./runtime/tv.js',sw)

    def test_roadmap_and_delivery_notes(self):
        roadmap=(ROOT/'docs/web/WEB_ROADMAP.md').read_text(); doc=(ROOT/'docs/web/WTV00_WTV03_TV_BROWSER_FOUNDATION.md').read_text()
        for token in ('WTV00','WTV01','WTV02','WTV03','WTV04 — 10-foot visual polish — delivered','WTV05 — TV interaction simplification — delivered'):
            self.assertIn(token,roadmap)
        for token in ('geometry-based spatial navigation','secure context','structuredClone','`?tv=0`'):
            self.assertIn(token,doc)

if __name__=='__main__': unittest.main()
