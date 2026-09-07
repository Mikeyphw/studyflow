from pathlib import Path
import subprocess, textwrap, unittest
ROOT=Path(__file__).resolve().parents[1]
class W11PortableTests(unittest.TestCase):
    def test_portable_ui_is_preview_first(self):
        html=(ROOT/'index.html').read_text(); events=(ROOT/'src/events.js').read_text()
        for token in ('portable-export','portable-import-file','portable-preview','portable-import-apply'): self.assertIn(token,html)
        self.assertIn('No local data has changed',events)
    def test_round_trip_preview_does_not_mutate(self):
        script=textwrap.dedent('''
            import { createInitialStudyState } from './src/domain.js';
            import { createPortablePackage, serializePortablePackage, parsePortablePackage, buildPortableImportPreview } from './src/portable.js';
            const now='2026-09-07T00:00:00.000Z'; const current=createInitialStudyState({now}); const before=JSON.stringify(current);
            const pkg=createPortablePackage(current,{now:()=>now}); const parsed=parsePortablePackage(serializePortablePackage(pkg),{now:()=>now}); const preview=buildPortableImportPreview(current,parsed);
            if(JSON.stringify(current)!==before) throw new Error('preview mutated current state');
            if(preview.summary.add||preview.summary.update||preview.summary.remove) throw new Error(JSON.stringify(preview.summary));
            if(parsed.manifest.counts.questions!==current.questions.length) throw new Error('manifest counts wrong');
        ''')
        subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,check=True)
    def test_invalid_and_future_packages_are_refused(self):
        script=textwrap.dedent('''
            import { createInitialStudyState } from './src/domain.js'; import { createPortablePackage, serializePortablePackage, parsePortablePackage } from './src/portable.js';
            const now='2026-09-07T00:00:00.000Z'; const state=createInitialStudyState({now}); const pkg=createPortablePackage(state,{now:()=>now});
            let future={...pkg,formatVersion:99}; let ok=false; try{parsePortablePackage(serializePortablePackage(future));}catch{ok=true} if(!ok)throw new Error('future version accepted');
            let bad=JSON.parse(JSON.stringify(pkg)); bad.payload.activities[0].planId='missing'; ok=false; try{parsePortablePackage(serializePortablePackage(bad));}catch{ok=true} if(!ok)throw new Error('invalid state accepted');
            bad=JSON.parse(JSON.stringify(pkg)); bad.manifest.counts.questions=999; ok=false; try{parsePortablePackage(serializePortablePackage(bad));}catch{ok=true} if(!ok)throw new Error('manifest mismatch accepted');
        ''')
        subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,check=True)
    def test_store_apply_is_revision_guarded(self):
        script=textwrap.dedent('''
            import { createInitialStudyState } from './src/domain.js'; import { createStudyStore } from './src/state.js'; import { createPortablePackage, buildPortableImportPreview } from './src/portable.js';
            let saved=null; const repo={load:()=>({status:'empty',state:null}),save:s=>{saved=JSON.parse(JSON.stringify(s));return{status:'saved'}},clear:()=>({status:'cleared'})}; const now=()=> '2026-09-07T00:00:00.000Z'; const store=createStudyStore({repository:repo,initialStateFactory:createInitialStudyState,now});
            const imported=createInitialStudyState({now:now()}); imported.plans[0].name='Imported plan'; const preview=buildPortableImportPreview(store.getState(),createPortablePackage(imported,{now}));
            store.addGoal({name:'Mutation',metric:'study-minutes',target:1,period:'weekly'}); let stale=false; try{store.applyPortableImportPreview(preview)}catch(e){stale=String(e).includes('stale')} if(!stale)throw new Error('stale preview applied');
            const fresh=buildPortableImportPreview(store.getState(),createPortablePackage(imported,{now})); store.applyPortableImportPreview(fresh); if(store.getState().plans[0].name!=='Imported plan')throw new Error('import not applied');
        ''')
        subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,check=True)
if __name__=='__main__': unittest.main()
