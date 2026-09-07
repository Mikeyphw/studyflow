from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]
class PC10ProductShellTest(unittest.TestCase):
    def run_node(self,source:str):
        r=subprocess.run(['node','--input-type=module','-e',source],cwd=WEB,text=True,capture_output=True)
        self.assertEqual(r.returncode,0,r.stderr or r.stdout)
    def test_v6_migration_preserves_user_data_and_does_not_reopen_onboarding(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createInitialStudyState,migrateStudyState,validateStudyState} from './runtime/domain.js';
            const current=createInitialStudyState({now:'2026-09-07T12:00:00Z'}); const legacy=JSON.parse(JSON.stringify(current)); legacy.schemaVersion=6; delete legacy.preferences; legacy.plans[0].name='Existing user plan'; const migrated=migrateStudyState(legacy,{now:'2026-09-07T13:00:00Z'}); assert.equal(migrated.schemaVersion,7); assert.equal(migrated.preferences.onboardingCompleted,true); assert.equal(migrated.plans[0].name,'Existing user plan'); assert.deepEqual(validateStudyState(migrated),[]);
        '''))
    def test_global_search_is_local_ranked_and_covers_core_records(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js'; import {searchStudyState} from './runtime/product.js';
            const storage={getItem(){return null},setItem(){},removeItem(){}}; const store=createStudyStore({repository:createBrowserRepository({storage})}); const subject=store.addSubject({name:'Data Structures'}); const topic=store.addTopic({subjectId:subject.id,name:'Binary Trees'}); store.addQuestion({subjectId:subject.id,topicId:topic.id,prompt:'What is a binary search tree?',answer:'BST'}); store.addMaterial({subjectId:subject.id,topicId:topic.id,title:'Tree notes'}); const results=searchStudyState(store.getState(),'binary'); assert.ok(results.some(x=>x.type==='topic'&&x.id===topic.id)); assert.ok(results.some(x=>x.type==='question')); assert.ok(results.every(x=>x.planId)); assert.equal(searchStudyState(store.getState(),'x').length,0);
        '''))
    def test_preferences_and_blank_onboarding_are_persisted_without_sample_history(self):
        self.run_node(textwrap.dedent(r'''
            import assert from 'node:assert/strict'; import {createBrowserRepository} from './runtime/storage.js'; import {createStudyStore} from './runtime/state.js';
            const values=new Map(); const storage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,String(v)),removeItem:k=>values.delete(k)}; const store=createStudyStore({repository:createBrowserRepository({storage})}); store.setPreferences({locale:'pt-BR',showArchivedInSearch:true}); assert.equal(store.getState().preferences.locale,'pt-BR'); store.completeOnboarding({locale:'pt-BR',mode:'LANGUAGE_DECK',planName:'Português',useSample:false}); const s=store.getState(); assert.equal(s.schemaVersion,7); assert.equal(s.preferences.onboardingCompleted,true); assert.equal(s.plans.length,1); assert.equal(s.plans[0].mode,'LANGUAGE_DECK'); assert.equal(s.subjects.length,0); assert.equal(s.sessions.length,0); assert.equal(s.evidence.length,0);
        '''))
    def test_product_shell_declares_search_settings_privacy_locale_and_onboarding(self):
        source=(WEB/'src/react/App.tsx').read_text(encoding='utf-8')
        for token in ('GlobalSearchDialog','SettingsDialog','OnboardingWizard','Search local study data','uiText(state.preferences.locale,"privacy")','Português (Brasil)','Run onboarding again'):
            self.assertIn(token,source)
        product=(WEB/'src/product.ts').read_text(encoding='utf-8')
        self.assertIn('installDocumentLocalization',product); self.assertIn('PT_BR_EXACT',product)
