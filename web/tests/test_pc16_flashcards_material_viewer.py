from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

class PC16FlashcardsMaterialViewerParityTest(unittest.TestCase):
    def run_node(self, source:str)->None:
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True,check=False)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_flashcard_crud_schedules_initial_review_and_suspend_is_safe(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict';
            import {createStudyStore} from './runtime/state.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}};
            const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00Z'});
            store.createPlan({name:'Cards',mode:'GENERAL_SKILL'},{activate:true}); const s=store.addSubject({name:'Law'}),t=store.addTopic({subjectId:s.id,name:'Rights'});
            const basic=store.addFlashcard({subjectId:s.id,topicId:t.id,type:'BASIC',front:'Article 1?',back:'Answer',tags:[' Law ','law','exam']});
            let st=store.getState(); assert.equal(st.flashcards.length,1); assert.deepEqual(st.flashcards[0].tags,['Law','law','exam']); const due=st.reviews.find(r=>r.sourceType==='flashcard'&&r.sourceId===basic.id); assert.ok(due); assert.equal(due.status,'due'); assert.equal(due.dueAt,'2026-09-07T12:00:00Z'); assert.equal(due.intervalDays,null);
            assert.throws(()=>store.addFlashcard({type:'CLOZE',front:'Cloze',back:'Answer'}),/Cloze flashcards require cloze text/);
            const cloze=store.addFlashcard({type:'CLOZE',front:'Fill',back:'Word',clozeText:'The {{c1::word}}',tags:['cloze']}); assert.equal(cloze.type,'CLOZE');
            store.setFlashcardSuspended(basic.id,true); st=store.getState(); assert.equal(st.flashcards.find(f=>f.id===basic.id).suspended,true); assert.equal(st.reviews.find(r=>r.sourceType==='flashcard'&&r.sourceId===basic.id).status,'dismissed');
            store.setFlashcardSuspended(basic.id,false); st=store.getState(); assert.equal(st.flashcards.find(f=>f.id===basic.id).suspended,false); assert.ok(st.reviews.some(r=>r.sourceType==='flashcard'&&r.sourceId===basic.id&&r.status==='due'));
        '''))

    def test_completed_flashcard_history_is_retained_and_deletion_fails_closed(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js';
            let now='2026-09-07T12:00:00Z'; const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=>now}); store.createPlan({name:'History',mode:'GENERAL_SKILL'},{activate:true}); const c=store.addFlashcard({front:'F',back:'B'}); const r=store.getState().reviews.find(x=>x.sourceType==='flashcard'&&x.sourceId===c.id); now='2026-09-07T12:05:00Z'; store.completeReview(r.id,{rating:'good'}); const st=store.getState(); assert.ok(st.reviews.some(x=>x.sourceId===c.id&&x.status==='completed')); assert.ok(st.reviews.some(x=>x.sourceId===c.id&&x.status==='due')); assert.throws(()=>store.deleteFlashcard(c.id),/retained for study history/); store.setFlashcardSuspended(c.id,true); assert.ok(store.getState().flashcards.some(x=>x.id===c.id&&x.suspended));
        '''))

    def test_material_viewer_is_mime_driven_and_fail_closed(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {planMaterialViewer,materialViewerIsInline,materialViewerNeedsAttachment} from './runtime/retention_viewer.js';
            const m=(x)=>({id:'m',planId:'p',subjectId:null,topicId:null,title:'Material',kind:'file',locator:null,mimeType:null,attachmentId:null,sizeBytes:null,archived:false,tags:[],createdAt:'2026-09-07T00:00:00Z',updatedAt:'2026-09-07T00:00:00Z',...x});
            const cases=[['application/pdf','local-pdf'],['image/png','local-image'],['audio/mpeg','local-audio'],['video/mp4','local-video'],['text/plain','local-text'],['application/json','local-text'],['application/zip','local-download']]; for(const [mime,kind] of cases){const p=planMaterialViewer(m({mimeType:mime,attachmentId:'att'})); assert.equal(p.kind,kind); assert.equal(materialViewerNeedsAttachment(p),true);}
            assert.equal(materialViewerIsInline(planMaterialViewer(m({mimeType:'application/pdf',attachmentId:'att'}))),true); const web=planMaterialViewer(m({kind:'link',locator:'https://example.com/doc'})); assert.equal(web.kind,'web-page'); assert.equal(web.uri,'https://example.com/doc'); assert.equal(materialViewerNeedsAttachment(web),false);
            assert.equal(planMaterialViewer(m({kind:'link',locator:'ftp://example.com/file'})).kind,'invalid'); assert.equal(planMaterialViewer(m({locator:'local-blob:missing'})).kind,'missing'); assert.equal(planMaterialViewer(m({locator:null})).kind,'missing');
        '''))

    def test_flashcards_are_portable_searchable_and_deep_link_to_reviews(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createStudyStore} from './runtime/state.js'; import {createPortablePackage,buildPortableImportPreview} from './runtime/portable.js'; import {searchStudyState} from './runtime/product.js'; import {routeActivityDeepLink} from './runtime/task_workflows.js';
            const repo={load(){return {status:'empty',state:null}},save(){return {status:'saved'}},clear(){}}; const store=createStudyStore({repository:repo,now:()=> '2026-09-07T12:00:00Z'}); store.createPlan({name:'Portable cards',mode:'GENERAL_SKILL'},{activate:true}); const s=store.addSubject({name:'Portuguese'}),t=store.addTopic({subjectId:s.id,name:'Grammar'}); const card=store.addFlashcard({subjectId:s.id,topicId:t.id,front:'Crase rule',back:'Use before feminine noun',tags:['grammar']}); const a=store.addActivity({title:'Review cards',kind:'review',activityType:'FLASHCARDS',subjectId:s.id,topicId:t.id}); const route=routeActivityDeepLink(store.getState(),a.id); assert.equal(route.practiceTab,'reviews'); assert.equal(route.activityType,'FLASHCARDS');
            const idx=searchStudyState(store.getState(),'crase'); const hit=idx.find(x=>x.type==='flashcard'&&x.id===card.id); assert.ok(hit); assert.match(hit.searchText,/crase rule/i);
            const pkg=createPortablePackage(store.getState()); assert.ok(pkg.payload.flashcards.some(x=>x.id===card.id)); const review=pkg.payload.reviews.find(x=>x.sourceType==='flashcard'&&x.sourceId===card.id); assert.ok(review); const preview=buildPortableImportPreview(store.getState(),pkg,{mode:'new-plan'}); assert.equal(preview.mode,'new-plan'); assert.ok(preview.summary.add>0); const imported=preview.nextState.flashcards.find(x=>x.front==='Crase rule'&&x.id!==card.id); assert.ok(imported); const importedReview=preview.nextState.reviews.find(x=>x.sourceType==='flashcard'&&x.sourceId===imported.id); assert.ok(importedReview);
        '''))

    def test_schema7_migration_ui_and_offline_shell_expose_pc16(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createInitialStudyState,migrateStudyState} from './runtime/domain.js'; const s=createInitialStudyState({now:'2026-09-07T00:00:00Z'}); const legacy=structuredClone(s); delete legacy.flashcards; const m=migrateStudyState(legacy); assert.deepEqual(m.flashcards,[]); assert.equal(m.schemaVersion,7);
        '''))
        ui=(WEB/'src/react/App.tsx').read_text(); sw=(WEB/'service-worker.js').read_text(); roadmap=(WEB.parent/'docs/web/WEB_ROADMAP.md').read_text()
        for token in ('Material Viewer','Create flashcard','Flashcards activity context','Open'):
            self.assertIn(token,ui)
        self.assertIn('runtime/retention_viewer.js',sw)
        self.assertIn('PC16 | Flashcards + Material Viewer parity — delivered',roadmap)

if __name__=='__main__': unittest.main()
