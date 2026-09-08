"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tvMockChoiceOptionId = tvMockChoiceOptionId;
exports.tvMockRemainingMs = tvMockRemainingMs;
exports.tvMockProgressPercent = tvMockProgressPercent;
exports.tvMockAnsweredCount = tvMockAnsweredCount;
exports.tvMockQuestionProgressPercent = tvMockQuestionProgressPercent;
function tvMockChoiceOptionId(index) { return `choice-${Math.max(0, Math.trunc(index))}`; }
function tvMockRemainingMs(durationMinutes, startedAtEpochMillis, nowEpochMillis, pausedAtEpochMillis, pausedAccumulatedMs) {
    const total = Math.max(1, Number(durationMinutes) || 1) * 60000;
    if (startedAtEpochMillis == null)
        return total;
    const endOfElapsed = pausedAtEpochMillis == null ? nowEpochMillis : pausedAtEpochMillis;
    const elapsed = Math.max(0, endOfElapsed - startedAtEpochMillis - Math.max(0, pausedAccumulatedMs || 0));
    return Math.max(0, total - elapsed);
}
function tvMockProgressPercent(durationMinutes, remainingMs) {
    const total = Math.max(1, Number(durationMinutes) || 1) * 60000;
    const remaining = Math.max(0, Math.min(total, Number(remainingMs) || 0));
    return Math.max(0, Math.min(100, Math.round((1 - remaining / total) * 100)));
}
function tvMockAnsweredCount(answers) { return Object.values(answers).filter(value => String(value !== null && value !== void 0 ? value : "").trim().length > 0).length; }
function tvMockQuestionProgressPercent(index, total) { return total <= 0 ? 0 : Math.max(0, Math.min(100, Math.round(((Math.max(0, index) + 1) / total) * 100))); }
