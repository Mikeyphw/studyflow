"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TV_HISTORY_STATE_KEY = void 0;
exports.screenFromHash = screenFromHash;
exports.readTvHistoryEntry = readTvHistoryEntry;
exports.tvHistoryState = tvHistoryState;
exports.tvHistoryUrl = tvHistoryUrl;
exports.replaceTvHistoryEntry = replaceTvHistoryEntry;
exports.pushTvHistoryEntry = pushTvHistoryEntry;
const SCREENS = new Set(["today", "plan", "practice", "progress", "session", "questions", "mock", "review", "schedule", "library"]);
const OVERLAYS = new Set(["search", "settings", "material"]);
exports.TV_HISTORY_STATE_KEY = "studyflowTvHistory";
function screenFromHash(hash) {
    const candidate = hash.replace(/^#/, "");
    return SCREENS.has(candidate) ? candidate : "today";
}
function readTvHistoryEntry(state, hash) {
    const fallback = { version: 1, screen: screenFromHash(hash), overlay: null, overlayId: null };
    if (!state || typeof state !== "object")
        return fallback;
    const raw = state[exports.TV_HISTORY_STATE_KEY];
    if (!raw || typeof raw !== "object")
        return fallback;
    const value = raw;
    const screen = SCREENS.has(value.screen) ? value.screen : fallback.screen;
    const overlay = OVERLAYS.has(value.overlay) ? value.overlay : null;
    const overlayId = overlay === "material" && typeof value.overlayId === "string" && value.overlayId ? value.overlayId : null;
    return { version: 1, screen, overlay, overlayId };
}
function tvHistoryState(entry) { return { [exports.TV_HISTORY_STATE_KEY]: entry }; }
function tvHistoryUrl(screen) { const query = typeof location === "undefined" ? "" : location.search; return `${typeof location === "undefined" ? "" : location.pathname}${query}#${screen}`; }
function replaceTvHistoryEntry(entry) {
    if (typeof history === "undefined")
        return;
    history.replaceState(tvHistoryState(entry), "", tvHistoryUrl(entry.screen));
}
function pushTvHistoryEntry(entry) {
    if (typeof history === "undefined")
        return;
    history.pushState(tvHistoryState(entry), "", tvHistoryUrl(entry.screen));
}
