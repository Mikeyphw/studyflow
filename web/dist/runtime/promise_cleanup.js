"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.STUDY_STRATEGY_TYPES = void 0;
exports.normalizeStudyStrategyType = normalizeStudyStrategyType;
exports.resolvePublicExamDate = resolvePublicExamDate;
exports.officialCountdownDate = officialCountdownDate;
exports.daysUntilOfficialExam = daysUntilOfficialExam;
exports.publicExamCoverageTopicCount = publicExamCoverageTopicCount;
exports.shouldOfferStudyCycle = shouldOfferStudyCycle;
function localDateKey(value) {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, "0");
    const day = String(value.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}
exports.STUDY_STRATEGY_TYPES = [
    "FIXED_WEEKLY",
    "STUDY_CYCLE",
    "GOAL_BASED",
    "ADAPTIVE",
    "FREE_GUIDED",
    "INTENSIVE_SPRINT",
    "COUNTDOWN",
];
function normalizeStudyStrategyType(value) {
    const candidate = String(value !== null && value !== void 0 ? value : "").trim().toUpperCase();
    return exports.STUDY_STRATEGY_TYPES.includes(candidate) ? candidate : "FIXED_WEEKLY";
}
function resolvePublicExamDate(plan) {
    var _a;
    if (plan.mode !== "EXAM_PLAN")
        return { date: null, source: "none", official: false };
    const target = (_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.target;
    if ((target === null || target === void 0 ? void 0 : target.dateStatus) === "confirmed" && target.examDate)
        return { date: target.examDate, source: "confirmed-target", official: true };
    if ((target === null || target === void 0 ? void 0 : target.dateStatus) === "expected" && target.examDate)
        return { date: target.examDate, source: "expected-target", official: false };
    // Legacy plan-only dates remain official only when no target date is carrying a newer truth state.
    if (!(target === null || target === void 0 ? void 0 : target.examDate) && plan.examDate)
        return { date: plan.examDate, source: "legacy-plan", official: true };
    return { date: null, source: "none", official: false };
}
function officialCountdownDate(plan) {
    const resolved = resolvePublicExamDate(plan);
    return resolved.official ? resolved.date : null;
}
function dateOnlyNumber(value) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!m)
        throw new TypeError(`Invalid date-only value: ${value}`);
    return Math.floor(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])) / 86400000);
}
function daysUntilOfficialExam(plan, at = new Date()) {
    const examDate = officialCountdownDate(plan);
    if (!examDate)
        return null;
    return Math.max(0, dateOnlyNumber(examDate) - dateOnlyNumber(localDateKey(at)));
}
function publicExamCoverageTopicCount(state, planId) {
    return state.topics.filter(topic => topic.planId === planId && topic.status !== "archived").length;
}
function shouldOfferStudyCycle(plan) {
    return Boolean(plan && plan.configuration.schedule.strategyType === "STUDY_CYCLE");
}
