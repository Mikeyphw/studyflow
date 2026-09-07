from __future__ import annotations
import argparse, json, re, subprocess, sys, tomllib
from html.parser import HTMLParser
from pathlib import Path

WEB=Path(__file__).resolve().parents[1]; ROOT=WEB.parent

def _text(path:Path, errors:list[str])->str:
    try:return path.read_text(encoding='utf-8')
    except Exception as exc: errors.append(f'missing/unreadable {path.relative_to(ROOT)}: {exc}'); return ''
def _require(errors:list[str], condition:bool, message:str)->None:
    if not condition: errors.append(message)

def validate_contract()->list[str]:
    e=[]
    try:data=tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8'))
    except Exception as exc:return [f'.devtool.toml invalid: {exc}']
    target=data.get('targets',{}).get('studyflow_web',{})
    _require(e,target.get('type')=='node','studyflow_web must remain a Node target'); _require(e,target.get('root')=='web','studyflow_web root must remain web'); _require(e,target.get('execution_environment')=='native-termux','studyflow_web must remain native-termux'); _require(e,'tasks' not in target,'web target must not define custom task overrides')
    v=target.get('validation',{}); _require(e,v.get('max_workers')==1,'web validation max_workers must remain 1'); _require(e,v.get('cpu_limit')==1,'web validation cpu_limit must remain 1'); _require(e,v.get('parallel') is False,'web validation parallel must remain false')
    try:pkg=json.loads((WEB/'package.json').read_text()); lock=json.loads((WEB/'package-lock.json').read_text())
    except Exception as exc:return e+[f'package metadata invalid: {exc}']
    _require(e,pkg.get('private') is True,'web package must remain private')
    _require(e,pkg.get('dependencies')=={'react':'19.2.8','react-dom':'19.2.8'},'React runtime dependencies must be pinned')
    _require(e,pkg.get('devDependencies')=={'typescript':'5.8.3'},'TypeScript dependency must be pinned')
    for script in ('build','test','lint','dev'): _require(e,script in pkg.get('scripts',{}),f'missing npm script: {script}')
    _require(e,lock.get('lockfileVersion')==3,'package-lock must remain v3')
    for key in ('node_modules/react','node_modules/react-dom','node_modules/scheduler','node_modules/typescript'): _require(e,key in lock.get('packages',{}),f'lock missing {key}')
    return e

class Shell(HTMLParser):
    def __init__(self):super().__init__();self.scripts=[];self.links=[];self.ids=set();self.metas=[];self.doctype=False
    def handle_decl(self,decl):self.doctype=self.doctype or decl.lower().strip()=='doctype html'
    def handle_starttag(self,tag,attrs):
        d=dict(attrs); self.ids.add(d['id']) if d.get('id') else None
        if tag=='script':self.scripts.append(d)
        if tag=='link':self.links.append(d)
        if tag=='meta':self.metas.append(d)

def validate_shell()->list[str]:
    e=validate_contract(); html=_text(WEB/'index.html',e); ui=_text(WEB/'src/react/App.tsx',e); p=Shell(); p.feed(html)
    _require(e,p.doctype,'index must use HTML5 doctype'); _require(e,'root' in p.ids,'React root missing'); _require(e,any(m.get('name')=='viewport' and 'width=device-width' in str(m.get('content','')) for m in p.metas),'mobile viewport missing')
    _require(e,len(p.scripts)==1 and p.scripts[0].get('src')=='app.js' and 'defer' in p.scripts[0],'index must load one deferred local app.js bootloader')
    _require(e,any(l.get('rel')=='stylesheet' and l.get('href')=='styles.css' for l in p.links),'styles.css link missing'); _require(e,any(l.get('rel')=='manifest' for l in p.links),'PWA manifest link missing')
    for token in ('id="today"','id="plan"','id="practice"','id="progress"','id="plan-overview"','id="plan-schedule"','id="plan-content"','id="plan-library"','id="practice-reviews"','id="practice-questions"','id="practice-tests"','id="practice-errors"','id="progress-summary"','id="progress-performance"','id="progress-goals"','id="progress-tools"'):_require(e,token in ui,f'React semantic destination missing: {token}')
    _require(e,'http://' not in html and 'https://' not in html,'runtime shell must not depend on remote URLs')
    return e

def validate_visual()->list[str]:
    e=[]; css=_text(WEB/'styles.css',e)
    for token in ('--bg','--surface','--text','--muted','--border','--accent','safe-area-inset-bottom','focus-visible','prefers-reduced-motion','clamp('):_require(e,token in css,f'CSS system missing {token}')
    _require(e,'@media (max-width:' not in css,'CSS must remain mobile-first (no max-width breakpoint)'); _require(e,bool(re.search(r'@media[^\{]*min-width\s*:\s*(?:700|768)px',css)),'tablet min-width enhancement missing')
    return e

def validate_interactions()->list[str]:
    e=[]; loader=_text(WEB/'app.js',e); ui=_text(WEB/'src/react/App.tsx',e)
    for token in ('useSyncExternalStore','onClick','onSubmit','setInterval','clearInterval','store.startFocus','store.previewSchedule','store.answerQuestion','store.applyPortableImportPreview'):_require(e,token in ui,f'React interaction missing {token}')
    for token in ('PACKAGE_MODULES','react-dom/client','runtime/main.js','fetch(','localRequire'):_require(e,token in loader,f'browser runtime loader missing {token}')
    try:r=subprocess.run(['node','--check','app.js'],cwd=WEB,text=True,capture_output=True); _require(e,r.returncode==0,f'app.js syntax invalid: {r.stderr.strip()}')
    except Exception as exc:e.append(f'node app.js syntax check failed: {exc}')
    return e

def validate_typescript()->list[str]:
    e=[]
    for rel in ('tsconfig.json','src/types.ts','src/domain.ts','src/indexeddb.ts','src/storage.ts','src/state.ts','src/portable.ts','src/pwa.ts','src/main.tsx','src/react/App.tsx','runtime/package.json','tests/test_w14_typescript_migration.py','tests/test_w15_react_ui.py'):_require(e,(WEB/rel).is_file(),f'TypeScript/React migration missing {rel}')
    ts=_text(WEB/'tsconfig.json',e)
    try:cfg=json.loads(ts); _require(e,cfg.get('compilerOptions',{}).get('strict') is True,'TypeScript strict mode must be enabled'); _require(e,cfg.get('compilerOptions',{}).get('module')=='CommonJS','tracked browser runtime must compile to CommonJS')
    except Exception as exc:e.append(f'tsconfig invalid: {exc}')
    types=_text(WEB/'src/types.ts',e)
    for token in ('interface StudyState','interface StudyStore','interface StudyRepository','interface AttachmentRepository','interface SchedulePreview','interface PortableImportPreview'):_require(e,token in types,f'typed contract missing {token}')
    for legacy in ('domain.js','indexeddb.js','storage.js','state.js','portable.js','pwa.js','events.js','render.js'):
        text=_text(WEB/'src'/legacy,e)
        _require(e,'migration tombstone' in text and len(text)<500,f'legacy source must be a migration tombstone: {legacy}')
    return e

def validate_domain()->list[str]:
    e=[]; d=_text(WEB/'src/domain.ts',e).lower()
    for forbidden in ('react','document.','window.','localstorage','indexeddb','fetch('):_require(e,forbidden not in d,f'domain must not depend on {forbidden}')
    for token in ('createplan','createsubject','createtopic','createactivity','createstudysession','creategoal','creatematerial','createreview','createquestion','createevidence','validateStudyState'.lower()):_require(e,token in d,f'domain API missing {token}')
    return e

def validate_state()->list[str]:
    e=[]; s=_text(WEB/'src/state.ts',e).lower(); main=_text(WEB/'src/main.tsx',e)
    for token in ('startfocus','addsubject','addtopic','previewschedule','answerquestion','applyportableimportpreview'):_require(e,token in s,f'store API missing {token}')
    _require(e,'indexeddb' not in s,'state module must not own IndexedDB'); _require(e,'createBrowserPersistence' in main and 'createStudyStore' in main,'main must compose persistence + store outside React')
    return e

def validate_indexeddb()->list[str]:
    e=[]; s=_text(WEB/'src/indexeddb.ts',e); storage=_text(WEB/'src/storage.ts',e)
    for token in ('createIndexedDbPersistence','ATTACHMENT_STORE','objectStore','indexedDB'):_require(e,token in s,f'IndexedDB layer missing {token}')
    _require(e,'createBrowserPersistence' in storage,'storage composition missing createBrowserPersistence'); return e

def validate_pwa()->list[str]:
    e=[]; p=_text(WEB/'src/pwa.ts',e); sw=_text(WEB/'service-worker.js',e); ui=_text(WEB/'src/react/App.tsx',e)
    for token in ('serviceWorker.register','beforeinstallprompt','appinstalled','offline'):_require(e,token in p,f'PWA registration missing {token}')
    for token in ('CACHE_VERSION','cache.addAll(APP_SHELL)','request.mode === "navigate"','runtime/main.js','react.production.js','react-dom-client.production.js'):_require(e,token in sw,f'service worker missing {token}')
    _require(e,'id="install-app"' in ui and 'id="pwa-status"' in ui,'React PWA controls missing'); return e

def validate_react()->list[str]:
    e=[]; ui=_text(WEB/'src/react/App.tsx',e); main=_text(WEB/'src/main.tsx',e)
    for token in ('from "react"','useSyncExternalStore','function Today','function Plan','function Practice','function Progress'):_require(e,token in ui,f'React UI missing {token}')
    _require(e,'createRoot' in main,'React createRoot bootstrap missing'); _require(e,'createStudyStore' in main,'store must be constructed outside React component tree'); return e

def validate_focus():return validate_state()
def validate_plan_content():return validate_state()
def validate_schedule():return validate_state()
def validate_practice():return validate_state()
def validate_progress():return validate_domain()
def validate_portable():
    e=[]; p=_text(WEB/'src/portable.ts',e)
    for token in ('createPortablePackage','buildPortableImportPreview','bytesIncluded'):_require(e,token in p,f'portable layer missing {token}')
    return e

def validate_all()->list[str]:
    groups=(validate_shell,validate_visual,validate_interactions,validate_typescript,validate_domain,validate_state,validate_indexeddb,validate_pwa,validate_react,validate_portable)
    out=[]
    for fn in groups:
        for item in fn():
            if item not in out: out.append(item)
    return out

def main()->int:
    parser=argparse.ArgumentParser(); parser.add_argument('--contract',action='store_true'); parser.add_argument('--shell',action='store_true'); parser.add_argument('--all',action='store_true'); args=parser.parse_args()
    errors=validate_contract() if args.contract else validate_shell() if args.shell else validate_all()
    if errors:
        for error in errors: print(f'ERROR: {error}',file=sys.stderr)
        return 1
    print('StudyFlow web validation passed'); return 0
if __name__=='__main__':raise SystemExit(main())
