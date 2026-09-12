"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StudyFlowWebTvAttentionRuntime = void 0;
exports.webTvAttentionCapabilities = webTvAttentionCapabilities;
exports.webTvAttentionRouteForCategory = webTvAttentionRouteForCategory;
exports.webTvAttentionActionsForCategory = webTvAttentionActionsForCategory;
exports.bannerFromWebAttentionEntry = bannerFromWebAttentionEntry;
exports.projectWebAttentionToTv = projectWebAttentionToTv;
exports.installStudyFlowWebTvAttentionBridge = installStudyFlowWebTvAttentionBridge;
const TV_ACTION_LIMIT = 3;
const ACTIVE_STATES = new Set(["planned", "scheduled", "delivery_pending", "delivered", "acknowledged", "snoozed", "missed"]);
function webTvAttentionCapabilities(capabilities) {
    return {
        foregroundBanners: true,
        systemNotifications: false,
        backgroundAlarms: false,
        closedBrowserDelivery: false,
        notificationApiAvailable: capabilities.notifications,
        serviceWorkerAvailable: capabilities.serviceWorker,
        designatedNotifierFallback: true,
        deliveryMode: "foreground-only",
    };
}
function webTvAttentionRouteForCategory(category) {
    switch (category) {
        case "due_review": return "#reviews";
        case "planned_study":
        case "session_ending":
        case "session_completed": return "#session";
        case "exam_countdown": return "#progress";
        case "schedule_change": return "#schedule";
        default: return "#today";
    }
}
function webTvAttentionActionsForCategory(category) {
    const actions = category === "due_review"
        ? ["open", "snooze", "move_to_phone"]
        : category === "planned_study" || category === "session_ending" || category === "session_completed"
            ? ["continue", "snooze", "move_to_phone"]
            : ["open", "dismiss", "move_to_phone"];
    return actions.slice(0, TV_ACTION_LIMIT);
}
function bannerFromWebAttentionEntry(entry) {
    var _a;
    const payload = (_a = entry.occurrence.payload) !== null && _a !== void 0 ? _a : {};
    const title = typeof payload.title === "string" ? payload.title : `StudyFlow ${entry.occurrence.category.replace(/_/g, " ")}`;
    const body = typeof payload.body === "string" ? payload.body : "Use the TV foreground banner, or continue this reminder on your designated phone/desktop/Web notifier.";
    const routeHash = typeof payload.routeHash === "string" ? payload.routeHash : webTvAttentionRouteForCategory(entry.occurrence.category);
    return {
        occurrenceId: entry.occurrence.occurrenceId,
        category: entry.occurrence.category,
        title,
        body,
        routeHash,
        actions: webTvAttentionActionsForCategory(entry.occurrence.category),
        updatedAtEpochMillis: entry.updatedAtEpochMillis,
    };
}
function projectWebAttentionToTv(snapshot) {
    var _a, _b, _c, _d;
    const recentBanners = snapshot.entries
        .filter(entry => ACTIVE_STATES.has(entry.state))
        .slice(0, 8)
        .map(bannerFromWebAttentionEntry);
    return {
        capabilities: webTvAttentionCapabilities(snapshot.capabilities),
        activeBanner: (_a = recentBanners[0]) !== null && _a !== void 0 ? _a : null,
        recentBanners,
        badgeCount: snapshot.badgeCount,
        lastScanAtEpochMillis: snapshot.lastScanAtEpochMillis,
        focusProfile: (_d = (_c = (_b = snapshot.preferences) === null || _b === void 0 ? void 0 : _b.focusPolicy) === null || _c === void 0 ? void 0 : _c.profile) !== null && _d !== void 0 ? _d : "balanced",
        focusOutcomes: snapshot.entries.map(entry => { var _a; return (_a = entry.policyAudit) === null || _a === void 0 ? void 0 : _a.outcome; }).filter((value) => !!value).slice(0, 8),
        focusControlsReadOnly: true,
        source: "att10-web-runtime",
    };
}
class StudyFlowWebTvAttentionRuntime {
    constructor(webRuntime) {
        this.webRuntime = webRuntime;
    }
    snapshot() {
        return projectWebAttentionToTv(this.webRuntime.snapshot());
    }
    applyAction(occurrenceId, action) {
        const mapped = action === "move_to_phone" ? "open" : action === "continue" ? "open" : action;
        void this.webRuntime.applyAction(occurrenceId, mapped);
    }
}
exports.StudyFlowWebTvAttentionRuntime = StudyFlowWebTvAttentionRuntime;
function installStudyFlowWebTvAttentionBridge(runtime) {
    var _a, _b;
    if (typeof document === "undefined")
        return () => { };
    const root = document.documentElement;
    const render = () => {
        var _a;
        const snapshot = runtime.snapshot();
        root.dataset.studyflowTvAttention = snapshot.capabilities.deliveryMode;
        root.dataset.studyflowTvAttentionFallback = snapshot.capabilities.designatedNotifierFallback ? "designated-notifier" : "none";
        root.dataset.studyflowTvAttentionBadge = String(snapshot.badgeCount);
        root.dataset.studyflowTvAttentionFocusProfile = snapshot.focusProfile;
        root.dataset.studyflowTvAttentionFocusControls = snapshot.focusControlsReadOnly ? "remote-safe-read-only" : "local";
        (_a = globalThis.dispatchEvent) === null || _a === void 0 ? void 0 : _a.call(globalThis, new CustomEvent("studyflow:tv-attention-snapshot", { detail: snapshot }));
    };
    const onAttention = () => render();
    render();
    (_a = globalThis.addEventListener) === null || _a === void 0 ? void 0 : _a.call(globalThis, "studyflow:attention-snapshot", onAttention);
    (_b = globalThis.addEventListener) === null || _b === void 0 ? void 0 : _b.call(globalThis, "studyflow:attention-drain", onAttention);
    return () => {
        var _a, _b;
        (_a = globalThis.removeEventListener) === null || _a === void 0 ? void 0 : _a.call(globalThis, "studyflow:attention-snapshot", onAttention);
        (_b = globalThis.removeEventListener) === null || _b === void 0 ? void 0 : _b.call(globalThis, "studyflow:attention-drain", onAttention);
        delete root.dataset.studyflowTvAttention;
        delete root.dataset.studyflowTvAttentionFallback;
        delete root.dataset.studyflowTvAttentionBadge;
        delete root.dataset.studyflowTvAttentionFocusProfile;
        delete root.dataset.studyflowTvAttentionFocusControls;
    };
}
