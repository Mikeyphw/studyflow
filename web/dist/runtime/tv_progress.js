"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tvProgressRecommendedSurface = tvProgressRecommendedSurface;
exports.buildTvProgressSnapshot = buildTvProgressSnapshot;
const domain_1 = require("./domain");
const DAY_MS = 86400000;
const valueRecord = (value) => value && typeof value === "object" ? value : {};
const observedMs = (value) => { const ms = Date.parse(String(value !== null && value !== void 0 ? value : "")); return Number.isFinite(ms) ? ms : null; };
const percent = (value) => value == null ? null : Math.max(0, Math.min(100, Math.round(value * 100)));
const isoDay = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
function tvProgressRecommendedSurface(input) {
    if (input.openErrors > 0 || input.dueReviews > 0)
        return "REVIEW";
    if (input.accuracyPercent != null && input.accuracyPercent < 75)
        return "QUESTION_SET";
    if (input.activeTopics > 0 && input.coveredTopics < input.activeTopics)
        return "STUDY_SESSION";
    return "QUESTION_SET";
}
function buildTvProgressSnapshot(state, at = new Date()) {
    var _a, _b;
    const planId = state.activePlanId;
    const plan = (_a = state.plans.find(item => item.id === planId)) !== null && _a !== void 0 ? _a : null;
    const metrics = (0, domain_1.deriveProgressMetrics)(state, { at: at.toISOString(), planId });
    const atMs = at.getTime();
    const sinceMs = atMs - 7 * DAY_MS;
    const weeklyEvidence = state.evidence.filter(item => item.planId === planId && item.kind === "question-attempt").filter(item => { const ms = observedMs(item.observedAt); return ms != null && ms >= sinceMs && ms <= atMs; });
    const weeklySessions = state.sessions.filter(item => item.planId === planId && item.status === "completed").filter(item => { var _a, _b; const ms = observedMs((_b = (_a = item.endedAt) !== null && _a !== void 0 ? _a : item.updatedAt) !== null && _b !== void 0 ? _b : item.startedAt); return ms != null && ms >= sinceMs && ms <= atMs; });
    const subjectOrder = new Map(state.subjects.filter(subject => subject.planId === planId).slice().sort((a, b) => a.order - b.order || a.name.localeCompare(b.name)).map((subject, index) => [subject.id, index]));
    const rows = state.subjects.filter(subject => subject.planId === planId && !subject.archived).map(subject => {
        const topics = state.topics.filter(topic => topic.planId === planId && topic.subjectId === subject.id && topic.status !== "archived");
        const topicIds = new Set(topics.map(topic => topic.id));
        const covered = new Set(state.evidence.filter(item => item.planId === planId && item.topicId != null && topicIds.has(item.topicId)).map(item => item.topicId));
        const attempts = weeklyEvidence.filter(item => { var _a, _b, _c; return ((_c = (_a = item.subjectId) !== null && _a !== void 0 ? _a : (_b = state.questions.find(question => question.id === item.questionId)) === null || _b === void 0 ? void 0 : _b.subjectId) !== null && _c !== void 0 ? _c : null) === subject.id; });
        const gradable = attempts.filter(item => valueRecord(item.value).gradable === true);
        const correct = gradable.filter(item => valueRecord(item.value).correct === true).length;
        const accuracyPercent = gradable.length ? Math.round(correct / gradable.length * 100) : null;
        const weeklyStudyMinutes = weeklySessions.reduce((total, session) => { var _a; const activity = state.activities.find(item => item.id === session.activityId); return (activity === null || activity === void 0 ? void 0 : activity.subjectId) === subject.id ? total + Number((_a = session.durationMinutes) !== null && _a !== void 0 ? _a : 0) : total; }, 0);
        const dueReviews = state.reviews.filter(review => review.planId === planId && review.subjectId === subject.id && review.status === "due" && Date.parse(review.dueAt) <= atMs).length;
        const openErrors = state.errorEntries.filter(error => error.planId === planId && error.subjectId === subject.id && error.status === "open").length;
        const uncovered = Math.max(0, topics.length - covered.size);
        const accuracyPenalty = accuracyPercent == null ? 0 : Math.max(0, 80 - accuracyPercent) / 10;
        const coveragePenalty = topics.length ? uncovered / topics.length * 4 : 0;
        const attentionScore = Math.round((openErrors * 3 + dueReviews * 2 + accuracyPenalty + coveragePenalty) * 100) / 100;
        const recommendedSurface = tvProgressRecommendedSurface({ accuracyPercent, dueReviews, openErrors, coveredTopics: covered.size, activeTopics: topics.length });
        const reasons = [];
        if (openErrors)
            reasons.push(`${openErrors} open error${openErrors === 1 ? "" : "s"}`);
        if (dueReviews)
            reasons.push(`${dueReviews} due review${dueReviews === 1 ? "" : "s"}`);
        if (accuracyPercent != null && accuracyPercent < 80)
            reasons.push(`${accuracyPercent}% recent accuracy`);
        if (uncovered)
            reasons.push(`${uncovered} topic${uncovered === 1 ? "" : "s"} without evidence`);
        if (!reasons.length)
            reasons.push("No current weakness signal");
        return { subjectId: subject.id, name: subject.name, weeklyStudyMinutes, weeklyQuestionAttempts: attempts.length, accuracyPercent, coveredTopics: covered.size, activeTopics: topics.length, dueReviews, openErrors, attentionScore, recommendedSurface, reason: reasons.join(" · ") };
    }).sort((a, b) => { var _a, _b; return ((_a = subjectOrder.get(a.subjectId)) !== null && _a !== void 0 ? _a : 999) - ((_b = subjectOrder.get(b.subjectId)) !== null && _b !== void 0 ? _b : 999) || a.name.localeCompare(b.name); });
    const weakSubjects = rows.filter(row => row.attentionScore > 0).slice().sort((a, b) => b.attentionScore - a.attentionScore || a.name.localeCompare(b.name)).slice(0, 6);
    const weekStart = new Date(atMs - 6 * DAY_MS);
    const weeklyGradable = weeklyEvidence.filter(item => valueRecord(item.value).gradable === true);
    const weeklyCorrect = weeklyGradable.filter(item => valueRecord(item.value).correct === true).length;
    const weeklyAccuracyPercent = weeklyGradable.length ? Math.round(weeklyCorrect / weeklyGradable.length * 100) : null;
    return { planId, planName: (_b = plan === null || plan === void 0 ? void 0 : plan.name) !== null && _b !== void 0 ? _b : "No active plan", weekLabel: `${isoDay(weekStart)} → ${isoDay(at)}`, weeklyStudyMinutes: metrics.weeklyStudyMinutes, weeklyQuestionAttempts: weeklyEvidence.length, accuracyPercent: weeklyAccuracyPercent, readinessPercent: metrics.readiness, readinessReason: metrics.readinessReason, reviewCompletionPercent: percent(metrics.reviewCompletionRate), coveragePercent: percent(metrics.topicCoverage), subjects: rows, weakSubjects };
}
