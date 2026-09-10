"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TV_PARITY_ERROR_CATEGORIES = exports.TV_PARITY_ZOOM_STEP = exports.TV_PARITY_MAX_ZOOM = exports.TV_PARITY_MIN_ZOOM = void 0;
exports.tvParityPage = tvParityPage;
exports.tvParityZoom = tvParityZoom;
exports.tvParityGoalActions = tvParityGoalActions;
exports.tvParityNextErrorCategory = tvParityNextErrorCategory;
exports.tvParityErrorResolutionAction = tvParityErrorResolutionAction;
exports.tvParitySearchScore = tvParitySearchScore;
exports.TV_PARITY_MIN_ZOOM = 50;
exports.TV_PARITY_MAX_ZOOM = 300;
exports.TV_PARITY_ZOOM_STEP = 25;
exports.TV_PARITY_ERROR_CATEGORIES = [
    "CONTENT_GAP", "FORGOTTEN", "CONCEPT_CONFUSION", "INTERPRETATION", "DISTRACTION",
    "CALCULATION", "TIME_PRESSURE", "DISPUTED", "OTHER",
];
function tvParityPage(current, total, delta) {
    if (total <= 0)
        return 0;
    return Math.max(0, Math.min(total - 1, current + delta));
}
function tvParityZoom(current, delta) {
    return Math.max(exports.TV_PARITY_MIN_ZOOM, Math.min(exports.TV_PARITY_MAX_ZOOM, current + delta));
}
function tvParityGoalActions(input) {
    if (input.completed)
        return [];
    if (input.paused)
        return ["RESUME"];
    return input.canComplete ? ["COMPLETE", "PAUSE"] : ["PAUSE"];
}
function tvParityNextErrorCategory(current) {
    const index = exports.TV_PARITY_ERROR_CATEGORIES.indexOf(current.toUpperCase());
    return exports.TV_PARITY_ERROR_CATEGORIES[(index + 1) % exports.TV_PARITY_ERROR_CATEGORIES.length];
}
function tvParityErrorResolutionAction(locallyResolved) {
    return locallyResolved ? "REOPEN" : "RESOLVE";
}
function tvParitySearchScore(query, title, subtitle) {
    const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    const normalizedTitle = title.toLowerCase(), normalizedSubtitle = subtitle.toLowerCase();
    if (terms.some(term => !normalizedTitle.includes(term) && !normalizedSubtitle.includes(term)))
        return null;
    return terms.reduce((sum, term) => sum + (normalizedTitle === term ? 100 : normalizedTitle.startsWith(term) ? 70 :
        normalizedTitle.includes(term) ? 45 : 20), 0);
}
