"use strict";

import type {
  StudyFlowAttentionCategory,
  StudyFlowAttentionDeliveryState,
  StudyFlowAttentionLedgerEntry,
  StudyFlowAttentionLedgerSnapshot,
} from "./sflp_link";
import type {
  StudyFlowAttentionDeliveryRoute,
  StudyFlowAttentionDeliverySurface,
  StudyFlowAttentionDeviceAvailability,
  StudyFlowAttentionDeviceCapability,
  StudyFlowAttentionDeliveryRole,
  StudyFlowAttentionRouteReason,
} from "./attention_router";

export type StudyFlowAttentionPlatformReliabilityStatus = "ready" | "degraded" | "foreground_only" | "blocked" | "offline";

export interface StudyFlowAttentionRouteAuditTarget {
  deviceId: string;
  clientKind: StudyFlowAttentionDeviceCapability["clientKind"];
  surface: StudyFlowAttentionDeliverySurface;
  role: StudyFlowAttentionDeliveryRole;
}

export interface StudyFlowAttentionRouteAudit {
  occurrenceId: string;
  reason: StudyFlowAttentionRouteReason;
  decidedAtEpochMillis: number;
  owner: StudyFlowAttentionRouteAuditTarget | null;
  mirrors: StudyFlowAttentionRouteAuditTarget[];
  candidateCount: number;
  eligibleCount: number;
  suppressedDeviceIds: string[];
  policyOutcome?: string;
  explanation: string;
}

export interface StudyFlowAttentionAnalytics {
  totalCount: number;
  activeCount: number;
  deliveredCount: number;
  missedCount: number;
  deferredCount: number;
  snoozedCount: number;
  dismissedCount: number;
  doneCount: number;
  silentCount: number;
  compactOnlyCount: number;
  routedToPhoneCount: number;
  escalatedMirrorCount: number;
  duplicateSuppressedCount: number;
  userActionRequiredCount: number;
  deliveryAttemptCount: number;
  auditedRouteCount: number;
  averageDeliveryDelayMillis: number | null;
}

export interface StudyFlowAttentionCategoryAnalytics {
  category: StudyFlowAttentionCategory;
  totalCount: number;
  deliveredCount: number;
  missedCount: number;
  doneCount: number;
}

export interface StudyFlowAttentionPlatformReliabilityDiagnostic {
  deviceId: string;
  clientKind: StudyFlowAttentionDeviceCapability["clientKind"];
  status: StudyFlowAttentionPlatformReliabilityStatus;
  availability: StudyFlowAttentionDeviceAvailability;
  deliverySurfaces: StudyFlowAttentionDeliverySurface[];
  notificationPermissionGranted: boolean;
  truthfulBackgroundDelivery: boolean;
  ownerRouteCount: number;
  mirrorRouteCount: number;
  targetedEntryCount: number;
  lastSeenAtEpochMillis?: number;
  explanation: string;
}

export interface StudyFlowAttentionReliabilityEvidenceSnapshot {
  generatedAtEpochMillis: number;
  workspaceId?: string;
  analytics: StudyFlowAttentionAnalytics;
  categoryAnalytics: StudyFlowAttentionCategoryAnalytics[];
  platforms: StudyFlowAttentionPlatformReliabilityDiagnostic[];
  evidence: StudyFlowAttentionLedgerEntry[];
  routeAudits: StudyFlowAttentionRouteAudit[];
}

const DELIVERED_STATES = new Set<StudyFlowAttentionDeliveryState>(["delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled", "dismissed"]);
const ACTIVE_STATES = new Set<StudyFlowAttentionDeliveryState>(["planned", "scheduled", "delivery_pending", "snoozed", "rescheduled", "missed"]);
const CATEGORIES: StudyFlowAttentionCategory[] = ["planned_study", "due_review", "missed_activity", "daily_summary", "session_ending", "session_completed", "exam_countdown", "schedule_change", "plan_warning", "sync_attention"];

export function routeAuditFromDeliveryRoute(route: StudyFlowAttentionDeliveryRoute): StudyFlowAttentionRouteAudit {
  const target = (value: NonNullable<StudyFlowAttentionDeliveryRoute["owner"]>): StudyFlowAttentionRouteAuditTarget => ({
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
    policyOutcome: route.policyOutcome ?? route.diagnostics.policyOutcome,
    explanation: route.diagnostics.explanation,
  };
}

export function analyzeWebAttentionReliability(
  ledger: StudyFlowAttentionLedgerSnapshot,
  devices: StudyFlowAttentionDeviceCapability[],
  nowEpochMillis: number,
  workspaceId?: string,
  staleAfterMillis = 5 * 60_000,
): StudyFlowAttentionReliabilityEvidenceSnapshot {
  const entries = [...ledger.entries]
    .filter(entry => !workspaceId || entry.occurrence.workspaceId === workspaceId)
    .sort((a, b) => b.updatedAtEpochMillis - a.updatedAtEpochMillis);
  const routeAudits = entries.map(entry => entry.routeAudit as StudyFlowAttentionRouteAudit | undefined).filter((value): value is StudyFlowAttentionRouteAudit => !!value).sort((a, b) => b.decidedAtEpochMillis - a.decidedAtEpochMillis);
  const delays = entries.flatMap(entry => entry.deliveredAtEpochMillis == null ? [] : [Math.max(0, entry.deliveredAtEpochMillis - (entry.occurrence.originalScheduledAtEpochMillis ?? entry.occurrence.scheduledAtEpochMillis))]);
  const outcome = (entry: StudyFlowAttentionLedgerEntry, value: string) => entry.policyAudit?.outcome === value;
  const analytics: StudyFlowAttentionAnalytics = {
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

function platformDiagnostic(
  device: StudyFlowAttentionDeviceCapability,
  entries: StudyFlowAttentionLedgerEntry[],
  audits: StudyFlowAttentionRouteAudit[],
  nowEpochMillis: number,
  staleAfterMillis: number,
): StudyFlowAttentionPlatformReliabilityDiagnostic {
  const stale = device.lastSeenAtEpochMillis != null && nowEpochMillis - device.lastSeenAtEpochMillis > staleAfterMillis;
  const tv = device.clientKind === "android_tv" || device.clientKind === "web_tv";
  const background = device.clientKind === "android"
    ? !!device.notificationPermissionGranted && (device.deliverySurfaces.includes("android_alarm") || device.deliverySurfaces.includes("system_notification"))
    : device.clientKind === "web"
      ? !!device.notificationPermissionGranted && device.deliverySurfaces.includes("web_push")
      : false;
  const status: StudyFlowAttentionPlatformReliabilityStatus = device.availability === "offline" || stale
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
    ownerRouteCount: audits.filter(audit => audit.owner?.deviceId === device.deviceId).length,
    mirrorRouteCount: audits.filter(audit => audit.mirrors.some(mirror => mirror.deviceId === device.deviceId)).length,
    targetedEntryCount: entries.filter(entry => entry.targetDeviceId === device.deviceId || (entry.occurrence.targetDeviceIds ?? []).includes(device.deviceId)).length,
    lastSeenAtEpochMillis: device.lastSeenAtEpochMillis,
    explanation,
  };
}

export function exportWebAttentionReliabilityJson(snapshot: StudyFlowAttentionReliabilityEvidenceSnapshot): string {
  return JSON.stringify(snapshot, null, 2);
}

export function exportWebAttentionReliabilityCsv(snapshot: StudyFlowAttentionReliabilityEvidenceSnapshot): string {
  const cell = (value: unknown) => {
    const text = value == null ? "" : String(value);
    return /[",\n\r]/.test(text) ? `"${text.split('"').join('""')}"` : text;
  };
  const rows = [["occurrence_id", "workspace_id", "category", "state", "policy_outcome", "route_reason", "owner_device", "mirror_devices", "scheduled_at", "updated_at", "delivered_at", "delivery_attempts"]];
  for (const entry of snapshot.evidence) rows.push([
    entry.occurrence.occurrenceId,
    entry.occurrence.workspaceId,
    entry.occurrence.category,
    entry.state,
    entry.policyAudit?.outcome ?? "",
    entry.routeAudit?.reason ?? "",
    entry.routeAudit?.owner?.deviceId ?? "",
    entry.routeAudit?.mirrors?.map(value => value.deviceId).join("|") ?? "",
    entry.occurrence.scheduledAtEpochMillis,
    entry.updatedAtEpochMillis,
    entry.deliveredAtEpochMillis ?? "",
    entry.deliveryAttempts,
  ] as unknown[] as string[]);
  return rows.map(row => row.map(cell).join(",")).join("\n") + "\n";
}
