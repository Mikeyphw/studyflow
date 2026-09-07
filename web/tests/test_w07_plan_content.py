from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]


class W07PlanContentTest(unittest.TestCase):
    def run_node(self, source: str) -> None:
        result = subprocess.run(
            ["node", "--input-type=module", "-e", source],
            cwd=WEB,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_subject_topic_crud_archive_reorder_and_notes_persist(self) -> None:
        self.run_node(r'''
import assert from "node:assert/strict";
import { createBrowserRepository } from "./runtime/storage.js";
import { createStudyStore } from "./runtime/state.js";
const values = new Map();
const storage = {
  getItem(key) { return values.has(key) ? values.get(key) : null; },
  setItem(key, value) { values.set(key, String(value)); },
  removeItem(key) { values.delete(key); },
};
const now = () => "2026-09-06T23:00:00.000Z";
const store = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
const subject = store.addSubject({ name: "Algorithms", weight: 8.5, notes: "Core material" });
assert.equal(subject.weight, 8.5);
const topic = store.addTopic({ subjectId: subject.id, name: "Graphs", priority: "high", status: "learning", notes: "Practice BFS" });
assert.equal(topic.priority, "high");
store.updateTopic(topic.id, { status: "reviewing" });
assert.equal(store.getState().topics.find((candidate) => candidate.id === topic.id).status, "reviewing");
store.moveSubject(subject.id, "up");
store.updateSubject(subject.id, { archived: true });
assert.equal(store.getState().subjects.find((candidate) => candidate.id === subject.id).archived, true);
const reloaded = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
assert.equal(reloaded.getState().topics.find((candidate) => candidate.id === topic.id).notes, "Practice BFS");
''')

    def test_safe_delete_refuses_linked_content_and_allows_empty_records(self) -> None:
        self.run_node(r'''
import assert from "node:assert/strict";
import { createBrowserRepository } from "./runtime/storage.js";
import { createStudyStore } from "./runtime/state.js";
const storage = { getItem() { return null; }, setItem() {}, removeItem() {} };
const store = createStudyStore({ repository: createBrowserRepository({ storage }), now: () => "2026-09-06T23:10:00.000Z" });
const subject = store.addSubject({ name: "Temporary" });
const topic = store.addTopic({ subjectId: subject.id, name: "Disposable" });
store.deleteTopic(topic.id);
store.deleteSubject(subject.id);
assert.equal(store.getState().subjects.some((candidate) => candidate.id === subject.id), false);
assert.throws(() => store.deleteSubject("subject-portuguese"));
''')

    def test_content_ui_has_react_editors_and_archive_actions(self) -> None:
        ui=(WEB/"src/react/App.tsx").read_text(encoding="utf-8")
        for token in ("Add subject", "Add topic", "store.addSubject", "store.addTopic", "store.updateSubject", "store.updateTopic", "Archive"):
            self.assertIn(token,ui)

    def test_domain_stays_framework_and_storage_independent(self) -> None:
        domain=(WEB/"src/domain.ts").read_text(encoding="utf-8").lower()
        self.assertNotIn("react",domain); self.assertNotIn("localstorage",domain); self.assertNotIn("indexeddb",domain)

if __name__ == "__main__":
    unittest.main()
