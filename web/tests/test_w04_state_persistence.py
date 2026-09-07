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

    def test_state_storage_render_and_event_boundaries_are_separate(self) -> None:
        app = (WEB / "app.js").read_text(encoding="utf-8")
        for path in ("domain.js", "storage.js", "state.js", "render.js", "events.js"):
            self.assertTrue((WEB / "src" / path).is_file(), path)
        for token in ("createBrowserPersistence", "createStudyStore", "renderStudyState", "bindPersistentStudyEvents"):
            self.assertIn(token, app)
        self.assertNotIn("localStorage", app)
        storage = (WEB / "src/storage.js").read_text(encoding="utf-8")
        self.assertIn("localStorage", storage)
        self.assertIn("studyflow.local.state.v", storage)
        self.assertIn("createBrowserRepository", storage)
        self.assertIn("createBrowserPersistence", storage)
        self.assertNotIn("fetch(", storage)

    def test_quick_add_domain_activity_persists_across_store_recreation(self) -> None:
        self.run_node(
            r'''
import assert from "node:assert/strict";
import { createBrowserRepository, STORAGE_KEY } from "./src/storage.js";
import { createStudyStore } from "./src/state.js";

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
import { createBrowserRepository, STORAGE_KEY } from "./src/storage.js";
import { createStudyStore } from "./src/state.js";

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

    def test_w04_persistence_boundary_remains_separate_after_w12_upgrade(self) -> None:
        app = (WEB / "app.js").read_text(encoding="utf-8").lower()
        storage = (WEB / "src/storage.js").read_text(encoding="utf-8").lower()
        state = (WEB / "src/state.js").read_text(encoding="utf-8").lower()
        indexed = (WEB / "src/indexeddb.js").read_text(encoding="utf-8").lower()
        self.assertNotIn("localstorage", app)
        self.assertIn("localstorage", storage)
        self.assertIn("startfocus", state)
        self.assertIn("studyflow.local.state.v1", storage)
        self.assertNotIn("indexeddb", state)
        self.assertIn("indexeddb", indexed)


if __name__ == "__main__":
    unittest.main()
