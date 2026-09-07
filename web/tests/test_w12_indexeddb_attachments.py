from pathlib import Path
import subprocess, textwrap, unittest
ROOT=Path(__file__).resolve().parents[1]

class W12IndexedDbAttachmentsTest(unittest.TestCase):
    def run_node(self, source):
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=ROOT,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_browser_bootstrap_prefers_indexeddb_and_migrates_localstorage(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict';
            import { FakeIndexedDBFactory } from './tests/fake_indexeddb.mjs';
            import { createInitialStudyState } from './src/domain.js';
            import { createBrowserPersistence } from './src/storage.js';
            import { createStudyStore } from './src/state.js';
            const fake=new FakeIndexedDBFactory(); const legacy=createInitialStudyState({now:'2026-09-07T01:00:00.000Z'}); legacy.schemaVersion=3; legacy.activities.push({...legacy.activities[0],id:'activity-pre-w12',title:'Before W12'});
            const values=new Map([['studyflow.local.state.v3',JSON.stringify(legacy)]]); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)};
            const first=await createBrowserPersistence({storage,indexedDBFactory:fake,dbName:'w12-test',now:()=> '2026-09-07T01:05:00.000Z'});
            assert.equal(first.backend,'indexeddb'); assert.equal(first.repository.load().status,'migrated'); assert.equal(first.repository.load().state.schemaVersion,4); assert.equal(first.repository.load().state.activities.some(a=>a.id==='activity-pre-w12'),true); assert.equal(values.has('studyflow.local.state.v3'),false);
            const store=createStudyStore({repository:first.repository,now:()=> '2026-09-07T01:06:00.000Z'}); const added=store.addActivity({title:'After W12',kind:'study',durationMinutes:25}); await first.repository.flush();
            const second=await createBrowserPersistence({storage,indexedDBFactory:fake,dbName:'w12-test',now:()=> '2026-09-07T01:07:00.000Z'}); assert.equal(second.repository.load().status,'loaded'); assert.equal(second.repository.load().state.activities.some(a=>a.id===added.id),true);
        '''))

    def test_attachment_bytes_and_material_metadata_are_separate(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict'; import { FakeIndexedDBFactory } from './tests/fake_indexeddb.mjs'; import { createBrowserPersistence } from './src/storage.js'; import { createStudyStore } from './src/state.js';
            const fake=new FakeIndexedDBFactory(); const persistence=await createBrowserPersistence({storage:null,indexedDBFactory:fake,dbName:'attachment-test',now:()=> '2026-09-07T02:00:00.000Z'}); const store=createStudyStore({repository:persistence.repository,now:()=> '2026-09-07T02:00:00.000Z'});
            const file=new File(['hello studyflow'],'notes.txt',{type:'text/plain',lastModified:123}); const attachment=await persistence.attachments.put(file); const record=await persistence.attachments.get(attachment.id); assert.equal(await record.blob.text(),'hello studyflow'); assert.equal(record.mimeType,'text/plain');
            const material=store.addMaterial({title:'Notes',kind:'file',attachmentId:attachment.id,locator:`local-blob:${attachment.id}`,mimeType:attachment.mimeType,sizeBytes:attachment.sizeBytes}); assert.equal(material.attachmentId,attachment.id); assert.equal(store.getState().materials[0].sizeBytes,file.size); await persistence.repository.flush();
            assert.equal((await persistence.attachments.list()).length,1); await persistence.attachments.delete(attachment.id); assert.equal(await persistence.attachments.get(attachment.id),null);
        '''))

    def test_portable_json_does_not_claim_to_embed_attachment_bytes(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict'; import { createInitialStudyState, createMaterial } from './src/domain.js'; import { createPortablePackage } from './src/portable.js';
            const state=createInitialStudyState({now:'2026-09-07T03:00:00.000Z'}); state.materials.push(createMaterial({planId:state.activePlanId,title:'Local PDF',kind:'file',attachmentId:'attachment-1',locator:'local-blob:attachment-1',mimeType:'application/pdf',sizeBytes:1234,createdAt:'2026-09-07T03:00:00.000Z',updatedAt:'2026-09-07T03:00:00.000Z'})); const pkg=createPortablePackage(state,{now:()=> '2026-09-07T03:01:00.000Z'}); assert.equal(pkg.manifest.attachments.bytesIncluded,false); assert.equal(pkg.manifest.attachments.references,1); assert.equal(pkg.payload.materials[0].attachmentId,null); assert.equal(pkg.payload.materials[0].locator,null);
        '''))

    def test_storage_boundary_is_explicit(self):
        storage=(ROOT/'src/storage.js').read_text(); indexed=(ROOT/'src/indexeddb.js').read_text(); domain=(ROOT/'src/domain.js').read_text().lower()
        self.assertIn('createBrowserPersistence',storage); self.assertIn('createIndexedDbPersistence',indexed); self.assertIn('ATTACHMENT_STORE',indexed); self.assertNotIn('indexeddb',domain)

if __name__=='__main__': unittest.main()
