"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.deriveQuestionQuotaPlan = deriveQuestionQuotaPlan;
exports.deriveReadinessExplanation = deriveReadinessExplanation;
exports.deriveWeeklyCoach = deriveWeeklyCoach;
exports.buildContentCleanupPreview = buildContentCleanupPreview;
exports.buildExamRunbook = buildExamRunbook;
exports.buildStudySprintPreview = buildStudySprintPreview;
exports.buildExamReportSnapshot = buildExamReportSnapshot;
exports.renderExamReportHtml = renderExamReportHtml;
const schedule_1 = require("./schedule");
const modes_1 = require("./modes");
const domain_1 = require("./domain");
const day = 86400000;
const clean = (v) => String(v ?? "").trim().toLowerCase().replace(/\s+/g, " ");
const atMs = (at) => at instanceof Date ? at.getTime() : Date.parse(at);
function active(state) { return state.plans.find(p => p.id === state.activePlanId) ?? null; }
function weekStart(ms) { const d = new Date(ms), wd = (d.getUTCDay() + 6) % 7; d.setUTCDate(d.getUTCDate() - wd); d.setUTCHours(0, 0, 0, 0); return d.getTime(); }
function deriveQuestionQuotaPlan(state, at = new Date()) { const plan = active(state); const configured = plan?.configuration.practice.weeklyQuestionQuota ?? null; const start = weekStart(at.getTime()); const completed = state.evidence.filter(e => e.planId === plan?.id && e.kind === "question-attempt" && Date.parse(e.observedAt) >= start).length; if (configured == null)
    return { configured: null, completed, remaining: null, dailyTarget: null, reason: "No weekly question quota is configured." }; const remaining = Math.max(0, configured - completed), daysLeft = Math.max(1, 7 - ((at.getUTCDay() + 6) % 7)); return { configured, completed, remaining, dailyTarget: Math.ceil(remaining / daysLeft), reason: remaining ? `${remaining} questions remain in the configured weekly quota.` : "Weekly question quota is complete." }; }
function deriveReadinessExplanation(state) { const m = (0, domain_1.deriveProgressMetrics)(state); const signals = [{ id: "study", label: "Weekly study", value: m.weeklyStudyMinutes, status: m.weeklyStudyMinutes >= 120 ? "good" : m.weeklyStudyMinutes > 0 ? "risk" : "unknown", reason: m.weeklyStudyMinutes ? `${m.weeklyStudyMinutes} evidenced study minutes this week.` : "No completed study-session evidence this week." }, { id: "accuracy", label: "Question accuracy", value: m.accuracy, status: m.accuracy == null ? "unknown" : m.accuracy >= .7 ? "good" : "risk", reason: m.accuracy == null ? "No graded question evidence yet." : `${Math.round(m.accuracy * 100)}% over ${m.gradedAttempts} graded attempts.` }, { id: "coverage", label: "Topic coverage", value: m.topicCoverage, status: m.topicCoverage == null ? "unknown" : m.topicCoverage >= .6 ? "good" : "risk", reason: m.topicCoverage == null ? "No active topic coverage evidence." : `${m.coveredTopics}/${m.activeTopics} active topics have evidence.` }, { id: "reviews", label: "Review completion", value: m.reviewCompletionRate, status: m.reviewCompletionRate == null ? "unknown" : m.reviewCompletionRate >= .7 ? "good" : "risk", reason: m.reviewCompletionRate == null ? "No completed/due review ratio can be derived." : `${Math.round(m.reviewCompletionRate * 100)}% review completion.` }]; return { value: m.readiness, confidence: m.confidence, reason: m.readinessReason, signals }; }
function deriveWeeklyCoach(state, at = new Date()) { const plan = active(state), readiness = deriveReadinessExplanation(state), quota = deriveQuestionQuotaPlan(state, at); if (!plan)
    return { status: "insufficient", generatedAt: at.toISOString(), items: [] }; const items = []; for (const s of readiness.signals) {
    if (s.status === "good")
        items.push({ id: `win-${s.id}`, kind: "win", title: `${s.label} is stable`, reason: s.reason, action: null, evidenceCount: s.value == null ? 0 : 1 });
    else if (s.status === "risk")
        items.push({ id: `risk-${s.id}`, kind: "risk", title: `${s.label} needs attention`, reason: s.reason, action: s.id === "reviews" ? "Open Practice → Reviews" : s.id === "accuracy" ? "Build a weakness drill" : "Preview the next 7 days", evidenceCount: s.value == null ? 0 : 1 });
} const weakness = (0, modes_1.buildWeaknessDrill)(state, { limit: 5, minQuestions: 2 }); if (weakness.ready)
    items.push({ id: "action-weakness", kind: "action", title: "Run a weakness drill", reason: weakness.reasons[0], action: "Open Practice → Questions → Weakness drill", evidenceCount: weakness.signals.length }); if (quota.remaining != null && quota.remaining > 0)
    items.push({ id: "action-quota", kind: "action", title: `Complete ${quota.dailyTarget} questions today`, reason: quota.reason, action: "Open Practice → Questions", evidenceCount: quota.completed }); if (!items.length)
    items.push({ id: "action-evidence", kind: "action", title: "Record one visible study action", reason: "There is not enough recent evidence for a stronger coaching recommendation.", action: "Complete a Focus session or graded question", evidenceCount: 0 }); return { status: items.some(x => x.evidenceCount > 0) ? "ready" : "insufficient", generatedAt: at.toISOString(), items: items.slice(0, 8) }; }
function buildContentCleanupPreview(state, at = new Date()) {
    const plan = active(state), issues = [];
    const add = (kind, entityId, label, reason, action) => { issues.push({ id: `${kind}:${entityId}`, kind, entityId, label, reason, applicable: action != null, action }); };
    if (plan) {
        const seen = new Map();
        for (const q of state.questions.filter(q => q.planId === plan.id)) {
            const key = clean(q.prompt);
            if (!key)
                continue;
            if (seen.has(key))
                add("duplicate-question", q.id, q.prompt, "Normalized question text duplicates an older question. Review manually before removing either record.", null);
            else
                seen.set(key, q.id);
        }
        for (const t of state.topics.filter(t => t.planId === plan.id && t.status !== "archived")) {
            const linked = state.topics.some(x => x.parentTopicId === t.id && x.status !== "archived") || state.activities.some(x => x.topicId === t.id) || state.questions.some(x => x.topicId === t.id) || state.materials.some(x => x.topicId === t.id) || state.reviews.some(x => x.topicId === t.id) || state.errorEntries.some(x => x.topicId === t.id) || state.evidence.some(x => x.topicId === t.id);
            if (!linked)
                add("orphan-topic", t.id, t.name, "Active leaf topic has no study, question, material, review, error, child-topic or evidence reference.", "archive-topic");
        }
        for (const r of state.reviews.filter(r => r.planId === plan.id && r.status === "due")) {
            const bad = (r.subjectId && !state.subjects.some(s => s.id === r.subjectId && s.planId === plan.id)) || (r.topicId && !state.topics.some(t => t.id === r.topicId && t.planId === plan.id)) || (r.sourceType === "question" && r.sourceId && !state.questions.some(q => q.id === r.sourceId && q.planId === plan.id));
            if (bad)
                add("detached-review", r.id, `Review ${r.id}`, "Pending review points at content that no longer belongs to the active plan.", "dismiss-review");
        }
        for (const a of state.activities.filter(a => a.planId === plan.id && a.status === "planned")) {
            const bad = (a.subjectId && !state.subjects.some(s => s.id === a.subjectId && s.planId === plan.id && !s.archived)) || (a.topicId && !state.topics.some(t => t.id === a.topicId && t.planId === plan.id && t.status !== "archived"));
            if (bad)
                add("invalid-activity", a.id, a.title, "Planned activity references missing or inactive content.", "cancel-activity");
        }
        for (const m of state.materials.filter(m => m.planId === plan.id)) {
            if (m.topicId && !state.topics.some(t => t.id === m.topicId && t.planId === plan.id))
                add("detached-material", m.id, m.title, "Material points at a topic that is no longer present. Kept for manual review.", null);
        }
    }
    const summary = { "duplicate-question": 0, "orphan-topic": 0, "detached-review": 0, "invalid-activity": 0, "detached-material": 0 };
    for (const i of issues)
        summary[i.kind]++;
    return { baseRevision: state.revision, generatedAt: at.toISOString(), issues, summary, applicableCount: issues.filter(i => i.applicable).length };
}
function buildExamRunbook(state, at = new Date()) { const plan = active(state); if (!plan || plan.mode !== "EXAM_PLAN")
    return { status: "not-exam-plan", daysRemaining: null, phase: null, items: [] }; const target = plan.configuration.publicExam?.target; if (target?.dateStatus !== "confirmed" || !target.examDate)
    return { status: "no-date", daysRemaining: null, phase: plan.configuration.publicExam?.studyPhase ?? null, items: [{ id: "confirm-date", priority: "high", title: "Confirm the exam date", reason: "Runbook urgency is disabled until the target date is confirmed." }] }; const exam = Date.parse(`${target.examDate}T12:00:00Z`), daysRemaining = Math.ceil((exam - at.getTime()) / day); const items = []; const due = state.reviews.filter(r => r.planId === plan.id && r.status === "due").length, errors = state.errorEntries.filter(e => e.planId === plan.id && e.status === "open").length; if (daysRemaining <= 7)
    items.push({ id: "freeze", priority: "urgent", title: "Freeze new low-priority content", reason: `${Math.max(0, daysRemaining)} days remain; favor reviews, errors and exam-shaped practice.` }); if (due)
    items.push({ id: "reviews", priority: daysRemaining <= 7 ? "urgent" : "high", title: `Clear ${due} due reviews`, reason: "Persisted review backlog should be reduced before adding optional work." }); if (errors)
    items.push({ id: "errors", priority: daysRemaining <= 7 ? "urgent" : "high", title: `Revisit ${errors} open error entries`, reason: "Open error evidence is a direct remediation target." }); items.push({ id: "mock", priority: daysRemaining <= 15 ? "high" : "normal", title: "Preview an exam-shaped mock", reason: "Use the stored blueprint and question supply check before recording a result." }); items.push({ id: "backup", priority: "normal", title: "Export a full local backup", reason: "Portable v2 preserves state and attachment bytes before the exam window." }); return { status: "ready", daysRemaining, phase: plan.configuration.publicExam?.studyPhase ?? null, items: items.slice(0, 6) }; }
function buildStudySprintPreview(state, at = new Date()) { const schedule = (0, schedule_1.buildAdaptiveSchedulePreview)(state, { at: at.toISOString(), days: 7, maxBlocks: 21 }); return { baseRevision: state.revision, generatedAt: at.toISOString(), schedule, days: schedule.days.map(d => ({ date: d.date, plannedMinutes: d.plannedMinutes, remainingMinutes: d.remainingMinutes, overloaded: d.overloaded })), reasons: ["Seven-day sprint reuses the safe adaptive schedule engine.", ...schedule.reasons] }; }
function buildExamReportSnapshot(state, at = new Date()) { const plan = active(state), readiness = deriveReadinessExplanation(state), coach = deriveWeeklyCoach(state, at), quota = deriveQuestionQuotaPlan(state, at), since = at.getTime() - 7 * day, ev = state.evidence.filter(e => e.planId === plan?.id && Date.parse(e.observedAt) >= since), sessions = state.sessions.filter(s => s.planId === plan?.id && s.status === "completed" && Date.parse(s.endedAt ?? s.updatedAt) >= since), due = state.reviews.filter(r => r.planId === plan?.id && r.status === "due"), mocks = state.evidence.filter(e => e.planId === plan?.id && e.kind === "mock-exam-result").map(e => Number(e.value?.percent)).filter(Number.isFinite), weak = (0, modes_1.buildWeaknessDrill)(state, { limit: 5, minQuestions: 1 }), cleanup = buildContentCleanupPreview(state, at); return { generatedAt: at.toISOString(), planId: plan?.id ?? null, planName: plan?.name ?? null, readiness, coach, quota, weeklyStudyMinutes: sessions.reduce((n, s) => n + (s.durationMinutes ?? 0), 0), sevenDayEvidence: ev.length, weakAreas: weak.signals.slice(0, 5).map(s => state.topics.find(t => t.id === s.topicId)?.name ?? state.subjects.find(x => x.id === s.subjectId)?.name ?? "Unknown"), dueReviews: due.length, overdueReviews: due.filter(r => Date.parse(r.dueAt) < at.getTime()).length, mockCount: mocks.length, mockAverage: mocks.length ? Math.round(mocks.reduce((a, b) => a + b, 0) / mocks.length * 100) / 100 : null, cleanup: cleanup.summary, nextActions: coach.items.filter(i => i.kind !== "win").map(i => i.title).slice(0, 6) }; }
const esc = (v) => String(v ?? "").replace(/[&<>\"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
function renderExamReportHtml(report) { return `<!doctype html><html><head><meta charset="utf-8"><title>StudyFlow exam report</title><style>body{font:16px system-ui;max-width:860px;margin:40px auto;padding:0 20px}table{border-collapse:collapse}td,th{border:1px solid #bbb;padding:6px 10px}small{color:#555}</style></head><body><h1>StudyFlow exam report</h1><p><strong>${esc(report.planName ?? "No active plan")}</strong><br><small>Generated ${esc(report.generatedAt)}</small></p><h2>Readiness</h2><p>${report.readiness.value == null ? "No data" : `${Math.round(report.readiness.value)}%`} · ${esc(report.readiness.reason)}</p><h2>Last 7 days</h2><table><tr><th>Study minutes</th><th>Evidence</th><th>Due reviews</th><th>Mocks</th></tr><tr><td>${report.weeklyStudyMinutes}</td><td>${report.sevenDayEvidence}</td><td>${report.dueReviews}</td><td>${report.mockCount}</td></tr></table><h2>Weak areas</h2><ul>${report.weakAreas.length ? report.weakAreas.map(x => `<li>${esc(x)}</li>`).join("") : "<li>No evidence-backed weak area available.</li>"}</ul><h2>Next safe actions</h2><ul>${report.nextActions.length ? report.nextActions.map(x => `<li>${esc(x)}</li>`).join("") : "<li>Record one visible study action.</li>"}</ul></body></html>`; }
