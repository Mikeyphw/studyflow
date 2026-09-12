import assert from "node:assert/strict";
import { buildStudyFlowCalendarExport, calendarAttentionSummary } from "../runtime/calendar_attention.js";

const now = new Date(2026, 8, 12, 8, 0, 0).getTime();
const activePlan = {
  id: "plan-1",
  name: "Banco do Brasil",
  mode: "EXAM_PLAN",
  status: "active",
  examDate: "2026-10-20",
  configuration: { publicExam: { target: { examDate: "2026-10-20" } } },
};
const state = {
  activePlanId: "plan-1",
  preferences: { locale: "en" },
  plans: [activePlan],
  subjects: [{ id: "sub-1", planId: "plan-1", name: "Portuguese" }],
  topics: [],
  activities: [
    { id: "a1", planId: "plan-1", subjectId: "sub-1", topicId: null, title: "Grammar block", kind: "study", activityType: "THEORY", durationMinutes: 50, status: "planned", origin: "manual", queue: "today", scheduledFor: "2026-09-12", scheduledStartMinute: 9 * 60, locked: false, scheduleProvenance: null },
    { id: "a2", planId: "plan-1", subjectId: "sub-1", topicId: null, title: "Done block", kind: "study", activityType: "THEORY", durationMinutes: 30, status: "completed", origin: "manual", queue: "today", scheduledFor: "2026-09-12", scheduledStartMinute: 10 * 60, locked: false, scheduleProvenance: null },
  ],
  reviews: [{ id: "r1", planId: "plan-1", subjectId: "sub-1", topicId: null, dueAt: "2026-09-12T08:00:00", status: "due" }],
};

const feed = buildStudyFlowCalendarExport(state, { nowEpochMillis: now, reminderLeadMinutes: 15 });
assert.equal(feed.diagnostics.inputItemCount, 4);
assert.equal(feed.diagnostics.completedSuppressedCount, 1);
assert.equal(feed.events.length, 3);
assert.ok(feed.icsText.includes("BEGIN:VCALENDAR"));
assert.ok(feed.icsText.includes("X-STUDYFLOW-ATTENTION-INTEGRATION:ATT09"));
assert.ok(feed.icsText.includes("TRIGGER:-PT15M"));
assert.ok(feed.events.every(event => event.attentionOccurrence));
assert.ok(calendarAttentionSummary(feed).includes("attention reminders"));

const privateFeed = buildStudyFlowCalendarExport(state, { nowEpochMillis: now, privacyMode: "busy_only" });
assert.ok(privateFeed.icsText.includes("SUMMARY:Busy"));
assert.ok(!privateFeed.icsText.includes("Grammar block"));

const noReviews = buildStudyFlowCalendarExport(state, { nowEpochMillis: now, includeReviews: false });
assert.equal(noReviews.events.some(event => event.item.kind === "review"), false);
assert.equal(noReviews.diagnostics.reviewSuppressedCount, 1);

console.log("ATT09 calendar runner OK");
