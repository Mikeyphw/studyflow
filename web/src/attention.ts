"use strict";

import type { Activity, Review, StudyState, StudyStore } from "./types";
import {
  StudyFlowBrowserAttentionLedgerStorage,
  StudyFlowNotificationMediaRuntime,
  type StudyFlowAttentionCategory,
  type StudyFlowAttentionDeliveryState,
  type StudyFlowAttentionLedgerEntry,
  type StudyFlowAttentionLedgerSnapshot,
  type StudyFlowAttentionOccurrence,
  type StudyFlowNotificationDeliveryEnvelope,
  type StudyFlowWebLinkPeer,
} from "./sflp_link";
import {
  notificationPermissionState,
  requestStudyFlowNotificationPermission,
  showStudyFlowBrowserNotification,
  studyFlowPwaCapabilities,
  studyFlowServiceWorkerReady,
  type StudyFlowPwaCapabilities,
} from "./pwa";
import {
  attentionDeviceFromWebPeer,
  attentionTopologyFromWebPeer,
  routeStudyFlowAttentionDeliveryWithPolicy,
  studyFlowAttentionRouteTargetsDevice,
  type StudyFlowAttentionDeliveryRoute,
  type StudyFlowAttentionDeviceCapability,
} from "./attention_router";
import {
  analyzeWebAttentionReliability,
  exportWebAttentionReliabilityCsv,
  exportWebAttentionReliabilityJson,
  routeAuditFromDeliveryRoute,
  type StudyFlowAttentionReliabilityEvidenceSnapshot,
} from "./attention_reliability";
import {
  defaultWebAttentionFocusPolicy,
  evaluateWebAttentionFocusPolicy,
  normalizeWebAttentionFocusPolicy,
  type StudyFlowAttentionFocusPolicy,
  type StudyFlowAttentionPolicyDecision,
} from "./attention_focus_policy";

const WEB_ATTENTION_PREFERENCES_KEY = "studyflow.attention.web.preferences.v1";
const WEB_PUSH_SUBSCRIPTION_KEY = "studyflow.attention.web.push.v1";
const WEB_ATTENTION_SCAN_INTERVAL_MS = 30_000;
const WEB_ATTENTION_HISTORY_LIMIT = 512;
const WEB_NOTIFICATION_MAX_ACTIONS = 2;
const MINUTE_MS = 60_000;
const DAY_MS = 86_400_000;

export type StudyFlowWebAttentionPermission = NotificationPermission | "unsupported";
export type StudyFlowWebBackgroundDelivery = "foreground-only" | "push-ready" | "push-subscribed";

export interface StudyFlowWebAttentionPreferences {
  version: 1;
  systemNotificationsEnabled: boolean;
  reminderLeadMinutes: number;
  missedDelayMinutes: number;
  sessionLeadMinutes: number;
  dailySummaryMinute: number;
  categories: Record<StudyFlowAttentionCategory, boolean>;
  focusPolicy: StudyFlowAttentionFocusPolicy;
}

export interface StudyFlowWebPushSubscriptionRecord {
  endpoint: string;
  expirationTime: number | null;
  keys: Record<string, string>;
  savedAtEpochMillis: number;
}

export interface StudyFlowWebAttentionRouteSummary {
  localDeviceId: string;
  deliverableCount: number;
  ownedCount: number;
  mirroredCount: number;
  suppressedCount: number;
  routes: StudyFlowAttentionDeliveryRoute[];
}

export interface StudyFlowWebAttentionSnapshot {
  permission: StudyFlowWebAttentionPermission;
  preferences: StudyFlowWebAttentionPreferences;
  capabilities: StudyFlowPwaCapabilities;
  backgroundDelivery: StudyFlowWebBackgroundDelivery;
  pushSubscription: StudyFlowWebPushSubscriptionRecord | null;
  entries: StudyFlowAttentionLedgerEntry[];
  upcomingCount: number;
  recentCount: number;
  badgeCount: number;
  lastScanAtEpochMillis: number | null;
  running: boolean;
  routeSummary: StudyFlowWebAttentionRouteSummary;
  reliability: StudyFlowAttentionReliabilityEvidenceSnapshot;
}

export interface StudyFlowWebAttentionEnvironment {
  now(): number;
  notificationPermission(): StudyFlowWebAttentionPermission;
  requestNotificationPermission(): Promise<StudyFlowWebAttentionPermission>;
  capabilities(): StudyFlowPwaCapabilities;
  showNotification(title: string, options: NotificationOptions): Promise<boolean>;
  setBadge(count: number): Promise<void>;
  clearBadge(): Promise<void>;
  openRoute(routeHash: string): void;
  listenServiceWorkerMessages(listener: (message: unknown) => void): () => void;
  requestServiceWorkerDrain(): Promise<void>;
  pushManager(): Promise<PushManager | null>;
}

export interface StudyFlowWebAttentionRuntimeOptions {
  environment?: StudyFlowWebAttentionEnvironment;
  storage?: Storage | null;
  autoStart?: boolean;
}

export interface StudyFlowWebAttentionServiceWorkerEvent {
  kind: "push-delivered" | "notification-click" | "notification-close";
  occurrence?: StudyFlowAttentionOccurrence;
  title?: string;
  body?: string;
  routeHash?: string;
  action?: string;
  occurredAtEpochMillis?: number;
}

const TERMINAL_STATES = new Set<StudyFlowAttentionDeliveryState>([
  "completed", "dismissed", "expired", "cancelled",
]);
const ACTIVE_DELIVERY_STATES = new Set<StudyFlowAttentionDeliveryState>([
  "planned", "scheduled", "delivery_pending",
]);

function defaultCategories(): Record<StudyFlowAttentionCategory, boolean> {
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

export function defaultWebAttentionPreferences(): StudyFlowWebAttentionPreferences {
  return {
    version: 1,
    systemNotificationsEnabled: false,
    reminderLeadMinutes: 10,
    missedDelayMinutes: 15,
    sessionLeadMinutes: 5,
    dailySummaryMinute: 8 * 60,
    categories: defaultCategories(),
    focusPolicy: defaultWebAttentionFocusPolicy(),
  };
}

function normalizePreferences(value: unknown): StudyFlowWebAttentionPreferences {
  const defaults = defaultWebAttentionPreferences();
  if (!value || typeof value !== "object") return defaults;
  const candidate = value as Partial<StudyFlowWebAttentionPreferences>;
  const clamp = (value: unknown, fallback: number, min: number, max: number) => {
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
    categories: { ...defaults.categories, ...(candidate.categories ?? {}) },
    focusPolicy: normalizeWebAttentionFocusPolicy(candidate.focusPolicy),
  };
}

function emptyLedger(): StudyFlowAttentionLedgerSnapshot {
  return { schemaVersion: 1, entries: [], lastSequenceBySource: {}, legacyDeliveredOccurrenceIds: [] };
}

function parseLedger(encoded: string | null): StudyFlowAttentionLedgerSnapshot {
  if (!encoded) return emptyLedger();
  try {
    const parsed = JSON.parse(encoded) as Partial<StudyFlowAttentionLedgerSnapshot>;
    if (parsed.schemaVersion !== 1 || !Array.isArray(parsed.entries) || !parsed.lastSequenceBySource) return emptyLedger();
    return {
      schemaVersion: 1,
      entries: parsed.entries as StudyFlowAttentionLedgerEntry[],
      lastSequenceBySource: parsed.lastSequenceBySource as Record<string, number>,
      legacyDeliveredOccurrenceIds: Array.isArray(parsed.legacyDeliveredOccurrenceIds) ? parsed.legacyDeliveredOccurrenceIds : [],
    };
  } catch { return emptyLedger(); }
}

export function stableWebAttentionOccurrenceId(parts: string[]): string {
  const readable = parts.join(":").replace(/[^A-Za-z0-9._:-]/g, "-");
  if (readable.length > 0 && readable.length <= 128) return readable;
  let hash = 2166136261;
  for (const char of parts.join("\u001f")) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return `attn-${(hash >>> 0).toString(16).padStart(8, "0")}`;
}

function localDateAtMinute(dateKey: string, minute: number): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) return null;
  const [year, month, day] = dateKey.split("-").map(Number);
  const date = new Date(year, month - 1, day, Math.floor(minute / 60), minute % 60, 0, 0);
  const value = date.getTime();
  return Number.isFinite(value) ? value : null;
}

function localDateKeyAt(epochMillis: number): string {
  const date = new Date(epochMillis);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function routeHashForCategory(category: StudyFlowAttentionCategory): string {
  if (category === "due_review") return "#practice";
  if (category === "exam_countdown") return "#progress";
  if (category === "schedule_change") return "#plan";
  return "#today";
}

export function describeStudyFlowWebOccurrence(
  occurrence: StudyFlowAttentionOccurrence,
  state: StudyState,
): { title: string; body: string; routeHash: string } {
  const payload = occurrence.payload ?? {};
  const titleOverride = typeof payload.title === "string" ? payload.title : null;
  const bodyOverride = typeof payload.body === "string" ? payload.body : null;
  const activityId = typeof payload.activityId === "string" ? payload.activityId : occurrence.scheduleEntityId;
  const reviewId = typeof payload.reviewId === "string" ? payload.reviewId : undefined;
  const activity = activityId ? state.activities.find(value => value.id === activityId) : undefined;
  const review = reviewId ? state.reviews.find(value => value.id === reviewId) : undefined;
  const subjectId = activity?.subjectId ?? review?.subjectId ?? (typeof payload.subjectId === "string" ? payload.subjectId : null);
  const subject = subjectId ? state.subjects.find(value => value.id === subjectId)?.name : null;
  const routeHash = typeof payload.routeHash === "string" ? payload.routeHash : routeHashForCategory(occurrence.category);
  if (titleOverride || bodyOverride) return { title: titleOverride ?? "StudyFlow", body: bodyOverride ?? "StudyFlow needs your attention.", routeHash };
  switch (occurrence.category) {
    case "planned_study": return { title: activity?.title ?? "Study block coming up", body: subject ? `${subject} starts soon.` : "Your next study block starts soon.", routeHash };
    case "due_review": return { title: "Review due", body: subject ? `${subject} has a review ready.` : "A spaced review is ready.", routeHash };
    case "missed_activity": return { title: "Study block missed", body: activity?.title ? `${activity.title} is still unfinished.` : "A planned study block is still unfinished.", routeHash };
    case "daily_summary": return { title: "Today in StudyFlow", body: String(payload.summary ?? "Open Today to see your plan and reviews."), routeHash };
    case "session_ending": return { title: "Session ending soon", body: activity?.title ? `${activity.title} is almost complete.` : "Your study timer is almost complete.", routeHash };
    case "session_completed": return { title: "Session complete", body: activity?.title ? `${activity.title} finished.` : "Your study session is complete.", routeHash };
    case "exam_countdown": return { title: "Exam countdown", body: String(payload.summary ?? "Your exam is getting closer."), routeHash };
    case "schedule_change": return { title: "Schedule updated", body: "Your StudyFlow schedule changed.", routeHash };
    case "plan_warning": return { title: "Study plan needs attention", body: "Open StudyFlow to review your plan.", routeHash };
    case "sync_attention": return { title: "StudyFlow", body: "A connected StudyFlow device sent an update.", routeHash };
  }
}

function actionKindsForOccurrence(occurrence: StudyFlowAttentionOccurrence): string[] {
  const explicit = occurrence.actions?.map(value => value.kind) ?? [];
  const candidates = explicit.length ? explicit : occurrence.category === "due_review"
    ? ["review_now", "snooze"]
    : occurrence.category === "planned_study"
      ? ["start_now", "snooze"]
      : ["open", "dismiss"];
  return candidates.slice(0, WEB_NOTIFICATION_MAX_ACTIONS);
}

function actionLabel(kind: string): string {
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

function base64UrlToBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = globalThis.atob(padded);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

export class StudyFlowWebPushRegistry {
  constructor(
    private readonly storage: Storage | null,
    private readonly environment: StudyFlowWebAttentionEnvironment,
  ) {}

  savedSubscription(): StudyFlowWebPushSubscriptionRecord | null {
    try {
      const encoded = this.storage?.getItem(WEB_PUSH_SUBSCRIPTION_KEY) ?? null;
      if (!encoded) return null;
      const value = JSON.parse(encoded) as StudyFlowWebPushSubscriptionRecord;
      return typeof value.endpoint === "string" ? value : null;
    } catch { return null; }
  }

  async subscribe(applicationServerKey: string): Promise<StudyFlowWebPushSubscriptionRecord> {
    if (!applicationServerKey.trim()) throw new Error("A Web Push application-server key is required.");
    const manager = await this.environment.pushManager();
    if (!manager) throw new Error("Web Push is unavailable in this browser context.");
    const subscription = await manager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(applicationServerKey.trim()) });
    const json = subscription.toJSON();
    const record: StudyFlowWebPushSubscriptionRecord = {
      endpoint: subscription.endpoint,
      expirationTime: subscription.expirationTime,
      keys: { ...(json.keys ?? {}) },
      savedAtEpochMillis: this.environment.now(),
    };
    try { this.storage?.setItem(WEB_PUSH_SUBSCRIPTION_KEY, JSON.stringify(record)); } catch { /* local fallback is capability-only */ }
    return record;
  }

  async unsubscribe(): Promise<boolean> {
    const manager = await this.environment.pushManager();
    const subscription = await manager?.getSubscription() ?? null;
    const result = subscription ? await subscription.unsubscribe() : true;
    if (result) {
      try { this.storage?.removeItem(WEB_PUSH_SUBSCRIPTION_KEY); } catch { /* ignore */ }
    }
    return result;
  }
}

export function createBrowserAttentionEnvironment(): StudyFlowWebAttentionEnvironment {
  const navigatorObject = globalThis.navigator;
  return {
    now: () => Date.now(),
    notificationPermission: () => notificationPermissionState(),
    requestNotificationPermission: () => requestStudyFlowNotificationPermission(),
    capabilities: () => studyFlowPwaCapabilities(),
    showNotification: (title, options) => showStudyFlowBrowserNotification(title, options),
    async setBadge(count) {
      const nav = navigatorObject as Navigator & { setAppBadge?: (value?: number) => Promise<void> };
      if (typeof nav?.setAppBadge === "function") await nav.setAppBadge(count);
    },
    async clearBadge() {
      const nav = navigatorObject as Navigator & { clearAppBadge?: () => Promise<void> };
      if (typeof nav?.clearAppBadge === "function") await nav.clearAppBadge();
    },
    openRoute(routeHash) {
      if (typeof globalThis.location !== "undefined") globalThis.location.hash = routeHash.replace(/^#/, "");
    },
    listenServiceWorkerMessages(listener) {
      if (!navigatorObject?.serviceWorker) return () => undefined;
      const handler = (event: MessageEvent) => listener(event.data);
      navigatorObject.serviceWorker.addEventListener("message", handler);
      return () => navigatorObject.serviceWorker.removeEventListener("message", handler);
    },
    async requestServiceWorkerDrain() {
      const registration = await studyFlowServiceWorkerReady();
      registration?.active?.postMessage({ type: "studyflow:attention-drain" });
    },
    async pushManager() {
      const registration = await studyFlowServiceWorkerReady();
      return registration?.pushManager ?? null;
    },
  };
}

function safeBrowserLocalStorage(): Storage | null { try { return globalThis.localStorage ?? null; } catch { return null; } }

export class StudyFlowWebAttentionRuntime {
  private readonly environment: StudyFlowWebAttentionEnvironment;
  private readonly storage: Storage | null;
  private readonly ledgerStorage: StudyFlowBrowserAttentionLedgerStorage;
  private readonly pushRegistry: StudyFlowWebPushRegistry;
  private preferences: StudyFlowWebAttentionPreferences;
  private running = false;
  private timer: ReturnType<typeof setInterval> | null = null;
  private unsubscribeStore: (() => void) | null = null;
  private unsubscribeServiceWorker: (() => void) | null = null;
  private listeners = new Set<() => void>();
  private lastScanAtEpochMillis: number | null = null;
  private lastSessionIds = new Set<string>();

  constructor(
    private readonly store: StudyStore,
    private readonly peer: StudyFlowWebLinkPeer,
    options: StudyFlowWebAttentionRuntimeOptions = {},
  ) {
    this.environment = options.environment ?? createBrowserAttentionEnvironment();
    this.storage = options.storage === undefined ? safeBrowserLocalStorage() : options.storage;
    this.ledgerStorage = new StudyFlowBrowserAttentionLedgerStorage(`studyflow.attention.v1.${peer.identity.deviceId}`);
    this.pushRegistry = new StudyFlowWebPushRegistry(this.storage, this.environment);
    this.preferences = this.loadPreferences();
    this.lastSessionIds = new Set(this.store.getState().sessions.map(value => value.id));
    if (options.autoStart !== false) this.start();
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    this.unsubscribeStore = this.store.subscribe(() => { void this.scan(); });
    this.unsubscribeServiceWorker = this.environment.listenServiceWorkerMessages(message => { void this.handleServiceWorkerMessage(message); });
    this.timer = setInterval(() => { void this.scan(); }, WEB_ATTENTION_SCAN_INTERVAL_MS);
    void this.environment.requestServiceWorkerDrain();
    void this.scan();
  }

  stop(): void {
    this.running = false;
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.unsubscribeStore?.();
    this.unsubscribeStore = null;
    this.unsubscribeServiceWorker?.();
    this.unsubscribeServiceWorker = null;
    this.emit();
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  snapshot(): StudyFlowWebAttentionSnapshot {
    const ledger = this.readLedger();
    const now = this.environment.now();
    const entries = [...ledger.entries].sort((a, b) => b.updatedAtEpochMillis - a.updatedAtEpochMillis);
    const upcomingCount = entries.filter(entry => ACTIVE_DELIVERY_STATES.has(entry.state) && entry.occurrence.scheduledAtEpochMillis > now).length;
    const recentCount = entries.filter(entry => !ACTIVE_DELIVERY_STATES.has(entry.state) && entry.updatedAtEpochMillis >= now - 7 * DAY_MS).length;
    const badgeCount = entries.filter(entry => ["delivered", "acknowledged", "missed"].includes(entry.state) && !TERMINAL_STATES.has(entry.state)).length;
    const pushSubscription = this.pushRegistry.savedSubscription();
    const capabilities = this.environment.capabilities();
    const localDevice = this.localAttentionDevice(now, capabilities, pushSubscription);
    const reliabilityTopology = entries.length > 0
      ? attentionTopologyFromWebPeer(this.peer, entries[0].occurrence.workspaceId, now, localDevice)
      : { devices: [localDevice], context: { localDeviceId: localDevice.deviceId, staleAfterMillis: 60_000, allowTvForegroundOwners: true } };
    const reliability = analyzeWebAttentionReliability(ledger, reliabilityTopology.devices, now);
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
      routeSummary: this.routeSummary(entries, now, localDevice),
      reliability,
    };
  }


  private localAttentionDevice(
    now: number,
    capabilities: StudyFlowPwaCapabilities,
    pushSubscription: StudyFlowWebPushSubscriptionRecord | null,
  ) {
    const webTv = this.peer.identity.clientKind === "web_tv";
    return attentionDeviceFromWebPeer(this.peer, {
      availability: this.running ? "foreground" : "online",
      foregroundActive: this.running,
      notificationPermissionGranted: !webTv && this.environment.notificationPermission() === "granted",
      designatedNotifier: !webTv && (this.preferences.systemNotificationsEnabled || !!pushSubscription),
      primaryUserDevice: !webTv,
      deliverySurfaces: webTv
        ? ["foreground_banner" as const, "ledger_only" as const]
        : [
            ...(pushSubscription ? ["web_push" as const] : []),
            ...(capabilities.notifications ? ["system_notification" as const] : []),
            "widget_or_badge" as const,
          ],
      lastSeenAtEpochMillis: now,
    });
  }

  private routeSummary(
    entries: StudyFlowAttentionLedgerEntry[],
    now: number,
    local: StudyFlowAttentionDeviceCapability,
  ): StudyFlowWebAttentionRouteSummary {
    const activeEntries = entries.filter(entry => ACTIVE_DELIVERY_STATES.has(entry.state));
    const routes = activeEntries.map(entry => routeStudyFlowAttentionDeliveryWithPolicy(
      entry.occurrence,
      [local],
      now,
      this.preferences.focusPolicy,
      {
        localDeviceId: this.peer.identity.deviceId,
        primaryDeviceId: this.peer.identity.deviceId,
        designatedNotifierDeviceIds: local.designatedNotifier ? [this.peer.identity.deviceId] : [],
      },
      entry,
    ));
    return {
      localDeviceId: this.peer.identity.deviceId,
      deliverableCount: routes.filter(route => route.deliverable).length,
      ownedCount: routes.filter(route => route.owner?.deviceId === this.peer.identity.deviceId).length,
      mirroredCount: routes.filter(route => route.mirrors.some(mirror => mirror.deviceId === this.peer.identity.deviceId)).length,
      suppressedCount: routes.filter(route => !route.deliverable).length,
      routes,
    };
  }

  exportReliabilityJson(): string { return exportWebAttentionReliabilityJson(this.snapshot().reliability); }
  exportReliabilityCsv(): string { return exportWebAttentionReliabilityCsv(this.snapshot().reliability); }

  async requestBrowserNotifications(): Promise<StudyFlowWebAttentionPermission> {
    const permission = await this.environment.requestNotificationPermission();
    if (permission === "granted") this.setPreferences({ systemNotificationsEnabled: true });
    else if (permission === "denied") this.setPreferences({ systemNotificationsEnabled: false });
    this.emit();
    return permission;
  }

  setPreferences(input: Partial<Omit<StudyFlowWebAttentionPreferences, "version" | "categories">> & { categories?: Partial<Record<StudyFlowAttentionCategory, boolean>> }): StudyFlowWebAttentionPreferences {
    this.preferences = normalizePreferences({
      ...this.preferences,
      ...input,
      categories: { ...this.preferences.categories, ...(input.categories ?? {}) },
    });
    this.persistPreferences();
    void this.scan();
    this.emit();
    return this.preferences;
  }

  setFocusPolicy(input: Partial<StudyFlowAttentionFocusPolicy>): StudyFlowAttentionFocusPolicy {
    const focusPolicy = normalizeWebAttentionFocusPolicy({
      ...this.preferences.focusPolicy,
      ...input,
      quietWindow: input.quietWindow === undefined ? this.preferences.focusPolicy.quietWindow : input.quietWindow,
      categoryPolicies: input.categoryPolicies ?? this.preferences.focusPolicy.categoryPolicies,
    });
    this.preferences = normalizePreferences({ ...this.preferences, focusPolicy });
    this.persistPreferences();
    void this.scan();
    this.emit();
    return focusPolicy;
  }

  setFocusCategoryMode(category: StudyFlowAttentionCategory, mode: StudyFlowAttentionFocusPolicy["categoryPolicies"][number]["mode"]): StudyFlowAttentionFocusPolicy {
    const categoryPolicies = this.preferences.focusPolicy.categoryPolicies.map(value => value.category === category ? { ...value, mode } : value);
    return this.setFocusPolicy({ categoryPolicies });
  }

  async subscribePush(applicationServerKey: string): Promise<StudyFlowWebPushSubscriptionRecord> {
    const record = await this.pushRegistry.subscribe(applicationServerKey);
    this.emit();
    return record;
  }

  async unsubscribePush(): Promise<boolean> {
    const result = await this.pushRegistry.unsubscribe();
    this.emit();
    return result;
  }

  async ingestRemoteDelivery(delivery: StudyFlowNotificationDeliveryEnvelope): Promise<"accepted" | "duplicate" | "expired" | "rejected"> {
    const mediaRuntime = new StudyFlowNotificationMediaRuntime(this.peer.identity, this.ledgerStorage);
    const result = mediaRuntime.applyNotificationDelivery(delivery);
    if (result.status !== "accepted") return result.status;
    const occurrence = delivery.notification.attentionOccurrence ?? this.readLedger().entries.find(entry => entry.sequence === delivery.sequence && entry.sourceDeviceId === delivery.sourceDeviceId)?.occurrence;
    if (occurrence) {
      const explicitTargets = occurrence.targetDeviceIds ?? [];
      // SFLP envelopes are target-aware, and ATT12 also preserves the occurrence-level
      // ATT07 explicit target list so a broadcast transport cannot accidentally surface
      // a reminder on a non-target browser.
      if (!explicitTargets.length || explicitTargets.includes(this.peer.identity.deviceId)) {
        await this.surfaceOccurrence(occurrence, delivery.notification.title, delivery.notification.body, delivery.notification.route?.workspaceId ? routeHashForCategory(occurrence.category) : undefined, false);
      }
    }
    this.emit();
    return result.status;
  }

  async applyAction(occurrenceId: string, action: string, snoozeMinutes = 10): Promise<void> {
    const ledger = this.readLedger();
    const entry = ledger.entries.find(value => value.occurrence.occurrenceId === occurrenceId);
    if (!entry) return;
    const now = this.environment.now();
    if (action === "snooze") {
      this.transition(occurrenceId, "snoozed", now);
      const snoozed: StudyFlowAttentionOccurrence = {
        ...entry.occurrence,
        occurrenceId: stableWebAttentionOccurrenceId([entry.occurrence.occurrenceId, "snooze", String(now)]),
        scheduledAtEpochMillis: now + Math.max(1, snoozeMinutes) * MINUTE_MS,
        originalScheduledAtEpochMillis: entry.occurrence.originalScheduledAtEpochMillis ?? entry.occurrence.scheduledAtEpochMillis,
        expiresAtEpochMillis: now + Math.max(1, snoozeMinutes) * MINUTE_MS + 4 * 60 * MINUTE_MS,
      };
      this.upsertOccurrence(snoozed, "scheduled", now);
    } else if (action === "dismiss") {
      this.transition(occurrenceId, "dismissed", now);
    } else if (action === "move_to_phone") {
      this.transition(occurrenceId, "acknowledged", now);
      globalThis.dispatchEvent?.(new CustomEvent("studyflow:attention-move-to-phone", {
        detail: { occurrenceId, fromDeviceId: this.peer.identity.deviceId, requestedAtEpochMillis: now },
      }));
    } else if (action === "mark_done") {
      const activityId = typeof entry.occurrence.payload?.activityId === "string" ? entry.occurrence.payload.activityId : entry.occurrence.scheduleEntityId;
      const reviewId = typeof entry.occurrence.payload?.reviewId === "string" ? entry.occurrence.payload.reviewId : null;
      if (reviewId) this.store.completeReview(reviewId);
      else if (activityId) {
        const activity = this.store.getState().activities.find(value => value.id === activityId);
        if (activity?.status === "planned") this.store.startFocus({ activityId, durationMinutes: activity.durationMinutes });
      }
      this.transition(occurrenceId, "completed", now);
    } else {
      this.transition(occurrenceId, action === "start_now" || action === "review_now" ? "started" : "acknowledged", now);
      const route = typeof entry.occurrence.payload?.routeHash === "string" ? entry.occurrence.payload.routeHash : routeHashForCategory(entry.occurrence.category);
      this.environment.openRoute(route);
    }
    await this.updateBadge();
    this.emit();
  }

  async scan(): Promise<void> {
    if (!this.running) return;
    const now = this.environment.now();
    const state = this.store.getState();
    for (const occurrence of this.planOccurrences(state, now)) this.upsertOccurrence(occurrence, "scheduled", now);
    this.captureCompletedSessions(state, now);
    this.expireOldEntries(now);
    const ledger = this.readLedger();
    const capabilities = this.environment.capabilities();
    const localDevice = this.localAttentionDevice(now, capabilities, this.pushRegistry.savedSubscription());
    const due = ledger.entries
      .filter(entry => ACTIVE_DELIVERY_STATES.has(entry.state) && entry.occurrence.scheduledAtEpochMillis <= now)
      .filter(entry => !entry.occurrence.expiresAtEpochMillis || entry.occurrence.expiresAtEpochMillis >= now)
      .sort((a, b) => a.occurrence.scheduledAtEpochMillis - b.occurrence.scheduledAtEpochMillis);
    for (const entry of due) {
      const topology = attentionTopologyFromWebPeer(this.peer, entry.occurrence.workspaceId, now, localDevice);
      const route = routeStudyFlowAttentionDeliveryWithPolicy(
        entry.occurrence,
        topology.devices,
        now,
        this.preferences.focusPolicy,
        topology.context,
        entry,
      );
      this.recordRouteDecision(route);
      // ATT12: preserve ATT07 explicit-target safety at the production delivery edge.
      // A local browser may hold the occurrence in its replicated ledger, but only a
      // selected owner/mirror may surface it. Non-targeted entries remain scheduled.
      if (!studyFlowAttentionRouteTargetsDevice(route, this.peer.identity.deviceId)) continue;
      const decision = evaluateWebAttentionFocusPolicy(entry.occurrence, this.preferences.focusPolicy, now, entry, "web");
      this.recordPolicyDecision(decision);
      if (decision.outcome === "suppress_duplicate" || decision.outcome === "route_to_phone") continue;
      if (decision.outcome === "defer_until_quiet_end" && decision.deferredUntilEpochMillis) {
        this.upsertOccurrence({ ...entry.occurrence, scheduledAtEpochMillis: decision.deferredUntilEpochMillis }, "scheduled", now);
        this.recordPolicyDecision(decision);
        continue;
      }
      if (decision.outcome === "compact_only" || decision.outcome === "silent_delivery") {
        this.transition(entry.occurrence.occurrenceId, entry.occurrence.category === "missed_activity" ? "missed" : "delivered", now, 1);
        this.recordPolicyDecision(decision);
        continue;
      }
      await this.surfaceOccurrence(entry.occurrence);
      this.recordPolicyDecision(decision);
    }
    this.lastScanAtEpochMillis = now;
    await this.updateBadge();
    this.emit();
  }

  private planOccurrences(state: StudyState, now: number): StudyFlowAttentionOccurrence[] {
    const output: StudyFlowAttentionOccurrence[] = [];
    const activePlan = state.plans.find(value => value.id === state.activePlanId) ?? null;
    const horizon = now + 7 * DAY_MS;
    if (this.preferences.categories.planned_study) {
      for (const activity of state.activities.filter(value => value.status === "planned" && value.scheduledFor && value.scheduledStartMinute != null)) {
        const start = localDateAtMinute(activity.scheduledFor!, activity.scheduledStartMinute!);
        if (start == null || start < now - DAY_MS || start > horizon) continue;
        const scheduled = start - this.preferences.reminderLeadMinutes * MINUTE_MS;
        output.push(this.activityOccurrence(activity, "planned_study", scheduled, start + 2 * 60 * MINUTE_MS));
      }
    }
    if (this.preferences.categories.missed_activity) {
      for (const activity of state.activities.filter(value => value.status === "planned" && value.scheduledFor && value.scheduledStartMinute != null)) {
        const start = localDateAtMinute(activity.scheduledFor!, activity.scheduledStartMinute!);
        if (start == null || start > now || start < now - DAY_MS) continue;
        const scheduled = start + Math.max(activity.durationMinutes, this.preferences.missedDelayMinutes) * MINUTE_MS;
        output.push(this.activityOccurrence(activity, "missed_activity", scheduled, start + DAY_MS));
      }
    }
    if (this.preferences.categories.due_review) {
      for (const review of state.reviews.filter(value => value.status === "due")) {
        const dueAt = new Date(review.dueAt).getTime();
        if (!Number.isFinite(dueAt) || dueAt < now - 7 * DAY_MS || dueAt > horizon) continue;
        output.push(this.reviewOccurrence(review, dueAt, dueAt + 7 * DAY_MS));
      }
    }
    if (this.preferences.categories.session_ending && state.focus.status === "running" && state.focus.endsAt) {
      const endsAt = new Date(state.focus.endsAt).getTime();
      if (Number.isFinite(endsAt)) {
        const scheduled = Math.max(now, endsAt - this.preferences.sessionLeadMinutes * MINUTE_MS);
        output.push({
          occurrenceId: stableWebAttentionOccurrenceId(["web", "session-ending", state.focus.activityId ?? state.focus.startedAt ?? "focus", String(endsAt)]),
          workspaceId: activePlan?.id ? `plan-${activePlan.id}` : "workspace-main",
          scheduleEntityId: state.focus.activityId ?? undefined,
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
          occurrenceId: stableWebAttentionOccurrenceId(["web", "daily-summary", activePlan?.id ?? "workspace-main", dateKey]),
          workspaceId: activePlan?.id ? `plan-${activePlan.id}` : "workspace-main",
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
      const examDate = activePlan.configuration.publicExam?.target.examDate ?? activePlan.examDate;
      if (examDate && /^\d{4}-\d{2}-\d{2}$/.test(examDate)) {
        const examStart = localDateAtMinute(examDate, 9 * 60);
        const todayStart = localDateAtMinute(localDateKeyAt(now), 0);
        if (examStart != null && todayStart != null) {
          const days = Math.ceil((examStart - todayStart) / DAY_MS);
          if ([30, 14, 7, 3, 1, 0].includes(days)) {
            const scheduled = localDateAtMinute(localDateKeyAt(now), 9 * 60) ?? now;
            output.push({
              occurrenceId: stableWebAttentionOccurrenceId(["web", "exam-countdown", activePlan.id, localDateKeyAt(now)]),
              workspaceId: `plan-${activePlan.id}`,
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

  private activityOccurrence(activity: Activity, category: "planned_study" | "missed_activity", scheduledAt: number, expiresAt: number): StudyFlowAttentionOccurrence {
    return {
      occurrenceId: stableWebAttentionOccurrenceId(["web", category, activity.id, String(scheduledAt)]),
      workspaceId: `plan-${activity.planId}`,
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

  private reviewOccurrence(review: Review, scheduledAt: number, expiresAt: number): StudyFlowAttentionOccurrence {
    return {
      occurrenceId: stableWebAttentionOccurrenceId(["web", "due-review", review.id, String(scheduledAt)]),
      workspaceId: `plan-${review.planId}`,
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

  private captureCompletedSessions(state: StudyState, now: number): void {
    const next = new Set(state.sessions.map(value => value.id));
    if (this.preferences.categories.session_completed) {
      for (const session of state.sessions) {
        if (this.lastSessionIds.has(session.id) || session.status !== "completed" || !session.endedAt) continue;
        const endedAt = new Date(session.endedAt).getTime();
        if (!Number.isFinite(endedAt) || endedAt < now - 15 * MINUTE_MS) continue;
        const occurrence: StudyFlowAttentionOccurrence = {
          occurrenceId: stableWebAttentionOccurrenceId(["web", "session-completed", session.id]),
          workspaceId: `plan-${session.planId}`,
          scheduleEntityId: session.activityId ?? undefined,
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

  private async surfaceOccurrence(occurrence: StudyFlowAttentionOccurrence, title?: string, body?: string, routeHash?: string, transition = true): Promise<void> {
    const now = this.environment.now();
    const state = this.store.getState();
    if (!this.preferences.categories[occurrence.category]) return;
    if (transition) this.transition(occurrence.occurrenceId, occurrence.category === "missed_activity" ? "missed" : "delivered", now, 1);
    const presentation = describeStudyFlowWebOccurrence(occurrence, state);
    const resolvedTitle = title ?? presentation.title;
    const resolvedBody = body ?? presentation.body;
    const resolvedRoute = routeHash ?? presentation.routeHash;
    if (this.peer.identity.clientKind !== "web_tv" && this.preferences.systemNotificationsEnabled && this.environment.notificationPermission() === "granted") {
      const actions = actionKindsForOccurrence(occurrence).map(action => ({ action, title: actionLabel(action) }));
      await this.environment.showNotification(resolvedTitle, {
        body: resolvedBody,
        tag: occurrence.occurrenceId,
        renotify: false,
        data: { occurrence, occurrenceId: occurrence.occurrenceId, routeHash: resolvedRoute },
        actions,
      } as NotificationOptions);
    }
  }

  private async handleServiceWorkerMessage(message: unknown): Promise<void> {
    if (!message || typeof message !== "object") return;
    const envelope = message as { type?: string; event?: StudyFlowWebAttentionServiceWorkerEvent };
    if (envelope.type !== "studyflow:attention-event" || !envelope.event) return;
    const event = envelope.event;
    if (event.kind === "push-delivered" && event.occurrence) {
      this.upsertOccurrence(event.occurrence, "delivered", event.occurredAtEpochMillis ?? this.environment.now());
    } else if (event.kind === "notification-click" && event.occurrence) {
      this.upsertOccurrence(event.occurrence, "acknowledged", event.occurredAtEpochMillis ?? this.environment.now());
      await this.applyAction(event.occurrence.occurrenceId, event.action || "open");
    } else if (event.kind === "notification-close" && event.occurrence) {
      this.upsertOccurrence(event.occurrence, "dismissed", event.occurredAtEpochMillis ?? this.environment.now());
    }
    await this.updateBadge();
    this.emit();
  }

  private upsertOccurrence(occurrence: StudyFlowAttentionOccurrence, state: StudyFlowAttentionDeliveryState, now: number): void {
    const ledger = this.readLedger();
    const existing = ledger.entries.find(value => value.occurrence.occurrenceId === occurrence.occurrenceId);
    if (existing && TERMINAL_STATES.has(existing.state)) return;
    if (existing && !ACTIVE_DELIVERY_STATES.has(existing.state) && ACTIVE_DELIVERY_STATES.has(state)) return;
    const entry: StudyFlowAttentionLedgerEntry = {
      occurrence: existing ? { ...existing.occurrence, ...occurrence } : occurrence,
      state: existing && !ACTIVE_DELIVERY_STATES.has(existing.state) ? existing.state : state,
      updatedAtEpochMillis: now,
      deliveredAtEpochMillis: existing?.deliveredAtEpochMillis,
      acknowledgedAtEpochMillis: existing?.acknowledgedAtEpochMillis,
      sourceDeviceId: existing?.sourceDeviceId ?? occurrence.sourceDeviceId,
      targetDeviceId: existing?.targetDeviceId,
      sequence: existing?.sequence,
      deliveryAttempts: existing?.deliveryAttempts ?? 0,
      policyAudit: existing?.policyAudit,
      routeAudit: existing?.routeAudit,
    };
    this.writeLedger({ ...ledger, entries: [...ledger.entries.filter(value => value.occurrence.occurrenceId !== occurrence.occurrenceId), entry].slice(-WEB_ATTENTION_HISTORY_LIMIT) });
  }

  private recordRouteDecision(route: StudyFlowAttentionDeliveryRoute): void {
    const ledger = this.readLedger();
    const existing = ledger.entries.find(value => value.occurrence.occurrenceId === route.occurrenceId);
    if (!existing) return;
    const entry: StudyFlowAttentionLedgerEntry = {
      ...existing,
      updatedAtEpochMillis: Math.max(existing.updatedAtEpochMillis, route.decidedAtEpochMillis),
      targetDeviceId: route.owner?.deviceId ?? existing.targetDeviceId,
      routeAudit: routeAuditFromDeliveryRoute(route),
    };
    this.writeLedger({ ...ledger, entries: [...ledger.entries.filter(value => value.occurrence.occurrenceId !== route.occurrenceId), entry].slice(-WEB_ATTENTION_HISTORY_LIMIT) });
  }

  private recordPolicyDecision(decision: StudyFlowAttentionPolicyDecision): void {
    const ledger = this.readLedger();
    const existing = ledger.entries.find(value => value.occurrence.occurrenceId === decision.occurrenceId);
    if (!existing) return;
    const entry: StudyFlowAttentionLedgerEntry = {
      ...existing,
      updatedAtEpochMillis: Math.max(existing.updatedAtEpochMillis, decision.evaluatedAtEpochMillis),
      policyAudit: {
        profile: decision.profile,
        outcome: decision.outcome,
        evaluatedAtEpochMillis: decision.evaluatedAtEpochMillis,
        deferredUntilEpochMillis: decision.deferredUntilEpochMillis,
        explanation: decision.explanation,
      },
    };
    this.writeLedger({ ...ledger, entries: [...ledger.entries.filter(value => value.occurrence.occurrenceId !== decision.occurrenceId), entry].slice(-WEB_ATTENTION_HISTORY_LIMIT) });
  }

  private transition(occurrenceId: string, state: StudyFlowAttentionDeliveryState, now: number, attemptDelta = 0): void {
    const ledger = this.readLedger();
    const existing = ledger.entries.find(value => value.occurrence.occurrenceId === occurrenceId);
    if (!existing) return;
    const entry: StudyFlowAttentionLedgerEntry = {
      ...existing,
      state,
      updatedAtEpochMillis: now,
      deliveredAtEpochMillis: state === "delivered" || state === "missed" ? existing.deliveredAtEpochMillis ?? now : existing.deliveredAtEpochMillis,
      acknowledgedAtEpochMillis: ["acknowledged", "started", "completed", "dismissed", "snoozed", "rescheduled"].includes(state) ? existing.acknowledgedAtEpochMillis ?? now : existing.acknowledgedAtEpochMillis,
      deliveryAttempts: existing.deliveryAttempts + attemptDelta,
    };
    this.writeLedger({ ...ledger, entries: [...ledger.entries.filter(value => value.occurrence.occurrenceId !== occurrenceId), entry].slice(-WEB_ATTENTION_HISTORY_LIMIT) });
  }

  private expireOldEntries(now: number): void {
    const ledger = this.readLedger();
    let changed = false;
    const entries = ledger.entries.map(entry => {
      if (!TERMINAL_STATES.has(entry.state) && entry.occurrence.expiresAtEpochMillis && entry.occurrence.expiresAtEpochMillis < now) {
        changed = true;
        return { ...entry, state: "expired" as StudyFlowAttentionDeliveryState, updatedAtEpochMillis: now };
      }
      return entry;
    });
    if (changed) this.writeLedger({ ...ledger, entries });
  }

  private readLedger(): StudyFlowAttentionLedgerSnapshot {
    return parseLedger(this.ledgerStorage.readEncodedSnapshot());
  }

  private writeLedger(snapshot: StudyFlowAttentionLedgerSnapshot): void {
    this.ledgerStorage.writeEncodedSnapshot(JSON.stringify(snapshot));
  }

  private loadPreferences(): StudyFlowWebAttentionPreferences {
    try {
      const encoded = this.storage?.getItem(WEB_ATTENTION_PREFERENCES_KEY) ?? null;
      return encoded ? normalizePreferences(JSON.parse(encoded)) : defaultWebAttentionPreferences();
    } catch { return defaultWebAttentionPreferences(); }
  }

  private persistPreferences(): void {
    try { this.storage?.setItem(WEB_ATTENTION_PREFERENCES_KEY, JSON.stringify(this.preferences)); } catch { /* browser storage may be unavailable */ }
  }

  private async updateBadge(): Promise<void> {
    const count = this.snapshot().badgeCount;
    try {
      if (count > 0) await this.environment.setBadge(count);
      else await this.environment.clearBadge();
    } catch { /* progressive enhancement only */ }
  }

  private emit(): void { for (const listener of this.listeners) listener(); }
}

export interface StudyFlowWebAttentionBridgeHandle { dispose(): void; }

export function installStudyFlowWebAttentionBridge(runtime: StudyFlowWebAttentionRuntime): StudyFlowWebAttentionBridgeHandle {
  const target = globalThis as typeof globalThis & {
    studyFlowWebAttentionRuntime?: StudyFlowWebAttentionRuntime;
    studyFlowApplyNotificationDelivery?: (delivery: StudyFlowNotificationDeliveryEnvelope) => Promise<string>;
  };
  target.studyFlowWebAttentionRuntime = runtime;
  target.studyFlowApplyNotificationDelivery = delivery => runtime.ingestRemoteDelivery(delivery);
  const onDelivery = (event: Event) => {
    const delivery = (event as CustomEvent<StudyFlowNotificationDeliveryEnvelope>).detail;
    if (delivery) void runtime.ingestRemoteDelivery(delivery);
  };
  globalThis.addEventListener?.("studyflow:sflp-notification-delivery", onDelivery as EventListener);
  return {
    dispose() {
      globalThis.removeEventListener?.("studyflow:sflp-notification-delivery", onDelivery as EventListener);
      if (target.studyFlowWebAttentionRuntime === runtime) delete target.studyFlowWebAttentionRuntime;
      delete target.studyFlowApplyNotificationDelivery;
    },
  };
}
