from pathlib import Path
import subprocess, textwrap, unittest
ROOT=Path(__file__).resolve().parents[1]

MEMORY_REPO=r'''
class MemAttachments { constructor(){this.map=new Map()} async put(blob,meta={}){const id=meta.id??`a-${this.map.size+1}`;const rec={id,name:meta.name??'attachment',mimeType:blob.type||'application/octet-stream',sizeBytes:blob.size,lastModified:null,createdAt:'2026-09-07T12:00:00.000Z',blob};this.map.set(id,rec);return {...rec,blob:undefined}} async get(id){return this.map.get(id)??null} async list(){return [...this.map.values()].map(({blob,...x})=>x)} async delete(id){this.map.delete(id)} async clear(){this.map.clear()} }
'''

class PC05PortableV2Attachments(unittest.TestCase):
    def run_node(self, source):
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=ROOT,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)

    def test_v2_round_trip_restores_verified_attachment_bytes(self):
        self.run_node(textwrap.dedent(f'''
            import assert from 'node:assert/strict'; import {{ createInitialStudyState, createMaterial }} from './runtime/domain.js'; import {{ createStudyStore }} from './runtime/state.js'; import {{ createPortablePackageV2, parsePortablePackageV2, serializePortablePackage, buildPortableImportPreview, applyPortableImportWithAttachments }} from './runtime/portable.js';
            {MEMORY_REPO}
            const now=()=> '2026-09-07T12:00:00.000Z'; const state=createInitialStudyState({{now:now()}}); const source=new MemAttachments(); await source.put(new Blob(['attachment bytes'],{{type:'text/plain'}}),{{id:'attachment-1',name:'notes.txt'}}); state.materials.push(createMaterial({{planId:state.activePlanId,title:'Notes',kind:'file',attachmentId:'attachment-1',locator:'local-blob:attachment-1',mimeType:'text/plain',sizeBytes:16,createdAt:now(),updatedAt:now()}}));
            const pkg=await createPortablePackageV2(state,source,{{now}}); assert.equal(pkg.formatVersion,2); assert.equal(pkg.manifest.attachments.bytesIncluded,true); assert.match(pkg.attachments[0].sha256,/^[0-9a-f]{{64}}$/); const parsed=await parsePortablePackageV2(serializePortablePackage(pkg),{{now}}); assert.equal(parsed.attachmentRecords.length,1);
            const targetAttachments=new MemAttachments(); const targetState=createInitialStudyState({{now:now()}}); targetState.materials=[]; const repo={{load:()=>({{status:'loaded',state:targetState}}),save:()=>({{status:'saved'}}),clear:()=>({{status:'cleared'}})}}; const store=createStudyStore({{repository:repo,now}}); const preview=buildPortableImportPreview(store.getState(),parsed,{{mode:'replace'}}); await applyPortableImportWithAttachments(store,preview,targetAttachments); const restored=await targetAttachments.get('attachment-1'); assert.equal(await restored.blob.text(),'attachment bytes'); assert.equal(store.getState().materials[0].attachmentId,'attachment-1');
        '''))

    def test_v2_tampering_is_rejected_before_preview(self):
        self.run_node(textwrap.dedent(f'''
            import assert from 'node:assert/strict'; import {{ createInitialStudyState, createMaterial }} from './runtime/domain.js'; import {{ createPortablePackageV2, parsePortablePackageV2, serializePortablePackage }} from './runtime/portable.js';
            {MEMORY_REPO}
            const now=()=> '2026-09-07T12:00:00.000Z'; const state=createInitialStudyState({{now:now()}}); const a=new MemAttachments(); await a.put(new Blob(['abc'],{{type:'text/plain'}}),{{id:'attachment-1',name:'a.txt'}}); state.materials.push(createMaterial({{planId:state.activePlanId,title:'A',kind:'file',attachmentId:'attachment-1',locator:'local-blob:attachment-1',mimeType:'text/plain',sizeBytes:3,createdAt:now(),updatedAt:now()}})); const pkg=await createPortablePackageV2(state,a,{{now}}); pkg.attachments[0].base64=btoa('abd'); let rejected=false; try{{await parsePortablePackageV2(serializePortablePackage(pkg),{{now}})}}catch(e){{rejected=String(e).includes('checksum')}} assert.equal(rejected,true);
        '''))

    def test_v1_remains_importable_while_v2_is_latest(self):
        self.run_node(textwrap.dedent('''
            import assert from 'node:assert/strict'; import { createInitialStudyState } from './runtime/domain.js'; import { PORTABLE_FORMAT_VERSION, createPortablePackage, parsePortablePackage, serializePortablePackage } from './runtime/portable.js'; const now=()=> '2026-09-07T12:00:00.000Z'; const state=createInitialStudyState({now:now()}); const legacy=createPortablePackage(state,{now}); assert.equal(legacy.formatVersion,1); assert.equal(PORTABLE_FORMAT_VERSION,2); assert.equal(parsePortablePackage(serializePortablePackage(legacy),{now}).payload.schemaVersion,6);
        '''))

    def test_create_as_new_plan_remaps_state_and_attachment_ids(self):
        self.run_node(textwrap.dedent(f'''
            import assert from 'node:assert/strict'; import {{ createInitialStudyState, createMaterial }} from './runtime/domain.js'; import {{ createStudyStore }} from './runtime/state.js'; import {{ createPortablePackageV2, parsePortablePackageV2, serializePortablePackage, buildPortableImportPreview, applyPortableImportWithAttachments }} from './runtime/portable.js';
            {MEMORY_REPO}
            const now=()=> '2026-09-07T12:00:00.000Z'; const sourceState=createInitialStudyState({{now:now()}}); sourceState.plans[0].name='Source plan'; const sourceAttachments=new MemAttachments(); await sourceAttachments.put(new Blob(['new-plan-bytes'],{{type:'text/plain'}}),{{id:'attachment-1',name:'x.txt'}}); sourceState.materials.push(createMaterial({{planId:sourceState.activePlanId,subjectId:sourceState.subjects[0].id,title:'X',kind:'file',attachmentId:'attachment-1',locator:'local-blob:attachment-1',mimeType:'text/plain',sizeBytes:14,createdAt:now(),updatedAt:now()}})); const parsed=await parsePortablePackageV2(serializePortablePackage(await createPortablePackageV2(sourceState,sourceAttachments,{{now}})),{{now}});
            const current=createInitialStudyState({{now:now()}}); current.plans[0].name='Current plan'; current.materials=[]; const repo={{load:()=>({{status:'loaded',state:current}}),save:()=>({{status:'saved'}}),clear:()=>({{status:'cleared'}})}}; const store=createStudyStore({{repository:repo,now}}); const targetAttachments=new MemAttachments(); const preview=buildPortableImportPreview(store.getState(),parsed,{{mode:'new-plan'}}); assert.equal(preview.nextState.plans.length,2); assert.notEqual(preview.nextState.activePlanId,current.activePlanId); assert.equal(preview.summary.remove,0); const mapped=Object.values(preview.attachmentIdMap)[0]; assert.ok(mapped&&mapped!=='attachment-1'); await applyPortableImportWithAttachments(store,preview,targetAttachments); assert.equal(store.getState().plans.some(p=>p.name==='Current plan'),true); assert.equal(store.getState().plans.some(p=>p.name.includes('Source plan')),true); assert.equal(await (await targetAttachments.get(mapped)).blob.text(),'new-plan-bytes');
        '''))

    def test_stale_preview_does_not_write_attachment_bytes(self):
        self.run_node(textwrap.dedent(f'''
            import assert from 'node:assert/strict'; import {{ createInitialStudyState }} from './runtime/domain.js'; import {{ createStudyStore }} from './runtime/state.js'; import {{ createPortablePackageV2, parsePortablePackageV2, serializePortablePackage, buildPortableImportPreview, applyPortableImportWithAttachments }} from './runtime/portable.js';
            {MEMORY_REPO}
            const now=()=> '2026-09-07T12:00:00.000Z'; const source=createInitialStudyState({{now:now()}}); const parsed=await parsePortablePackageV2(serializePortablePackage(await createPortablePackageV2(source,new MemAttachments(),{{now}})),{{now}}); const current=createInitialStudyState({{now:now()}}); const repo={{load:()=>({{status:'loaded',state:current}}),save:()=>({{status:'saved'}}),clear:()=>({{status:'cleared'}})}}; const store=createStudyStore({{repository:repo,now}}); const preview=buildPortableImportPreview(store.getState(),parsed); store.addGoal({{name:'mutation',metric:'study-minutes',target:1,period:'weekly'}}); const target=new MemAttachments(); let stale=false; try{{await applyPortableImportWithAttachments(store,preview,target)}}catch(e){{stale=String(e).includes('stale')}} assert.equal(stale,true); assert.equal((await target.list()).length,0);
        '''))

if __name__=='__main__': unittest.main()
