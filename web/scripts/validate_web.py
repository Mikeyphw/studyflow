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
    _require(e,target.get('runner')=='node','studyflow_web must use the canonical Node runner'); _require(e,target.get('platform')=='web','studyflow_web platform must remain web'); _require(e,target.get('root')=='web','studyflow_web root must remain web'); _require(e,target.get('execution_environment')=='native-termux','studyflow_web must remain native-termux'); _require(e,'tasks' not in target,'web target must not define custom task overrides')
    v=target.get('validation',{}); _require(e,v.get('max_workers')==2,'web validation max_workers must remain 2'); _require(e,v.get('cpu_limit')==2,'web validation cpu_limit must remain 2'); _require(e,v.get('parallel') is True,'web validation parallel must remain enabled')
    try:pkg=json.loads((WEB/'package.json').read_text()); lock=json.loads((WEB/'package-lock.json').read_text())
    except Exception as exc:return e+[f'package metadata invalid: {exc}']
    _require(e,pkg.get('private') is True,'web package must remain private')
    _require(e,pkg.get('version')=='1.0.0','StudyFlow Local release version must be 1.0.0')
    _require(e,lock.get('version')=='1.0.0' and lock.get('packages',{}).get('',{}).get('version')=='1.0.0','package-lock release identity must be 1.0.0')
    _require(e,pkg.get('dependencies')=={'react':'19.2.8','react-dom':'19.2.8'},'React runtime dependencies must be pinned')
    _require(e,pkg.get('devDependencies')=={'typescript':'5.8.3'},'TypeScript dependency must be pinned')
    for script in ('build','test','lint','dev','release:check','release:package'): _require(e,script in pkg.get('scripts',{}),f'missing npm script: {script}')
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
    for rel in ('tsconfig.json','src/types.ts','src/domain.ts','src/exam_foundation.ts','src/content_import.ts','src/indexeddb.ts','src/storage.ts','src/state.ts','src/portable.ts','src/pwa.ts','src/main.tsx','src/react/App.tsx','src/modes.ts','src/mode_import.ts','src/intelligence.ts','src/hardening.ts','src/release.ts','src/product.ts','runtime/package.json','tests/test_w14_typescript_migration.py','tests/test_w15_react_ui.py','tests/test_w16_public_exam_workflow.py','tests/test_w17_content_modes.py','tests/test_w19_release_seal.py'):_require(e,(WEB/rel).is_file(),f'TypeScript/React migration missing {rel}')
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
    for token in ('CACHE_VERSION','cache.addAll(APP_SHELL)','request.mode === "navigate"','caches.match("./index.html")','appShellUrls.has(url.href)','runtime/main.js','runtime/content_import.js','runtime/modes.js','runtime/exam_foundation.js','runtime/hardening.js','runtime/release.js','runtime/product.js','react.production.js','react-dom-client.production.js'):_require(e,token in sw,f'service worker missing {token}')
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
    _require(e,'STATE_SCHEMA_VERSION = 7' in domain,'current state schema must be v7 after PC10')
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
    _require(e,'__STUDYFLOW_CACHE_VERSION__' in sw,'W19/PC19 service-worker must expose the build-time content-addressed cache placeholder')
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
    for token in ('PlanContentImportFormat','PlanContentImportMode','PlanContentImportPreview','parentTopicId','studyCycles: Record<string,StudyCycleState>','schemaVersion: 7'):_require(e,token in types,f'PC02/PC03 typed contract missing {token}')
    for token in ('detectPlanContentImportFormat','parsePlanContentImport','buildPlanContentImportPreview','duplicate-input','reconcile','new-plan'):_require(e,token in importer,f'PC02 import pipeline missing {token}')
    for token in ('createPlan(input','renamePlan(','switchPlan(','setPlanArchived(','duplicatePlan(','deletePlan(','previewPlanContentImport','applyPlanContentImportPreview','parentTopicId','studyCycles'):_require(e,token in state,f'PC03 store lifecycle/hierarchy missing {token}')
    for token in ('STATE_SCHEMA_VERSION = 7','LEGACY_STATE_SCHEMA_VERSIONS = Object.freeze([1, 2, 3, 4, 5, 6])','selectTopicChildren','topicDepth','buildTopicPath','topic hierarchy cycle detected'):_require(e,token in domain,f'PC03 domain/migration missing {token}')
    _require(e,'studyflow.local.state.v5' in storage,'PC03 localStorage v5 migration key missing')
    for token in ('Plan workspace','Create plan','Archived plans','Import Plan Content','Preview import','Apply import preview','Import as new plan','Parent topic','TopicTree'):_require(e,token in ui,f'PC02/PC03 React workflow missing {token}')
    _require(e,'runtime/content_import.js' in sw,'PC02 runtime must be available in the offline app shell')
    _require(e,'PC02 | Generic Plan Content import — delivered' in roadmap,'PC02 roadmap delivery marker missing'); _require(e,'PC03 | Plan lifecycle + nested topics — delivered' in roadmap,'PC03 roadmap delivery marker missing')
    for token in ('parse → normalize → validate → preview/diff → explicit apply','State schema **v6**','per-plan map','does not clone sessions, evidence, reviews, errors'):_require(e,token in doc,f'PC02/PC03 documentation missing {token}')
    for rel in ('tests/test_pc02_plan_content_import.py','tests/test_pc03_plan_lifecycle_hierarchy.py'):_require(e,(WEB/rel).is_file(),f'PC02/PC03 regression suite missing {rel}')
    return e


def validate_parity_schedule_portable()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); schedule=_text(WEB/'src/schedule.ts',e); modes=_text(WEB/'src/modes.ts',e); state=_text(WEB/'src/state.ts',e); portable=_text(WEB/'src/portable.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC04_PC05_SCHEDULER_PORTABILITY.md',e)
    for token in ('ScheduleConfiguration','ScheduleAvailabilityWindow','FixedScheduleBlock','ScheduleConflict','ActivityScheduleProvenance','PortableAttachmentPayload','PortableImportMode'):_require(e,token in types,f'PC04/PC05 typed contract missing {token}')
    for token in ('buildAdaptiveSchedulePreview','locked-overdue','No remaining capacity','Study-cycle subject in persisted cursor order','fixedBlocks','availability'):_require(e,token in schedule,f'PC04 scheduler closure missing {token}')
    for token in ('setScheduleConfiguration','setActivityLocked','scheduleProvenance','buildAdaptiveSchedulePreview'):_require(e,token in state,f'PC04 store integration missing {token}')
    _require(e,'schedule:defaultScheduleConfiguration()' in modes and 'normalizeScheduleConfiguration(raw.schedule)' in modes,'PC04 schedule configuration must normalize for every study mode')
    for token in ('Capacity & availability','Fixed commitments','Preview 7 days','Unscheduled work','Schedule placement locked'):_require(e,token in ui,f'PC04 React workflow missing {token}')
    for token in ('PORTABLE_FORMAT_VERSION = 2','LEGACY_PORTABLE_FORMAT_VERSION = 1','createPortablePackageV2','parsePortablePackageV2','SHA-256','applyPortableImportWithAttachments','new-plan','attachmentIdMap'):_require(e,token in portable,f'PC05 portable v2 closure missing {token}')
    for token in ('Portable package v2','Export full v2','Export state-only v1','Import active package plan as new'):_require(e,token in ui,f'PC05 React workflow missing {token}')
    _require(e,'runtime/schedule.js' in sw,'PC04 schedule runtime must be cached by the offline app shell')
    _require(e,'PC04 | Full scheduler/conflicts/locks/overload/rescheduling — delivered' in roadmap,'PC04 roadmap delivery marker missing'); _require(e,'PC05 | Portable package v2 — delivered' in roadmap,'PC05 roadmap delivery marker missing')
    for token in ('Completed/cancelled history is never rewritten','attachment bytes encoded','SHA-256','Import active package plan as new','state schema remains **v6**'):_require(e,token in doc,f'PC04/PC05 documentation missing {token}')
    for rel in ('tests/test_pc04_schedule_capacity_conflicts.py','tests/test_pc05_portable_v2_attachments.py'):_require(e,(WEB/rel).is_file(),f'PC04/PC05 regression suite missing {rel}')
    return e

def validate_parity_practice_concurso()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); importer=_text(WEB/'src/question_import.ts',e); modes=_text(WEB/'src/modes.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC06_PC07_PRACTICE_CONCURSO_PARITY.md',e)
    for token in ('QuestionImportPreview','WeaknessDrillPreview','SavedPracticeSet','PracticeConfiguration','PublicExamStudyPhase','LegislationEvidenceLink','PublicExamPriorityAction','EditalMatrixRow'):_require(e,token in types,f'PC06/PC07 typed contract missing {token}')
    for token in ('detectQuestionImportFormat','parseQuestionImport','buildQuestionImportPreview','duplicate','subject','topic'):_require(e,token in importer,f'PC06 question import pipeline missing {token}')
    for token in ('buildWeaknessDrill','filterPracticeQuestions','derivePublicExamNextActions','deriveEditalMatrix','PUBLIC_EXAM_STARTER_TEMPLATES','topicPaths','syllabusFingerprint'):_require(e,token in modes,f'PC06/PC07 domain workflow missing {token}')
    for token in ('previewQuestionImport','applyQuestionImportPreview','savePracticeSet','deletePracticeSet','reclassifyError','setPublicExamStudyPhase','applyPublicExamStarterTemplate','verifyLegislation','linkLegislationEvidence','moveCurrentCycleSubjectToEnd','skipStudyCycleSubject'):_require(e,token in state,f'PC06/PC07 store workflow missing {token}')
    for token in ('Question import','Weakness drill','Save current filter','Weekly question quota','Study phase','Edital Matrix','Explainable next actions','Local starter templates','Exam incidence'):_require(e,token in ui,f'PC06/PC07 React workflow missing {token}')
    _require(e,'runtime/question_import.js' in sw,'PC06 question-import runtime must be cached by the offline app shell')
    _require(e,'PC06 | Practice parity: question import/filtering/saved sets/weakness drills — delivered' in roadmap,'PC06 roadmap delivery marker missing')
    _require(e,'PC07 | Full Concurso Público parity closure — delivered' in roadmap,'PC07 roadmap delivery marker missing')
    for token in ('preview-first CSV, TSV/Anki-style and Markdown question import','only questions matching the signaled topic/subject','pre-edital, post-edital and final-stretch','archive-not-delete','Unknown incidence/mastery/cutoff values remain unknown'):_require(e,token in doc,f'PC06/PC07 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc06_practice_parity.py').is_file(),'PC06 regression suite missing tests/test_pc06_practice_parity.py')
    return e

def validate_parity_modes_coach()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); modes=_text(WEB/'src/modes.ts',e); importer=_text(WEB/'src/mode_import.ts',e); intel=_text(WEB/'src/intelligence.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC08_PC09_MODES_COACH_PARITY.md',e)
    for token in ('ProgrammingCodeFile','ModeImportPreview','LanguageReviewPrompt','WeeklyCoachReview','ContentCleanupPreview','ExamReportSnapshot','ExamRunbook','StudySprintPreview'):_require(e,token in types,f'PC08/PC09 typed contract missing {token}')
    for token in ('tokenizeCode','assessProgrammingAttempt','buildLanguageReviewPrompt'):_require(e,token in modes,f'PC08 mode workflow missing {token}')
    for token in ('detectModeImportFormat','buildModeImportPreview','PROGRAMMING_COURSE','LANGUAGE_DECK'):_require(e,token in importer,f'PC08 import pipeline missing {token}')
    for token in ('deriveWeeklyCoach','deriveReadinessExplanation','deriveQuestionQuotaPlan','buildContentCleanupPreview','buildExamReportSnapshot','renderExamReportHtml','buildExamRunbook','buildStudySprintPreview'):_require(e,token in intel,f'PC09 intelligence workflow missing {token}')
    for token in ('revealProgrammingSolution','previewModeImport','applyModeImportPreview','recordLanguageReview','applyContentCleanupPreview'):_require(e,token in state,f'PC08/PC09 store integration missing {token}')
    for token in ('Safety boundary','Six-direction review studio','Preview import','Weekly coach','Preview smart cleanup','Export HTML exam report','Preview 7-day sprint'):_require(e,token in ui,f'PC08/PC09 React workflow missing {token}')
    _require(e,'runtime/mode_import.js' in sw and 'runtime/intelligence.js' in sw,'PC08/PC09 runtimes must be cached by the offline app shell')
    _require(e,'PC08 | Programming + Language content-mode parity closure — delivered' in roadmap,'PC08 roadmap delivery marker missing'); _require(e,'PC09 | Weekly coach/readiness/cleanup/report/runbook/sprint parity — delivered' in roadmap,'PC09 roadmap delivery marker missing')
    for token in ('does not execute arbitrary shell commands or pasted code','all six review directions','Smart content cleanup is preview-first','requires a confirmed exam date','seven-day sprint now only edits type/duration of existing unlocked planned activities'):_require(e,token in doc,f'PC08/PC09 documentation missing {token}')
    for rel in ('tests/test_pc08_content_modes_parity.py','tests/test_pc09_coach_cleanup_report_sprint.py'):_require(e,(WEB/rel).is_file(),f'PC08/PC09 regression suite missing {rel}')
    return e

def validate_parity_product_release()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); product=_text(WEB/'src/product.ts',e); domain=_text(WEB/'src/domain.ts',e); state=_text(WEB/'src/state.ts',e); storage=_text(WEB/'src/storage.ts',e); ui=_text(WEB/'src/react/App.tsx',e); pkg=_text(WEB/'package.json',e); build=_text(WEB/'scripts/build_dist.mjs',e); check=_text(WEB/'scripts/check_release.mjs',e); pack=_text(WEB/'scripts/package_release.py',e); release=_text(WEB/'src/release.ts',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC10_PC11_PRODUCT_RELEASE_SEAL.md',e)
    for token in ('AppLocale','AppPreferences','GlobalSearchResult','schemaVersion: 7'):_require(e,token in types,f'PC10 typed product-shell contract missing {token}')
    for token in ('searchStudyState','normalizeAppPreferences','validateStarterWorkspace','installDocumentLocalization','pt-BR'):_require(e,token in product,f'PC10 product module missing {token}')
    for token in ('STATE_SCHEMA_VERSION = 7','LEGACY_STATE_SCHEMA_VERSIONS = Object.freeze([1, 2, 3, 4, 5, 6])','preferences = normalizeAppPreferences'):_require(e,token in domain,f'PC10 v7 migration missing {token}')
    _require(e,'studyflow.local.state.v6' in storage,'PC10 storage must migrate the v6 key')
    for token in ('setPreferences','completeOnboarding'):_require(e,token in state,f'PC10 store integration missing {token}')
    for token in ('GlobalSearchDialog','SettingsDialog','OnboardingWizard','Search local study data','uiText(state.preferences.locale,"privacy")','Application language'):_require(e,token in ui,f'PC10 React product shell missing {token}')
    for token in ('build_dist.mjs','release:package','--directory dist'):_require(e,token in pkg,f'PC11 package script missing {token}')
    for token in ('join(dist,"vendor")','react.production.js','release-manifest.json','runtimeDependency','self-contained'):_require(e,token in build,f'PC11 dist builder missing {token}')
    for token in ('runtime node_modules path forbidden','release-manifest.json','parity release OK'):_require(e,token in check,f'PC11 release verifier missing {token}')
    for token in ('studyflow-local-1.0.0.zip','ZIP_DEFLATED','1980,1,1','sha256'):_require(e,token in pack,f'PC11 deterministic packager missing {token}')
    for token in ('releaseSeal:"parity-closure"','runtimeArtifact:"self-contained-dist"'):_require(e,token in release,f'PC11 release contract missing {token}')
    _require(e,'runtime/product.js' in sw,'PC10 product runtime must be available in the source offline shell')
    _require(e,'PC10 | Product shell parity: search/settings/privacy/i18n/onboarding — delivered' in roadmap,'PC10 roadmap delivery marker missing')
    _require(e,'PC11 | Self-contained production artifact + parity-closure release seal — delivered' in roadmap,'PC11 roadmap delivery marker missing')
    for token in ('global search is local-only','schema **v7**','PC18 supersedes','dist/vendor','does not reference `node_modules` at runtime','deterministic ZIP'):_require(e,token in doc,f'PC10/PC11 documentation missing {token}')
    for rel in ('tests/test_pc10_product_shell.py','tests/test_pc11_release_artifact.py'):_require(e,(WEB/rel).is_file(),f'PC10/PC11 regression suite missing {rel}')
    return e

def validate_parity_exam_foundation()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); foundation=_text(WEB/'src/exam_foundation.ts',e); modes=_text(WEB/'src/modes.ts',e); state=_text(WEB/'src/state.ts',e); schedule=_text(WEB/'src/schedule.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC12_EXAM_FOUNDATION_PARITY.md',e)
    for token in ('StudyPhaseDefinition','PublicExamPreEditalSnapshot','StudyReferenceProvenance','archived:boolean','tags:string[]'):_require(e,token in types,f'PC12 typed foundation contract missing {token}')
    for token in ('derivePreEditalCockpit','PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS','BB_STUDY_PHASE_SUGGESTIONS','validateStudyPhaseDefinitions','deriveActiveStudyPhase','REFERENCE_POSSIBLY_STALE_AFTER_DAYS','encodeReferenceProvenance','deriveMaterialReferenceFreshness'):_require(e,token in foundation,f'PC12 foundation logic missing {token}')
    for token in ('PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS','phaseDefinitions','cockpitStaleReferenceDays'):_require(e,token in modes,f'PC12 public-exam normalization missing {token}')
    for token in ('setStudyPhaseDefinitions','applyBbStudyPhaseSuggestions','setMaterialReferenceProvenance','verifyMaterialReference','replaceMaterialReference'):_require(e,token in state,f'PC12 store integration missing {token}')
    for token in ('deriveActiveStudyPhase','Active phase'):_require(e,token in schedule,f'PC12 scheduler integration missing {token}')
    for token in ('pre-edital-cockpit','Study phase planner','MaterialReferenceControls','reference provenance','90 days'):_require(e,token in ui,f'PC12 React workflow missing {token}')
    _require(e,'runtime/exam_foundation.js' in sw,'PC12 foundation runtime must be cached by the offline app shell')
    _require(e,'PC12 | Cockpit/reference/phase foundation (OV09 + OV14 + OV15) — delivered' in roadmap,'PC12 roadmap delivery marker missing')
    for token in ('OV09','OV14','OV15','365 days','90 days','StudyFlow suggestion','Replacement'):_require(e,token in doc,f'PC12 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc12_exam_foundation.py').is_file(),'PC12 regression suite missing tests/test_pc12_exam_foundation.py')
    return e

def validate_parity_task_workflows()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); workflows=_text(WEB/'src/task_workflows.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC13_TASK_WORKFLOWS_PARITY.md',e)
    for token in ('StudyActivityType','BbAgenteComercialReferencePreset','WritingRubric','WritingChecklist','PracticeRouteContext'):_require(e,token in types,f'PC13 typed workflow contract missing {token}')
    for token in ('BB_AGENTE_COMERCIAL_PRE_EDITAL','BB_REFERENCE_SCHEDULED_MINUTES = 1140','BB_SUBJECT_WEIGHTS','routeActivityDeepLink','WRITING_PREFIX','writingChecklistComplete','deriveWritingWeeklyProgress'):_require(e,token in workflows,f'PC13 workflow logic missing {token}')
    for token in ('provisionBbAgenteComercialPreset','startWritingSession','saveWritingDraft','completeWriting','Writing sessions must finish through the Writing Workspace'):_require(e,token in state,f'PC13 store integration missing {token}')
    for token in ('BB Agente Comercial — pre-edital','Writing Workspace','Finish Writing','Create BB Agente Comercial preset plan','Open {title(a.activityType)}'):_require(e,token in ui,f'PC13 React workflow missing {token}')
    _require(e,'runtime/task_workflows.js' in sw,'PC13 task workflow runtime must be cached by the offline app shell')
    _require(e,'PC13 | BB Agente Comercial preset + Writing Workspace + activity deep links (OV10 + OV11 + OV13) — delivered' in roadmap,'PC13 roadmap delivery marker missing')
    for token in ('OV10','OV11','OV13','1,140 scheduled minutes','never by title text','does **not** claim'):_require(e,token in doc,f'PC13 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc13_task_workflows.py').is_file(),'PC13 regression suite missing tests/test_pc13_task_workflows.py')
    return e

def validate_parity_intelligence_schedule()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); parity=_text(WEB/'src/parity_intelligence.ts',e); schedule=_text(WEB/'src/schedule.ts',e); state=_text(WEB/'src/state.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC14_INTELLIGENCE_SCHEDULE_PARITY.md',e)
    for token in ('ReadinessBand','QuestionQuotaSubject','ScheduleCalendarView','EvidenceTimelineEvent','completeGoal'):_require(e,token in types,f'PC14 typed parity contract missing {token}')
    for token in ('deriveExamReadinessParity','weightedPresent','derivePublicExamQuestionQuotaPlan','.35*weightShares','.20*underShares','.20*accuracyShares','.15*errorShares','.10*reviewShares','allocateExactly','buildUnifiedEvidenceTimeline','scheduleCalendarWindow','deriveScheduleCalendarSnapshot','localDateKey'):_require(e,token in parity,f'PC14 parity intelligence missing {token}')
    for token in ('getFullYear()','getMonth()+1','getDate()','setDate(','getDay()'):_require(e,token in schedule,f'PC14 scheduler local-date semantics missing {token}')
    _require(e,'const today = localDateKey(at);' in state,'PC14 applied schedule queue must use local civil date')
    for token in ('Readable calendar schedule','Today','This week','Next week','Calendar','Unified Evidence Timeline','Exam readiness model','35% exam weight','Mark complete'):_require(e,token in ui,f'PC14 React parity workflow missing {token}')
    _require(e,'runtime/parity_intelligence.js' in sw,'PC14 parity intelligence runtime must be cached by the offline app shell')
    _require(e,'PC14 | Multi-signal readiness + subject quota + unified Evidence Timeline + local readable schedule (Phase 48/55 + OV16 + OV17) — delivered' in roadmap,'PC14 roadmap delivery marker missing')
    for token in ('24% coverage','35% exam weight','largest-remainder','Monday–Sunday','does not fabricate','UTC-midnight'):_require(e,token in doc,f'PC14 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc14_intelligence_schedule_parity.py').is_file(),'PC14 regression suite missing tests/test_pc14_intelligence_schedule_parity.py')
    return e

def validate_parity_intelligence_refinement()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); intel=_text(WEB/'src/intelligence.ts',e); refine=_text(WEB/'src/refinement.ts',e); state=_text(WEB/'src/state.ts',e); product=_text(WEB/'src/product.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC15_INTELLIGENCE_REFINEMENT_PARITY.md',e)
    for token in ('WeeklyCoachSignalKind','ContentCleanupSeverity','ExamReportNextAction','ExamRunbookWindow','StudySprintFocus','SamplePlanTemplateId','SamplePlanBlueprint','applyStudySprintPreview','provisionSamplePlan'):_require(e,token in types,f'PC15 typed refinement contract missing {token}')
    for token in ('deriveWeeklyCoachRefined','buildContentCleanupRefined','buildExamRunbookRefined','buildStudySprintRefined','validateStudySprintApply','buildExamReportRefined','renderExamReportPdf','SAMPLE_PLAN_BLUEPRINTS'):_require(e,token in refine,f'PC15 refinement engine missing {token}')
    for token in ('review-backlog','mock-exam-signal','stale-resource','stale-plan','inconsistent-tag','stale-review','broken-schedule-alignment','severityWeight={info:2,review:5,action:10}','freeformNotesIncluded:false','at.getTime()-generated>120_000','slice(0,7)'):_require(e,token in refine,f'PC15 original intelligence rule missing {token}')
    for token in ('buildContentCleanupPreview','buildExamReportSnapshot','renderExamReportPdf','buildExamRunbook','buildStudySprintPreview','deriveWeeklyCoach'):_require(e,token in intel,f'PC15 public intelligence wrapper missing {token}')
    for token in ('provisionSampleTemplate','sample-plan-provisioned','applyStudySprintPreview','Smart Content Cleanup is review-only'):_require(e,token in state,f'PC15 store integration missing {token}')
    for token in ('public-exam-12-week','university-semester','certification-bootcamp','free-study-habit'):_require(e,token in product or token in refine,f'PC15 starter template missing {token}')
    for token in ('12-week public exam','University semester','Certification bootcamp','Free-study habit','Weekly schedule preview','Preview smart cleanup','Preview 7-day sprint','Export PDF exam report','Preview only. Cleanup actions are non-destructive','cannot create IDs, move starts, change status, touch locked activities, or increase duration'):_require(e,token in ui,f'PC15 React refinement surface missing {token}')
    _require(e,'runtime/refinement.js' in sw,'PC15 refinement runtime must be cached by the offline app shell')
    _require(e,'PC15 | Intelligence refinement: coach + cleanup + source-backed report + runbook + safe 7-day sprint + onboarding blueprints (Phases 58–64) — delivered' in roadmap,'PC15 roadmap delivery marker missing')
    for token in ('current local Monday–Sunday week','2 points per info finding, 5 per review finding, and 10 per action finding','freeformNotesIncluded=false','cannot create or delete IDs','120 seconds','12-week public exam','University semester','Certification bootcamp','Free-study habit','cloud sync queue'):_require(e,token in doc,f'PC15 documentation missing {token}')
    for rel in ('tests/test_pc09_coach_cleanup_report_sprint.py','tests/test_pc10_product_shell.py','tests/test_pc14_intelligence_schedule_parity.py','tests/test_pc15_intelligence_refinement.py'):_require(e,(WEB/rel).is_file(),f'PC15 focused regression suite missing {rel}')
    return e

def validate_parity_flashcards_viewer()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); domain=_text(WEB/'src/domain.ts',e); state=_text(WEB/'src/state.ts',e); viewer=_text(WEB/'src/retention_viewer.ts',e); portable=_text(WEB/'src/portable.ts',e); product=_text(WEB/'src/product.ts',e); task=_text(WEB/'src/task_workflows.ts',e); refine=_text(WEB/'src/refinement.ts',e); ui=_text(WEB/'src/react/App.tsx',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC16_FLASHCARDS_MATERIAL_VIEWER_PARITY.md',e)
    for token in ('FlashcardType','interface Flashcard','MaterialViewerKind','MaterialViewerPlan','flashcards:Flashcard[]','addFlashcard','setFlashcardSuspended','deleteFlashcard'):_require(e,token in types,f'PC16 typed retention/viewer contract missing {token}')
    for token in ('createFlashcard','Cloze flashcards require cloze text','state.flashcards','flashcardIds','missing flashcard sourceId'):_require(e,token in domain,f'PC16 flashcard domain/migration missing {token}')
    for token in ('flashcard-created','flashcard-suspended','flashcard-resumed','Reviewed flashcards are retained for study history','sourceType:"flashcard"'):_require(e,token in state,f'PC16 flashcard store integration missing {token}')
    for token in ('planMaterialViewer','local-text','local-image','local-audio','local-video','local-pdf','local-download','web-page','Only local attachments and explicit HTTP(S) resources can be opened in the web app.'):_require(e,token in viewer,f'PC16 material viewer policy missing {token}')
    _require(e,'flashcards:"flashcard"' in portable,'PC16 portable remap must include flashcards'); _require(e,'result("flashcard"' in product,'PC16 global search must index flashcards')
    for token in ('type==="FLASHCARDS"||type==="REVIEW"','activityType==="FLASHCARDS"'):_require(e,token in task or token in ui,f'PC16 flashcard activity routing missing {token}')
    for token in ('state.flashcards','const fids=new Set','sourceType==="flashcard"'):_require(e,token in refine,f'PC16 cleanup integration missing {token}')
    for token in ('Material Viewer','Create flashcard','Flashcards activity context','classification is local and MIME-based'):_require(e,token in ui,f'PC16 React workflow missing {token}')
    _require(e,'runtime/retention_viewer.js' in sw,'PC16 material viewer runtime must be cached by the offline app shell')
    _require(e,'PC16 | Flashcards + Material Viewer parity — delivered' in roadmap,'PC16 roadmap delivery marker missing')
    for token in ('BASIC','CLOZE','immediate','Suspending','MIME','FileProvider','ACTION_VIEW','schema number remains 7'):_require(e,token in doc,f'PC16 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc16_flashcards_material_viewer.py').is_file(),'PC16 regression suite missing tests/test_pc16_flashcards_material_viewer.py')
    return e

def validate_parity_promise_cleanup()->list[str]:
    e=[]; types=_text(WEB/'src/types.ts',e); cleanup=_text(WEB/'src/promise_cleanup.ts',e); schedule=_text(WEB/'src/schedule.ts',e); state=_text(WEB/'src/state.ts',e); foundation=_text(WEB/'src/exam_foundation.ts',e); modes=_text(WEB/'src/modes.ts',e); parity=_text(WEB/'src/parity_intelligence.ts',e); refine=_text(WEB/'src/refinement.ts',e); ui=_text(WEB/'src/react/App.tsx',e); styles=_text(WEB/'styles.css',e); sw=_text(WEB/'service-worker.js',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC17_CORRECTNESS_PROMISE_CLEANUP.md',e)
    for token in ('StudyStrategyType','RecurringScheduleBlock','strategyType:StudyStrategyType','recurringBlocks:RecurringScheduleBlock[]','addScheduledActivity','hiddenPastCount'):_require(e,token in types,f'PC17 typed correctness contract missing {token}')
    for token in ('STUDY_STRATEGY_TYPES','FIXED_WEEKLY','STUDY_CYCLE','GOAL_BASED','ADAPTIVE','FREE_GUIDED','INTENSIVE_SPRINT','COUNTDOWN','resolvePublicExamDate','officialCountdownDate','daysUntilOfficialExam','publicExamCoverageTopicCount','shouldOfferStudyCycle'):_require(e,token in cleanup,f'PC17 promise-cleanup engine missing {token}')
    for token in ('recurringBlocks','Recurring schedule block:','persisted local date/time','preferredStart','preferredDate'):_require(e,token in schedule,f'PC17 recurring scheduler contract missing {token}')
    for token in ('addScheduledActivity(input)','Scheduled activity requires an explicit local date','Scheduled activity topic must belong to the selected subject','schedule-configuration-updated','topics-covered'):_require(e,token in state,f'PC17 store ownership/coverage contract missing {token}')
    _require(e,'resolvePublicExamDate(plan)' in foundation,'PC17 pre-edital cockpit must consume canonical date resolver')
    _require(e,'daysUntilOfficialExam(plan,at)' in modes,'PC17 final-stretch guidance must consume canonical date resolver')
    _require(e,'daysUntilOfficialExam(plan,at)' in parity,'PC17 readiness must consume canonical date resolver')
    _require(e,'daysUntilOfficialExam(p,at)' in refine,'PC17 refinement/runbook path must consume canonical date resolver')
    for token in ('StudyCycleTodayCard','STUDY_STRATEGY_TYPES','Manual scheduled activity','Weekly recurring blocks','Search title, subject or topic','Show past activities','Subject evidence','No observed subject evidence yet'):_require(e,token in ui,f'PC17 React promise cleanup missing {token}')
    for token in ('color-scheme: dark','--bg: #0b0f14','--surface: #121923'):_require(e,token in styles,f'PC17 dark-only shell contract missing {token}')
    for token in ('color-scheme: light','prefers-color-scheme: light'):_require(e,token not in styles,f'PC17 must not expose a light theme contract: {token}')
    _require(e,'light mode' not in ui.lower() and 'theme-light' not in ui.lower(),'PC17 must not expose a light-mode UI toggle')
    _require(e,'runtime/promise_cleanup.js' in sw,'PC17 correctness runtime must be cached by the offline app shell')
    _require(e,'PC17 | OV18 + OV19 correctness/promise cleanup parity — delivered' in roadmap,'PC17 roadmap delivery marker missing')
    for token in ('confirmed','expected','legacy plan-only','seven original values','actual number of active topics','does not silently relocate','Show past activities','Android-specific'):_require(e,token in doc,f'PC17 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc17_correctness_promise_cleanup.py').is_file(),'PC17 regression suite missing tests/test_pc17_correctness_promise_cleanup.py')
    return e

def validate_parity_final_seal()->list[str]:
    e=[]; encrypted=_text(WEB/'src/encrypted_portable.ts',e); domain=_text(WEB/'src/domain.ts',e); modes=_text(WEB/'src/modes.ts',e); schedule=_text(WEB/'src/schedule.ts',e); qimport=_text(WEB/'src/question_import.ts',e); types=_text(WEB/'src/types.ts',e); ui=_text(WEB/'src/react/App.tsx',e); styles=_text(WEB/'styles.css',e); sw=_text(WEB/'service-worker.js',e); release=_text(WEB/'src/release.ts',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC18_ENCRYPTED_PORTABLE_FULL_APPLICABLE_PARITY_SEAL.md',e)
    for token in ('STUDYFLOWENC','ENCRYPTED_PORTABLE_VERSION = 1','210_000','PBKDF2','SHA-256','AES-GCM','length:256','additionalData:header','tagLength:ENCRYPTED_PORTABLE_TAG_BITS','ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH = 8','EncryptedPortableAuthenticationError'):_require(e,token in encrypted,f'PC18 encrypted portable contract missing {token}')
    for token in ('temporalScope: future ? "future" : "today"','Earliest future scheduled work','Scheduled for current local day'):_require(e,token in domain,f'PC18 Today temporal contract missing {token}')
    for token in ('examBoard:string|null','sourceFilter:string|null','requiredTags:string[]','includeDisputed:boolean','minimumOverallScorePercent:number|null','tags:string[]'):_require(e,token in types,f'PC18 mock/question typed contract missing {token}')
    for token in ('mockQuestionMatches','requiredTags.every','buildBbReferenceMockBlueprint','previewBbReferenceMockExam','BB_OBJECTIVE_QUESTION_COUNT','fundacao cesgranrio','minimumOverallScorePercent:null'):_require(e,token in modes,f'PC18 Mock Studio/BB contract missing {token}')
    for token in ('confirmed exam proximity','examDays<=7?30','examDays<=30?20','examDays<=90?10'):_require(e,token in schedule,f'PC18 confirmed exam-proximity scheduler signal missing {token}')
    _require(e,'splitTags' in qimport and 'tags:splitTags' in qimport,'PC18 question import must make tags reachable')
    for token in ('Manual question tags','Mock tag filter','Export encrypted v2','Decrypt and preview','Password loss is unrecoverable','70 objective questions'):_require(e,token in ui,f'PC18 React workflow missing {token}')
    _require(e,'runtime/encrypted_portable.js' in sw,'PC18 encrypted portability runtime must be cached by the offline shell')
    _require(e,'encryptedPortableEnvelope:1' in release,'PC18 release identity must expose encrypted envelope v1')
    _require(e,'color-scheme: dark' in styles and 'color-scheme: light' not in styles and 'prefers-color-scheme: light' not in styles,'PC18 must preserve the dark-only shell contract')
    _require(e,'PC18 | Encrypted portable backup + final applicable parity seal — delivered' in roadmap,'PC18 roadmap delivery marker missing')
    for token in ('full applicable functional parity','STUDYFLOWENC','210,000','AES-256-GCM','expected dates do not trigger','[11, 4, 5, 3, 5, 10, 16, 16]','no invented objective cutoff'):_require(e,token.lower() in doc.lower(),f'PC18 documentation missing {token}')
    _require(e,(WEB/'tests/test_pc18_encrypted_final_parity.py').is_file(),'PC18 regression suite missing tests/test_pc18_encrypted_final_parity.py')
    return e

def validate_interaction_runtime_audit()->list[str]:
    e=[]; product=_text(WEB/'src/product.ts',e); ui=_text(WEB/'src/react/App.tsx',e); worker=_text(WEB/'service-worker.js',e); build=_text(WEB/'scripts/build_dist.mjs',e); check=_text(WEB/'scripts/check_release.mjs',e); roadmap=_text(ROOT/'docs/web/WEB_ROADMAP.md',e); doc=_text(ROOT/'docs/web/PC19_INTERACTION_RUNTIME_AUDIT_HOTFIX.md',e)
    for token in ('current!==state.rendered','next!==current','LocalizedValue','MutationObserver'):_require(e,token in product,f'PC19 idempotent dynamic localization missing {token}')
    _require(e,'Encrypted portable backups are not included' not in product and 'Backups portáteis criptografados não fazem parte' not in product,'PC19 product copy still denies encrypted portable backups')
    for token in ('dataset.studyflowInteractive="ready"','delete document.documentElement.dataset.studyflowInteractive'):_require(e,token in ui,f'PC19 interaction liveness marker missing {token}')
    _require(e,'__STUDYFLOW_CACHE_VERSION__' in worker,'PC19 service-worker cache placeholder missing')
    for token in ('const cacheSeed=','createHash("sha256").update(cacheSeed)','cacheVersion'):_require(e,token in build,f'PC19 content-addressed cache build missing {token}')
    for token in ('manifest.cacheVersion','service worker cache version does not match release manifest'):_require(e,token in check,f'PC19 release cache verification missing {token}')
    try:
        manifest=json.loads((WEB/'manifest.webmanifest').read_text(encoding='utf-8')); _require(e,manifest.get('background_color')=='#0b0f14','PC19 PWA background must match dark-only shell'); _require(e,manifest.get('theme_color')=='#121923','PC19 PWA theme must match dark-only shell')
    except Exception as exc:e.append(f'PC19 manifest invalid: {exc}')
    styles=_text(WEB/'styles.css',e)
    for token in ('.skip-link {','transform: translateY(calc(-100% - 1rem))','.skip-link:focus-visible','textarea:focus-visible','background: var(--surface-muted)','select {\n    color-scheme: dark;'):_require(e,token in styles,f'PC19 real-device dark shell styling missing {token}')
    _require(e,'input:not([type="checkbox"]):not([type="radio"]):not([type="file"])' in styles,'PC19 general editable-control dark styling missing')
    _require(e,(WEB/'tests/test_pc19_interaction_runtime.py').is_file(),'PC19 regression suite missing')
    _require(e,(WEB/'scripts/browser_interaction_smoke.py').is_file(),'PC19 browser interaction smoke missing')
    _require(e,'PC19 | Interaction runtime audit/hotfix — delivered' in roadmap,'PC19 roadmap delivery marker missing')
    for token in ('main-thread starvation','MutationObserver','content-addressed','browser interaction smoke','dark-only'):_require(e,token in doc,f'PC19 audit documentation missing {token}')
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
    groups=(validate_shell,validate_visual,validate_interactions,validate_typescript,validate_domain,validate_state,validate_indexeddb,validate_pwa,validate_react,validate_portable,validate_public_exam,validate_content_modes,validate_hardening,validate_release,validate_parity_correctness,validate_parity_plan_content,validate_parity_schedule_portable,validate_parity_practice_concurso,validate_parity_modes_coach,validate_parity_product_release,validate_parity_exam_foundation,validate_parity_task_workflows,validate_parity_intelligence_schedule,validate_parity_intelligence_refinement,validate_parity_flashcards_viewer,validate_parity_promise_cleanup,validate_parity_final_seal,validate_interaction_runtime_audit)
    out=[]
    for fn in groups:
        for item in fn():
            if item not in out: out.append(item)
    return out

def main()->int:
    parser=argparse.ArgumentParser(); parser.add_argument('--contract',action='store_true'); parser.add_argument('--shell',action='store_true'); parser.add_argument('--hardening',action='store_true'); parser.add_argument('--release',action='store_true'); parser.add_argument('--parity-correctness',action='store_true'); parser.add_argument('--parity-plan-content',action='store_true'); parser.add_argument('--parity-schedule-portable',action='store_true'); parser.add_argument('--parity-practice-concurso',action='store_true'); parser.add_argument('--parity-modes-coach',action='store_true'); parser.add_argument('--parity-product-release',action='store_true'); parser.add_argument('--parity-exam-foundation',action='store_true'); parser.add_argument('--parity-task-workflows',action='store_true'); parser.add_argument('--parity-intelligence-schedule',action='store_true'); parser.add_argument('--parity-intelligence-refinement',action='store_true'); parser.add_argument('--parity-flashcards-viewer',action='store_true'); parser.add_argument('--parity-promise-cleanup',action='store_true'); parser.add_argument('--parity-final-seal',action='store_true'); parser.add_argument('--interaction-runtime-audit',action='store_true'); parser.add_argument('--all',action='store_true'); args=parser.parse_args()
    errors=validate_contract() if args.contract else validate_shell() if args.shell else validate_hardening() if args.hardening else validate_release() if args.release else validate_parity_correctness() if args.parity_correctness else validate_parity_plan_content() if args.parity_plan_content else validate_parity_schedule_portable() if args.parity_schedule_portable else validate_parity_practice_concurso() if args.parity_practice_concurso else validate_parity_modes_coach() if args.parity_modes_coach else validate_parity_product_release() if args.parity_product_release else validate_parity_exam_foundation() if args.parity_exam_foundation else validate_parity_task_workflows() if args.parity_task_workflows else validate_parity_intelligence_schedule() if args.parity_intelligence_schedule else validate_parity_intelligence_refinement() if args.parity_intelligence_refinement else validate_parity_flashcards_viewer() if args.parity_flashcards_viewer else validate_parity_promise_cleanup() if args.parity_promise_cleanup else validate_parity_final_seal() if args.parity_final_seal else validate_interaction_runtime_audit() if args.interaction_runtime_audit else validate_all()
    if errors:
        for error in errors: print(f'ERROR: {error}',file=sys.stderr)
        return 1
    print('StudyFlow web validation passed'); return 0
if __name__=='__main__':raise SystemExit(main())
