"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StudyFlowWebAttentionRuntime = exports.StudyFlowWebPushRegistry = void 0;
exports.defaultWebAttentionPreferences = defaultWebAttentionPreferences;
exports.stableWebAttentionOccurrenceId = stableWebAttentionOccurrenceId;
exports.describeStudyFlowWebOccurrence = describeStudyFlowWebOccurrence;
exports.createBrowserAttentionEnvironment = createBrowserAttentionEnvironment;
exports.installStudyFlowWebAttentionBridge = installStudyFlowWebAttentionBridge;
const sflp_link_1 = require("./sflp_link");
const pwa_1 = require("./pwa");
const attention_router_1 = require("./attention_router");
const WEB_ATTENTION_PREFERENCES_KEY = "studyflow.attention.web.preferences.v1";
const WEB_PUSH_SUBSCRIPTION_KEY = "studyflow.attention.web.push.v1";
const WEB_ATTENTION_SCAN_INTERVAL_MS = 30000;
const WEB_ATTENTION_HISTORY_LIMIT = 512;
const WEB_NOTIFICATION_MAX_ACTIONS = 2;
const MINUTE_MS = 60000;
const DAY_MS = 86400000;
const TERMINAL_STATES = new Set([
    "completed", "dismissed", "expired", "cancelled",
]);
const ACTIVE_DELIVERY_STATES = new Set([
    "planned", "scheduled", "delivery_pending",
]);
function defaultCategories() {
    return {
        planned_study: true,
        due_review: true,
        missed_activity: true,
        daily_summary: true,
        session_ending: true,
        session_completed: true,
        exam_countdown: true,
        schedule_change: true,
        plan_warning: true,
        sync_attention: true,
    };
}
function defaultWebAttentionPreferences() {
    return {
        version: 1,
        systemNotificationsEnabled: false,
        reminderLeadMinutes: 10,
        missedDelayMinutes: 15,
        sessionLeadMinutes: 5,
        dailySummaryMinute: 8 * 60,
        categories: defaultCategories(),
    };
}
function normalizePreferences(value) {
    var _a;
    const defaults = defaultWebAttentionPreferences();
    if (!value || typeof value !== "object")
        return defaults;
    const candidate = value;
    const clamp = (value, fallback, min, max) => {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? Math.max(min, Math.min(max, Math.round(parsed))) : fallback;
    };
    return {
        version: 1,
        systemNotificationsEnabled: candidate.systemNotificationsEnabled === true,
        reminderLeadMinutes: clamp(candidate.reminderLeadMinutes, defaults.reminderLeadMinutes, 0, 240),
        missedDelayMinutes: clamp(candidate.missedDelayMinutes, defaults.missedDelayMinutes, 0, 720),
        sessionLeadMinutes: clamp(candidate.sessionLeadMinutes, defaults.sessionLeadMinutes, 0, 60),
        dailySummaryMinute: clamp(candidate.dailySummaryMinute, defaults.dailySummaryMinute, 0, 1439),
        categories: { ...defaults.categories, ...((_a = candidate.categories) !== null && _a !== void 0 ? _a : {}) },
    };
}
function emptyLedger() {
    return { schemaVersion: 1, entries: [], lastSequenceBySource: {}, legacyDeliveredOccurrenceIds: [] };
}
function parseLedger(encoded) {
    if (!encoded)
        return emptyLedger();
    try {
        const parsed = JSON.parse(encoded);
        if (parsed.schemaVersion !== 1 || !Array.isArray(parsed.entries) || !parsed.lastSequenceBySource)
            return emptyLedger();
        return {
            schemaVersion: 1,
            entries: parsed.entries,
            lastSequenceBySource: parsed.lastSequenceBySource,
            legacyDeliveredOccurrenceIds: Array.isArray(parsed.legacyDeliveredOccurrenceIds) ? parsed.legacyDeliveredOccurrenceIds : [],
        };
    }
    catch {
        return emptyLedger();
    }
}
function stableWebAttentionOccurrenceId(parts) {
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
function localDateAtMinute(dateKey, minute) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey))
        return null;
    const [year, month, day] = dateKey.split("-").map(Number);
    const date = new Date(year, month - 1, day, Math.floor(minute / 60), minute % 60, 0, 0);
    const value = date.getTime();
    return Number.isFinite(value) ? value : null;
}
function localDateKeyAt(epochMillis) {
    const date = new Date(epochMillis);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
function routeHashForCategory(category) {
    if (category === "due_review")
        return "#practice";
    if (category === "exam_countdown")
        return "#progress";
    if (category === "schedule_change")
        return "#plan";
    return "#today";
}
function describeStudyFlowWebOccurrence(occurrence, state) {
    var _a, _b, _c, _d, _e, _f, _g;
    const payload = (_a = occurrence.payload) !== null && _a !== void 0 ? _a : {};
    const titleOverride = typeof payload.title === "string" ? payload.title : null;
    const bodyOverride = typeof payload.body === "string" ? payload.body : null;
    const activityId = typeof payload.activityId === "string" ? payload.activityId : occurrence.scheduleEntityId;
    const reviewId = typeof payload.reviewId === "string" ? payload.reviewId : undefined;
    const activity = activityId ? state.activities.find(value => value.id === activityId) : undefined;
    const review = reviewId ? state.reviews.find(value => value.id === reviewId) : undefined;
    const subjectId = (_c = (_b = activity === null || activity === void 0 ? void 0 : activity.subjectId) !== null && _b !== void 0 ? _b : review === null || review === void 0 ? void 0 : review.subjectId) !== null && _c !== void 0 ? _c : (typeof payload.subjectId === "string" ? payload.subjectId : null);
    const subject = subjectId ? (_d = state.subjects.find(value => value.id === subjectId)) === null || _d === void 0 ? void 0 : _d.name : null;
    const routeHash = typeof payload.routeHash === "string" ? payload.routeHash : routeHashForCategory(occurrence.category);
    if (titleOverride || bodyOverride)
        return { title: titleOverride !== null && titleOverride !== void 0 ? titleOverride : "StudyFlow", body: bodyOverride !== null && bodyOverride !== void 0 ? bodyOverride : "StudyFlow needs your attention.", routeHash };
    switch (occurrence.category) {
        case "planned_study": return { title: (_e = activity === null || activity === void 0 ? void 0 : activity.title) !== null && _e !== void 0 ? _e : "Study block coming up", body: subject ? `${subject} starts soon.` : "Your next study block starts soon.", routeHash };
        case "due_review": return { title: "Review due", body: subject ? `${subject} has a review ready.` : "A spaced review is ready.", routeHash };
        case "missed_activity": return { title: "Study block missed", body: (activity === null || activity === void 0 ? void 0 : activity.title) ? `${activity.title} is still unfinished.` : "A planned study block is still unfinished.", routeHash };
        case "daily_summary": return { title: "Today in StudyFlow", body: String((_f = payload.summary) !== null && _f !== void 0 ? _f : "Open Today to see your plan and reviews."), routeHash };
        case "session_ending": return { title: "Session ending soon", body: (activity === null || activity === void 0 ? void 0 : activity.title) ? `${activity.title} is almost complete.` : "Your study timer is almost complete.", routeHash };
        case "session_completed": return { title: "Session complete", body: (activity === null || activity === void 0 ? void 0 : activity.title) ? `${activity.title} finished.` : "Your study session is complete.", routeHash };
        case "exam_countdown": return { title: "Exam countdown", body: String((_g = payload.summary) !== null && _g !== void 0 ? _g : "Your exam is getting closer."), routeHash };
        case "schedule_change": return { title: "Schedule updated", body: "Your StudyFlow schedule changed.", routeHash };
        case "plan_warning": return { title: "Study plan needs attention", body: "Open StudyFlow to review your plan.", routeHash };
        case "sync_attention": return { title: "StudyFlow", body: "A connected StudyFlow device sent an update.", routeHash };
    }
}
function actionKindsForOccurrence(occurrence) {
    var _a, _b;
    const explicit = (_b = (_a = occurrence.actions) === null || _a === void 0 ? void 0 : _a.map(value => value.kind)) !== null && _b !== void 0 ? _b : [];
    const candidates = explicit.length ? explicit : occurrence.category === "due_review"
        ? ["review_now", "snooze"]
        : occurrence.category === "planned_study"
            ? ["start_now", "snooze"]
            : ["open", "dismiss"];
    return candidates.slice(0, WEB_NOTIFICATION_MAX_ACTIONS);
}
function actionLabel(kind) {
    switch (kind) {
        case "review_now": return "Review";
        case "start_now": return "Start";
        case "snooze": return "Snooze";
        case "reschedule": return "Reschedule";
        case "mark_done": return "Done";
        case "dismiss": return "Dismiss";
        case "continue": return "Continue";
        default: return "Open";
    }
}
function base64UrlToBytes(value) {
    const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
    const binary = globalThis.atob(padded);
    return Uint8Array.from(binary, char => char.charCodeAt(0));
}
class StudyFlowWebPushRegistry {
    constructor(storage, environment) {
        this.storage = storage;
        this.environment = environment;
    }
    savedSubscription() {
        var _a, _b;
        try {
            const encoded = (_b = (_a = this.storage) === null || _a === void 0 ? void 0 : _a.getItem(WEB_PUSH_SUBSCRIPTION_KEY)) !== null && _b !== void 0 ? _b : null;
            if (!encoded)
                return null;
            const value = JSON.parse(encoded);
            return typeof value.endpoint === "string" ? value : null;
        }
        catch {
            return null;
        }
    }
    async subscribe(applicationServerKey) {
        var _a, _b;
        if (!applicationServerKey.trim())
            throw new Error("A Web Push application-server key is required.");
        const manager = await this.environment.pushManager();
        if (!manager)
            throw new Error("Web Push is unavailable in this browser context.");
        const subscription = await manager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(applicationServerKey.trim()) });
        const json = subscription.toJSON();
        const record = {
            endpoint: subscription.endpoint,
            expirationTime: subscription.expirationTime,
            keys: { ...((_a = json.keys) !== null && _a !== void 0 ? _a : {}) },
            savedAtEpochMillis: this.environment.now(),
        };
        try {
            (_b = this.storage) === null || _b === void 0 ? void 0 : _b.setItem(WEB_PUSH_SUBSCRIPTION_KEY, JSON.stringify(record));
        }
        catch { /* local fallback is capability-only */ }
        return record;
    }
    async unsubscribe() {
        var _a, _b;
        const manager = await this.environment.pushManager();
        const subscription = (_a = await (manager === null || manager === void 0 ? void 0 : manager.getSubscription())) !== null && _a !== void 0 ? _a : null;
        const result = subscription ? await subscription.unsubscribe() : true;
        if (result) {
            try {
                (_b = this.storage) === null || _b === void 0 ? void 0 : _b.removeItem(WEB_PUSH_SUBSCRIPTION_KEY);
            }
            catch { /* ignore */ }
        }
        return result;
    }
}
exports.StudyFlowWebPushRegistry = StudyFlowWebPushRegistry;
function createBrowserAttentionEnvironment() {
    const navigatorObject = globalThis.navigator;
    return {
        now: () => Date.now(),
        notificationPermission: () => (0, pwa_1.notificationPermissionState)(),
        requestNotificationPermission: () => (0, pwa_1.requestStudyFlowNotificationPermission)(),
        capabilities: () => (0, pwa_1.studyFlowPwaCapabilities)(),
        showNotification: (title, options) => (0, pwa_1.showStudyFlowBrowserNotification)(title, options),
        async setBadge(count) {
            const nav = navigatorObject;
            if (typeof (nav === null || nav === void 0 ? void 0 : nav.setAppBadge) === "function")
                await nav.setAppBadge(count);
        },
        async clearBadge() {
            const nav = navigatorObject;
            if (typeof (nav === null || nav === void 0 ? void 0 : nav.clearAppBadge) === "function")
                await nav.clearAppBadge();
        },
        openRoute(routeHash) {
            if (typeof globalThis.location !== "undefined")
                globalThis.location.hash = routeHash.replace(/^#/, "");
        },
        listenServiceWorkerMessages(listener) {
            if (!(navigatorObject === null || navigatorObject === void 0 ? void 0 : navigatorObject.serviceWorker))
                return () => undefined;
            const handler = (event) => listener(event.data);
            navigatorObject.serviceWorker.addEventListener("message", handler);
            return () => navigatorObject.serviceWorker.removeEventListener("message", handler);
        },
        async requestServiceWorkerDrain() {
            var _a;
            const registration = await (0, pwa_1.studyFlowServiceWorkerReady)();
            (_a = registration === null || registration === void 0 ? void 0 : registration.active) === null || _a === void 0 ? void 0 : _a.postMessage({ type: "studyflow:attention-drain" });
        },
        async pushManager() {
            var _a;
            const registration = await (0, pwa_1.studyFlowServiceWorkerReady)();
            return (_a = registration === null || registration === void 0 ? void 0 : registration.pushManager) !== null && _a !== void 0 ? _a : null;
        },
    };
}
class StudyFlowWebAttentionRuntime {
    constructor(store, peer, options = {}) {
        var _a, _b;
        this.store = store;
        this.peer = peer;
        this.running = false;
        this.timer = null;
        this.unsubscribeStore = null;
        this.unsubscribeServiceWorker = null;
        this.listeners = new Set();
        this.lastScanAtEpochMillis = null;
        this.lastSessionIds = new Set();
        this.environment = (_a = options.environment) !== null && _a !== void 0 ? _a : createBrowserAttentionEnvironment();
        this.storage = options.storage === undefined ? ((_b = globalThis.localStorage) !== null && _b !== void 0 ? _b : null) : options.storage;
        this.ledgerStorage = new sflp_link_1.StudyFlowBrowserAttentionLedgerStorage(`studyflow.attention.v1.${peer.identity.deviceId}`);
        this.pushRegistry = new StudyFlowWebPushRegistry(this.storage, this.environment);
        this.preferences = this.loadPreferences();
        this.lastSessionIds = new Set(this.store.getState().sessions.map(value => value.id));
        if (options.autoStart !== false)
            this.start();
    }
    start() {
        if (this.running)
            return;
        this.running = true;
        this.unsubscribeStore = this.store.subscribe(() => { void this.scan(); });
        this.unsubscribeServiceWorker = this.environment.listenServiceWorkerMessages(message => { void this.handleServiceWorkerMessage(message); });
        this.timer = setInterval(() => { void this.scan(); }, WEB_ATTENTION_SCAN_INTERVAL_MS);
        void this.environment.requestServiceWorkerDrain();
        void this.scan();
    }
    stop() {
        var _a, _b;
        this.running = false;
        if (this.timer)
            clearInterval(this.timer);
        this.timer = null;
        (_a = this.unsubscribeStore) === null || _a === void 0 ? void 0 : _a.call(this);
        this.unsubscribeStore = null;
        (_b = this.unsubscribeServiceWorker) === null || _b === void 0 ? void 0 : _b.call(this);
        this.unsubscribeServiceWorker = null;
        this.emit();
    }
    subscribe(listener) {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }
    snapshot() {
        const ledger = this.readLedger();
        const now = this.environment.now();
        const entries = [...ledger.entries].sort((a, b) => b.updatedAtEpochMillis - a.updatedAtEpochMillis);
        const upcomingCount = entries.filter(entry => ACTIVE_DELIVERY_STATES.has(entry.state) && entry.occurrence.scheduledAtEpochMillis > now).length;
        const recentCount = entries.filter(entry => !ACTIVE_DELIVERY_STATES.has(entry.state) && entry.updatedAtEpochMillis >= now - 7 * DAY_MS).length;
        const badgeCount = entries.filter(entry => ["delivered", "acknowledged", "missed"].includes(entry.state) && !TERMINAL_STATES.has(entry.state)).length;
        const pushSubscription = this.pushRegistry.savedSubscription();
        const capabilities = this.environment.capabilities();
        return {
            permission: this.environment.notificationPermission(),
            preferences: this.preferences,
            capabilities,
            backgroundDelivery: pushSubscription ? "push-subscribed" : capabilities.push ? "push-ready" : "foreground-only",
            pushSubscription,
            entries,
            upcomingCount,
            recentCount,
            badgeCount,
            lastScanAtEpochMillis: this.lastScanAtEpochMillis,
            running: this.running,
            routeSummary: this.routeSummary(entries, now, capabilities, pushSubscription),
        };
    }
    routeSummary(entries, now, capabilities, pushSubscription) {
        const local = (0, attention_router_1.attentionDeviceFromWebPeer)(this.peer, {
            availability: this.running ? "foreground" : "online",
            foregroundActive: this.running,
            notificationPermissionGranted: this.environment.notificationPermission() === "granted",
            designatedNotifier: this.preferences.systemNotificationsEnabled || !!pushSubscription,
            primaryUserDevice: true,
            deliverySurfaces: [
                ...(pushSubscription || capabilities.push ? ["web_push"] : []),
                ...(capabilities.notifications ? ["system_notification"] : []),
                "widget_or_badge",
            ],
            lastSeenAtEpochMillis: now,
        });
        const activeEntries = entries.filter(entry => ACTIVE_DELIVERY_STATES.has(entry.state));
        const routes = activeEntries.map(entry => (0, attention_router_1.routeStudyFlowAttentionDelivery)(entry.occurrence, [local], now, {
            localDeviceId: this.peer.identity.deviceId,
            primaryDeviceId: this.peer.identity.deviceId,
            designatedNotifierDeviceIds: local.designatedNotifier ? [this.peer.identity.deviceId] : [],
        }, entry));
        return {
            localDeviceId: this.peer.identity.deviceId,
            deliverableCount: routes.filter(route => route.deliverable).length,
            ownedCount: routes.filter(route => { var _a; return ((_a = route.owner) === null || _a === void 0 ? void 0 : _a.deviceId) === this.peer.identity.deviceId; }).length,
            mirroredCount: routes.filter(route => route.mirrors.some(mirror => mirror.deviceId === this.peer.identity.deviceId)).length,
            suppressedCount: routes.filter(route => !route.deliverable).length,
            routes,
        };
    }
    async requestBrowserNotifications() {
        const permission = await this.environment.requestNotificationPermission();
        if (permission === "granted")
            this.setPreferences({ systemNotificationsEnabled: true });
        else if (permission === "denied")
            this.setPreferences({ systemNotificationsEnabled: false });
        this.emit();
        return permission;
    }
    setPreferences(input) {
        var _a;
        this.preferences = normalizePreferences({
            ...this.preferences,
            ...input,
            categories: { ...this.preferences.categories, ...((_a = input.categories) !== null && _a !== void 0 ? _a : {}) },
        });
        this.persistPreferences();
        void this.scan();
        this.emit();
        return this.preferences;
    }
    async subscribePush(applicationServerKey) {
        const record = await this.pushRegistry.subscribe(applicationServerKey);
        this.emit();
        return record;
    }
    async unsubscribePush() {
        const result = await this.pushRegistry.unsubscribe();
        this.emit();
        return result;
    }
    async ingestRemoteDelivery(delivery) {
        var _a, _b, _c;
        const mediaRuntime = new sflp_link_1.StudyFlowNotificationMediaRuntime(this.peer.identity, this.ledgerStorage);
        const result = mediaRuntime.applyNotificationDelivery(delivery);
        if (result.status !== "accepted")
            return result.status;
        const occurrence = (_a = delivery.notification.attentionOccurrence) !== null && _a !== void 0 ? _a : (_b = this.readLedger().entries.find(entry => entry.sequence === delivery.sequence && entry.sourceDeviceId === delivery.sourceDeviceId)) === null || _b === void 0 ? void 0 : _b.occurrence;
        if (occurrence) {
            await this.surfaceOccurrence(occurrence, delivery.notification.title, delivery.notification.body, ((_c = delivery.notification.route) === null || _c === void 0 ? void 0 : _c.workspaceId) ? routeHashForCategory(occurrence.category) : undefined, false);
        }
        this.emit();
        return result.status;
    }
    async applyAction(occurrenceId, action, snoozeMinutes = 10) {
        var _a, _b, _c, _d;
        const ledger = this.readLedger();
        const entry = ledger.entries.find(value => value.occurrence.occurrenceId === occurrenceId);
        if (!entry)
            return;
        const now = this.environment.now();
        if (action === "snooze") {
            this.transition(occurrenceId, "snoozed", now);
            const snoozed = {
                ...entry.occurrence,
                occurrenceId: stableWebAttentionOccurrenceId([entry.occurrence.occurrenceId, "snooze", String(now)]),
                scheduledAtEpochMillis: now + Math.max(1, snoozeMinutes) * MINUTE_MS,
                originalScheduledAtEpochMillis: (_a = entry.occurrence.originalScheduledAtEpochMillis) !== null && _a !== void 0 ? _a : entry.occurrence.scheduledAtEpochMillis,
                expiresAtEpochMillis: now + Math.max(1, snoozeMinutes) * MINUTE_MS + 4 * 60 * MINUTE_MS,
            };
            this.upsertOccurrence(snoozed, "scheduled", now);
        }
        else if (action === "dismiss") {
            this.transition(occurrenceId, "dismissed", now);
        }
        else if (action === "mark_done") {
            const activityId = typeof ((_b = entry.occurrence.payload) === null || _b === void 0 ? void 0 : _b.activityId) === "string" ? entry.occurrence.payload.activityId : entry.occurrence.scheduleEntityId;
            const reviewId = typeof ((_c = entry.occurrence.payload) === null || _c === void 0 ? void 0 : _c.reviewId) === "string" ? entry.occurrence.payload.reviewId : null;
            if (reviewId)
                this.store.completeReview(reviewId);
            else if (activityId) {
                const activity = this.store.getState().activities.find(value => value.id === activityId);
                if ((activity === null || activity === void 0 ? void 0 : activity.status) === "planned")
                    this.store.startFocus({ activityId, durationMinutes: activity.durationMinutes });
            }
            this.transition(occurrenceId, "completed", now);
        }
        else {
            this.transition(occurrenceId, action === "start_now" || action === "review_now" ? "started" : "acknowledged", now);
            const route = typeof ((_d = entry.occurrence.payload) === null || _d === void 0 ? void 0 : _d.routeHash) === "string" ? entry.occurrence.payload.routeHash : routeHashForCategory(entry.occurrence.category);
            this.environment.openRoute(route);
        }
        await this.updateBadge();
        this.emit();
    }
    async scan() {
        if (!this.running)
            return;
        const now = this.environment.now();
        const state = this.store.getState();
        for (const occurrence of this.planOccurrences(state, now))
            this.upsertOccurrence(occurrence, "scheduled", now);
        this.captureCompletedSessions(state, now);
        this.expireOldEntries(now);
        const ledger = this.readLedger();
        const due = ledger.entries
            .filter(entry => ACTIVE_DELIVERY_STATES.has(entry.state) && entry.occurrence.scheduledAtEpochMillis <= now)
            .filter(entry => !entry.occurrence.expiresAtEpochMillis || entry.occurrence.expiresAtEpochMillis >= now)
            .sort((a, b) => a.occurrence.scheduledAtEpochMillis - b.occurrence.scheduledAtEpochMillis);
        for (const entry of due)
            await this.surfaceOccurrence(entry.occurrence);
        this.lastScanAtEpochMillis = now;
        await this.updateBadge();
        this.emit();
    }
    planOccurrences(state, now) {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k;
        const output = [];
        const activePlan = (_a = state.plans.find(value => value.id === state.activePlanId)) !== null && _a !== void 0 ? _a : null;
        const horizon = now + 7 * DAY_MS;
        if (this.preferences.categories.planned_study) {
            for (const activity of state.activities.filter(value => value.status === "planned" && value.scheduledFor && value.scheduledStartMinute != null)) {
                const start = localDateAtMinute(activity.scheduledFor, activity.scheduledStartMinute);
                if (start == null || start < now - DAY_MS || start > horizon)
                    continue;
                const scheduled = start - this.preferences.reminderLeadMinutes * MINUTE_MS;
                output.push(this.activityOccurrence(activity, "planned_study", scheduled, start + 2 * 60 * MINUTE_MS));
            }
        }
        if (this.preferences.categories.missed_activity) {
            for (const activity of state.activities.filter(value => value.status === "planned" && value.scheduledFor && value.scheduledStartMinute != null)) {
                const start = localDateAtMinute(activity.scheduledFor, activity.scheduledStartMinute);
                if (start == null || start > now || start < now - DAY_MS)
                    continue;
                const scheduled = start + Math.max(activity.durationMinutes, this.preferences.missedDelayMinutes) * MINUTE_MS;
                output.push(this.activityOccurrence(activity, "missed_activity", scheduled, start + DAY_MS));
            }
        }
        if (this.preferences.categories.due_review) {
            for (const review of state.reviews.filter(value => value.status === "due")) {
                const dueAt = new Date(review.dueAt).getTime();
                if (!Number.isFinite(dueAt) || dueAt < now - 7 * DAY_MS || dueAt > horizon)
                    continue;
                output.push(this.reviewOccurrence(review, dueAt, dueAt + 7 * DAY_MS));
            }
        }
        if (this.preferences.categories.session_ending && state.focus.status === "running" && state.focus.endsAt) {
            const endsAt = new Date(state.focus.endsAt).getTime();
            if (Number.isFinite(endsAt)) {
                const scheduled = Math.max(now, endsAt - this.preferences.sessionLeadMinutes * MINUTE_MS);
                output.push({
                    occurrenceId: stableWebAttentionOccurrenceId(["web", "session-ending", (_c = (_b = state.focus.activityId) !== null && _b !== void 0 ? _b : state.focus.startedAt) !== null && _c !== void 0 ? _c : "focus", String(endsAt)]),
                    workspaceId: (_d = activePlan === null || activePlan === void 0 ? void 0 : activePlan.id) !== null && _d !== void 0 ? _d : "workspace-main",
                    scheduleEntityId: (_e = state.focus.activityId) !== null && _e !== void 0 ? _e : undefined,
                    category: "session_ending",
                    priority: "normal",
                    scheduledAtEpochMillis: scheduled,
                    originalScheduledAtEpochMillis: scheduled,
                    expiresAtEpochMillis: endsAt + 15 * MINUTE_MS,
                    deliveryPolicy: "active_then_primary",
                    sourceDeviceId: this.peer.identity.deviceId,
                    actions: [{ kind: "open" }, { kind: "continue" }],
                    payload: { activityId: state.focus.activityId, routeHash: "#today" },
                });
            }
        }
        if (this.preferences.categories.daily_summary) {
            const dateKey = localDateKeyAt(now);
            const scheduled = localDateAtMinute(dateKey, this.preferences.dailySummaryMinute);
            if (scheduled != null) {
                const planned = state.activities.filter(value => value.status === "planned" && value.scheduledFor === dateKey).length;
                const reviews = state.reviews.filter(value => value.status === "due" && new Date(value.dueAt).getTime() <= now + DAY_MS).length;
                output.push({
                    occurrenceId: stableWebAttentionOccurrenceId(["web", "daily-summary", (_f = activePlan === null || activePlan === void 0 ? void 0 : activePlan.id) !== null && _f !== void 0 ? _f : "workspace-main", dateKey]),
                    workspaceId: (_g = activePlan === null || activePlan === void 0 ? void 0 : activePlan.id) !== null && _g !== void 0 ? _g : "workspace-main",
                    category: "daily_summary",
                    priority: "low",
                    scheduledAtEpochMillis: scheduled,
                    originalScheduledAtEpochMillis: scheduled,
                    expiresAtEpochMillis: scheduled + 12 * 60 * MINUTE_MS,
                    deliveryPolicy: "active_then_primary",
                    sourceDeviceId: this.peer.identity.deviceId,
                    actions: [{ kind: "open" }],
                    payload: { summary: `${planned} planned block${planned === 1 ? "" : "s"} · ${reviews} review${reviews === 1 ? "" : "s"} due`, routeHash: "#today" },
                });
            }
        }
        if (this.preferences.categories.exam_countdown && activePlan) {
            const examDate = (_j = (_h = activePlan.configuration.publicExam) === null || _h === void 0 ? void 0 : _h.target.examDate) !== null && _j !== void 0 ? _j : activePlan.examDate;
            if (examDate && /^\d{4}-\d{2}-\d{2}$/.test(examDate)) {
                const examStart = localDateAtMinute(examDate, 9 * 60);
                const todayStart = localDateAtMinute(localDateKeyAt(now), 0);
                if (examStart != null && todayStart != null) {
                    const days = Math.ceil((examStart - todayStart) / DAY_MS);
                    if ([30, 14, 7, 3, 1, 0].includes(days)) {
                        const scheduled = (_k = localDateAtMinute(localDateKeyAt(now), 9 * 60)) !== null && _k !== void 0 ? _k : now;
                        output.push({
                            occurrenceId: stableWebAttentionOccurrenceId(["web", "exam-countdown", activePlan.id, localDateKeyAt(now)]),
                            workspaceId: activePlan.id,
                            category: "exam_countdown",
                            priority: days <= 1 ? "high" : "normal",
                            scheduledAtEpochMillis: scheduled,
                            originalScheduledAtEpochMillis: scheduled,
                            expiresAtEpochMillis: scheduled + 12 * 60 * MINUTE_MS,
                            deliveryPolicy: "active_then_primary",
                            sourceDeviceId: this.peer.identity.deviceId,
                            actions: [{ kind: "open" }],
                            payload: { summary: days === 0 ? "Exam day is today." : `${days} day${days === 1 ? "" : "s"} until ${activePlan.name}.`, routeHash: "#progress" },
                        });
                    }
                }
            }
        }
        return output;
    }
    activityOccurrence(activity, category, scheduledAt, expiresAt) {
        return {
            occurrenceId: stableWebAttentionOccurrenceId(["web", category, activity.id, String(scheduledAt)]),
            workspaceId: activity.planId,
            scheduleEntityId: activity.id,
            category,
            priority: category === "missed_activity" ? "high" : "normal",
            scheduledAtEpochMillis: scheduledAt,
            originalScheduledAtEpochMillis: scheduledAt,
            expiresAtEpochMillis: expiresAt,
            deliveryPolicy: "active_then_primary",
            sourceDeviceId: this.peer.identity.deviceId,
            actions: category === "planned_study"
                ? [{ kind: "start_now" }, { kind: "snooze", payload: { minutes: 10 } }]
                : [{ kind: "open" }, { kind: "dismiss" }],
            payload: { activityId: activity.id, subjectId: activity.subjectId, routeHash: "#today" },
        };
    }
    reviewOccurrence(review, scheduledAt, expiresAt) {
        return {
            occurrenceId: stableWebAttentionOccurrenceId(["web", "due-review", review.id, String(scheduledAt)]),
            workspaceId: review.planId,
            scheduleEntityId: review.id,
            category: "due_review",
            priority: scheduledAt < this.environment.now() - DAY_MS ? "high" : "normal",
            scheduledAtEpochMillis: scheduledAt,
            originalScheduledAtEpochMillis: scheduledAt,
            expiresAtEpochMillis: expiresAt,
            deliveryPolicy: "active_then_primary",
            sourceDeviceId: this.peer.identity.deviceId,
            actions: [{ kind: "review_now" }, { kind: "snooze", payload: { minutes: 10 } }],
            payload: { reviewId: review.id, subjectId: review.subjectId, routeHash: "#practice" },
        };
    }
    captureCompletedSessions(state, now) {
        var _a;
        const next = new Set(state.sessions.map(value => value.id));
        if (this.preferences.categories.session_completed) {
            for (const session of state.sessions) {
                if (this.lastSessionIds.has(session.id) || session.status !== "completed" || !session.endedAt)
                    continue;
                const endedAt = new Date(session.endedAt).getTime();
                if (!Number.isFinite(endedAt) || endedAt < now - 15 * MINUTE_MS)
                    continue;
                const occurrence = {
                    occurrenceId: stableWebAttentionOccurrenceId(["web", "session-completed", session.id]),
                    workspaceId: session.planId,
                    scheduleEntityId: (_a = session.activityId) !== null && _a !== void 0 ? _a : undefined,
                    category: "session_completed",
                    priority: "normal",
                    scheduledAtEpochMillis: endedAt,
                    originalScheduledAtEpochMillis: endedAt,
                    expiresAtEpochMillis: endedAt + 2 * 60 * MINUTE_MS,
                    deliveryPolicy: "active_then_primary",
                    sourceDeviceId: this.peer.identity.deviceId,
                    actions: [{ kind: "open" }, { kind: "dismiss" }],
                    payload: { activityId: session.activityId, routeHash: "#today" },
                };
                this.upsertOccurrence(occurrence, "scheduled", now);
            }
        }
        this.lastSessionIds = next;
    }
    async surfaceOccurrence(occurrence, title, body, routeHash, transition = true) {
        const now = this.environment.now();
        const state = this.store.getState();
        if (!this.preferences.categories[occurrence.category])
            return;
        if (transition)
            this.transition(occurrence.occurrenceId, occurrence.category === "missed_activity" ? "missed" : "delivered", now, 1);
        const presentation = describeStudyFlowWebOccurrence(occurrence, state);
        const resolvedTitle = title !== null && title !== void 0 ? title : presentation.title;
        const resolvedBody = body !== null && body !== void 0 ? body : presentation.body;
        const resolvedRoute = routeHash !== null && routeHash !== void 0 ? routeHash : presentation.routeHash;
        if (this.preferences.systemNotificationsEnabled && this.environment.notificationPermission() === "granted") {
            const actions = actionKindsForOccurrence(occurrence).map(action => ({ action, title: actionLabel(action) }));
            await this.environment.showNotification(resolvedTitle, {
                body: resolvedBody,
                tag: occurrence.occurrenceId,
                renotify: false,
                data: { occurrence, occurrenceId: occurrence.occurrenceId, routeHash: resolvedRoute },
                actions,
            });
        }
    }
    async handleServiceWorkerMessage(message) {
        var _a, _b, _c;
        if (!message || typeof message !== "object")
            return;
        const envelope = message;
        if (envelope.type !== "studyflow:attention-event" || !envelope.event)
            return;
        const event = envelope.event;
        if (event.kind === "push-delivered" && event.occurrence) {
            this.upsertOccurrence(event.occurrence, "delivered", (_a = event.occurredAtEpochMillis) !== null && _a !== void 0 ? _a : this.environment.now());
        }
        else if (event.kind === "notification-click" && event.occurrence) {
            this.upsertOccurrence(event.occurrence, "acknowledged", (_b = event.occurredAtEpochMillis) !== null && _b !== void 0 ? _b : this.environment.now());
            await this.applyAction(event.occurrence.occurrenceId, event.action || "open");
        }
        else if (event.kind === "notification-close" && event.occurrence) {
            this.upsertOccurrence(event.occurrence, "dismissed", (_c = event.occurredAtEpochMillis) !== null && _c !== void 0 ? _c : this.environment.now());
        }
        await this.updateBadge();
        this.emit();
    }
    upsertOccurrence(occurrence, state, now) {
        var _a, _b;
        const ledger = this.readLedger();
        const existing = ledger.entries.find(value => value.occurrence.occurrenceId === occurrence.occurrenceId);
        if (existing && TERMINAL_STATES.has(existing.state))
            return;
        if (existing && !ACTIVE_DELIVERY_STATES.has(existing.state) && ACTIVE_DELIVERY_STATES.has(state))
            return;
        const entry = {
            occurrence: existing ? { ...existing.occurrence, ...occurrence } : occurrence,
            state: existing && !ACTIVE_DELIVERY_STATES.has(existing.state) ? existing.state : state,
            updatedAtEpochMillis: now,
            deliveredAtEpochMillis: existing === null || existing === void 0 ? void 0 : existing.deliveredAtEpochMillis,
            acknowledgedAtEpochMillis: existing === null || existing === void 0 ? void 0 : existing.acknowledgedAtEpochMillis,
            sourceDeviceId: (_a = existing === null || existing === void 0 ? void 0 : existing.sourceDeviceId) !== null && _a !== void 0 ? _a : occurrence.sourceDeviceId,
            targetDeviceId: existing === null || existing === void 0 ? void 0 : existing.targetDeviceId,
            sequence: existing === null || existing === void 0 ? void 0 : existing.sequence,
            deliveryAttempts: (_b = existing === null || existing === void 0 ? void 0 : existing.deliveryAttempts) !== null && _b !== void 0 ? _b : 0,
        };
        this.writeLedger({ ...ledger, entries: [...ledger.entries.filter(value => value.occurrence.occurrenceId !== occurrence.occurrenceId), entry].slice(-WEB_ATTENTION_HISTORY_LIMIT) });
    }
    transition(occurrenceId, state, now, attemptDelta = 0) {
        var _a, _b;
        const ledger = this.readLedger();
        const existing = ledger.entries.find(value => value.occurrence.occurrenceId === occurrenceId);
        if (!existing)
            return;
        const entry = {
            ...existing,
            state,
            updatedAtEpochMillis: now,
            deliveredAtEpochMillis: state === "delivered" || state === "missed" ? (_a = existing.deliveredAtEpochMillis) !== null && _a !== void 0 ? _a : now : existing.deliveredAtEpochMillis,
            acknowledgedAtEpochMillis: ["acknowledged", "started", "completed", "dismissed", "snoozed", "rescheduled"].includes(state) ? (_b = existing.acknowledgedAtEpochMillis) !== null && _b !== void 0 ? _b : now : existing.acknowledgedAtEpochMillis,
            deliveryAttempts: existing.deliveryAttempts + attemptDelta,
        };
        this.writeLedger({ ...ledger, entries: [...ledger.entries.filter(value => value.occurrence.occurrenceId !== occurrenceId), entry].slice(-WEB_ATTENTION_HISTORY_LIMIT) });
    }
    expireOldEntries(now) {
        const ledger = this.readLedger();
        let changed = false;
        const entries = ledger.entries.map(entry => {
            if (!TERMINAL_STATES.has(entry.state) && entry.occurrence.expiresAtEpochMillis && entry.occurrence.expiresAtEpochMillis < now) {
                changed = true;
                return { ...entry, state: "expired", updatedAtEpochMillis: now };
            }
            return entry;
        });
        if (changed)
            this.writeLedger({ ...ledger, entries });
    }
    readLedger() {
        return parseLedger(this.ledgerStorage.readEncodedSnapshot());
    }
    writeLedger(snapshot) {
        this.ledgerStorage.writeEncodedSnapshot(JSON.stringify(snapshot));
    }
    loadPreferences() {
        var _a, _b;
        try {
            const encoded = (_b = (_a = this.storage) === null || _a === void 0 ? void 0 : _a.getItem(WEB_ATTENTION_PREFERENCES_KEY)) !== null && _b !== void 0 ? _b : null;
            return encoded ? normalizePreferences(JSON.parse(encoded)) : defaultWebAttentionPreferences();
        }
        catch {
            return defaultWebAttentionPreferences();
        }
    }
    persistPreferences() {
        var _a;
        try {
            (_a = this.storage) === null || _a === void 0 ? void 0 : _a.setItem(WEB_ATTENTION_PREFERENCES_KEY, JSON.stringify(this.preferences));
        }
        catch { /* browser storage may be unavailable */ }
    }
    async updateBadge() {
        const count = this.snapshot().badgeCount;
        try {
            if (count > 0)
                await this.environment.setBadge(count);
            else
                await this.environment.clearBadge();
        }
        catch { /* progressive enhancement only */ }
    }
    emit() { for (const listener of this.listeners)
        listener(); }
}
exports.StudyFlowWebAttentionRuntime = StudyFlowWebAttentionRuntime;
function installStudyFlowWebAttentionBridge(runtime) {
    var _a;
    const target = globalThis;
    target.studyFlowWebAttentionRuntime = runtime;
    target.studyFlowApplyNotificationDelivery = delivery => runtime.ingestRemoteDelivery(delivery);
    const onDelivery = (event) => {
        const delivery = event.detail;
        if (delivery)
            void runtime.ingestRemoteDelivery(delivery);
    };
    (_a = globalThis.addEventListener) === null || _a === void 0 ? void 0 : _a.call(globalThis, "studyflow:sflp-notification-delivery", onDelivery);
    return {
        dispose() {
            var _a;
            (_a = globalThis.removeEventListener) === null || _a === void 0 ? void 0 : _a.call(globalThis, "studyflow:sflp-notification-delivery", onDelivery);
            if (target.studyFlowWebAttentionRuntime === runtime)
                delete target.studyFlowWebAttentionRuntime;
            delete target.studyFlowApplyNotificationDelivery;
        },
    };
}
