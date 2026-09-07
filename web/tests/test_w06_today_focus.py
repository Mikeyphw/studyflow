from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]


class W06TodayFocusTest(unittest.TestCase):
    def run_node(self, source: str) -> None:
        result = subprocess.run(
            ["node", "--input-type=module", "-e", source],
            cwd=WEB,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_focus_uses_persisted_absolute_timestamps_and_survives_reload(self) -> None:
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
let clock = "2026-09-06T20:00:00.000Z";
const now = () => clock;
const first = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
const activity = first.getState().activities[0];
first.startFocus({ activityId: activity.id, durationMinutes: 25, note: "read carefully" });
const started = first.getFocusSnapshot();
assert.equal(started.status, "running");
assert.equal(started.endsAt, "2026-09-06T20:25:00.000Z");
assert.equal(started.note, "read carefully");
clock = "2026-09-06T20:05:00.000Z";
const second = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
assert.equal(second.getFocusSnapshot(clock).remainingMs, 20 * 60_000);
second.pauseFocus();
assert.equal(second.getFocusSnapshot().status, "paused");
clock = "2026-09-06T20:10:00.000Z";
second.startFocus();
assert.equal(second.getFocusSnapshot().endsAt, "2026-09-06T20:30:00.000Z");
clock = "2026-09-06T20:15:00.000Z";
const session = second.completeFocus();
assert.equal(session.status, "completed");
assert.equal(session.durationMinutes, 10);
assert.equal(second.getState().activities.find((candidate) => candidate.id === activity.id).status, "completed");
assert.equal(second.getState().evidence.at(-1).kind, "focus-session-completed");
assert.equal(second.getFocusSnapshot().status, "idle");
''')

    def test_cancelled_focus_is_history_but_not_completion_evidence(self) -> None:
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
let clock = "2026-09-06T21:00:00.000Z";
const now = () => clock;
const store = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
store.startFocus({ durationMinutes: 25 });
clock = "2026-09-06T21:03:00.000Z";
const session = store.cancelFocus({ note: "interrupted" });
assert.equal(session.status, "cancelled");
assert.equal(store.getState().evidence.length, 0);
assert.equal(store.getState().sessions.at(-1).note, "interrupted");
''')

    def test_recommendation_is_derived_only_from_existing_queue_data(self) -> None:
        self.run_node(r'''
import assert from "node:assert/strict";
import { createInitialStudyState, recommendNextActivity } from "./runtime/domain.js";
const state = createInitialStudyState({ now: "2026-09-06T22:00:00.000Z" });
const recommendation = recommendNextActivity(state);
assert.ok(state.activities.some((activity) => activity.id === recommendation.activity.id));
assert.ok(recommendation.signals.includes("Queued for today"));
assert.equal(recommendation.reason.includes("weak"), false);
''')

    def test_w04_v1_storage_is_migrated_without_losing_user_activities(self) -> None:
        self.run_node(r'''
import assert from "node:assert/strict";
import { createInitialStudyState } from "./runtime/domain.js";
import { createBrowserRepository, STORAGE_KEY } from "./runtime/storage.js";
import { createStudyStore } from "./runtime/state.js";
const legacy = createInitialStudyState({ now: "2026-09-06T19:00:00.000Z" });
legacy.schemaVersion = 1; delete legacy.focus; delete legacy.studyCycle;
legacy.activities.push({ ...legacy.activities[0], id: "activity-user-v1", title: "My existing W04 activity" });
const values = new Map([["studyflow.local.state.v1", JSON.stringify(legacy)]]);
const storage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: (key) => values.delete(key) };
const now = () => "2026-09-06T19:05:00.000Z";
const store = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
assert.equal(store.getPersistenceStatus().status, "migrated");
assert.equal(store.getState().schemaVersion, 7);
assert.ok(store.getState().activities.some((activity) => activity.id === "activity-user-v1"));
assert.ok(values.has(STORAGE_KEY));
''')

    def test_study_cycle_cursor_is_persistent_and_explicit(self) -> None:
        self.run_node(r'''
import assert from "node:assert/strict";
import { createBrowserRepository } from "./runtime/storage.js";
import { createStudyStore } from "./runtime/state.js";
import { selectCurrentCycleSubject } from "./runtime/domain.js";
const values = new Map(); const storage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: (key) => values.delete(key) };
const now = () => "2026-09-06T22:30:00.000Z"; const first = createStudyStore({ repository: createBrowserRepository({ storage, now }), now });
const before = selectCurrentCycleSubject(first.getState()).id; first.advanceStudyCycle(); const after = selectCurrentCycleSubject(first.getState()).id; assert.notEqual(before, after);
const second = createStudyStore({ repository: createBrowserRepository({ storage, now }), now }); assert.equal(selectCurrentCycleSubject(second.getState()).id, after);
''')

    def test_focus_ui_exposes_complete_cancel_note_and_queue(self) -> None:
        ui=(WEB/"src/react/App.tsx").read_text(encoding="utf-8")
        for token in ("Focus", "Session note", "Complete", "Cancel", "Today queue", "getFocusSnapshot"):
            self.assertIn(token,ui)

if __name__ == "__main__":
    unittest.main()
