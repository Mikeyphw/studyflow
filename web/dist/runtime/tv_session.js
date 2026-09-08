"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tvStudySessionPhase = tvStudySessionPhase;
exports.buildTvStudySessionSegments = buildTvStudySessionSegments;
exports.tvStudySessionProgressPercent = tvStudySessionProgressPercent;
exports.tvStudySessionSegmentIndex = tvStudySessionSegmentIndex;
function tvStudySessionPhase(status, completed = false) {
    if (completed)
        return "completed";
    if (status === "running")
        return "running";
    if (status === "paused")
        return "paused";
    return "ready";
}
function buildTvStudySessionSegments(durationMinutes) {
    const total = Math.max(1, Math.round(Number(durationMinutes) || 25));
    if (total < 15)
        return [{ id: "core", label: "Focused study", minutes: total }];
    if (total < 30)
        return [{ id: "core", label: "Focused study", minutes: Math.max(1, total - 5) }, { id: "recall", label: "Recall & close", minutes: 5 }];
    const opening = Math.min(5, Math.max(2, Math.round(total * .1)));
    const closing = Math.min(5, Math.max(3, Math.round(total * .1)));
    return [{ id: "prepare", label: "Settle in", minutes: opening }, { id: "core", label: "Core study", minutes: Math.max(1, total - opening - closing) }, { id: "recall", label: "Recall & close", minutes: closing }];
}
function tvStudySessionProgressPercent(durationMinutes, remainingMs) {
    const total = Math.max(1, Number(durationMinutes) || 25) * 60000;
    const remaining = Math.max(0, Math.min(total, Number(remainingMs) || 0));
    return Math.max(0, Math.min(100, Math.round((1 - remaining / total) * 100)));
}
function tvStudySessionSegmentIndex(segments, durationMinutes, remainingMs) {
    if (!segments.length)
        return 0;
    const elapsed = Math.max(0, (Math.max(1, Number(durationMinutes) || 25) * 60000) - Math.max(0, Number(remainingMs) || 0));
    let boundary = 0;
    for (let i = 0; i < segments.length; i++) {
        boundary += segments[i].minutes * 60000;
        if (elapsed < boundary)
            return i;
    }
    return segments.length - 1;
}
