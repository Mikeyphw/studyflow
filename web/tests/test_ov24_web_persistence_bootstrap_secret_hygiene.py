from pathlib import Path
import subprocess
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Ov24WebPersistenceBootstrapSecretHygieneTest(unittest.TestCase):
    def run_node(self, source: str) -> None:
        result = subprocess.run(
            ["node", "--input-type=module", "-e", source],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_indexeddb_save_is_pending_until_flush_and_stale_tab_is_rejected(self):
        self.run_node(textwrap.dedent(
            """
            import assert from 'node:assert/strict';
            import { FakeIndexedDBFactory } from './tests/fake_indexeddb.mjs';
            import { createBrowserPersistence } from './runtime/storage.js';
            import { createStudyStore } from './runtime/state.js';
            const fake = new FakeIndexedDBFactory();
            const first = await createBrowserPersistence({ storage:null, indexedDBFactory:fake, dbName:'ov24-durability', now:()=> '2026-09-14T21:24:00.000Z' });
            const firstStore = createStudyStore({ repository:first.repository, now:()=> '2026-09-14T21:24:01.000Z' });
            assert.equal(first.repository.load().status, 'pending');
            await first.repository.flush();
            assert.equal(first.repository.load().status, 'loaded');
            const tabA = await createBrowserPersistence({ storage:null, indexedDBFactory:fake, dbName:'ov24-durability', now:()=> '2026-09-14T21:24:02.000Z' });
            const tabB = await createBrowserPersistence({ storage:null, indexedDBFactory:fake, dbName:'ov24-durability', now:()=> '2026-09-14T21:24:03.000Z' });
            const storeA = createStudyStore({ repository:tabA.repository, now:()=> '2026-09-14T21:24:04.000Z' });
            const storeB = createStudyStore({ repository:tabB.repository, now:()=> '2026-09-14T21:24:05.000Z' });
            storeA.addActivity({ title:'Committed in tab A', kind:'study', durationMinutes:25 });
            await tabA.repository.flush();
            storeB.addActivity({ title:'Stale tab B write', kind:'study', durationMinutes:30 });
            await assert.rejects(() => tabB.repository.flush(), /another StudyFlow tab/i);
            const reload = await createBrowserPersistence({ storage:null, indexedDBFactory:fake, dbName:'ov24-durability', now:()=> '2026-09-14T21:24:06.000Z' });
            const state = reload.repository.load().state;
            assert.equal(state.activities.some(activity => activity.title === 'Committed in tab A'), true);
            assert.equal(state.activities.some(activity => activity.title === 'Stale tab B write'), false);
            """
        ))

    def test_indexeddb_initialization_failure_is_visible_without_localstorage_downgrade(self):
        self.run_node(textwrap.dedent(
            """
            import assert from 'node:assert/strict';
            import { createInitialStudyState } from './runtime/domain.js';
            import { createBrowserPersistence } from './runtime/storage.js';
            class BrokenIndexedDBFactory { open(){ const request={}; setTimeout(()=>{ request.error = new Error('blocked by browser policy'); request.onerror?.(); }, 0); return request; } }
            const values = new Map();
            const storage = { getItem:key=>values.get(key)??null, setItem:(key,value)=>values.set(key,String(value)), removeItem:key=>values.delete(key) };
            const persistence = await createBrowserPersistence({ storage, indexedDBFactory:new BrokenIndexedDBFactory(), dbName:'ov24-broken-idb' });
            assert.equal(persistence.backend, 'unavailable');
            assert.equal(persistence.repository.load().status, 'unavailable');
            assert.equal(persistence.repository.save(createInitialStudyState()).status, 'unavailable');
            assert.match(persistence.fallbackReason, /persistence disabled instead of silently falling back/i);
            assert.equal(values.size, 0);
            """
        ))

    def test_portable_runtime_focus_is_sanitized_and_size_is_checked_before_materialization(self):
        self.run_node(textwrap.dedent(
            """
            import assert from 'node:assert/strict';
            import { createInitialStudyState, createFocusState } from './runtime/domain.js';
            import { assertPortableByteLength, createPortablePackage, createPortablePackageV2, parsePortablePackage, serializePortablePackage } from './runtime/portable.js';
            const state = createInitialStudyState({ now:'2026-09-14T21:24:00.000Z' });
            state.focus = createFocusState({ status:'running', activityId:state.activities[0].id, startedAt:'2026-09-14T21:00:00.000Z', lastResumedAt:'2026-09-14T21:00:00.000Z', endsAt:'2026-09-14T21:45:00.000Z', durationMinutes:45 });
            const legacy = createPortablePackage(state, { now:()=> '2026-09-14T21:24:07.000Z' });
            assert.equal(legacy.payload.focus.status, 'idle');
            assert.equal(legacy.payload.focus.activityId, null);
            const v2 = await createPortablePackageV2(state, null, { now:()=> '2026-09-14T21:24:08.000Z' });
            assert.equal(v2.payload.focus.status, 'idle');
            const tampered = JSON.parse(serializePortablePackage(legacy));
            tampered.payload.focus = { ...state.focus };
            const reparsed = parsePortablePackage(serializePortablePackage(tampered));
            assert.equal(reparsed.payload.focus.status, 'idle');
            assert.throws(() => assertPortableByteLength(11, 10, 'Plan Content import'), /before materialization|local import limit/i);
            """
        ))

    def test_denied_localstorage_does_not_crash_bootstrap_link_install(self):
        self.run_node(textwrap.dedent(
            """
            import assert from 'node:assert/strict';
            const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
            Object.defineProperty(globalThis, 'localStorage', { configurable:true, get(){ throw new Error('localStorage denied'); } });
            const { installStudyFlowWebSflpPeer, StudyFlowBrowserAttentionLedgerStorage } = await import('./runtime/sflp_link.js');
            assert.equal(installStudyFlowWebSflpPeer('web'), null);
            const ledger = new StudyFlowBrowserAttentionLedgerStorage('ov24-ledger');
            ledger.writeEncodedSnapshot('{"schemaVersion":1,"entries":[]}');
            assert.equal(ledger.readEncodedSnapshot(), '{"schemaVersion":1,"entries":[]}');
            if (descriptor) Object.defineProperty(globalThis, 'localStorage', descriptor);
            else delete globalThis.localStorage;
            """
        ))

    def test_ui_pwa_and_secret_hygiene_contracts_are_wired(self):
        app = (ROOT / "src/react/App.tsx").read_text()
        pwa = (ROOT / "src/pwa.ts").read_text()
        main = (ROOT / "src/main.tsx").read_text()
        self.assertIn("readBoundedFileText(file,MAX_PLAN_CONTENT_IMPORT_BYTES", app)
        self.assertIn("readBoundedFileBytes(file,MAX_ENCRYPTED_PORTABLE_BYTES", app)
        self.assertIn("finally{setExportPassword(\"\");setExportConfirm(\"\")}", app)
        self.assertIn("finally{setImportPassword(\"\")}", app)
        self.assertIn("File upload is disabled because this browser session has no IndexedDB attachment storage", app)
        self.assertIn("Material removed and local blob cleaned", app)
        self.assertIn("isPromiseLike(r)", app)
        self.assertIn("registrationPromise = null", pwa)
        self.assertIn("event.persisted", main)
        self.assertIn("attentionRuntime?.start()", main)


if __name__ == "__main__":
    unittest.main()
