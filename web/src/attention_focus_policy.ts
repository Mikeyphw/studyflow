"use strict";

import type {
  StudyFlowAttentionCategory,
  StudyFlowAttentionLedgerEntry,
  StudyFlowAttentionOccurrence,
} from "./sflp_link";

export type StudyFlowAttentionFocusProfile =
  | "balanced"
  | "exam_mode"
  | "work_mode"
  | "sleep_mode"
  | "travel_offline"
  | "minimal_tv";

export type StudyFlowAttentionPolicyOutcome =
  | "deliver_now"
  | "defer_until_quiet_end"
  | "silent_delivery"
  | "compact_only"
  | "route_to_phone"
  | "escalate_mirror"
  | "suppress_duplicate"
  | "require_user_action";

export type StudyFlowAttentionEscalationMode =
  | "normal"
  | "silent"
  | "defer"
  | "compact_only"
  | "route_to_phone"
  | "mirror_if_unhandled"
  | "require_user_action";

export interface StudyFlowAttentionQuietWindow {
  enabled: boolean;
  startMinute: number;
  endMinute: number;
  timezoneId?: string;
}

export interface StudyFlowAttentionCategoryEscalationPolicy {
  category: StudyFlowAttentionCategory;
  mode: StudyFlowAttentionEscalationMode;
  bypassQuietHours: boolean;
  mirrorAfterMinutes: number;
}

export interface StudyFlowAttentionFocusPolicy {
  version: 1;
  enabled: boolean;
  profile: StudyFlowAttentionFocusProfile;
  quietWindow: StudyFlowAttentionQuietWindow | null;
  categoryPolicies: StudyFlowAttentionCategoryEscalationPolicy[];
  allowHighPriorityQuietBypass: boolean;
  allowTimeSensitiveQuietBypass: boolean;
  maxEscalationMirrors: number;
}

export interface StudyFlowAttentionPolicyDecision {
  occurrenceId: string;
  profile: StudyFlowAttentionFocusProfile;
  outcome: StudyFlowAttentionPolicyOutcome;
  evaluatedAtEpochMillis: number;
  deferredUntilEpochMillis?: number;
  quietHoursActive: boolean;
  deliverSilently: boolean;
  compactOnly: boolean;
  preferPhone: boolean;
  requestedMirrorCount: number;
  requiresUserAction: boolean;
  explanation: string;
}

const DEFAULT_CATEGORY_POLICIES: StudyFlowAttentionCategoryEscalationPolicy[] = [
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
const CATEGORIES: StudyFlowAttentionCategory[] = DEFAULT_CATEGORY_POLICIES.map(value => value.category);
const HANDLED = new Set(["delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled", "dismissed", "missed", "expired", "cancelled"]);

export function defaultWebAttentionFocusPolicy(_timezoneId = resolvedTimezoneId()): StudyFlowAttentionFocusPolicy {
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

export function normalizeWebAttentionFocusPolicy(value: unknown): StudyFlowAttentionFocusPolicy {
  const defaults = defaultWebAttentionFocusPolicy();
  if (!value || typeof value !== "object") return defaults;
  const raw = value as Partial<StudyFlowAttentionFocusPolicy>;
  const profiles: StudyFlowAttentionFocusProfile[] = ["balanced", "exam_mode", "work_mode", "sleep_mode", "travel_offline", "minimal_tv"];
  const modes: StudyFlowAttentionEscalationMode[] = ["normal", "silent", "defer", "compact_only", "route_to_phone", "mirror_if_unhandled", "require_user_action"];
  const provided = new Map((Array.isArray(raw.categoryPolicies) ? raw.categoryPolicies : []).map(item => [item.category, item]));
  const categoryPolicies = DEFAULT_CATEGORY_POLICIES.map(defaultPolicy => {
    const candidate = provided.get(defaultPolicy.category);
    return {
      category: defaultPolicy.category,
      mode: candidate && modes.includes(candidate.mode) ? candidate.mode : defaultPolicy.mode,
      bypassQuietHours: candidate?.bypassQuietHours ?? defaultPolicy.bypassQuietHours,
      mirrorAfterMinutes: clampInt(candidate?.mirrorAfterMinutes, defaultPolicy.mirrorAfterMinutes, 0, 720),
    } satisfies StudyFlowAttentionCategoryEscalationPolicy;
  });
  const q = raw.quietWindow;
  let startMinute = clampInt(q?.startMinute, 22 * 60, 0, 1439);
  let endMinute = clampInt(q?.endMinute, 7 * 60, 0, 1439);
  if (startMinute === endMinute) endMinute = (endMinute + 1) % 1440;
  return {
    version: 1,
    enabled: raw.enabled !== false,
    profile: profiles.includes(raw.profile as StudyFlowAttentionFocusProfile) ? raw.profile as StudyFlowAttentionFocusProfile : defaults.profile,
    quietWindow: q == null ? null : {
      enabled: q?.enabled !== false,
      startMinute,
      endMinute,
      timezoneId: q?.timezoneId?.trim() || resolvedTimezoneId(),
    },
    categoryPolicies,
    allowHighPriorityQuietBypass: raw.allowHighPriorityQuietBypass !== false,
    allowTimeSensitiveQuietBypass: raw.allowTimeSensitiveQuietBypass !== false,
    maxEscalationMirrors: clampInt(raw.maxEscalationMirrors, 1, 0, 3),
  };
}

export function evaluateWebAttentionFocusPolicy(
  occurrence: StudyFlowAttentionOccurrence,
  policyInput: StudyFlowAttentionFocusPolicy,
  nowEpochMillis: number,
  ledgerEntry?: StudyFlowAttentionLedgerEntry | null,
  localClientKind: "android" | "desktop" | "web" | "android_tv" | "web_tv" = "web",
): StudyFlowAttentionPolicyDecision {
  const policy = normalizeWebAttentionFocusPolicy(policyInput);
  if (ledgerEntry && HANDLED.has(ledgerEntry.state)) return decision(occurrence, policy, nowEpochMillis, "suppress_duplicate", "ATT01 ledger already records this occurrence as handled.");
  if (!policy.enabled) return decision(occurrence, policy, nowEpochMillis, "deliver_now", "ATT10 focus policy is disabled; existing delivery behavior is preserved.");
  const category = effectiveCategoryPolicy(policy, occurrence.category);
  const quiet = policy.quietWindow;
  const inQuiet = !!quiet?.enabled && quietContains(quiet, nowEpochMillis, occurrence.timezoneId);
  const priority = occurrence.priority ?? "normal";
  const bypass = category.bypassQuietHours ||
    (policy.allowHighPriorityQuietBypass && priority === "high") ||
    (policy.allowTimeSensitiveQuietBypass && priority === "time_sensitive");
  if (inQuiet && !bypass && quiet) {
    let outcome: StudyFlowAttentionPolicyOutcome;
    if (policy.profile === "sleep_mode") outcome = "defer_until_quiet_end";
    else if (policy.profile === "minimal_tv") outcome = "route_to_phone";
    else if (policy.profile === "travel_offline") outcome = "compact_only";
    else if (category.mode === "silent") outcome = "silent_delivery";
    else if (category.mode === "compact_only") outcome = "compact_only";
    else if (category.mode === "route_to_phone") outcome = "route_to_phone";
    else if (category.mode === "require_user_action") outcome = "require_user_action";
    else outcome = "defer_until_quiet_end";
    return decision(
      occurrence, policy, nowEpochMillis, outcome,
      `Quiet hours are active in ${quiet.timezoneId || occurrence.timezoneId || "UTC"}; ATT10 applied ${outcome}.`,
      outcome === "defer_until_quiet_end" ? nextQuietEnd(quiet, nowEpochMillis, occurrence.timezoneId) : undefined,
      true,
    );
  }
  let outcome: StudyFlowAttentionPolicyOutcome = "deliver_now";
  if (policy.profile === "minimal_tv" && (localClientKind === "android_tv" || localClientKind === "web_tv")) outcome = "route_to_phone";
  else if (policy.profile === "travel_offline") outcome = "compact_only";
  else if (policy.profile === "exam_mode" && occurrence.category === "exam_countdown") outcome = "require_user_action";
  else if (category.mode === "silent") outcome = "silent_delivery";
  else if (category.mode === "compact_only") outcome = "compact_only";
  else if (category.mode === "route_to_phone") outcome = "route_to_phone";
  else if (category.mode === "mirror_if_unhandled" && nowEpochMillis >= (occurrence.originalScheduledAtEpochMillis ?? occurrence.scheduledAtEpochMillis) + category.mirrorAfterMinutes * 60_000) outcome = "escalate_mirror";
  else if (category.mode === "require_user_action") outcome = "require_user_action";
  return decision(occurrence, policy, nowEpochMillis, outcome, `ATT10 ${policy.profile} evaluated ${occurrence.category} as ${outcome}.`);
}

function effectiveCategoryPolicy(policy: StudyFlowAttentionFocusPolicy, category: StudyFlowAttentionCategory): StudyFlowAttentionCategoryEscalationPolicy {
  const defaultPolicy = DEFAULT_CATEGORY_POLICIES.find(value => value.category === category)
    ?? { category, mode: "normal", bypassQuietHours: false, mirrorAfterMinutes: 15 } satisfies StudyFlowAttentionCategoryEscalationPolicy;
  const base = policy.categoryPolicies.find(value => value.category === category) ?? defaultPolicy;
  switch (policy.profile) {
    case "exam_mode":
      if (["exam_countdown", "due_review", "planned_study"].includes(category)) return { ...base, mode: category === "exam_countdown" ? "require_user_action" : "mirror_if_unhandled", bypassQuietHours: category === "exam_countdown" };
      return base;
    case "work_mode": return ["daily_summary", "schedule_change", "plan_warning"].includes(category) ? { ...base, mode: "silent" } : base;
    case "sleep_mode": return { ...base, mode: "defer" };
    case "travel_offline": return { ...base, mode: "compact_only" };
    case "minimal_tv": return { ...base, mode: "route_to_phone" };
    default: return base;
  }
}

function decision(
  occurrence: StudyFlowAttentionOccurrence,
  policy: StudyFlowAttentionFocusPolicy,
  now: number,
  outcome: StudyFlowAttentionPolicyOutcome,
  explanation: string,
  deferredUntilEpochMillis?: number,
  quietHoursActive = false,
): StudyFlowAttentionPolicyDecision {
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

export function quietContains(window: StudyFlowAttentionQuietWindow, epochMillis: number, fallbackTimezoneId?: string): boolean {
  if (!window.enabled) return false;
  const minute = minuteInZone(epochMillis, validTimezone(window.timezoneId || fallbackTimezoneId));
  return window.startMinute < window.endMinute
    ? minute >= window.startMinute && minute < window.endMinute
    : minute >= window.startMinute || minute < window.endMinute;
}

export function nextQuietEnd(window: StudyFlowAttentionQuietWindow, epochMillis: number, fallbackTimezoneId?: string): number {
  if (!quietContains(window, epochMillis, fallbackTimezoneId)) return epochMillis;
  // Deliberately evaluate wall-clock minutes in the configured IANA timezone. This remains correct across overnight windows and DST shifts.
  const limit = epochMillis + 30 * 60 * 60_000;
  let cursor = epochMillis + 60_000;
  while (cursor <= limit && quietContains(window, cursor, fallbackTimezoneId)) cursor += 60_000;
  return Math.min(cursor, limit);
}

function minuteInZone(epochMillis: number, timezoneId: string): number {
  const formatter = new Intl.DateTimeFormat("en-US", { timeZone: timezoneId, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
  const parts = formatter.formatToParts(new Date(epochMillis));
  const hour = Number(parts.find(part => part.type === "hour")?.value ?? "0") % 24;
  const minute = Number(parts.find(part => part.type === "minute")?.value ?? "0");
  return hour * 60 + minute;
}

function validTimezone(value?: string): string {
  const candidate = value || "UTC";
  try { new Intl.DateTimeFormat("en-US", { timeZone: candidate }).format(0); return candidate; }
  catch { return "UTC"; }
}

function resolvedTimezoneId(): string {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; }
  catch { return "UTC"; }
}

function clampInt(value: unknown, fallback: number, min: number, max: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? Math.max(min, Math.min(max, Math.round(numeric))) : fallback;
}
