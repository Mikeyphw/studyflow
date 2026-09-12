"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultWebAttentionFocusPolicy = defaultWebAttentionFocusPolicy;
exports.normalizeWebAttentionFocusPolicy = normalizeWebAttentionFocusPolicy;
exports.evaluateWebAttentionFocusPolicy = evaluateWebAttentionFocusPolicy;
exports.quietContains = quietContains;
exports.nextQuietEnd = nextQuietEnd;
const DEFAULT_CATEGORY_POLICIES = [
    { category: "planned_study", mode: "mirror_if_unhandled", bypassQuietHours: false, mirrorAfterMinutes: 15 },
    { category: "due_review", mode: "mirror_if_unhandled", bypassQuietHours: false, mirrorAfterMinutes: 30 },
    { category: "missed_activity", mode: "mirror_if_unhandled", bypassQuietHours: false, mirrorAfterMinutes: 20 },
    { category: "exam_countdown", mode: "require_user_action", bypassQuietHours: true, mirrorAfterMinutes: 15 },
    { category: "daily_summary", mode: "silent", bypassQuietHours: false, mirrorAfterMinutes: 15 },
    { category: "session_ending", mode: "normal", bypassQuietHours: true, mirrorAfterMinutes: 15 },
    { category: "session_completed", mode: "normal", bypassQuietHours: true, mirrorAfterMinutes: 15 },
    { category: "schedule_change", mode: "silent", bypassQuietHours: false, mirrorAfterMinutes: 15 },
    { category: "plan_warning", mode: "require_user_action", bypassQuietHours: false, mirrorAfterMinutes: 15 },
    { category: "sync_attention", mode: "compact_only", bypassQuietHours: false, mirrorAfterMinutes: 15 },
];
const CATEGORIES = DEFAULT_CATEGORY_POLICIES.map(value => value.category);
const HANDLED = new Set(["delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled", "dismissed", "missed", "expired", "cancelled"]);
function defaultWebAttentionFocusPolicy(_timezoneId = resolvedTimezoneId()) {
    return {
        version: 1,
        enabled: true,
        profile: "balanced",
        quietWindow: null,
        categoryPolicies: DEFAULT_CATEGORY_POLICIES.map(value => ({ ...value })),
        allowHighPriorityQuietBypass: true,
        allowTimeSensitiveQuietBypass: true,
        maxEscalationMirrors: 1,
    };
}
function normalizeWebAttentionFocusPolicy(value) {
    var _a;
    const defaults = defaultWebAttentionFocusPolicy();
    if (!value || typeof value !== "object")
        return defaults;
    const raw = value;
    const profiles = ["balanced", "exam_mode", "work_mode", "sleep_mode", "travel_offline", "minimal_tv"];
    const modes = ["normal", "silent", "defer", "compact_only", "route_to_phone", "mirror_if_unhandled", "require_user_action"];
    const provided = new Map((Array.isArray(raw.categoryPolicies) ? raw.categoryPolicies : []).map(item => [item.category, item]));
    const categoryPolicies = DEFAULT_CATEGORY_POLICIES.map(defaultPolicy => {
        var _a;
        const candidate = provided.get(defaultPolicy.category);
        return {
            category: defaultPolicy.category,
            mode: candidate && modes.includes(candidate.mode) ? candidate.mode : defaultPolicy.mode,
            bypassQuietHours: (_a = candidate === null || candidate === void 0 ? void 0 : candidate.bypassQuietHours) !== null && _a !== void 0 ? _a : defaultPolicy.bypassQuietHours,
            mirrorAfterMinutes: clampInt(candidate === null || candidate === void 0 ? void 0 : candidate.mirrorAfterMinutes, defaultPolicy.mirrorAfterMinutes, 0, 720),
        };
    });
    const q = raw.quietWindow;
    let startMinute = clampInt(q === null || q === void 0 ? void 0 : q.startMinute, 22 * 60, 0, 1439);
    let endMinute = clampInt(q === null || q === void 0 ? void 0 : q.endMinute, 7 * 60, 0, 1439);
    if (startMinute === endMinute)
        endMinute = (endMinute + 1) % 1440;
    return {
        version: 1,
        enabled: raw.enabled !== false,
        profile: profiles.includes(raw.profile) ? raw.profile : defaults.profile,
        quietWindow: q == null ? null : {
            enabled: (q === null || q === void 0 ? void 0 : q.enabled) !== false,
            startMinute,
            endMinute,
            timezoneId: ((_a = q === null || q === void 0 ? void 0 : q.timezoneId) === null || _a === void 0 ? void 0 : _a.trim()) || resolvedTimezoneId(),
        },
        categoryPolicies,
        allowHighPriorityQuietBypass: raw.allowHighPriorityQuietBypass !== false,
        allowTimeSensitiveQuietBypass: raw.allowTimeSensitiveQuietBypass !== false,
        maxEscalationMirrors: clampInt(raw.maxEscalationMirrors, 1, 0, 3),
    };
}
function evaluateWebAttentionFocusPolicy(occurrence, policyInput, nowEpochMillis, ledgerEntry, localClientKind = "web") {
    var _a, _b;
    const policy = normalizeWebAttentionFocusPolicy(policyInput);
    if (ledgerEntry && HANDLED.has(ledgerEntry.state))
        return decision(occurrence, policy, nowEpochMillis, "suppress_duplicate", "ATT01 ledger already records this occurrence as handled.");
    if (!policy.enabled)
        return decision(occurrence, policy, nowEpochMillis, "deliver_now", "ATT10 focus policy is disabled; existing delivery behavior is preserved.");
    const category = effectiveCategoryPolicy(policy, occurrence.category);
    const quiet = policy.quietWindow;
    const inQuiet = !!(quiet === null || quiet === void 0 ? void 0 : quiet.enabled) && quietContains(quiet, nowEpochMillis, occurrence.timezoneId);
    const priority = (_a = occurrence.priority) !== null && _a !== void 0 ? _a : "normal";
    const bypass = category.bypassQuietHours ||
        (policy.allowHighPriorityQuietBypass && priority === "high") ||
        (policy.allowTimeSensitiveQuietBypass && priority === "time_sensitive");
    if (inQuiet && !bypass && quiet) {
        let outcome;
        if (policy.profile === "sleep_mode")
            outcome = "defer_until_quiet_end";
        else if (policy.profile === "minimal_tv")
            outcome = "route_to_phone";
        else if (policy.profile === "travel_offline")
            outcome = "compact_only";
        else if (category.mode === "silent")
            outcome = "silent_delivery";
        else if (category.mode === "compact_only")
            outcome = "compact_only";
        else if (category.mode === "route_to_phone")
            outcome = "route_to_phone";
        else if (category.mode === "require_user_action")
            outcome = "require_user_action";
        else
            outcome = "defer_until_quiet_end";
        return decision(occurrence, policy, nowEpochMillis, outcome, `Quiet hours are active in ${quiet.timezoneId || occurrence.timezoneId || "UTC"}; ATT10 applied ${outcome}.`, outcome === "defer_until_quiet_end" ? nextQuietEnd(quiet, nowEpochMillis, occurrence.timezoneId) : undefined, true);
    }
    let outcome = "deliver_now";
    if (policy.profile === "minimal_tv" && (localClientKind === "android_tv" || localClientKind === "web_tv"))
        outcome = "route_to_phone";
    else if (policy.profile === "travel_offline")
        outcome = "compact_only";
    else if (policy.profile === "exam_mode" && occurrence.category === "exam_countdown")
        outcome = "require_user_action";
    else if (category.mode === "silent")
        outcome = "silent_delivery";
    else if (category.mode === "compact_only")
        outcome = "compact_only";
    else if (category.mode === "route_to_phone")
        outcome = "route_to_phone";
    else if (category.mode === "mirror_if_unhandled" && nowEpochMillis >= ((_b = occurrence.originalScheduledAtEpochMillis) !== null && _b !== void 0 ? _b : occurrence.scheduledAtEpochMillis) + category.mirrorAfterMinutes * 60000)
        outcome = "escalate_mirror";
    else if (category.mode === "require_user_action")
        outcome = "require_user_action";
    return decision(occurrence, policy, nowEpochMillis, outcome, `ATT10 ${policy.profile} evaluated ${occurrence.category} as ${outcome}.`);
}
function effectiveCategoryPolicy(policy, category) {
    var _a, _b;
    const defaultPolicy = (_a = DEFAULT_CATEGORY_POLICIES.find(value => value.category === category)) !== null && _a !== void 0 ? _a : { category, mode: "normal", bypassQuietHours: false, mirrorAfterMinutes: 15 };
    const base = (_b = policy.categoryPolicies.find(value => value.category === category)) !== null && _b !== void 0 ? _b : defaultPolicy;
    switch (policy.profile) {
        case "exam_mode":
            if (["exam_countdown", "due_review", "planned_study"].includes(category))
                return { ...base, mode: category === "exam_countdown" ? "require_user_action" : "mirror_if_unhandled", bypassQuietHours: category === "exam_countdown" };
            return base;
        case "work_mode": return ["daily_summary", "schedule_change", "plan_warning"].includes(category) ? { ...base, mode: "silent" } : base;
        case "sleep_mode": return { ...base, mode: "defer" };
        case "travel_offline": return { ...base, mode: "compact_only" };
        case "minimal_tv": return { ...base, mode: "route_to_phone" };
        default: return base;
    }
}
function decision(occurrence, policy, now, outcome, explanation, deferredUntilEpochMillis, quietHoursActive = false) {
    return {
        occurrenceId: occurrence.occurrenceId,
        profile: policy.profile,
        outcome,
        evaluatedAtEpochMillis: now,
        deferredUntilEpochMillis,
        quietHoursActive,
        deliverSilently: outcome === "silent_delivery",
        compactOnly: outcome === "compact_only",
        preferPhone: outcome === "route_to_phone",
        requestedMirrorCount: outcome === "escalate_mirror" ? policy.maxEscalationMirrors : 0,
        requiresUserAction: outcome === "require_user_action",
        explanation,
    };
}
function quietContains(window, epochMillis, fallbackTimezoneId) {
    if (!window.enabled)
        return false;
    const minute = minuteInZone(epochMillis, validTimezone(window.timezoneId || fallbackTimezoneId));
    return window.startMinute < window.endMinute
        ? minute >= window.startMinute && minute < window.endMinute
        : minute >= window.startMinute || minute < window.endMinute;
}
function nextQuietEnd(window, epochMillis, fallbackTimezoneId) {
    if (!quietContains(window, epochMillis, fallbackTimezoneId))
        return epochMillis;
    // Deliberately evaluate wall-clock minutes in the configured IANA timezone. This remains correct across overnight windows and DST shifts.
    const limit = epochMillis + 30 * 60 * 60000;
    let cursor = epochMillis + 60000;
    while (cursor <= limit && quietContains(window, cursor, fallbackTimezoneId))
        cursor += 60000;
    return Math.min(cursor, limit);
}
function minuteInZone(epochMillis, timezoneId) {
    var _a, _b, _c, _d;
    const formatter = new Intl.DateTimeFormat("en-US", { timeZone: timezoneId, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
    const parts = formatter.formatToParts(new Date(epochMillis));
    const hour = Number((_b = (_a = parts.find(part => part.type === "hour")) === null || _a === void 0 ? void 0 : _a.value) !== null && _b !== void 0 ? _b : "0") % 24;
    const minute = Number((_d = (_c = parts.find(part => part.type === "minute")) === null || _c === void 0 ? void 0 : _c.value) !== null && _d !== void 0 ? _d : "0");
    return hour * 60 + minute;
}
function validTimezone(value) {
    const candidate = value || "UTC";
    try {
        new Intl.DateTimeFormat("en-US", { timeZone: candidate }).format(0);
        return candidate;
    }
    catch {
        return "UTC";
    }
}
function resolvedTimezoneId() {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    }
    catch {
        return "UTC";
    }
}
function clampInt(value, fallback, min, max) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? Math.max(min, Math.min(max, Math.round(numeric))) : fallback;
}
