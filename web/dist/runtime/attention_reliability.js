"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.routeAuditFromDeliveryRoute = routeAuditFromDeliveryRoute;
exports.analyzeWebAttentionReliability = analyzeWebAttentionReliability;
exports.exportWebAttentionReliabilityJson = exportWebAttentionReliabilityJson;
exports.exportWebAttentionReliabilityCsv = exportWebAttentionReliabilityCsv;
const DELIVERED_STATES = new Set(["delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled", "dismissed"]);
const ACTIVE_STATES = new Set(["planned", "scheduled", "delivery_pending", "snoozed", "rescheduled", "missed"]);
const CATEGORIES = ["planned_study", "due_review", "missed_activity", "daily_summary", "session_ending", "session_completed", "exam_countdown", "schedule_change", "plan_warning", "sync_attention"];
function routeAuditFromDeliveryRoute(route) {
    var _a;
    const target = (value) => ({
        deviceId: value.deviceId,
        clientKind: value.clientKind,
        surface: value.surface,
        role: value.role,
    });
    return {
        occurrenceId: route.occurrenceId,
        reason: route.reason,
        decidedAtEpochMillis: route.decidedAtEpochMillis,
        owner: route.owner ? target(route.owner) : null,
        mirrors: route.mirrors.map(target),
        candidateCount: route.diagnostics.candidateCount,
        eligibleCount: route.diagnostics.eligibleCount,
        suppressedDeviceIds: [...route.diagnostics.suppressedDeviceIds],
        policyOutcome: (_a = route.policyOutcome) !== null && _a !== void 0 ? _a : route.diagnostics.policyOutcome,
        explanation: route.diagnostics.explanation,
    };
}
function analyzeWebAttentionReliability(ledger, devices, nowEpochMillis, workspaceId, staleAfterMillis = 5 * 60000) {
    const entries = [...ledger.entries]
        .filter(entry => !workspaceId || entry.occurrence.workspaceId === workspaceId)
        .sort((a, b) => b.updatedAtEpochMillis - a.updatedAtEpochMillis);
    const routeAudits = entries.map(entry => entry.routeAudit).filter((value) => !!value).sort((a, b) => b.decidedAtEpochMillis - a.decidedAtEpochMillis);
    const delays = entries.flatMap(entry => { var _a; return entry.deliveredAtEpochMillis == null ? [] : [Math.max(0, entry.deliveredAtEpochMillis - ((_a = entry.occurrence.originalScheduledAtEpochMillis) !== null && _a !== void 0 ? _a : entry.occurrence.scheduledAtEpochMillis))]; });
    const outcome = (entry, value) => { var _a; return ((_a = entry.policyAudit) === null || _a === void 0 ? void 0 : _a.outcome) === value; };
    const analytics = {
        totalCount: entries.length,
        activeCount: entries.filter(entry => ACTIVE_STATES.has(entry.state)).length,
        deliveredCount: entries.filter(entry => DELIVERED_STATES.has(entry.state)).length,
        missedCount: entries.filter(entry => entry.state === "missed").length,
        deferredCount: entries.filter(entry => outcome(entry, "defer_until_quiet_end")).length,
        snoozedCount: entries.filter(entry => entry.state === "snoozed").length,
        dismissedCount: entries.filter(entry => entry.state === "dismissed").length,
        doneCount: entries.filter(entry => entry.state === "completed").length,
        silentCount: entries.filter(entry => outcome(entry, "silent_delivery")).length,
        compactOnlyCount: entries.filter(entry => outcome(entry, "compact_only")).length,
        routedToPhoneCount: entries.filter(entry => outcome(entry, "route_to_phone")).length,
        escalatedMirrorCount: entries.filter(entry => outcome(entry, "escalate_mirror")).length,
        duplicateSuppressedCount: entries.filter(entry => outcome(entry, "suppress_duplicate")).length,
        userActionRequiredCount: entries.filter(entry => outcome(entry, "require_user_action")).length,
        deliveryAttemptCount: entries.reduce((sum, entry) => sum + entry.deliveryAttempts, 0),
        auditedRouteCount: routeAudits.length,
        averageDeliveryDelayMillis: delays.length ? Math.floor(delays.reduce((sum, value) => sum + value, 0) / delays.length) : null,
    };
    const categoryAnalytics = CATEGORIES.flatMap(category => {
        const selected = entries.filter(entry => entry.occurrence.category === category);
        return selected.length ? [{
                category,
                totalCount: selected.length,
                deliveredCount: selected.filter(entry => DELIVERED_STATES.has(entry.state)).length,
                missedCount: selected.filter(entry => entry.state === "missed").length,
                doneCount: selected.filter(entry => entry.state === "completed").length,
            }] : [];
    });
    const platforms = devices.map(device => platformDiagnostic(device, entries, routeAudits, nowEpochMillis, staleAfterMillis));
    return { generatedAtEpochMillis: nowEpochMillis, workspaceId, analytics, categoryAnalytics, platforms, evidence: entries, routeAudits };
}
function platformDiagnostic(device, entries, audits, nowEpochMillis, staleAfterMillis) {
    const stale = device.lastSeenAtEpochMillis != null && nowEpochMillis - device.lastSeenAtEpochMillis > staleAfterMillis;
    const tv = device.clientKind === "android_tv" || device.clientKind === "web_tv";
    const background = device.clientKind === "android"
        ? !!device.notificationPermissionGranted && (device.deliverySurfaces.includes("android_alarm") || device.deliverySurfaces.includes("system_notification"))
        : device.clientKind === "web"
            ? !!device.notificationPermissionGranted && device.deliverySurfaces.includes("web_push")
            : false;
    const status = device.availability === "offline" || stale
        ? "offline"
        : tv
            ? "foreground_only"
            : background
                ? "ready"
                : device.clientKind === "web"
                    ? "foreground_only"
                    : device.deliverySurfaces.includes("widget_or_badge") || device.deliverySurfaces.includes("desktop_tray")
                        ? "degraded"
                        : !device.notificationPermissionGranted
                            ? "blocked"
                            : "degraded";
    const explanation = status === "ready"
        ? "A durable supported delivery surface is currently available."
        : status === "foreground_only"
            ? tv ? "TV attention is foreground-only; closed-TV delivery requires a capable phone, desktop, or Web owner." : "Browser attention is foreground-only unless an actual Web Push subscription is available."
            : status === "offline"
                ? "The device is offline or stale and is not a reliable current delivery owner."
                : status === "blocked"
                    ? "The platform cannot currently surface a normal notification; ATT01 ledger evidence is still retained."
                    : "Attention can still be represented, but delivery depends on an in-app, tray, badge, or other limited surface.";
    return {
        deviceId: device.deviceId,
        clientKind: device.clientKind,
        status,
        availability: device.availability,
        deliverySurfaces: [...device.deliverySurfaces],
        notificationPermissionGranted: !!device.notificationPermissionGranted,
        truthfulBackgroundDelivery: background,
        ownerRouteCount: audits.filter(audit => { var _a; return ((_a = audit.owner) === null || _a === void 0 ? void 0 : _a.deviceId) === device.deviceId; }).length,
        mirrorRouteCount: audits.filter(audit => audit.mirrors.some(mirror => mirror.deviceId === device.deviceId)).length,
        targetedEntryCount: entries.filter(entry => { var _a; return entry.targetDeviceId === device.deviceId || ((_a = entry.occurrence.targetDeviceIds) !== null && _a !== void 0 ? _a : []).includes(device.deviceId); }).length,
        lastSeenAtEpochMillis: device.lastSeenAtEpochMillis,
        explanation,
    };
}
function exportWebAttentionReliabilityJson(snapshot) {
    return JSON.stringify(snapshot, null, 2);
}
function exportWebAttentionReliabilityCsv(snapshot) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l;
    const cell = (value) => {
        const text = value == null ? "" : String(value);
        return /[",\n\r]/.test(text) ? `"${text.split('"').join('""')}"` : text;
    };
    const rows = [["occurrence_id", "workspace_id", "category", "state", "policy_outcome", "route_reason", "owner_device", "mirror_devices", "scheduled_at", "updated_at", "delivered_at", "delivery_attempts"]];
    for (const entry of snapshot.evidence)
        rows.push([
            entry.occurrence.occurrenceId,
            entry.occurrence.workspaceId,
            entry.occurrence.category,
            entry.state,
            (_b = (_a = entry.policyAudit) === null || _a === void 0 ? void 0 : _a.outcome) !== null && _b !== void 0 ? _b : "",
            (_d = (_c = entry.routeAudit) === null || _c === void 0 ? void 0 : _c.reason) !== null && _d !== void 0 ? _d : "",
            (_g = (_f = (_e = entry.routeAudit) === null || _e === void 0 ? void 0 : _e.owner) === null || _f === void 0 ? void 0 : _f.deviceId) !== null && _g !== void 0 ? _g : "",
            (_k = (_j = (_h = entry.routeAudit) === null || _h === void 0 ? void 0 : _h.mirrors) === null || _j === void 0 ? void 0 : _j.map(value => value.deviceId).join("|")) !== null && _k !== void 0 ? _k : "",
            entry.occurrence.scheduledAtEpochMillis,
            entry.updatedAtEpochMillis,
            (_l = entry.deliveredAtEpochMillis) !== null && _l !== void 0 ? _l : "",
            entry.deliveryAttempts,
        ]);
    return rows.map(row => row.map(cell).join(",")).join("\n") + "\n";
}
