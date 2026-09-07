from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]


class W04StatePersistenceTest(unittest.TestCase):
    def run_node(self, source: str) -> None:
        result = subprocess.run(
            ["node", "--input-type=module", "-e", source],
            cwd=WEB,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_state_storage_and_react_ui_boundaries_are_separate(self) -> None:
        for path in ("domain.ts", "storage.ts", "state.ts", "indexeddb.ts", "main.tsx", "react/App.tsx"):
            self.assertTrue((WEB / "src" / path).is_file(), path)
        main=(WEB/"src/main.tsx").read_text(encoding="utf-8")
        ui=(WEB/"src/react/App.tsx").read_text(encoding="utf-8")
        self.assertIn("createBrowserPersistence", main); self.assertIn("createStudyStore", main)
        self.assertIn("useSyncExternalStore", ui)
        self.assertNotIn("localStorage", main)

    def test_quick_add_domain_activity_persists_across_store_recreation(self) -> None:
        self.run_node(
            r'''
import assert from "node:assert/strict";
import { createBrowserRepository, STORAGE_KEY } from "./runtime/storage.js";
import { createStudyStore } from "./runtime/state.js";

const values = new Map();
const storage = {
  getItem(key) { return values.has(key) ? values.get(key) : null; },
  setItem(key, value) { values.set(key, String(value)); },
  removeItem(key) { values.delete(key); },
};
const now = () => "2026-09-06T23:30:00.000Z";
const first = createStudyStore({ repository: createBrowserRepository({ storage }), now });
assert.equal(first.getState().activities.length, 3);
const added = first.addActivity({ title: "Durable review", kind: "review", durationMinutes: 25 });
assert.match(added.id, /^activity-/);
assert.equal(first.getPersistenceStatus().durable, true);
assert.ok(values.has(STORAGE_KEY));

const second = createStudyStore({ repository: createBrowserRepository({ storage }), now });
assert.equal(second.getPersistenceStatus().status, "loaded");
assert.equal(second.getState().activities.some((activity) => activity.id === added.id), true);
assert.equal(second.getState().activities.find((activity) => activity.id === added.id).title, "Durable review");
'''
        )

    def test_corrupt_saved_state_falls_back_safely_then_can_recover(self) -> None:
        self.run_node(
            r'''
import assert from "node:assert/strict";
import { createBrowserRepository, STORAGE_KEY } from "./runtime/storage.js";
import { createStudyStore } from "./runtime/state.js";

const values = new Map([[STORAGE_KEY, "{not-json"]]);
const storage = {
  getItem(key) { return values.has(key) ? values.get(key) : null; },
  setItem(key, value) { values.set(key, String(value)); },
  removeItem(key) { values.delete(key); },
};
const store = createStudyStore({
  repository: createBrowserRepository({ storage }),
  now: () => "2026-09-06T23:31:00.000Z",
});
assert.equal(store.getPersistenceStatus().status, "invalid");
assert.equal(store.getPersistenceStatus().durable, false);
assert.equal(store.getState().activePlanId, "plan-local-default");
store.addActivity({ title: "Recovery write", kind: "study", durationMinutes: 15 });
assert.equal(store.getPersistenceStatus().status, "saved");
assert.doesNotThrow(() => JSON.parse(values.get(STORAGE_KEY)));
'''
        )

    def test_w04_persistence_boundary_remains_separate_after_w15_upgrade(self) -> None:
        storage=(WEB/"src/storage.ts").read_text(encoding="utf-8").lower()
        state=(WEB/"src/state.ts").read_text(encoding="utf-8").lower()
        indexed=(WEB/"src/indexeddb.ts").read_text(encoding="utf-8").lower()
        self.assertIn("localstorage", storage); self.assertIn("startfocus", state); self.assertNotIn("indexeddb", state); self.assertIn("indexeddb", indexed)

if __name__ == "__main__":
    unittest.main()
