"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultAttentionSurfacesForClient = defaultAttentionSurfacesForClient;
exports.attentionDeviceFromWebPeer = attentionDeviceFromWebPeer;
exports.routeStudyFlowAttentionDelivery = routeStudyFlowAttentionDelivery;
exports.routeStudyFlowAttentionDeliveryWithPolicy = routeStudyFlowAttentionDeliveryWithPolicy;
exports.createStudyFlowAttentionHandoffRequest = createStudyFlowAttentionHandoffRequest;
const attention_focus_policy_1 = require("./attention_focus_policy");
const DEFAULT_STALE_MS = 180000;
const DEFAULT_MIRRORS = 2;
const HANDLED_STATES = new Set([
    "delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled",
    "dismissed", "missed", "expired", "cancelled",
]);
function defaultAttentionSurfacesForClient(kind) {
    switch (kind) {
        case "android": return ["android_alarm", "system_notification", "widget_or_badge"];
        case "desktop": return ["system_notification", "desktop_tray"];
        case "web": return ["web_push", "system_notification", "widget_or_badge"];
        case "android_tv":
        case "web_tv": return ["foreground_banner", "ledger_only"];
    }
}
function attentionDeviceFromWebPeer(peer, input = {}) {
    var _a, _b;
    return {
        deviceId: peer.identity.deviceId,
        displayName: peer.identity.displayName,
        clientKind: peer.identity.clientKind,
        availability: (_a = input.availability) !== null && _a !== void 0 ? _a : (input.foregroundActive ? "foreground" : "online"),
        deliverySurfaces: (_b = input.deliverySurfaces) !== null && _b !== void 0 ? _b : defaultAttentionSurfacesForClient(peer.identity.clientKind),
        foregroundActive: input.foregroundActive,
        notificationPermissionGranted: input.notificationPermissionGranted,
        primaryUserDevice: input.primaryUserDevice,
        designatedNotifier: input.designatedNotifier,
        batteryConstrained: input.batteryConstrained,
        lastSeenAtEpochMillis: input.lastSeenAtEpochMillis,
        mutedUntilEpochMillis: input.mutedUntilEpochMillis,
    };
}
function routeStudyFlowAttentionDelivery(occurrence, devices, nowEpochMillis, context = {}, ledgerEntry) {
    var _a, _b, _c, _d;
    if (new Set(devices.map(device => device.deviceId)).size !== devices.length) {
        throw new Error("Attention router devices must be unique");
    }
    if (ledgerEntry && HANDLED_STATES.has(ledgerEntry.state)) {
        return noRoute(occurrence, nowEpochMillis, devices.length, "ledger_already_handled", {
            explanation: `Already handled by ${(_a = ledgerEntry.targetDeviceId) !== null && _a !== void 0 ? _a : "ledger"}.`,
            ledgerState: ledgerEntry.state,
        });
    }
    const normalized = normalizeContext(context);
    const available = devices.filter(device => canOwn(device, occurrence, nowEpochMillis, normalized));
    const targetDeviceIds = (_b = occurrence.targetDeviceIds) !== null && _b !== void 0 ? _b : [];
    if (targetDeviceIds.length) {
        return chooseRoute(occurrence, devices, available.filter(device => targetDeviceIds.includes(device.deviceId)), [], nowEpochMillis, normalized, "explicit_target", "Explicit target device list controls ATT07 ownership.");
    }
    switch ((_c = occurrence.deliveryPolicy) !== null && _c !== void 0 ? _c : "active_then_primary") {
        case "local_only": {
            const localId = (_d = normalized.localDeviceId) !== null && _d !== void 0 ? _d : occurrence.sourceDeviceId;
            return chooseRoute(occurrence, devices, available.filter(device => device.deviceId === localId), [], nowEpochMillis, normalized, "local_only", "Local-only reminders never mirror to another StudyFlow client.");
        }
        case "primary_device": {
            const primary = available.filter(device => device.deviceId === normalized.primaryDeviceId);
            const chosen = primary.length ? primary : preferredFallback(available, normalized);
            return chooseRoute(occurrence, devices, chosen, [], nowEpochMillis, normalized, primary.length ? "primary_device" : "fallback_best_capable", "Primary-device routing falls back only when the primary cannot deliver.");
        }
        case "phone_and_desktop": {
            const personal = available.filter(device => device.clientKind === "android" || device.clientKind === "desktop");
            return chooseRoute(occurrence, devices, personal.length ? personal : preferredFallback(available, normalized), personal, nowEpochMillis, normalized, "phone_desktop_pair", "Phone/desktop policy owns once and mirrors to the other capable personal device.");
        }
        case "all_capable":
            return chooseRoute(occurrence, devices, available, available, nowEpochMillis, normalized, "all_capable", "All capable policy routes one owner and bounded mirror surfaces.");
        case "active_then_primary":
        default: {
            const active = normalized.foregroundDeviceId
                ? available.filter(device => device.deviceId === normalized.foregroundDeviceId)
                : available.filter(device => device.foregroundActive || device.availability === "foreground");
            if (active.length) {
                return chooseRoute(occurrence, devices, active, available.filter(device => !active.some(value => value.deviceId === device.deviceId)), nowEpochMillis, normalized, "active_foreground", "The foreground StudyFlow client owns the reminder first.");
            }
            const primary = available.filter(device => device.deviceId === normalized.primaryDeviceId);
            const chosen = primary.length ? primary : preferredFallback(available, normalized);
            return chooseRoute(occurrence, devices, chosen, [], nowEpochMillis, normalized, primary.length ? "primary_device" : "fallback_best_capable", "No active foreground client was available; ATT07 used a durable notifier.");
        }
    }
}
function routeStudyFlowAttentionDeliveryWithPolicy(occurrence, devices, nowEpochMillis, focusPolicy, context = {}, ledgerEntry, localClientKind = "web") {
    var _a, _b;
    const policyDecision = (0, attention_focus_policy_1.evaluateWebAttentionFocusPolicy)(occurrence, focusPolicy, nowEpochMillis, ledgerEntry, localClientKind);
    if (policyDecision.outcome === "suppress_duplicate" || policyDecision.outcome === "defer_until_quiet_end") {
        const base = noRoute(occurrence, nowEpochMillis, devices.length, policyDecision.outcome === "suppress_duplicate" ? "ledger_already_handled" : "no_capable_device", {
            explanation: policyDecision.explanation,
            ledgerState: ledgerEntry === null || ledgerEntry === void 0 ? void 0 : ledgerEntry.state,
        });
        return {
            ...base,
            policyOutcome: policyDecision.outcome,
            diagnostics: { ...base.diagnostics, policyOutcome: policyDecision.outcome, deferredUntilEpochMillis: policyDecision.deferredUntilEpochMillis },
        };
    }
    let policyDevices = devices;
    if (policyDecision.outcome === "route_to_phone")
        policyDevices = devices.filter(device => device.clientKind === "android");
    if (policyDecision.outcome === "compact_only")
        policyDevices = devices
            .filter(device => device.deliverySurfaces.includes("widget_or_badge"));
    const normalizedContext = {
        ...context,
        maxMirrors: policyDecision.outcome === "escalate_mirror"
            ? Math.min((_a = context.maxMirrors) !== null && _a !== void 0 ? _a : 2, policyDecision.requestedMirrorCount)
            : context.maxMirrors,
    };
    const base = routeStudyFlowAttentionDelivery(occurrence, policyDevices, nowEpochMillis, normalizedContext, ledgerEntry);
    const owner = policyDecision.compactOnly && base.owner
        ? { ...base.owner, surface: "widget_or_badge" }
        : base.owner;
    let mirrors = policyDecision.compactOnly
        ? base.mirrors.map(mirror => ({ ...mirror, surface: "widget_or_badge" }))
        : base.mirrors;
    if (policyDecision.outcome === "escalate_mirror" && owner) {
        const normalized = normalizeContext(normalizedContext);
        const explicitTargets = new Set((_b = occurrence.targetDeviceIds) !== null && _b !== void 0 ? _b : []);
        const used = new Set([owner.deviceId, ...mirrors.map(mirror => mirror.deviceId)]);
        const additional = policyDevices
            .filter(device => device.deviceId !== owner.deviceId)
            .filter(device => explicitTargets.size === 0 || explicitTargets.has(device.deviceId))
            .filter(device => canOwn(device, occurrence, nowEpochMillis, normalized))
            .sort((a, b) => compareDevice(a, b, occurrence, nowEpochMillis, normalized))
            .filter(device => !used.has(device.deviceId) && used.add(device.deviceId))
            .slice(0, Math.max(0, policyDecision.requestedMirrorCount - mirrors.length))
            .map(device => toTarget(device, occurrence, nowEpochMillis, normalized, "mirror"));
        mirrors = [...mirrors, ...additional];
    }
    const targetDeviceIds = [owner === null || owner === void 0 ? void 0 : owner.deviceId, ...mirrors.map(mirror => mirror.deviceId)]
        .filter((value) => !!value);
    return {
        ...base,
        owner,
        mirrors,
        deliverable: !!owner,
        targetDeviceIds,
        policyOutcome: policyDecision.outcome,
        deliverSilently: policyDecision.deliverSilently,
        compactOnly: policyDecision.compactOnly,
        requiresUserAction: policyDecision.requiresUserAction,
        diagnostics: {
            ...base.diagnostics,
            policyOutcome: policyDecision.outcome,
            deferredUntilEpochMillis: policyDecision.deferredUntilEpochMillis,
            explanation: `${base.diagnostics.explanation} ${policyDecision.explanation}`,
        },
    };
}
function createStudyFlowAttentionHandoffRequest(occurrence, fromDeviceId, toDeviceId, requestedAtEpochMillis, action = "move_to_device") {
    if (fromDeviceId === toDeviceId)
        throw new Error("Attention handoff requires two distinct devices");
    return {
        handoffId: stableId(["handoff", occurrence.occurrenceId, fromDeviceId, toDeviceId, String(requestedAtEpochMillis)]),
        occurrenceId: occurrence.occurrenceId,
        workspaceId: occurrence.workspaceId,
        fromDeviceId,
        toDeviceId,
        action,
        requestedAtEpochMillis,
    };
}
function chooseRoute(occurrence, allCandidates, candidates, mirrorPool, nowEpochMillis, context, reason, explanation) {
    var _a;
    const ranked = [...candidates].sort((a, b) => compareDevice(a, b, occurrence, nowEpochMillis, context));
    const ownerDevice = ranked[0];
    if (!ownerDevice)
        return noRoute(occurrence, nowEpochMillis, allCandidates.length, "no_capable_device", { explanation });
    const owner = toTarget(ownerDevice, occurrence, nowEpochMillis, context, "owner");
    const used = new Set([owner.deviceId]);
    const mirrors = [...mirrorPool]
        .filter(device => device.deviceId !== owner.deviceId && canOwn(device, occurrence, nowEpochMillis, context))
        .sort((a, b) => compareDevice(a, b, occurrence, nowEpochMillis, context))
        .filter(device => !used.has(device.deviceId) && used.add(device.deviceId))
        .slice(0, context.maxMirrors)
        .map(device => toTarget(device, occurrence, nowEpochMillis, context, "mirror"));
    const eligible = new Set([...ranked, ...mirrorPool.filter(device => canOwn(device, occurrence, nowEpochMillis, context))].map(device => device.deviceId));
    return {
        occurrenceId: occurrence.occurrenceId,
        workspaceId: occurrence.workspaceId,
        policy: (_a = occurrence.deliveryPolicy) !== null && _a !== void 0 ? _a : "active_then_primary",
        reason,
        decidedAtEpochMillis: nowEpochMillis,
        owner,
        mirrors,
        diagnostics: {
            candidateCount: allCandidates.length,
            eligibleCount: eligible.size,
            suppressedDeviceIds: allCandidates.map(device => device.deviceId).filter(deviceId => !eligible.has(deviceId)),
            explanation,
        },
        deliverable: true,
        targetDeviceIds: [owner.deviceId, ...mirrors.map(mirror => mirror.deviceId)],
    };
}
function noRoute(occurrence, nowEpochMillis, candidateCount, reason, options) {
    var _a;
    return {
        occurrenceId: occurrence.occurrenceId,
        workspaceId: occurrence.workspaceId,
        policy: (_a = occurrence.deliveryPolicy) !== null && _a !== void 0 ? _a : "active_then_primary",
        reason,
        decidedAtEpochMillis: nowEpochMillis,
        owner: null,
        mirrors: [],
        diagnostics: {
            candidateCount,
            eligibleCount: 0,
            suppressedDeviceIds: [],
            ledgerState: options.ledgerState,
            explanation: options.explanation,
        },
        deliverable: false,
        targetDeviceIds: [],
    };
}
function normalizeContext(context) {
    var _a, _b, _c, _d, _e, _f, _g;
    return {
        localDeviceId: (_a = context.localDeviceId) !== null && _a !== void 0 ? _a : "",
        primaryDeviceId: (_b = context.primaryDeviceId) !== null && _b !== void 0 ? _b : "",
        foregroundDeviceId: (_c = context.foregroundDeviceId) !== null && _c !== void 0 ? _c : "",
        designatedNotifierDeviceIds: (_d = context.designatedNotifierDeviceIds) !== null && _d !== void 0 ? _d : [],
        mutedDeviceIds: (_e = context.mutedDeviceIds) !== null && _e !== void 0 ? _e : [],
        maxMirrors: Math.max(0, Math.min(8, (_f = context.maxMirrors) !== null && _f !== void 0 ? _f : DEFAULT_MIRRORS)),
        staleAfterMillis: Math.max(1, (_g = context.staleAfterMillis) !== null && _g !== void 0 ? _g : DEFAULT_STALE_MS),
        allowTvForegroundOwners: context.allowTvForegroundOwners !== false,
    };
}
function bestSurface(device, now, context) {
    if (device.availability === "offline")
        return null;
    if (context.mutedDeviceIds.includes(device.deviceId))
        return null;
    if (device.mutedUntilEpochMillis && device.mutedUntilEpochMillis > now)
        return null;
    if (device.lastSeenAtEpochMillis != null && now - device.lastSeenAtEpochMillis > context.staleAfterMillis)
        return null;
    const surfaces = device.deliverySurfaces;
    if (surfaces.includes("android_alarm"))
        return "android_alarm";
    if (surfaces.includes("web_push") && device.notificationPermissionGranted !== false)
        return "web_push";
    if (surfaces.includes("system_notification") && device.notificationPermissionGranted !== false)
        return "system_notification";
    if (surfaces.includes("desktop_tray") && device.foregroundActive)
        return "desktop_tray";
    if (surfaces.includes("foreground_banner") && device.foregroundActive)
        return "foreground_banner";
    if (surfaces.includes("widget_or_badge"))
        return "widget_or_badge";
    return null;
}
function canOwn(device, occurrence, now, context) {
    if (occurrence.expiresAtEpochMillis != null && now > occurrence.expiresAtEpochMillis)
        return false;
    const surface = bestSurface(device, now, context);
    if (!surface)
        return false;
    if (!context.allowTvForegroundOwners && isTv(device.clientKind) && surface === "foreground_banner")
        return false;
    return true;
}
function score(device, occurrence, now, context) {
    var _a, _b;
    const surface = bestSurface(device, now, context);
    if (!surface)
        return Number.NEGATIVE_INFINITY;
    let value = surfaceScore(surface) + clientScore(device.clientKind) + priorityScore((_a = occurrence.priority) !== null && _a !== void 0 ? _a : "normal");
    if (device.deviceId === context.primaryDeviceId)
        value += 160;
    if (device.deviceId === context.localDeviceId)
        value += 25;
    if (device.foregroundActive || device.availability === "foreground")
        value += 90;
    if (device.designatedNotifier || context.designatedNotifierDeviceIds.includes(device.deviceId))
        value += 120;
    if (((_b = occurrence.targetDeviceIds) !== null && _b !== void 0 ? _b : []).includes(device.deviceId))
        value += 240;
    if (device.batteryConstrained)
        value -= 80;
    return value;
}
function surfaceScore(surface) {
    switch (surface) {
        case "android_alarm": return 1000;
        case "web_push": return 940;
        case "system_notification": return 900;
        case "desktop_tray": return 820;
        case "foreground_banner": return 700;
        case "widget_or_badge": return 220;
        case "ledger_only": return 0;
    }
}
function clientScore(kind) {
    switch (kind) {
        case "android": return 80;
        case "desktop": return 70;
        case "web": return 50;
        case "android_tv": return 20;
        case "web_tv": return 10;
    }
}
function priorityScore(priority) {
    switch (priority) {
        case "low": return -20;
        case "normal": return 0;
        case "high": return 40;
        case "time_sensitive": return 80;
    }
}
function compareDevice(a, b, occurrence, now, context) {
    var _a, _b;
    const delta = score(b, occurrence, now, context) - score(a, occurrence, now, context);
    if (delta !== 0)
        return delta;
    const seen = ((_a = b.lastSeenAtEpochMillis) !== null && _a !== void 0 ? _a : 0) - ((_b = a.lastSeenAtEpochMillis) !== null && _b !== void 0 ? _b : 0);
    if (seen !== 0)
        return seen;
    return a.deviceId.localeCompare(b.deviceId);
}
function toTarget(device, occurrence, now, context, role) {
    const surface = bestSurface(device, now, context);
    if (!surface)
        throw new Error("Eligible attention device lost its route surface");
    return {
        deviceId: device.deviceId,
        displayName: device.displayName,
        clientKind: device.clientKind,
        role,
        surface,
        score: score(device, occurrence, now, context),
    };
}
function preferredFallback(devices, context) {
    const designated = devices.filter(device => device.designatedNotifier || context.designatedNotifierDeviceIds.includes(device.deviceId));
    return designated.length ? designated : devices;
}
function isTv(kind) {
    return kind === "android_tv" || kind === "web_tv";
}
function stableId(parts) {
    const readable = parts.join(":").replace(/[^A-Za-z0-9._:-]/g, "-");
    if (readable.length > 0 && readable.length <= 128)
        return readable;
    let hash = 2166136261;
    for (const char of parts.join("\u001f")) {
        hash ^= char.charCodeAt(0);
        hash = Math.imul(hash, 16777619);
    }
    return `attn-${(hash >>> 0).toString(16).padStart(8, "0")}`;
}
