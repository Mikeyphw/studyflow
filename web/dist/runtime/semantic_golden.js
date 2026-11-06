"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.semanticPracticeScore = semanticPracticeScore;
exports.semanticSrs = semanticSrs;
exports.semanticScheduleGeneration = semanticScheduleGeneration;
exports.semanticScheduleRebalance = semanticScheduleRebalance;
exports.semanticStudyTime = semanticStudyTime;
exports.semanticGoal = semanticGoal;
exports.semanticError = semanticError;
exports.semanticConflict = semanticConflict;
exports.runSemanticGolden = runSemanticGolden;
const parity_intelligence_1 = require("./parity_intelligence");
const portable_1 = require("./portable");
const review_semantics_1 = require("./review_semantics");
const DAY_MS = 86400000;
const roundKotlin = (value) => Math.floor(value + 0.5);
function semanticPracticeScore(input) {
    const raw = input.correct - input.incorrect * input.negativeMarking;
    const scorePercent = input.total <= 0 ? 0 : Math.max(0, Math.min(100, roundKotlin(Math.max(0, raw) / input.total * 100)));
    return { scorePercent, passed: input.minimumScorePercent == null ? null : scorePercent >= input.minimumScorePercent };
}
function semanticSrs(input) {
    return (0, review_semantics_1.scheduleSpacedRepetition)(input);
}
function semanticScheduleGeneration(input) {
    if (input.dayOff)
        return { count: 0, firstStartEpochMillis: null };
    const start = new Date(input.startEpochMillis);
    let first = null, count = 0;
    for (let offset = 0; offset < input.days; offset++) {
        const d = new Date(start.getTime() + offset * DAY_MS);
        const javaDay = ((d.getUTCDay() + 6) % 7) + 1;
        if (javaDay === input.dayOfWeek) {
            const midnight = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
            const at = midnight + input.startMinuteOfDay * 60000;
            if (first == null)
                first = at;
            count++;
        }
    }
    return { count, firstStartEpochMillis: first };
}
function semanticScheduleRebalance(input) {
    if (input.handling === "SKIP")
        return { changed: 1, status: "SKIPPED", scheduledStartEpochMillis: input.startEpochMillis };
    if (input.handling === "POSTPONE")
        return { changed: 1, status: "PLANNED", scheduledStartEpochMillis: input.nowEpochMillis + 5 * 60000 };
    if (input.handling === "RETURN_TO_BACKLOG")
        return { changed: 1, status: "PLANNED", scheduledStartEpochMillis: null };
    return { changed: 0, status: null, scheduledStartEpochMillis: null };
}
function semanticStudyTime(input) { return { netMinutes: Math.max(0, input.grossDurationSeconds) / 60 }; }
function semanticGoal(input) { return { percent: input.targetValue <= 0 ? 0 : Math.max(0, Math.min(100, Math.trunc(input.currentValue / input.targetValue * 100))) }; }
function semanticError(input) {
    const conceptual = new Set(["CONTENT_GAP", "FORGOTTEN_CONTENT", "CONCEPT_CONFUSION"]);
    const priority = input.recurrenceCount >= 5 ? "CRITICAL" : conceptual.has(input.category) && input.recurrenceCount >= 3 ? "HIGH" : input.recurrenceCount >= 3 ? "MEDIUM" : "LOW";
    const suggestion = conceptual.has(input.category) ? "REVIEW_THEORY" : input.category === "CALCULATION" ? "PRACTICE_SIMILAR_QUESTIONS" : input.category === "INTERPRETATION" ? "RE_READ_STATEMENT" : input.category === "TIME_PRESSURE" ? "TIMED_DRILL" : input.category === "DISTRACTION" ? "SLOW_CHECKLIST" : input.category === "DISPUTED_QUESTION" ? "VERIFY_SOURCE" : "REVIEW_EXPLANATION";
    return { priority, suggestion };
}
function semanticConflict(input) {
    const sorted = input.operations.slice().sort((a, b) => a.logicalTimestamp - b.logicalTimestamp || a.deviceId.localeCompare(b.deviceId) || a.originSequence - b.originSequence || a.id.localeCompare(b.id));
    return { winnerOperationId: sorted[sorted.length - 1].id };
}
function runSemanticGolden(kind, input) {
    switch (kind) {
        case "readiness_band": return { band: (0, parity_intelligence_1.readinessBandForScore)(input.score) };
        case "schedule_generation": return semanticScheduleGeneration(input);
        case "schedule_rebalance": return semanticScheduleRebalance(input);
        case "question_score":
        case "mock_score": return semanticPracticeScore(input);
        case "srs": return semanticSrs(input);
        case "study_time": return semanticStudyTime(input);
        case "goal_progress": return semanticGoal(input);
        case "error_state": return semanticError(input);
        case "portable_serialization": return { canonical: (0, portable_1.stableStringify)(input.value) };
        case "sflp_conflict": return semanticConflict(input);
        default: throw new TypeError(`Unknown WEB20 vector kind: ${kind}`);
    }
}
