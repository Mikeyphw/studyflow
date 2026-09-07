from pathlib import Path
import re, subprocess, textwrap, unittest

ROOT=Path(__file__).resolve().parents[1]

class W18HardeningTest(unittest.TestCase):
    def run_node(self, source):
        result=subprocess.run(['node','--input-type=module','-e',source],cwd=ROOT,text=True,capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr or result.stdout)

    def test_accessibility_contract_has_keyboard_tabs_focus_and_named_controls(self):
        ui=(ROOT/'src/react/App.tsx').read_text()
        for token in ('role="tablist"','role="tab"','aria-selected','aria-controls','ArrowRight','ArrowLeft','Home','End','mainRef.current?.focus','tabIndex={-1}','role="status"','aria-atomic="true"'):
            self.assertIn(token,ui)
        controls=re.findall(r'<(?:input|textarea|select)\b[^>]*>',ui)
        self.assertGreater(len(controls),20)
        for control in controls:
            self.assertIn('aria-label=',control,control)
        css=(ROOT/'styles.css').read_text()
        self.assertIn('@media (forced-colors: active)',css)
        self.assertIn('@media (prefers-reduced-motion: reduce)',css)

    def test_indexeddb_recovers_last_verified_checkpoint(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict';
            import { FakeIndexedDBFactory } from './tests/fake_indexeddb.mjs';
            import { createBrowserPersistence } from './runtime/storage.js';
            import { createStudyStore } from './runtime/state.js';
            const fake=new FakeIndexedDBFactory();
            const first=await createBrowserPersistence({storage:null,indexedDBFactory:fake,dbName:'w18-recovery',now:()=> '2026-09-07T02:00:00.000Z'});
            const store=createStudyStore({repository:first.repository,now:()=> '2026-09-07T02:00:00.000Z'});
            store.addActivity({title:'Recovery checkpoint',kind:'study',durationMinutes:25});
            await first.repository.flush();
            store.addActivity({title:'Current state',kind:'study',durationMinutes:25});
            await first.repository.flush();
            const stateStore=fake.databases.get('w18-recovery').stores.get('state');
            const recovery=stateStore.get('recovery');
            assert.ok(recovery.activities.some((item)=>item.title==='Recovery checkpoint'));
            stateStore.set('current',{schemaVersion:999,revision:1});
            const second=await createBrowserPersistence({storage:null,indexedDBFactory:fake,dbName:'w18-recovery',now:()=> '2026-09-07T02:05:00.000Z'});
            const loaded=second.repository.load();
            assert.equal(loaded.status,'recovered');
            assert.match(loaded.reason,/last verified local checkpoint/i);
            assert.equal(loaded.state.activities.some((item)=>item.title==='Recovery checkpoint'),true);
            assert.equal(loaded.state.activities.some((item)=>item.title==='Current state'),false);
            assert.equal(stateStore.get('current').schemaVersion,6);
        '''))

    def test_storage_audit_finds_missing_and_orphan_blobs_without_rewriting_state(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict';
            import { FakeIndexedDBFactory } from './tests/fake_indexeddb.mjs';
            import { createBrowserPersistence } from './runtime/storage.js';
            import { createStudyStore } from './runtime/state.js';
            import { auditBrowserStorage, purgeOrphanAttachments } from './runtime/hardening.js';
            const fake=new FakeIndexedDBFactory();
            const persistence=await createBrowserPersistence({storage:null,indexedDBFactory:fake,dbName:'w18-audit',now:()=> '2026-09-07T03:00:00.000Z'});
            const store=createStudyStore({repository:persistence.repository,now:()=> '2026-09-07T03:00:00.000Z'});
            const kept=await persistence.attachments.put(new File(['kept'],'kept.txt',{type:'text/plain'}),{id:'attachment-kept'});
            await persistence.attachments.put(new File(['orphan'],'orphan.txt',{type:'text/plain'}),{id:'attachment-orphan'});
            store.addMaterial({title:'Kept',kind:'file',attachmentId:kept.id,locator:`local-blob:${kept.id}`,mimeType:kept.mimeType,sizeBytes:kept.sizeBytes});
            store.addMaterial({title:'Missing',kind:'file',attachmentId:'attachment-missing',locator:'local-blob:attachment-missing',mimeType:'text/plain',sizeBytes:10});
            const revision=store.getState().revision;
            const report=await auditBrowserStorage(store.getState(),persistence,{now:()=> '2026-09-07T03:05:00.000Z'});
            assert.equal(report.status,'problem');
            assert.deepEqual(report.missingAttachmentIds,['attachment-missing']);
            assert.deepEqual(report.orphanAttachmentIds,['attachment-orphan']);
            assert.equal(store.getState().revision,revision);
            const cleaned=await purgeOrphanAttachments(store.getState(),persistence);
            assert.deepEqual(cleaned.orphanAttachmentIds,[]);
            assert.deepEqual(cleaned.missingAttachmentIds,['attachment-missing']);
            assert.equal(store.getState().revision,revision);
        '''))

    def test_pwa_registration_exposes_listener_cleanup(self):
        pwa=(ROOT/'src/pwa.ts').read_text()
        self.assertIn('dispose()',pwa)
        self.assertIn('removeEventListener("online"',pwa)
        self.assertIn('removeEventListener("beforeinstallprompt"',pwa)
        ui=(ROOT/'src/react/App.tsx').read_text()
        self.assertIn('return()=>handle.dispose()',ui)

    def test_offline_cache_is_app_shell_only(self):
        worker=(ROOT/'service-worker.js').read_text()
        self.assertIn('appShellUrls.has(url.href)',worker)
        self.assertIn('caches.match("./index.html")',worker)
        self.assertNotIn('cached||fetch(request).then((response)=>{if(!response||response.status!==200',worker)
        self.assertNotIn('http://',worker); self.assertNotIn('https://',worker)

if __name__=='__main__': unittest.main()
