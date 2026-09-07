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
    _require(e,pkg.get('version')=='1.0.0','StudyFlow Local release version must be 1.0.0')
    _require(e,lock.get('version')=='1.0.0' and lock.get('packages',{}).get('',{}).get('version')=='1.0.0','package-lock release identity must be 1.0.0')
    _require(e,pkg.get('dependencies')=={'react':'19.2.8','react-dom':'19.2.8'},'React runtime dependencies must be pinned')
    _require(e,pkg.get('devDependencies')=={'typescript':'5.8.3'},'TypeScript dependency must be pinned')
    for script in ('build','test','lint','dev','release:check'): _require(e,script in pkg.get('scripts',{}),f'missing npm script: {script}')
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
    for rel in ('tsconfig.json','src/types.ts','src/domain.ts','src/content_import.ts','src/indexeddb.ts','src/storage.ts','src/state.ts','src/portable.ts','src/pwa.ts','src/main.tsx','src/react/App.tsx','src/modes.ts','src/hardening.ts','src/release.ts','runtime/package.json','tests/test_w14_typescript_migration.py','tests/test_w15_react_ui.py','tests/test_w16_public_exam_workflow.py','tests/test_w17_content_modes.py','tests/test_w18_hardening.py','tests/test_w19_release_seal.py'):_require(e,(WEB/rel).is_file(),f'TypeScript/React migration missing {rel}')
    ts=_text(WEB/'tsconfig.json',e)
    try:cfg=json.loads(ts); _require(e,cfg.get('compilerOptions',{}).get('strict') is True,'TypeScript strict mode must be enabled'); _require(e,cfg.get('compilerOptions',{}).get('module')=='CommonJS','tracked browser runtime must compile to CommonJS')
    except Exception as exc:e.append(f'tsconfig invalid: {exc}')
    types=_text(WEB/'src/types.ts',e)
    for token in ('interface StudyState','interface StudyStore','interface StudyRepository','interface AttachmentRepository','interface SchedulePreview','interface PortableImportPreview','interface PlanContentImportPreview'):_require(e,token in types,f'typed contract missing {token}')
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
    for token in ('startfocus','addsubject','addtopic','previewschedule','answerquestion','applyportableimportpreview','previewplancontentimport','applyplancontentimportpreview','switchplan','duplicateplan'):_require(e,token in s,f'store API missing {token}')
    _require(e,'indexeddb' not in s,'state module must not own IndexedDB'); _require(e,'createBrowserPersistence' in main and 'createStudyStore' in main,'main must compose persistence + store outside React')
    return e

def validate_indexeddb()->list[str]:
    e=[]; s=_text(WEB/'src/indexeddb.ts',e); storage=_text(WEB/'src/storage.ts',e)
    for token in ('createIndexedDbPersistence','ATTACHMENT_STORE','objectStore','indexedDB'):_require(e,token in s,f'IndexedDB layer missing {token}')
    _require(e,'createBrowserPersistence' in storage,'storage composition missing createBrowserPersistence'); return e

def validate_pwa()->list[str]:
    e=[]; p=_text(WEB/'src/pwa.ts',e); sw=_text(WEB/'service-worker.js',e); ui=_text(WEB/'src/react/App.tsx',e)
    for token in ('serviceWorker.register','beforeinstallprompt','appinstalled','offline'):_require(e,token in p,f'PWA registration missing {token}')
    for token in ('CACHE_VERSION','cache.addAll(APP_SHELL)','request.mode === "navigate"','caches.match("./index.html")','appShellUrls.has(url.href)','runtime/main.js','runtime/content_import.js','runtime/modes.js','runtime/hardening.js','runtime/release.js','react.production.js','react-dom-client.production.js'):_require(e,token in sw,f'service worker missing {token}')
    _require(e,'id="install-app"' in ui and 'id="pwa-status"' in ui,'React PWA controls missing'); return e

def validate_react()->list[str]:
    e=[]; ui=_text(WEB/'src/react/App.tsx',e); main=_text(WEB/'src/main.tsx',e)
    for token in ('from "react"','useSyncExternalStore','function Today','function Plan','function Practice','function Progress'):_require(e,token in ui,f'React UI missing {token}')
    _require(e,'createRoot' in main,'React createRoot bootstrap missing'); _require(e,'createStudyStore' in main,'store must be constructed outside React component tree'); return e

def validate_public_exam()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); modes=_text(WEB/'src/modes.ts',e); domain=_text(WEB/'src/domain.ts',e); storage=_text(WEB/'src/storage.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e)
    for token in ('ExamTargetProfile','ExamNoticeRevision','MockExamBlueprint','MockExamPreview','FinalStretchGuidance'):_require(e,token in types,f'W16 typed public-exam contract missing {token}')
    for token in ('buildExamNoticePreview','deriveFinalStretchGuidance','filterExamQuestions','previewMockExam','scoreMockExam','derivePublicExamDashboard'):_require(e,token in modes,f'W16 public-exam logic missing {token}')
    for token in ('updatePublicExamTarget','applyExamNoticePreview','addExamStage','addLegislation','setMockBlueprint','recordMockExam'):_require(e,token in state,f'W16 store operation missing {token}')
    for token in ('Concurso target','Concurso dashboard','Preview edital','Apply preview','Legislation','Mock Exam Studio','Banca'):_require(e,token in ui,f'W16 React workflow missing {token}')
    _require(e,'STATE_SCHEMA_VERSION = 6' in domain,'current state schema must be v6 after PC03')
    _require(e,'studyflow.local.state.v5' in storage and 'studyflow.local.state.v4' in storage,'v5/v4 localStorage migration keys missing')
    _require(e,'child_process' not in modes and 'exec(' not in modes and 'spawn(' not in modes,'mode domain must not expose arbitrary shell execution')
    return e

def validate_content_modes()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); modes=_text(WEB/'src/modes.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e)
    for token in ('PROGRAMMING_COURSE','LANGUAGE_DECK','READING_STUDY','GENERAL_SKILL'):_require(e,token in types and token in ui,f'W17 mode missing {token}')
    for token in ('createProgrammingExercise','createLanguageExpression','createReadingItem','createGeneralSkillDrill','deriveModeAnalytics'):_require(e,token in modes,f'W17 mode logic missing {token}')
    for token in ('recordProgrammingExercise','recordLanguageReview','updateReadingProgress','recordGeneralSkillDrill'):_require(e,token in state,f'W17 store operation missing {token}')
    for token in ('Programming course','Language deck','Reading study','General skill','Mode-aware analytics','sentence creation'):_require(e,token in ui,f'W17 React workflow missing {token}')
    _require(e,'manualReview' in state,'language sentence-creation manual-review contract missing')
    _require(e,'browser-only' in modes,'programming runtime boundary must remain browser-only')
    return e

def validate_hardening()->list[str]:
    e=[]; ui=_text(WEB/'src/react/App.tsx',e); css=_text(WEB/'styles.css',e); indexed=_text(WEB/'src/indexeddb.ts',e); hard=_text(WEB/'src/hardening.ts',e); pwa=_text(WEB/'src/pwa.ts',e); sw=_text(WEB/'service-worker.js',e); state=_text(WEB/'src/state.ts',e)
    for token in ('role="tablist"','role="tab"','aria-selected','aria-controls','ArrowRight','ArrowLeft','Home','End','mainRef.current?.focus','tabIndex={-1}','role="status"','aria-atomic="true"'):_require(e,token in ui,f'W18 accessibility hardening missing {token}')
    controls=re.findall(r'<(?:input|textarea|select)\b[^>]*>',ui)
    _require(e,bool(controls),'W18 accessibility control scan found no controls')
    for control in controls:_require(e,'aria-label=' in control,f'form control missing accessible name: {control[:100]}')
    for token in ('prefers-reduced-motion','forced-colors: active','focus-visible','--tap-size'):_require(e,token in css,f'W18 visual accessibility missing {token}')
    for token in ('RECOVERY_STATE_KEY','writeStateWithRecovery','status="recovered"','last verified local checkpoint'):_require(e,token in indexed or token in state,f'W18 IndexedDB recovery missing {token}')
    for token in ('auditBrowserStorage','purgeOrphanAttachments','missingAttachmentIds','orphanAttachmentIds'):_require(e,token in hard,f'W18 storage audit missing {token}')
    for token in ('dispose()','removeEventListener','removeEventListener("click"'):_require(e,token in pwa,f'W18 PWA listener cleanup missing {token}')
    for token in ('appShellUrls.has(url.href)','caches.match("./index.html")'):_require(e,token in sw,f'W18 offline cache hardening missing {token}')
    _require(e,'cache.put(request,copy)' in sw,'declared shell fetches must remain cacheable')
    return e

def validate_release()->list[str]:
    e=validate_hardening(); pkg=_text(WEB/'package.json',e); lock=_text(WEB/'package-lock.json',e); release=_text(WEB/'src/release.ts',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/W18_W19_HARDENING_RELEASE_SEAL.md',e); manifest=_text(WEB/'manifest.webmanifest',e)
    _require(e,'"version": "1.0.0"' in pkg,'W19 package version must be 1.0.0')
    _require(e,'"version": "1.0.0"' in lock,'W19 lockfile version must be 1.0.0')
    for token in ('version:"1.0.0"','STATE_SCHEMA_VERSION','STUDYFLOW_DB_VERSION','PORTABLE_FORMAT_VERSION','evaluateReleaseReadiness'):_require(e,token in release,f'W19 release contract missing {token}')
    _require(e,'studyflow-local-1.0.0' in sw,'W19 service-worker cache must use 1.0.0 release identity')
    _require(e,'W18 | Accessibility/testing/offline/storage hardening — delivered' in roadmap,'W18 roadmap delivery marker missing')
    _require(e,'W19 | StudyFlow Local 1.0 release seal — delivered' in roadmap,'W19 roadmap delivery marker missing')
    for token in ('StudyFlow Local 1.0.0','no required backend','unknown evidence remains distinct','npm run release:check'):_require(e,token in doc,f'W19 release documentation missing {token}')
    try:
        m=json.loads(manifest); _require(e,m.get('display')=='standalone' and m.get('start_url')=='./#today' and m.get('scope')=='./','W19 manifest launch contract changed')
    except Exception as exc:e.append(f'W19 manifest invalid: {exc}')
    runtime_sources='\n'.join(_text(path,e) for path in [WEB/'app.js',WEB/'service-worker.js',WEB/'src/main.tsx',WEB/'src/react/App.tsx',WEB/'src/storage.ts',WEB/'src/indexeddb.ts',WEB/'src/pwa.ts'])
    _require(e,'http://' not in runtime_sources and 'https://' not in runtime_sources,'W19 runtime must not require remote URLs')
    for forbidden in ('firebase','supabase','google-analytics','segment.io'):_require(e,forbidden not in runtime_sources.lower(),f'W19 runtime must not introduce {forbidden}')
    return e

def validate_parity_correctness()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); domain=_text(WEB/'src/domain.ts',e); modes=_text(WEB/'src/modes.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC00_PC01_CORRECTNESS_CLOSURE.md',e)
    for token in ('MockExamScoreResult','MockExamSectionScore','MockExamQuestionScore','intervalIndex','intervalDays','previousReviewId'):_require(e,token in types,f'PC00/PC01 typed contract missing {token}')
    for token in ('maximumScore','sectionMaximum','negativePoints*maximum','right-minus-wrong','rounding:"nearest-0.01"'):_require(e,token in modes,f'PC00 scoring closure missing {token}')
    for token in ('setReviewIntervalsDays','reviewIntervals(','normalizeReviewGrade','nextInterval','previousReviewId','policyIntervalsDays'):_require(e,token in state,f'PC01 review progression missing {token}')
    _require(e,'state.reviews = Array.isArray(state.reviews) ? state.reviews.map((review) => createReview(review)) : [];' in domain,'legacy v5 reviews must normalize progression fields')
    for token in ('Review intervals (days)','Again','Good','Easy','Weighted mocks use question points and section weights'):_require(e,token in ui,f'PC00/PC01 React workflow missing {token}')
    _require(e,'PC00 | Mock scoring correctness — delivered' in roadmap,'PC00 roadmap delivery marker missing'); _require(e,'PC01 | Configurable review progression — delivered' in roadmap,'PC01 roadmap delivery marker missing')
    for token in ('question points','section weight','failed reviews reset progression','top-level state schema remains v5'):_require(e,token in doc,f'PC00/PC01 correctness documentation missing {token}')
    for rel in ('tests/test_pc00_mock_scoring.py','tests/test_pc01_review_progression.py'):_require(e,(WEB/rel).is_file(),f'PC00/PC01 regression suite missing {rel}')
    return e

def validate_parity_plan_content()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); domain=_text(WEB/'src/domain.ts',e); importer=_text(WEB/'src/content_import.ts',e); state=_text(WEB/'src/state.ts',e); storage=_text(WEB/'src/storage.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC02_PC03_PLAN_IMPORT_HIERARCHY.md',e)
    for token in ('PlanContentImportFormat','PlanContentImportMode','PlanContentImportPreview','parentTopicId','studyCycles: Record<string,StudyCycleState>','schemaVersion: 6'):_require(e,token in types,f'PC02/PC03 typed contract missing {token}')
    for token in ('detectPlanContentImportFormat','parsePlanContentImport','buildPlanContentImportPreview','duplicate-input','reconcile','new-plan'):_require(e,token in importer,f'PC02 import pipeline missing {token}')
    for token in ('createPlan(input','renamePlan(','switchPlan(','setPlanArchived(','duplicatePlan(','deletePlan(','previewPlanContentImport','applyPlanContentImportPreview','parentTopicId','studyCycles'):_require(e,token in state,f'PC03 store lifecycle/hierarchy missing {token}')
    for token in ('STATE_SCHEMA_VERSION = 6','LEGACY_STATE_SCHEMA_VERSIONS = Object.freeze([1, 2, 3, 4, 5])','selectTopicChildren','topicDepth','buildTopicPath','topic hierarchy cycle detected'):_require(e,token in domain,f'PC03 domain/migration missing {token}')
    _require(e,'studyflow.local.state.v5' in storage,'PC03 localStorage v5 migration key missing')
    for token in ('Plan workspace','Create plan','Archived plans','Import Plan Content','Preview import','Apply import preview','Import as new plan','Parent topic','TopicTree'):_require(e,token in ui,f'PC02/PC03 React workflow missing {token}')
    _require(e,'runtime/content_import.js' in sw,'PC02 runtime must be available in the offline app shell')
    _require(e,'PC02 | Generic Plan Content import — delivered' in roadmap,'PC02 roadmap delivery marker missing'); _require(e,'PC03 | Plan lifecycle + nested topics — delivered' in roadmap,'PC03 roadmap delivery marker missing')
    for token in ('parse → normalize → validate → preview/diff → explicit apply','State schema **v6**','per-plan map','does not clone sessions, evidence, reviews, errors'):_require(e,token in doc,f'PC02/PC03 documentation missing {token}')
    for rel in ('tests/test_pc02_plan_content_import.py','tests/test_pc03_plan_lifecycle_hierarchy.py'):_require(e,(WEB/rel).is_file(),f'PC02/PC03 regression suite missing {rel}')
    return e

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
    groups=(validate_shell,validate_visual,validate_interactions,validate_typescript,validate_domain,validate_state,validate_indexeddb,validate_pwa,validate_react,validate_portable,validate_public_exam,validate_content_modes,validate_hardening,validate_release,validate_parity_correctness,validate_parity_plan_content)
    out=[]
    for fn in groups:
        for item in fn():
            if item not in out: out.append(item)
    return out

def main()->int:
    parser=argparse.ArgumentParser(); parser.add_argument('--contract',action='store_true'); parser.add_argument('--shell',action='store_true'); parser.add_argument('--hardening',action='store_true'); parser.add_argument('--release',action='store_true'); parser.add_argument('--parity-correctness',action='store_true'); parser.add_argument('--parity-plan-content',action='store_true'); parser.add_argument('--all',action='store_true'); args=parser.parse_args()
    errors=validate_contract() if args.contract else validate_shell() if args.shell else validate_hardening() if args.hardening else validate_release() if args.release else validate_parity_correctness() if args.parity_correctness else validate_parity_plan_content() if args.parity_plan_content else validate_all()
    if errors:
        for error in errors: print(f'ERROR: {error}',file=sys.stderr)
        return 1
    print('StudyFlow web validation passed'); return 0
if __name__=='__main__':raise SystemExit(main())
