"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SAMPLE_PLAN_BLUEPRINTS = void 0;
exports.deriveWeeklyCoachRefined = deriveWeeklyCoachRefined;
exports.buildContentCleanupRefined = buildContentCleanupRefined;
exports.buildExamRunbookRefined = buildExamRunbookRefined;
exports.buildStudySprintRefined = buildStudySprintRefined;
exports.validateStudySprintApply = validateStudySprintApply;
exports.buildExamReportRefined = buildExamReportRefined;
exports.renderExamReportHtmlRefined = renderExamReportHtmlRefined;
exports.renderExamReportPdf = renderExamReportPdf;
exports.listSamplePlanBlueprints = listSamplePlanBlueprints;
const schedule_1 = require("./schedule");
const modes_1 = require("./modes");
const promise_cleanup_1 = require("./promise_cleanup");
const parity_intelligence_1 = require("./parity_intelligence");
const DAY_MS = 86400000;
const normalizeText = (v) => String(v !== null && v !== void 0 ? v : "").trim().toLowerCase().replace(/\s+/g, " ");
const pct = (n, d) => d <= 0 ? null : Math.max(0, Math.min(100, Math.round(n * 100 / d)));
const activePlan = (state) => { var _a; return (_a = state.plans.find(p => p.id === state.activePlanId)) !== null && _a !== void 0 ? _a : null; };
const valueObject = (e) => e && typeof e.value === "object" && e.value && !Array.isArray(e.value) ? e.value : {};
function dateOnlyAdd(value, days) { const [y, m, d] = value.split("-").map(Number); const x = new Date(y, m - 1, d, 12); x.setDate(x.getDate() + days); return `${x.getFullYear().toString().padStart(4, "0")}-${String(x.getMonth() + 1).padStart(2, "0")}-${String(x.getDate()).padStart(2, "0")}`; }
function dateOnlyDiff(a, b) { const [ay, am, ad] = a.split("-").map(Number), [by, bm, bd] = b.split("-").map(Number); return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / DAY_MS); }
function mondayFor(value) { const [y, m, d] = value.split("-").map(Number); const x = new Date(y, m - 1, d, 12), off = (x.getDay() + 6) % 7; x.setDate(x.getDate() - off); return (0, parity_intelligence_1.localDateKey)(x); }
function analyticsSnapshot(state, at) {
    const plan = activePlan(state);
    if (!plan)
        return { overdueReviewCount: 0, questionAccuracyPercent: null, scheduleAdherencePercent: null, consistencyScorePercent: null, weakAreas: [] };
    const graded = state.evidence.filter(e => e.planId === plan.id && e.kind === "question-attempt" && valueObject(e).gradable === true);
    const accuracy = graded.length ? pct(graded.filter(e => valueObject(e).correct === true).length, graded.length) : null;
    const today = (0, parity_intelligence_1.localDateKey)(at);
    const observable = state.activities.filter(a => a.planId === plan.id && a.scheduledFor && a.scheduledFor <= today && a.status !== "cancelled");
    const adherence = observable.length ? pct(observable.filter(a => a.status === "completed").length, observable.length) : null;
    const weekStart = Date.parse(at.toISOString()) - 7 * DAY_MS;
    const sessions = state.sessions.filter(s => { var _a, _b; return s.planId === plan.id && s.status === "completed" && Date.parse((_a = s.endedAt) !== null && _a !== void 0 ? _a : s.updatedAt) >= weekStart && Date.parse((_b = s.endedAt) !== null && _b !== void 0 ? _b : s.updatedAt) <= at.getTime(); });
    const consistency = pct(new Set(sessions.map(s => { var _a; return (0, parity_intelligence_1.localDateKey)((_a = s.endedAt) !== null && _a !== void 0 ? _a : s.updatedAt); })).size, 5);
    const weak = (0, modes_1.buildWeaknessDrill)(state, { limit: 5, minQuestions: 1 });
    return { overdueReviewCount: state.reviews.filter(r => r.planId === plan.id && r.status === "due" && Date.parse(r.dueAt) < at.getTime()).length, questionAccuracyPercent: accuracy, scheduleAdherencePercent: adherence, consistencyScorePercent: consistency, weakAreas: weak.signals.map(s => { var _a, _b, _c, _d; return (_d = (_b = (_a = state.topics.find(t => t.id === s.topicId)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : (_c = state.subjects.find(x => x.id === s.subjectId)) === null || _c === void 0 ? void 0 : _c.name) !== null && _d !== void 0 ? _d : "Unknown"; }) };
}
function deriveWeeklyCoachRefined(state, at = new Date()) {
    var _a;
    const plan = activePlan(state);
    if (!plan)
        return { status: "insufficient", generatedAt: at.toISOString(), studiedMinutes: 0, evidenceCount: 0, readinessBand: null, items: [] };
    const readiness = (0, parity_intelligence_1.deriveExamReadinessParity)(state, at), analytics = analyticsSnapshot(state, at), today = (0, parity_intelligence_1.localDateKey)(at), weekStart = mondayFor(today), weekEnd = dateOnlyAdd(weekStart, 6);
    const timeline = (0, parity_intelligence_1.buildUnifiedEvidenceTimeline)(state, { planId: plan.id, limit: 50, at }).filter(e => { const k = (0, parity_intelligence_1.localDateKey)(e.occurredAt); return k >= weekStart && k <= weekEnd; });
    const studiedMinutes = timeline.filter(e => e.source === "session").reduce((n, e) => { var _a; return n + ((_a = e.minutes) !== null && _a !== void 0 ? _a : 0); }, 0);
    const adaptive = (0, schedule_1.buildAdaptiveSchedulePreview)(state, { at: at.toISOString(), days: 7, maxBlocks: 21 });
    const items = [];
    const add = (item) => { if (!items.some(x => x.signalKind === item.signalKind))
        items.push(item); };
    if (readiness.band === "on-track")
        add({ id: "readiness-stable", signalKind: "readiness-stable", tone: "win", kind: "win", title: "Readiness is stable", reason: `Readiness is ${readiness.value}% (${readiness.confidence} confidence).`, action: null, evidenceCount: readiness.evidenceSampleCount, scorePercent: readiness.value, count: null });
    else if (readiness.band)
        add({ id: "readiness-risk", signalKind: "readiness-risk", tone: "action", kind: "risk", title: "Readiness needs attention", reason: `Readiness is ${readiness.value}% (${readiness.band}).`, action: "Open Progress → Performance", evidenceCount: readiness.evidenceSampleCount, scorePercent: readiness.value, count: null });
    if (analytics.overdueReviewCount > 0)
        add({ id: "review-backlog", signalKind: "review-backlog", tone: "action", kind: "action", title: "Recover overdue reviews", reason: `${analytics.overdueReviewCount} overdue review${analytics.overdueReviewCount === 1 ? "" : "s"} need recovery.`, action: "Open Practice → Reviews", evidenceCount: analytics.overdueReviewCount, scorePercent: null, count: analytics.overdueReviewCount });
    if (analytics.questionAccuracyPercent != null && analytics.questionAccuracyPercent >= 1 && analytics.questionAccuracyPercent < 60)
        add({ id: "practice-accuracy", signalKind: "practice-accuracy", tone: "action", kind: "action", title: "Repair practice accuracy", reason: `Graded question accuracy is ${analytics.questionAccuracyPercent}%.`, action: "Open Practice → Questions", evidenceCount: state.evidence.filter(e => e.planId === plan.id && e.kind === "question-attempt").length, scorePercent: analytics.questionAccuracyPercent, count: null });
    if (analytics.scheduleAdherencePercent != null && analytics.scheduleAdherencePercent < 70)
        add({ id: "schedule-drift", signalKind: "schedule-drift", tone: "watch", kind: "risk", title: "Watch schedule drift", reason: `Observable schedule adherence is ${analytics.scheduleAdherencePercent}%.`, action: "Open Plan → Schedule", evidenceCount: state.activities.filter(a => a.planId === plan.id && a.scheduledFor).length, scorePercent: analytics.scheduleAdherencePercent, count: null });
    const mock = readiness.signals.find(s => s.id === "mocks");
    if ((mock === null || mock === void 0 ? void 0 : mock.value) != null && mock.value < 60)
        add({ id: "mock-signal", signalKind: "mock-exam-signal", tone: "watch", kind: "risk", title: "Mock evidence is weak", reason: mock.reason, action: "Open Practice → Tests", evidenceCount: mock.evidenceCount, scorePercent: mock.value, count: null });
    if (((_a = analytics.consistencyScorePercent) !== null && _a !== void 0 ? _a : 0) >= 80)
        add({ id: "consistency-win", signalKind: "consistency-win", tone: "win", kind: "win", title: "Consistency is a win", reason: `Recent consistency is ${analytics.consistencyScorePercent}%.`, action: null, evidenceCount: timeline.filter(e => e.source === "session").length, scorePercent: analytics.consistencyScorePercent, count: null });
    const executable = adaptive.operations.length;
    if (executable > 0)
        add({ id: "reschedule-ready", signalKind: "reschedule-ready", tone: "action", kind: "action", title: "A safe reschedule preview is available", reason: `${executable} adaptive schedule change${executable === 1 ? " is" : "s are"} available for preview.`, action: "Open Plan → Schedule", evidenceCount: executable, scorePercent: null, count: executable });
    if (timeline.length === 0)
        add({ id: "evidence-empty", signalKind: "evidence-empty", tone: "watch", kind: "risk", title: "This week has no visible evidence yet", reason: "Complete one visible study action before relying on coaching signals.", action: "Complete a Focus session or graded question", evidenceCount: 0, scorePercent: null, count: 0 });
    if (items.length === 0)
        add({ id: "readiness-stable-fallback", signalKind: "readiness-stable", tone: "win", kind: "win", title: "Keep the current plan", reason: "No stronger evidence-backed intervention is required this week.", action: null, evidenceCount: timeline.length, scorePercent: readiness.value, count: null });
    const priority = { "readiness-risk": 0, "readiness-stable": 0, "reschedule-ready": 1, "review-backlog": 2, "practice-accuracy": 3, "schedule-drift": 4, "mock-exam-signal": 5, "consistency-win": 6, "evidence-empty": 7 };
    items.sort((a, b) => { var _a, _b; return ((_a = priority[a.signalKind]) !== null && _a !== void 0 ? _a : 99) - ((_b = priority[b.signalKind]) !== null && _b !== void 0 ? _b : 99) || a.id.localeCompare(b.id); });
    return { status: timeline.length || readiness.value != null ? "ready" : "insufficient", generatedAt: at.toISOString(), studiedMinutes, evidenceCount: timeline.length, readinessBand: readiness.band, items: items.slice(0, 5) };
}
function duplicateQuestionKey(q) { var _a, _b; return [normalizeText(q.prompt), q.choices.map(normalizeText).join("|"), normalizeText(q.answer), normalizeText(q.explanation), normalizeText(q.source), normalizeText(q.examBoard), (_a = q.year) !== null && _a !== void 0 ? _a : "", (_b = q.difficulty) !== null && _b !== void 0 ? _b : ""].join("\u001f"); }
function buildContentCleanupRefined(state, at = new Date()) {
    var _a, _b, _c;
    const plan = activePlan(state);
    const issues = [];
    if (!plan)
        return { baseRevision: state.revision, generatedAt: at.toISOString(), healthScorePercent: 100, issues: [], summary: { "duplicate-question": 0, "orphan-topic": 0, "stale-resource": 0, "stale-plan": 0, "inconsistent-tag": 0, "stale-review": 0, "broken-schedule-alignment": 0 }, applicableCount: 0, previewActions: [] };
    const push = (kind, severity, ids, label, reason, canonicalValue = null) => { if (ids.length)
        issues.push({ id: `${kind}:${ids.join(",")}`, kind, severity, count: ids.length, affectedIds: [...new Set(ids)], canonicalValue, label, reason, applicable: false, action: null }); };
    const groups = new Map();
    for (const q of state.questions.filter(q => q.planId === plan.id)) {
        const k = duplicateQuestionKey(q);
        const arr = (_a = groups.get(k)) !== null && _a !== void 0 ? _a : [];
        arr.push(q);
        groups.set(k, arr);
    }
    for (const group of groups.values())
        if (group.length > 1) {
            const sorted = [...group].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
            push("duplicate-question", "review", sorted.slice(1).map(q => q.id), "Duplicate questions", `Normalized question content duplicates canonical question ${sorted[0].id}.`, sorted[0].id);
        }
    const childIds = new Set(state.topics.filter(t => t.planId === plan.id && t.parentTopicId).map(t => t.parentTopicId));
    const refTopics = new Set();
    for (const c of [state.activities, state.sessions, state.materials, state.flashcards, state.reviews, state.questions, state.evidence, state.errorEntries])
        for (const x of c)
            if (x.planId === plan.id && x.topicId)
                refTopics.add(x.topicId);
    push("orphan-topic", "info", state.topics.filter(t => t.planId === plan.id && t.status !== "archived" && !childIds.has(t.id) && !refTopics.has(t.id)).map(t => t.id), "Orphan topics", "Active leaf topics have no linked study content or evidence.");
    const cutoff = at.getTime() - 90 * DAY_MS;
    const referencedMaterials = new Set();
    for (const s of state.sessions.filter(s => s.planId === plan.id)) {
        const record = ((_b = s.note) !== null && _b !== void 0 ? _b : "").match(/material:([^\s]+)/);
        if (record)
            referencedMaterials.add(record[1]);
    }
    push("stale-resource", "info", state.materials.filter(m => m.planId === plan.id && m.archived && !referencedMaterials.has(m.id) && Date.parse(m.updatedAt) <= cutoff).map(m => m.id), "Stale resources", "Archived detached resources have not changed for at least 90 days.");
    const latest = Math.max(Date.parse(plan.updatedAt), ...state.questions.filter(x => x.planId === plan.id).map(x => Date.parse(x.updatedAt)), ...state.materials.filter(x => x.planId === plan.id).map(x => Date.parse(x.updatedAt)), ...state.errorEntries.filter(x => x.planId === plan.id).map(x => Date.parse(x.updatedAt)), ...state.sessions.filter(x => x.planId === plan.id).map(x => { var _a; return Date.parse((_a = x.endedAt) !== null && _a !== void 0 ? _a : x.startedAt); }), ...state.reviews.filter(x => x.planId === plan.id && x.completedAt).map(x => Date.parse(x.completedAt)));
    if (plan.status === "active" && Number.isFinite(latest) && at.getTime() - latest >= 60 * DAY_MS)
        push("stale-plan", "review", [plan.id], "Stale active plan", "The active plan has no recent content or completion evidence for at least 60 days.");
    const tagPairs = state.materials.filter(m => m.planId === plan.id).flatMap(m => m.tags.map(t => [m.id, t]));
    const byTag = new Map();
    for (const pair of tagPairs) {
        const k = normalizeText(pair[1]);
        if (!k)
            continue;
        const arr = (_c = byTag.get(k)) !== null && _c !== void 0 ? _c : [];
        arr.push(pair);
        byTag.set(k, arr);
    }
    for (const tagged of byTag.values()) {
        const variants = [...new Set(tagged.map(([, t]) => t.trim()).filter(Boolean))];
        if (variants.length > 1)
            push("inconsistent-tag", "info", tagged.map(([id]) => id), "Inconsistent tags", `Tag variants ${variants.join(", ")} normalize to the same value.`, variants[0].toLowerCase());
    }
    const activeSubjects = new Set(state.subjects.filter(s => s.planId === plan.id && !s.archived).map(s => s.id));
    const activeTopics = new Set(state.topics.filter(t => t.planId === plan.id && t.status !== "archived").map(t => t.id));
    const qids = new Set(state.questions.filter(q => q.planId === plan.id).map(q => q.id));
    const fids = new Set(state.flashcards.filter(f => f.planId === plan.id && !f.suspended).map(f => f.id));
    const eids = new Set(state.errorEntries.filter(e => e.planId === plan.id).map(e => e.id));
    const staleReviews = state.reviews.filter(r => r.planId === plan.id && r.status === "due" && ((r.subjectId && !activeSubjects.has(r.subjectId)) || (r.topicId && !activeTopics.has(r.topicId)) || (r.sourceType === "question" && r.sourceId && !qids.has(r.sourceId)) || (r.sourceType === "flashcard" && r.sourceId && !fids.has(r.sourceId)) || (r.sourceType === "error" && r.sourceId && !eids.has(r.sourceId)) || (!r.subjectId && !r.topicId && !r.sourceId))).map(r => r.id);
    push("stale-review", "action", staleReviews, "Stale review links", "Pending reviews point at missing or inactive content.");
    const broken = state.activities.filter(a => a.planId === plan.id && a.status === "planned" && ((a.subjectId && !activeSubjects.has(a.subjectId)) || (a.topicId && !activeTopics.has(a.topicId)))).map(a => a.id);
    push("broken-schedule-alignment", "action", broken, "Broken schedule alignment", "Planned activities point at missing or inactive content.");
    const severityWeight = { info: 2, review: 5, action: 10 };
    const healthScorePercent = Math.max(0, 100 - issues.reduce((n, i) => n + i.count * severityWeight[i.severity], 0));
    const kinds = ["duplicate-question", "orphan-topic", "stale-resource", "stale-plan", "inconsistent-tag", "stale-review", "broken-schedule-alignment"];
    const summary = Object.fromEntries(kinds.map(k => [k, issues.filter(i => i.kind === k).reduce((n, i) => n + i.count, 0)]));
    const actionKind = { "duplicate-question": "review-duplicate", "orphan-topic": "open-content", "stale-resource": "review-resource", "stale-plan": "review-plan", "inconsistent-tag": "normalize-tag", "stale-review": "review-queue-link", "broken-schedule-alignment": "review-schedule-link" };
    return { baseRevision: state.revision, generatedAt: at.toISOString(), healthScorePercent, issues, summary, applicableCount: 0, previewActions: issues.map(i => ({ kind: actionKind[i.kind], issueKind: i.kind, affectedCount: i.count, destructive: false, requiresConfirmation: true })) };
}
function missedActivityCount(state, planId, at) { const today = (0, parity_intelligence_1.localDateKey)(at); return state.activities.filter(a => a.planId === planId && a.status === "planned" && a.scheduledFor != null && a.scheduledFor < today).length; }
function examDays(state, at) { const p = activePlan(state); return (p === null || p === void 0 ? void 0 : p.mode) === "EXAM_PLAN" ? (0, promise_cleanup_1.daysUntilOfficialExam)(p, at) : null; }
function buildExamRunbookRefined(state, at = new Date()) {
    var _a, _b, _c, _d;
    const plan = activePlan(state);
    if (!plan || plan.mode !== "EXAM_PLAN")
        return { status: "not-exam-plan", daysRemaining: null, phase: null, readinessBand: null, items: [] };
    const days = examDays(state, at), readiness = (0, parity_intelligence_1.deriveExamReadinessParity)(state, at);
    if (days == null)
        return { status: "no-date", daysRemaining: null, phase: (_b = (_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.studyPhase) !== null && _b !== void 0 ? _b : null, readinessBand: readiness.band, items: [] };
    const analytics = analyticsSnapshot(state, at), mocks = state.evidence.filter(e => e.planId === plan.id && e.kind === "mock-exam-result"), items = [];
    const add = (id, window, action, priority, title, reason, count = null, focusLabel = null, navigation = null) => items.push({ id, window, action, priority, title, reason, count, focusLabel, navigation });
    if (analytics.overdueReviewCount > 0)
        add("reviews-overdue", "now", "recover-reviews", "urgent", `Recover ${analytics.overdueReviewCount} overdue reviews`, "Overdue review evidence takes priority over optional new work.", analytics.overdueReviewCount, null, "practice:reviews");
    analytics.weakAreas.slice(0, 2).forEach((label, i) => add(`weak-area-${i}`, "this-week", "attack-weak-area", "high", `Attack weak area: ${label}`, "Recent error/practice evidence marks this as a bounded remediation target.", null, label, "practice:questions"));
    if (analytics.questionAccuracyPercent != null && analytics.questionAccuracyPercent >= 1 && analytics.questionAccuracyPercent < 70)
        add("practice-recall", "this-week", "practice-recall", "high", "Practice active recall", "Question accuracy is below the 70% healthy threshold.", null, null, "practice:questions");
    if (days >= 2 && mocks.length === 0)
        add("mock-rehearsal", "this-week", "mock-rehearsal", "normal", "Run a mock rehearsal", "No submitted mock evidence exists and there is still enough lead time for one rehearsal.", null, null, "practice:tests");
    const missed = missedActivityCount(state, plan.id, at);
    if (missed > 0 || (analytics.scheduleAdherencePercent != null && analytics.scheduleAdherencePercent < 70))
        add("protect-schedule", "now", "protect-schedule", "urgent", "Protect the schedule", missed ? `${missed} scheduled activit${missed === 1 ? "y is" : "ies are"} overdue.` : `Schedule adherence is ${analytics.scheduleAdherencePercent}%.`, missed || null, null, "plan:schedule");
    if (days <= 2)
        add("final-review", "exam-eve", "final-review", "urgent", "Final review only", "The exam is within the final two-day window; prioritize review over new low-priority content.", null, null, "practice:reviews");
    if (items.length === 0)
        add("maintain-momentum", "this-week", "maintain-momentum", "normal", "Maintain momentum", "No stronger evidence-backed intervention is required.", null, null, "today");
    const order = { "recover-reviews": 0, "final-review": 1, "protect-schedule": 2, "attack-weak-area": 3, "practice-recall": 4, "mock-rehearsal": 5, "maintain-momentum": 6 };
    items.sort((a, b) => { var _a, _b; return ((_a = order[a.action]) !== null && _a !== void 0 ? _a : 99) - ((_b = order[b.action]) !== null && _b !== void 0 ? _b : 99) || a.id.localeCompare(b.id); });
    return { status: "ready", daysRemaining: days, phase: (_d = (_c = plan.configuration.publicExam) === null || _c === void 0 ? void 0 : _c.studyPhase) !== null && _d !== void 0 ? _d : null, readinessBand: readiness.band, items: items.slice(0, 6) };
}
function sprintVersion(activities) { return activities.map(a => [a.id, a.status, a.locked, a.scheduledFor, a.scheduledStartMinute, a.durationMinutes, a.activityType].join("|")).sort().join("\n"); }
function sprintFocus(state, at) { const a = analyticsSnapshot(state, at); if (a.overdueReviewCount > 0)
    return "review-recovery"; if (a.scheduleAdherencePercent != null && a.scheduleAdherencePercent < 60)
    return "schedule-recovery"; if (a.questionAccuracyPercent != null && a.questionAccuracyPercent >= 1 && a.questionAccuracyPercent < 60)
    return "practice-accuracy"; if (a.weakAreas.length)
    return "weakness"; return "balanced"; }
function sprintTargetType(current, focus, index, days) { if (current === "THEORY" && days != null) {
    if (days <= 1)
        return "REVIEW";
    if (days <= 2)
        return "QUESTIONS";
    if (days <= 7)
        return index % 2 === 0 ? "REVIEW" : "QUESTIONS";
    if (days <= 15 && index % 3 === 0)
        return "REVIEW";
} if (focus === "review-recovery")
    return index % 2 === 0 ? "REVIEW" : current; if (focus === "practice-accuracy")
    return index % 2 === 0 ? "THEORY" : "QUESTIONS"; if (focus === "weakness")
    return index % 2 === 0 ? "QUESTIONS" : "REVIEW"; return current; }
function buildStudySprintRefined(state, at = new Date()) {
    const plan = activePlan(state);
    const today = (0, parity_intelligence_1.localDateKey)(at), end = dateOnlyAdd(today, 6);
    const candidates = state.activities.filter(a => a.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && a.status === "planned" && !a.locked && a.scheduledFor != null && a.scheduledFor >= today && a.scheduledFor <= end).sort((a, b) => { var _a, _b; return String(a.scheduledFor).localeCompare(String(b.scheduledFor)) || ((_a = a.scheduledStartMinute) !== null && _a !== void 0 ? _a : 0) - ((_b = b.scheduledStartMinute) !== null && _b !== void 0 ? _b : 0) || a.id.localeCompare(b.id); }).slice(0, 7);
    const focus = sprintFocus(state, at), days = examDays(state, at);
    const mutations = [];
    for (const [i, a] of candidates.entries()) {
        const type = sprintTargetType(a.activityType, focus, i, days);
        const duration = focus === "schedule-recovery" ? Math.min(a.durationMinutes, 45) : focus === "balanced" ? a.durationMinutes : Math.min(a.durationMinutes, 60);
        if (type !== a.activityType || duration !== a.durationMinutes)
            mutations.push({ activityId: a.id, currentType: a.activityType, proposedType: type, currentDurationMinutes: a.durationMinutes, proposedDurationMinutes: duration, scheduledFor: a.scheduledFor, scheduledStartMinute: a.scheduledStartMinute });
    }
    const reasons = [`Focus: ${focus.replace(/-/g, " ")}.`, `Only ${candidates.length} existing unlocked planned activities in the next seven days are candidates.`, `IDs and start times are immutable; duration may only stay the same or shrink.`];
    return { baseRevision: state.revision, generatedAt: at.toISOString(), horizonDays: 7, focus, candidateIds: candidates.map(a => a.id), candidateCount: candidates.length, totalMinutes: candidates.reduce((n, a) => n + a.durationMinutes, 0), versionToken: sprintVersion(candidates), mutations, executable: mutations.length > 0, reasons };
}
function validateStudySprintApply(state, preview, at = new Date()) {
    if (preview.baseRevision !== state.revision)
        throw new TypeError("Study sprint preview is stale; refresh before applying");
    const generated = Date.parse(preview.generatedAt);
    if (!Number.isFinite(generated) || generated > at.getTime())
        throw new TypeError("Study sprint preview timestamp is invalid");
    if (at.getTime() - generated > 120000)
        throw new TypeError("Study sprint preview is stale; refresh before applying");
    if (preview.horizonDays !== 7 || preview.candidateCount < 0 || preview.candidateCount > 7)
        throw new TypeError("Study sprint preview boundary is invalid");
    const today = (0, parity_intelligence_1.localDateKey)(new Date(generated)), end = dateOnlyAdd(today, 7);
    const current = state.activities.filter(a => a.planId === state.activePlanId && a.status === "planned" && !a.locked && a.scheduledFor != null && a.scheduledFor >= today && a.scheduledFor <= end).sort((a, b) => { var _a, _b; return String(a.scheduledFor).localeCompare(String(b.scheduledFor)) || ((_a = a.scheduledStartMinute) !== null && _a !== void 0 ? _a : 0) - ((_b = b.scheduledStartMinute) !== null && _b !== void 0 ? _b : 0) || a.id.localeCompare(b.id); }).slice(0, 7);
    if (current.length !== preview.candidateCount || current.reduce((n, a) => n + a.durationMinutes, 0) !== preview.totalMinutes || sprintVersion(current) !== preview.versionToken)
        throw new TypeError("Study sprint candidate set changed; refresh before applying");
    const allowed = new Map(current.map(a => [a.id, a]));
    const seen = new Set();
    for (const m of preview.mutations) {
        const a = allowed.get(m.activityId);
        if (!a || !seen.add(m.activityId) || a.locked || a.status !== "planned")
            throw new TypeError("Study sprint mutation target is outside the approved candidate set");
        if (a.scheduledFor !== m.scheduledFor || a.scheduledStartMinute !== m.scheduledStartMinute)
            throw new TypeError("Study sprint cannot move an activity");
        if (a.durationMinutes !== m.currentDurationMinutes || a.activityType !== m.currentType)
            throw new TypeError("Study sprint source activity changed");
        if (m.proposedDurationMinutes < 1 || m.proposedDurationMinutes > m.currentDurationMinutes)
            throw new TypeError("Study sprint can only preserve or shorten duration");
        if (m.proposedDurationMinutes === m.currentDurationMinutes && m.proposedType === m.currentType)
            throw new TypeError("Study sprint executable mutations cannot be no-ops");
    }
    return current;
}
function buildExamReportRefined(state, at = new Date()) {
    var _a, _b, _c;
    const plan = activePlan(state), readiness = (0, parity_intelligence_1.deriveExamReadinessParity)(state, at), coach = deriveWeeklyCoachRefined(state, at), cleanup = buildContentCleanupRefined(state, at), analytics = analyticsSnapshot(state, at), since = at.getTime() - 7 * DAY_MS, weekEvidence = state.evidence.filter(e => e.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && Date.parse(e.observedAt) >= since && Date.parse(e.observedAt) <= at.getTime()), sessions = state.sessions.filter(s => { var _a; return s.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && s.status === "completed" && Date.parse((_a = s.endedAt) !== null && _a !== void 0 ? _a : s.updatedAt) >= since; }), due = state.reviews.filter(r => r.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && r.status === "due"), mocks = state.evidence.filter(e => e.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && e.kind === "mock-exam-result" && Number.isFinite(Number(valueObject(e).percent))), mockScores = mocks.map(e => Number(valueObject(e).percent)), tests = state.evidence.filter(e => e.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && e.kind === "practice-test-submitted"), testAnswered = tests.reduce((n, e) => n + (Number(valueObject(e).questionCount) || 0), 0), testCorrect = tests.reduce((n, e) => n + (Number(valueObject(e).correctCount) || 0), 0), recentQuestions = state.questions.filter(q => q.planId === (plan === null || plan === void 0 ? void 0 : plan.id) && Date.parse(q.createdAt) >= at.getTime() - 14 * DAY_MS), adaptive = (0, schedule_1.buildAdaptiveSchedulePreview)(state, { at: at.toISOString(), days: 7, maxBlocks: 21 }), weak = analytics.weakAreas;
    const nextActions = [];
    if (due.some(r => Date.parse(r.dueAt) < at.getTime()))
        nextActions.push("review-backlog");
    if (missedActivityCount(state, (_a = plan === null || plan === void 0 ? void 0 : plan.id) !== null && _a !== void 0 ? _a : "", at) > 0 || (analytics.scheduleAdherencePercent != null && analytics.scheduleAdherencePercent < 70))
        nextActions.push("recover-schedule");
    if (weak.length)
        nextActions.push("practice-weak-areas");
    if (mocks.length === 0)
        nextActions.push("run-mock-exam");
    if (cleanup.issues.length)
        nextActions.push("review-content-cleanup");
    if (adaptive.operations.length)
        nextActions.push("apply-safe-reschedule");
    if (!nextActions.length)
        nextActions.push("keep-plan");
    return { generatedAt: at.toISOString(), planId: (_b = plan === null || plan === void 0 ? void 0 : plan.id) !== null && _b !== void 0 ? _b : null, planName: (_c = plan === null || plan === void 0 ? void 0 : plan.name) !== null && _c !== void 0 ? _c : null, readiness, coach, weeklyStudyMinutes: sessions.reduce((n, s) => { var _a; return n + ((_a = s.durationMinutes) !== null && _a !== void 0 ? _a : 0); }, 0), sevenDayEvidence: weekEvidence.length, weakAreas: weak.slice(0, 5), missedActivityCount: plan ? missedActivityCount(state, plan.id, at) : 0, dueReviews: due.length, overdueReviews: due.filter(r => Date.parse(r.dueAt) < at.getTime()).length, practiceTestCount: tests.length, completedPracticeTestCount: tests.length, practiceTestAccuracyPercent: testAnswered ? pct(testCorrect, testAnswered) : null, mockCount: mocks.length, completedMockCount: mocks.length, bestMockScorePercent: mockScores.length ? Math.max(...mockScores) : null, mockAverage: mockScores.length ? Math.round(mockScores.reduce((a, b) => a + b, 0) / mockScores.length * 100) / 100 : null, recentQuestionCount: recentQuestions.length, executableRescheduleChangeCount: adaptive.operations.length, cleanupIssueCount: cleanup.issues.reduce((n, i) => n + i.count, 0), cleanupActionCount: cleanup.previewActions.reduce((n, a) => n + a.affectedCount, 0), cleanupHealthScorePercent: cleanup.healthScorePercent, cleanup: cleanup.summary, nextActions, metricProvenance: [{ metric: "readiness", source: "readiness" }, { metric: "study_time", source: "analytics" }, { metric: "question_accuracy", source: "analytics" }, { metric: "evidence_count", source: "raw-evidence" }, { metric: "schedule", source: "schedule" }, { metric: "review_health", source: "review-state" }, { metric: "content_health", source: "content-health" }], freeformNotesIncluded: false };
}
const escHtml = (v) => String(v !== null && v !== void 0 ? v : "").replace(/[&<>\"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
function renderExamReportHtmlRefined(report) { var _a, _b, _c, _d; return `<!doctype html><html><head><meta charset="utf-8"><title>StudyFlow exam report</title><style>body{font:16px system-ui;max-width:900px;margin:40px auto;padding:0 20px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #bbb;padding:6px 10px;text-align:left}small{color:#555}</style></head><body><h1>StudyFlow exam report</h1><p><strong>${escHtml((_a = report.planName) !== null && _a !== void 0 ? _a : "No active plan")}</strong><br><small>Generated ${escHtml(report.generatedAt)}</small></p><h2>Readiness</h2><p>${report.readiness.value == null ? "No data" : `${report.readiness.value}% · ${escHtml(report.readiness.band)}`} · ${escHtml(report.readiness.reason)}</p><h2>Last 7 days</h2><table><tr><th>Study minutes</th><th>Evidence</th><th>Missed</th><th>Due reviews</th><th>Mocks</th></tr><tr><td>${report.weeklyStudyMinutes}</td><td>${report.sevenDayEvidence}</td><td>${report.missedActivityCount}</td><td>${report.dueReviews}</td><td>${report.mockCount}</td></tr></table><h2>Practice & mocks</h2><p>Practice tests: ${report.completedPracticeTestCount}/${report.practiceTestCount} · accuracy ${(_b = report.practiceTestAccuracyPercent) !== null && _b !== void 0 ? _b : "No data"}% · best mock ${(_c = report.bestMockScorePercent) !== null && _c !== void 0 ? _c : "No data"}% · average mock ${(_d = report.mockAverage) !== null && _d !== void 0 ? _d : "No data"}%</p><h2>Content health</h2><p>${report.cleanupHealthScorePercent}/100 · ${report.cleanupIssueCount} issue(s) · ${report.cleanupActionCount} action-severity finding(s)</p><h2>Weak areas</h2><ul>${report.weakAreas.length ? report.weakAreas.map(x => `<li>${escHtml(x)}</li>`).join("") : "<li>No evidence-backed weak area available.</li>"}</ul><h2>Next safe actions</h2><ul>${report.nextActions.map(x => `<li>${escHtml(x.replace(/-/g, " "))}</li>`).join("")}</ul><h2>Metric provenance</h2><ul>${report.metricProvenance.map(x => `<li>${escHtml(x.metric)} — ${escHtml(x.source)}</li>`).join("")}</ul></body></html>`; }
function pdfEscape(s) { return s.replace(/\\/g, "\\\\").replace(/\(/g, "\\(").replace(/\)/g, "\\)").replace(/[^\x20-\x7E]/g, "?"); }
function renderExamReportPdf(report) { var _a, _b, _c, _d, _e; const lines = ["StudyFlow exam report", (_a = report.planName) !== null && _a !== void 0 ? _a : "No active plan", `Generated ${report.generatedAt}`, `Readiness: ${report.readiness.value == null ? "No data" : `${report.readiness.value}% ${(_b = report.readiness.band) !== null && _b !== void 0 ? _b : ""}`}`, `Study minutes (7d): ${report.weeklyStudyMinutes}`, `Evidence (7d): ${report.sevenDayEvidence}`, `Missed activities: ${report.missedActivityCount}`, `Due/overdue reviews: ${report.dueReviews}/${report.overdueReviews}`, `Practice test accuracy: ${(_c = report.practiceTestAccuracyPercent) !== null && _c !== void 0 ? _c : "No data"}`, `Mocks: ${report.mockCount}; best ${(_d = report.bestMockScorePercent) !== null && _d !== void 0 ? _d : "No data"}; avg ${(_e = report.mockAverage) !== null && _e !== void 0 ? _e : "No data"}`, `Content health: ${report.cleanupHealthScorePercent}/100`, "Weak areas:", ...report.weakAreas.slice(0, 5).map(x => `- ${x}`), "Next safe actions:", ...report.nextActions.slice(0, 6).map(x => `- ${x.replace(/-/g, " ")}`), "Metric provenance:", ...report.metricProvenance.map(x => `- ${x.metric}: ${x.source}`)]; const content = ["BT", "/F1 11 Tf", "50 790 Td", "14 TL", ...lines.flatMap((line, i) => [`${i ? "T* " : ""}(${pdfEscape(line).slice(0, 110)}) Tj`]), "ET"].join("\n"); const objs = ["<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>", "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>", `<< /Length ${content.length} >>\nstream\n${content}\nendstream`, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]; let pdf = "%PDF-1.4\n"; const offsets = [0]; for (let i = 0; i < objs.length; i++) {
    offsets.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${objs[i]}\nendobj\n`;
} const xref = pdf.length; pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`; for (let i = 1; i < offsets.length; i++)
    pdf += `${String(offsets[i]).padStart(10, "0")} 00000 n \n`; pdf += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`; return new TextEncoder().encode(pdf); }
const subject = (key, en, pt, weight, topics) => ({ key, name: { en, "pt-BR": pt }, weight, topics: topics.map(([k, e, p]) => ({ key: k, name: { en: e, "pt-BR": p } })) });
const rule = (weekday, startMinute, durationMinutes, subjectKey) => ({ weekday, startMinute, durationMinutes, subjectKey });
exports.SAMPLE_PLAN_BLUEPRINTS = {
    "public-exam-12-week": { id: "public-exam-12-week", label: { en: "12-week public exam", "pt-BR": "Concurso em 12 semanas" }, mode: "EXAM_PLAN", weeklyGoalHours: 20, preferredSessionMinutes: 50, adaptivePlanningEnabled: true, reviewNotificationsEnabled: true, strictExamModeEnabled: true, subjects: [subject("core", "Core theory", "Teoria central", 4, [["foundations", "Foundations", "Fundamentos"], ["high-incidence", "High-incidence topics", "Tópicos de alta incidência"], ["details", "Legal or conceptual details", "Detalhes legais ou conceituais"]]), subject("questions", "Questions and cases", "Questões e casos", 3, [["bank", "Question bank", "Banco de questões"], ["wrong", "Wrong-answer log", "Caderno de erros"]]), subject("reviews", "Review and flashcards", "Revisão e flashcards", 2, [["spaced", "Spaced review", "Revisão espaçada"], ["flashcards", "Flashcards", "Flashcards"]])], scheduleRules: [rule(1, 1140, 90, "core"), rule(3, 1140, 90, "questions"), rule(5, 1140, 75, "reviews"), rule(6, 540, 120, "questions")] },
    "university-semester": { id: "university-semester", label: { en: "University semester", "pt-BR": "Semestre universitário" }, mode: "GENERAL_SKILL", weeklyGoalHours: 12, preferredSessionMinutes: 45, adaptivePlanningEnabled: true, reviewNotificationsEnabled: true, strictExamModeEnabled: false, subjects: [subject("foundations", "Course foundations", "Fundamentos da disciplina", 3, [["syllabus", "Syllabus mapping", "Mapeamento da ementa"], ["notes", "Lecture notes", "Notas de aula"]]), subject("assignments", "Weekly assignments", "Trabalhos semanais", 4, [["exercise-lists", "Exercise lists", "Listas de exercícios"], ["seminar", "Seminar preparation", "Preparação de seminário"]]), subject("projects", "Lab and projects", "Laboratório e projetos", 3, [["lab-notes", "Lab notes", "Notas de laboratório"], ["milestones", "Project milestones", "Marcos do projeto"]])], scheduleRules: [rule(2, 1080, 75, "foundations"), rule(4, 1080, 90, "assignments"), rule(6, 600, 120, "projects")] },
    "certification-bootcamp": { id: "certification-bootcamp", label: { en: "Certification bootcamp", "pt-BR": "Preparação para certificação" }, mode: "EXAM_PLAN", weeklyGoalHours: 10, preferredSessionMinutes: 40, adaptivePlanningEnabled: true, reviewNotificationsEnabled: true, strictExamModeEnabled: true, subjects: [subject("objectives", "Exam objectives", "Objetivos da prova", 5, [["official", "Official objectives", "Objetivos oficiais"], ["high-incidence", "High-incidence topics", "Tópicos de alta incidência"]]), subject("rehearsal", "Practical rehearsal", "Treino prático", 5, [["practice-test", "Practice test", "Simulado"], ["checklist", "Practical checklist", "Checklist prático"]])], scheduleRules: [rule(1, 1200, 60, "objectives"), rule(3, 1200, 60, "rehearsal"), rule(6, 660, 120, "rehearsal")] },
    "free-study-habit": { id: "free-study-habit", label: { en: "Free-study habit", "pt-BR": "Hábito de estudo livre" }, mode: "GENERAL_SKILL", weeklyGoalHours: 6, preferredSessionMinutes: 30, adaptivePlanningEnabled: false, reviewNotificationsEnabled: true, strictExamModeEnabled: false, subjects: [subject("reading", "Free reading", "Leitura livre", 2, [["backlog", "Reading backlog", "Fila de leitura"], ["summaries", "Summary notes", "Notas de resumo"]]), subject("practice", "Creative practice", "Prática criativa", 3, [["routine", "Practice routine", "Rotina de prática"], ["reflection", "Reflection log", "Registro de reflexão"]])], scheduleRules: [rule(2, 1200, 45, "reading"), rule(5, 1200, 45, "practice"), rule(7, 600, 60, null)] },
};
function listSamplePlanBlueprints() { return Object.values(exports.SAMPLE_PLAN_BLUEPRINTS); }
