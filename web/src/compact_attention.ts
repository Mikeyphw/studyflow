"use strict";

import type { StudyFlowWebAttentionSnapshot } from "./attention";
import type {
  StudyFlowAttentionActionKind,
  StudyFlowAttentionCategory,
  StudyFlowAttentionLedgerEntry,
  StudyFlowAttentionPriority,
} from "./sflp_link";
import type {
  StudyFlowAttentionDeliveryRoute,
  StudyFlowAttentionDeliveryRole,
} from "./attention_router";

export type StudyFlowCompactCompanionFormFactor =
  | "wearable"
  | "lock_screen"
  | "notification_shade"
  | "quick_tile"
  | "desktop_mini"
  | "web_compact";
export type StudyFlowCompactCompanionAvailability = "connected" | "nearby" | "disconnected";
export type StudyFlowCompactAttentionRenderMode = "glance" | "quick_action" | "mirror_only" | "unavailable";

export interface StudyFlowCompactCompanionProfile {
  companionId: string;
  displayName: string;
  parentDeviceId: string;
  formFactor: StudyFlowCompactCompanionFormFactor;
  availability: StudyFlowCompactCompanionAvailability;
  maxActions: number;
  canHandoff: boolean;
  canVibrate: boolean;
  canShowSensitiveText: boolean;
  lastSeenAtEpochMillis?: number;
  mutedUntilEpochMillis?: number;
}

export interface StudyFlowCompactAttentionAction {
  kind: StudyFlowAttentionActionKind;
  label: string;
  destructive: boolean;
  requiresParentDevice: true;
}

export interface StudyFlowCompactAttentionCard {
  companionId: string;
  companionDisplayName: string;
  parentDeviceId: string;
  occurrenceId: string;
  category: StudyFlowAttentionCategory;
  priority: StudyFlowAttentionPriority;
  title: string;
  body: string;
  scheduledAtEpochMillis: number;
  renderMode: StudyFlowCompactAttentionRenderMode;
  parentRouteRole: StudyFlowAttentionDeliveryRole;
  ownerDeviceId?: string;
  actions: StudyFlowCompactAttentionAction[];
  sensitiveTextHidden: boolean;
  hapticHint: boolean;
}

export interface StudyFlowCompactCompanionDiagnostics {
  candidateCount: number;
  eligibleCount: number;
  projectedCount: number;
  suppressedCompanionIds: string[];
  parentRouteDeliverable: boolean;
  explanation: string;
}

export interface StudyFlowCompactAttentionProjection {
  occurrenceId: string | null;
  ownerDeviceId?: string;
  cards: StudyFlowCompactAttentionCard[];
  diagnostics: StudyFlowCompactCompanionDiagnostics;
  deliverable: boolean;
}

const DEFAULT_STALE_MS = 120_000;
const DEFAULT_LIMIT = 4;
const HANDLED_STATES = new Set(["delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled", "dismissed", "missed", "expired", "cancelled"]);

export function defaultWebCompactCompanionProfiles(localDeviceId: string, nowEpochMillis: number): StudyFlowCompactCompanionProfile[] {
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

export function projectAttentionRouteToCompactCompanions(
  entry: StudyFlowAttentionLedgerEntry,
  route: StudyFlowAttentionDeliveryRoute,
  companions: StudyFlowCompactCompanionProfile[],
  nowEpochMillis: number,
  staleAfterMillis = DEFAULT_STALE_MS,
  maxCompanions = DEFAULT_LIMIT,
): StudyFlowCompactAttentionProjection {
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
    ownerDeviceId: route.owner?.deviceId,
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

export function projectWebAttentionToCompactCompanions(
  snapshot: StudyFlowWebAttentionSnapshot,
  companions = defaultWebCompactCompanionProfiles(snapshot.routeSummary.localDeviceId, Date.now()),
): StudyFlowCompactAttentionProjection {
  const active = snapshot.entries.find(entry => !HANDLED_STATES.has(entry.state));
  if (!active) {
    return emptyProjection(null, null, companions, "No active attention entry is available for compact projection.");
  }
  const route = snapshot.routeSummary.routes.find(item => item.occurrenceId === active.occurrence.occurrenceId)
    ?? snapshot.routeSummary.routes.find(item => item.deliverable)
    ?? null;
  if (!route) {
    return emptyProjection(active.occurrence.occurrenceId, null, companions, "No ATT07 route is available for compact projection.");
  }
  return projectAttentionRouteToCompactCompanions(active, route, companions, Date.now());
}

function compactCardFromEntry(
  entry: StudyFlowAttentionLedgerEntry,
  route: StudyFlowAttentionDeliveryRoute,
  companion: StudyFlowCompactCompanionProfile,
): StudyFlowCompactAttentionCard {
  const role = route.owner?.deviceId === companion.parentDeviceId ? "owner" : "mirror";
  const [title, body] = compactText(entry, companion.canShowSensitiveText);
  return {
    companionId: companion.companionId,
    companionDisplayName: companion.displayName,
    parentDeviceId: companion.parentDeviceId,
    occurrenceId: entry.occurrence.occurrenceId,
    category: entry.occurrence.category,
    priority: entry.occurrence.priority ?? "normal",
    title,
    body,
    scheduledAtEpochMillis: entry.occurrence.scheduledAtEpochMillis,
    renderMode: renderModeFor(companion.formFactor, role),
    parentRouteRole: role,
    ownerDeviceId: route.owner?.deviceId,
    actions: compactActions(entry, companion.maxActions, companion.canHandoff),
    sensitiveTextHidden: !companion.canShowSensitiveText,
    hapticHint: companion.canVibrate && entry.occurrence.priority !== "low",
  };
}

function compactText(entry: StudyFlowAttentionLedgerEntry, showSensitive: boolean): [string, string] {
  if (!showSensitive) return [defaultTitle(entry.occurrence.category), defaultBody(entry.occurrence.category)];
  const payload = entry.occurrence.payload ?? {};
  const title = typeof payload.title === "string" ? payload.title : defaultTitle(entry.occurrence.category);
  const body = typeof payload.body === "string" ? payload.body : defaultBody(entry.occurrence.category);
  return [title, body];
}

function compactActions(entry: StudyFlowAttentionLedgerEntry, limit: number, canHandoff: boolean): StudyFlowCompactAttentionAction[] {
  const explicit = entry.occurrence.actions?.map(action => action.kind) ?? [];
  const defaults = defaultActions(entry.occurrence.category);
  const source = explicit.length ? explicit : defaults;
  const kinds = Array.from(new Set(source)).filter(kind => canHandoff || kind !== "move_to_device").slice(0, Math.max(1, Math.min(3, limit)));
  return kinds.map(kind => ({ kind, label: actionLabel(kind), destructive: kind === "dismiss", requiresParentDevice: true }));
}

function defaultActions(category: StudyFlowAttentionCategory): StudyFlowAttentionActionKind[] {
  if (category === "due_review") return ["review_now", "snooze", "move_to_device"];
  if (category === "planned_study" || category === "missed_activity") return ["start_now", "snooze", "move_to_device"];
  if (category === "session_ending" || category === "session_completed") return ["continue", "snooze", "move_to_device"];
  return ["open", "dismiss", "move_to_device"];
}

function actionLabel(kind: StudyFlowAttentionActionKind): string {
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

function defaultTitle(category: StudyFlowAttentionCategory): string {
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

function defaultBody(category: StudyFlowAttentionCategory): string {
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

function renderModeFor(
  formFactor: StudyFlowCompactCompanionFormFactor,
  role: StudyFlowAttentionDeliveryRole,
): StudyFlowCompactAttentionRenderMode {
  if (formFactor === "quick_tile") return "quick_action";
  if (formFactor === "notification_shade") return "mirror_only";
  return role === "owner" ? "glance" : "mirror_only";
}

function isCompanionAvailable(companion: StudyFlowCompactCompanionProfile, nowEpochMillis: number, staleAfterMillis: number): boolean {
  if (companion.availability === "disconnected") return false;
  if (companion.mutedUntilEpochMillis && companion.mutedUntilEpochMillis > nowEpochMillis) return false;
  if (companion.lastSeenAtEpochMillis && nowEpochMillis - companion.lastSeenAtEpochMillis > staleAfterMillis) return false;
  return true;
}

function emptyProjection(
  occurrenceId: string | null,
  route: StudyFlowAttentionDeliveryRoute | null,
  companions: StudyFlowCompactCompanionProfile[],
  explanation: string,
): StudyFlowCompactAttentionProjection {
  return {
    occurrenceId,
    ownerDeviceId: route?.owner?.deviceId,
    cards: [],
    diagnostics: {
      candidateCount: companions.length,
      eligibleCount: 0,
      projectedCount: 0,
      suppressedCompanionIds: companions.map(companion => companion.companionId),
      parentRouteDeliverable: route?.deliverable ?? false,
      explanation,
    },
    deliverable: false,
  };
}
