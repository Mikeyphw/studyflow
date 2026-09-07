from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]


class W05DomainModelTest(unittest.TestCase):
    def run_node(self, source: str) -> None:
        result = subprocess.run(
            ["node", "--input-type=module", "-e", source],
            cwd=WEB,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_domain_module_is_framework_storage_and_network_independent(self) -> None:
        text=(WEB/"src/domain.ts").read_text(encoding="utf-8").lower()
        for forbidden in ("react", "document.", "window.", "localstorage", "indexeddb", "fetch("):
            self.assertNotIn(forbidden,text)

    def test_all_core_entity_families_validate_together(self) -> None:
        self.run_node(
            r'''
import assert from "node:assert/strict";
import {
  createActivity, createEntityId, createEvidence, createGoal, createInitialStudyState,
  createMaterial, createQuestion, createReview, createStudySession,
  derivePlanInventory, selectActivePlan, selectSubjectsForPlan, selectTopicsForSubject,
  validateStudyState,
} from "./runtime/domain.js";

const now = "2026-09-06T23:32:00.000Z";
const state = createInitialStudyState({ now });
assert.deepEqual(validateStudyState(state), []);
assert.equal(createEntityId("topic", () => "fixed-id"), "topic-fixed-id");
const planId = state.activePlanId;
const subjectId = state.subjects[0].id;
const topicId = state.topics.find((topic) => topic.subjectId === subjectId).id;

const activity = createActivity({ id: "activity-domain-test", planId, subjectId, topicId, title: "Domain study", kind: "study", durationMinutes: 30, createdAt: now, updatedAt: now });
const session = createStudySession({ id: "session-domain-test", planId, activityId: activity.id, subjectId, topicId, startedAt: now, endedAt: now, durationMinutes: 30, createdAt: now, updatedAt: now });
const goal = createGoal({ id: "goal-domain-test", planId, name: "Weekly minutes", metric: "study_minutes", target: 300, createdAt: now, updatedAt: now });
const material = createMaterial({ id: "material-domain-test", planId, subjectId, topicId, title: "Constitution notes", kind: "note", createdAt: now, updatedAt: now });
const review = createReview({ id: "review-domain-test", planId, subjectId, topicId, dueAt: now, sourceType: "topic", sourceId: topicId, createdAt: now, updatedAt: now });
const question = createQuestion({ id: "question-domain-test", planId, subjectId, topicId, prompt: "What is the answer?", choices: ["A", "B"], answer: "A", createdAt: now, updatedAt: now });
const evidence = createEvidence({ id: "evidence-domain-test", planId, subjectId, topicId, activityId: activity.id, sessionId: session.id, questionId: question.id, kind: "practice-result", observedAt: now, value: { correct: true }, provenance: "question", createdAt: now, updatedAt: now });

state.activities.push(activity);
state.sessions.push(session);
state.goals.push(goal);
state.materials.push(material);
state.reviews.push(review);
state.questions.push(question);
state.evidence.push(evidence);
assert.deepEqual(validateStudyState(state), []);
assert.equal(selectActivePlan(state).id, planId);
assert.ok(selectSubjectsForPlan(state).length >= 3);
assert.ok(selectTopicsForSubject(state, subjectId).length >= 1);
const inventory = derivePlanInventory(state);
assert.equal(inventory.subjects, 3);
assert.ok(inventory.activities >= 4);
assert.ok(inventory.plannedMinutes > 0);
'''
        )

    def test_reference_validation_rejects_cross_entity_drift(self) -> None:
        self.run_node(
            r'''
import assert from "node:assert/strict";
import { createInitialStudyState, validateStudyState } from "./runtime/domain.js";
const state = createInitialStudyState({ now: "2026-09-06T23:33:00.000Z" });
state.topics[0] = { ...state.topics[0], subjectId: "subject-missing" };
const errors = validateStudyState(state);
assert.ok(errors.some((error) => error.includes("missing subjectId")));
'''
        )

    def test_supported_study_modes_are_explicit(self) -> None:
        text=(WEB/"src/domain.ts").read_text(encoding="utf-8")
        for mode in ("EXAM_PLAN","PROGRAMMING_COURSE","LANGUAGE_DECK","READING_STUDY","GENERAL_SKILL"):
            self.assertIn(mode,text)

if __name__ == "__main__":
    unittest.main()
