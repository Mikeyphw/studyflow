"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultWebCompactCompanionProfiles = defaultWebCompactCompanionProfiles;
exports.projectAttentionRouteToCompactCompanions = projectAttentionRouteToCompactCompanions;
exports.projectWebAttentionToCompactCompanions = projectWebAttentionToCompactCompanions;
const DEFAULT_STALE_MS = 120000;
const DEFAULT_LIMIT = 4;
const HANDLED_STATES = new Set(["delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled", "dismissed", "missed", "expired", "cancelled"]);
function defaultWebCompactCompanionProfiles(localDeviceId, nowEpochMillis) {
    return [
        {
            companionId: "web-compact-panel",
            displayName: "Compact panel",
            parentDeviceId: localDeviceId,
            formFactor: "web_compact",
            availability: "connected",
            maxActions: 3,
            canHandoff: true,
            canVibrate: false,
            canShowSensitiveText: true,
            lastSeenAtEpochMillis: nowEpochMillis,
        },
        {
            companionId: "web-compact-badge",
            displayName: "Compact badge",
            parentDeviceId: localDeviceId,
            formFactor: "notification_shade",
            availability: "connected",
            maxActions: 1,
            canHandoff: false,
            canVibrate: false,
            canShowSensitiveText: false,
            lastSeenAtEpochMillis: nowEpochMillis,
        },
    ];
}
function projectAttentionRouteToCompactCompanions(entry, route, companions, nowEpochMillis, staleAfterMillis = DEFAULT_STALE_MS, maxCompanions = DEFAULT_LIMIT) {
    var _a;
    if (!route.deliverable || HANDLED_STATES.has(entry.state)) {
        return emptyProjection(entry.occurrence.occurrenceId, route, companions, route.deliverable
            ? "ATT01 ledger already handled this occurrence; compact companions suppress replay."
            : "No parent device owns the route, so compact companions cannot independently deliver.");
    }
    const targetDevices = new Set(route.targetDeviceIds);
    const eligible = companions
        .filter(companion => targetDevices.has(companion.parentDeviceId))
        .filter(companion => isCompanionAvailable(companion, nowEpochMillis, staleAfterMillis))
        .sort((a, b) => a.parentDeviceId.localeCompare(b.parentDeviceId) || a.companionId.localeCompare(b.companionId));
    const cards = eligible.slice(0, maxCompanions).map(companion => compactCardFromEntry(entry, route, companion));
    return {
        occurrenceId: entry.occurrence.occurrenceId,
        ownerDeviceId: (_a = route.owner) === null || _a === void 0 ? void 0 : _a.deviceId,
        cards,
        diagnostics: {
            candidateCount: companions.length,
            eligibleCount: eligible.length,
            projectedCount: cards.length,
            suppressedCompanionIds: companions.filter(companion => !eligible.includes(companion)).map(companion => companion.companionId),
            parentRouteDeliverable: route.deliverable,
            explanation: "Compact companions mirror ATT07 parent ownership; they never become owners.",
        },
        deliverable: cards.length > 0,
    };
}
function projectWebAttentionToCompactCompanions(snapshot, companions = defaultWebCompactCompanionProfiles(snapshot.routeSummary.localDeviceId, Date.now())) {
    var _a, _b;
    const active = snapshot.entries.find(entry => !HANDLED_STATES.has(entry.state));
    if (!active) {
        return emptyProjection(null, null, companions, "No active attention entry is available for compact projection.");
    }
    const route = (_b = (_a = snapshot.routeSummary.routes.find(item => item.occurrenceId === active.occurrence.occurrenceId)) !== null && _a !== void 0 ? _a : snapshot.routeSummary.routes.find(item => item.deliverable)) !== null && _b !== void 0 ? _b : null;
    if (!route) {
        return emptyProjection(active.occurrence.occurrenceId, null, companions, "No ATT07 route is available for compact projection.");
    }
    return projectAttentionRouteToCompactCompanions(active, route, companions, Date.now());
}
function compactCardFromEntry(entry, route, companion) {
    var _a, _b, _c;
    const role = ((_a = route.owner) === null || _a === void 0 ? void 0 : _a.deviceId) === companion.parentDeviceId ? "owner" : "mirror";
    const [title, body] = compactText(entry, companion.canShowSensitiveText);
    return {
        companionId: companion.companionId,
        companionDisplayName: companion.displayName,
        parentDeviceId: companion.parentDeviceId,
        occurrenceId: entry.occurrence.occurrenceId,
        category: entry.occurrence.category,
        priority: (_b = entry.occurrence.priority) !== null && _b !== void 0 ? _b : "normal",
        title,
        body,
        scheduledAtEpochMillis: entry.occurrence.scheduledAtEpochMillis,
        renderMode: renderModeFor(companion.formFactor, role),
        parentRouteRole: role,
        ownerDeviceId: (_c = route.owner) === null || _c === void 0 ? void 0 : _c.deviceId,
        actions: compactActions(entry, companion.maxActions, companion.canHandoff),
        sensitiveTextHidden: !companion.canShowSensitiveText,
        hapticHint: companion.canVibrate && entry.occurrence.priority !== "low",
    };
}
function compactText(entry, showSensitive) {
    var _a;
    if (!showSensitive)
        return [defaultTitle(entry.occurrence.category), defaultBody(entry.occurrence.category)];
    const payload = (_a = entry.occurrence.payload) !== null && _a !== void 0 ? _a : {};
    const title = typeof payload.title === "string" ? payload.title : defaultTitle(entry.occurrence.category);
    const body = typeof payload.body === "string" ? payload.body : defaultBody(entry.occurrence.category);
    return [title, body];
}
function compactActions(entry, limit, canHandoff) {
    var _a, _b;
    const explicit = (_b = (_a = entry.occurrence.actions) === null || _a === void 0 ? void 0 : _a.map(action => action.kind)) !== null && _b !== void 0 ? _b : [];
    const defaults = defaultActions(entry.occurrence.category);
    const source = explicit.length ? explicit : defaults;
    const kinds = Array.from(new Set(source)).filter(kind => canHandoff || kind !== "move_to_device").slice(0, Math.max(1, Math.min(3, limit)));
    return kinds.map(kind => ({ kind, label: actionLabel(kind), destructive: kind === "dismiss", requiresParentDevice: true }));
}
function defaultActions(category) {
    if (category === "due_review")
        return ["review_now", "snooze", "move_to_device"];
    if (category === "planned_study" || category === "missed_activity")
        return ["start_now", "snooze", "move_to_device"];
    if (category === "session_ending" || category === "session_completed")
        return ["continue", "snooze", "move_to_device"];
    return ["open", "dismiss", "move_to_device"];
}
function actionLabel(kind) {
    switch (kind) {
        case "review_now": return "Review";
        case "start_now": return "Start";
        case "snooze": return "Snooze";
        case "reschedule": return "Move";
        case "mark_done": return "Done";
        case "dismiss": return "Dismiss";
        case "continue": return "Continue";
        case "move_to_device": return "Phone";
        default: return "Open";
    }
}
function defaultTitle(category) {
    return category === "due_review" ? "Review due"
        : category === "planned_study" ? "Study soon"
            : category === "missed_activity" ? "Catch up"
                : category === "daily_summary" ? "Today"
                    : category === "session_ending" ? "Timer ending"
                        : category === "session_completed" ? "Session done"
                            : category === "exam_countdown" ? "Exam countdown"
                                : category === "schedule_change" ? "Schedule changed"
                                    : category === "plan_warning" ? "Plan warning" : "StudyFlow update";
}
function defaultBody(category) {
    return category === "due_review" ? "A review is ready."
        : category === "planned_study" ? "Start from your phone or paired device."
            : category === "missed_activity" ? "A study block needs attention."
                : category === "daily_summary" ? "Open your daily plan."
                    : category === "session_ending" ? "Your session is almost complete."
                        : category === "session_completed" ? "Choose a next step."
                            : category === "exam_countdown" ? "Check your remaining plan."
                                : category === "schedule_change" ? "Review the updated schedule."
                                    : category === "plan_warning" ? "Open StudyFlow to fix it." : "A connected device sent an update.";
}
function renderModeFor(formFactor, role) {
    if (formFactor === "quick_tile")
        return "quick_action";
    if (formFactor === "notification_shade")
        return "mirror_only";
    return role === "owner" ? "glance" : "mirror_only";
}
function isCompanionAvailable(companion, nowEpochMillis, staleAfterMillis) {
    if (companion.availability === "disconnected")
        return false;
    if (companion.mutedUntilEpochMillis && companion.mutedUntilEpochMillis > nowEpochMillis)
        return false;
    if (companion.lastSeenAtEpochMillis && nowEpochMillis - companion.lastSeenAtEpochMillis > staleAfterMillis)
        return false;
    return true;
}
function emptyProjection(occurrenceId, route, companions, explanation) {
    var _a, _b;
    return {
        occurrenceId,
        ownerDeviceId: (_a = route === null || route === void 0 ? void 0 : route.owner) === null || _a === void 0 ? void 0 : _a.deviceId,
        cards: [],
        diagnostics: {
            candidateCount: companions.length,
            eligibleCount: 0,
            projectedCount: 0,
            suppressedCompanionIds: companions.map(companion => companion.companionId),
            parentRouteDeliverable: (_b = route === null || route === void 0 ? void 0 : route.deliverable) !== null && _b !== void 0 ? _b : false,
            explanation,
        },
        deliverable: false,
    };
}
