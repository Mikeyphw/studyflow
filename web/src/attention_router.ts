"use strict";

import type {
  StudyFlowAttentionDeliveryPolicy,
  StudyFlowAttentionDeliveryState,
  StudyFlowAttentionLedgerEntry,
  StudyFlowAttentionOccurrence,
  StudyFlowAttentionPriority,
  StudyFlowWebLinkPeer,
} from "./sflp_link";
import {
  evaluateWebAttentionFocusPolicy,
  type StudyFlowAttentionFocusPolicy,
  type StudyFlowAttentionPolicyOutcome,
} from "./attention_focus_policy";

export type StudyFlowAttentionDeliverySurface =
  | "android_alarm"
  | "system_notification"
  | "desktop_tray"
  | "web_push"
  | "foreground_banner"
  | "widget_or_badge"
  | "ledger_only";
export type StudyFlowAttentionDeviceAvailability = "online" | "foreground" | "away" | "offline";
export type StudyFlowAttentionDeliveryRole = "owner" | "mirror";
export type StudyFlowAttentionRouteReason =
  | "explicit_target"
  | "local_only"
  | "primary_device"
  | "active_foreground"
  | "phone_desktop_pair"
  | "all_capable"
  | "fallback_best_capable"
  | "no_capable_device"
  | "ledger_already_handled";

export interface StudyFlowAttentionDeviceCapability {
  deviceId: string;
  displayName: string;
  clientKind: "android" | "desktop" | "web" | "android_tv" | "web_tv";
  availability: StudyFlowAttentionDeviceAvailability;
  deliverySurfaces: StudyFlowAttentionDeliverySurface[];
  foregroundActive?: boolean;
  notificationPermissionGranted?: boolean;
  primaryUserDevice?: boolean;
  designatedNotifier?: boolean;
  batteryConstrained?: boolean;
  lastSeenAtEpochMillis?: number;
  mutedUntilEpochMillis?: number;
}

export interface StudyFlowAttentionRoutingContext {
  localDeviceId?: string;
  primaryDeviceId?: string;
  foregroundDeviceId?: string;
  designatedNotifierDeviceIds?: string[];
  mutedDeviceIds?: string[];
  maxMirrors?: number;
  staleAfterMillis?: number;
  allowTvForegroundOwners?: boolean;
}

export interface StudyFlowAttentionDeliveryTarget {
  deviceId: string;
  displayName: string;
  clientKind: StudyFlowAttentionDeviceCapability["clientKind"];
  role: StudyFlowAttentionDeliveryRole;
  surface: StudyFlowAttentionDeliverySurface;
  score: number;
}

export interface StudyFlowAttentionRouteDiagnostics {
  candidateCount: number;
  eligibleCount: number;
  suppressedDeviceIds: string[];
  ledgerState?: StudyFlowAttentionDeliveryState;
  explanation: string;
  policyOutcome?: StudyFlowAttentionPolicyOutcome;
  deferredUntilEpochMillis?: number;
}

export interface StudyFlowAttentionDeliveryRoute {
  occurrenceId: string;
  workspaceId: string;
  policy: StudyFlowAttentionDeliveryPolicy;
  reason: StudyFlowAttentionRouteReason;
  decidedAtEpochMillis: number;
  owner: StudyFlowAttentionDeliveryTarget | null;
  mirrors: StudyFlowAttentionDeliveryTarget[];
  diagnostics: StudyFlowAttentionRouteDiagnostics;
  deliverable: boolean;
  targetDeviceIds: string[];
  policyOutcome?: StudyFlowAttentionPolicyOutcome;
  deliverSilently?: boolean;
  compactOnly?: boolean;
  requiresUserAction?: boolean;
}

/** ATT12 call-site guard: route computation is not enough unless the surface checks ownership. */
export function studyFlowAttentionRouteTargetsDevice(route: StudyFlowAttentionDeliveryRoute, deviceId: string): boolean {
  return route.deliverable && route.targetDeviceIds.includes(deviceId);
}

export interface StudyFlowAttentionHandoffRequest {
  handoffId: string;
  occurrenceId: string;
  workspaceId: string;
  fromDeviceId: string;
  toDeviceId: string;
  action: "move_to_device" | "open" | "continue";
  requestedAtEpochMillis: number;
}

const DEFAULT_STALE_MS = 180_000;
const DEFAULT_MIRRORS = 2;
const HANDLED_STATES = new Set<StudyFlowAttentionDeliveryState>([
  "delivered", "acknowledged", "started", "completed", "snoozed", "rescheduled",
  "dismissed", "missed", "expired", "cancelled",
]);

export function defaultAttentionSurfacesForClient(kind: StudyFlowAttentionDeviceCapability["clientKind"]): StudyFlowAttentionDeliverySurface[] {
  switch (kind) {
    case "android": return ["android_alarm", "system_notification", "widget_or_badge"];
    case "desktop": return ["system_notification", "desktop_tray"];
    case "web": return ["web_push", "system_notification", "widget_or_badge"];
    case "android_tv":
    case "web_tv": return ["foreground_banner", "ledger_only"];
  }
}

export function attentionDeviceFromWebPeer(
  peer: StudyFlowWebLinkPeer,
  input: Partial<Omit<StudyFlowAttentionDeviceCapability, "deviceId" | "displayName" | "clientKind">> = {},
): StudyFlowAttentionDeviceCapability {
  return {
    deviceId: peer.identity.deviceId,
    displayName: peer.identity.displayName,
    clientKind: peer.identity.clientKind as StudyFlowAttentionDeviceCapability["clientKind"],
    availability: input.availability ?? (input.foregroundActive ? "foreground" : "online"),
    deliverySurfaces: input.deliverySurfaces ?? defaultAttentionSurfacesForClient(peer.identity.clientKind as StudyFlowAttentionDeviceCapability["clientKind"]),
    foregroundActive: input.foregroundActive,
    notificationPermissionGranted: input.notificationPermissionGranted,
    primaryUserDevice: input.primaryUserDevice,
    designatedNotifier: input.designatedNotifier,
    batteryConstrained: input.batteryConstrained,
    lastSeenAtEpochMillis: input.lastSeenAtEpochMillis,
    mutedUntilEpochMillis: input.mutedUntilEpochMillis,
  };
}

export function routeStudyFlowAttentionDelivery(
  occurrence: StudyFlowAttentionOccurrence,
  devices: StudyFlowAttentionDeviceCapability[],
  nowEpochMillis: number,
  context: StudyFlowAttentionRoutingContext = {},
  ledgerEntry?: StudyFlowAttentionLedgerEntry | null,
): StudyFlowAttentionDeliveryRoute {
  if (new Set(devices.map(device => device.deviceId)).size !== devices.length) {
    throw new Error("Attention router devices must be unique");
  }
  if (ledgerEntry && HANDLED_STATES.has(ledgerEntry.state)) {
    return noRoute(occurrence, nowEpochMillis, devices.length, "ledger_already_handled", {
      explanation: `Already handled by ${ledgerEntry.targetDeviceId ?? "ledger"}.`,
      ledgerState: ledgerEntry.state,
    });
  }
  const normalized = normalizeContext(context);
  const available = devices.filter(device => canOwn(device, occurrence, nowEpochMillis, normalized));
  const targetDeviceIds = occurrence.targetDeviceIds ?? [];
  if (targetDeviceIds.length) {
    return chooseRoute(occurrence, devices, available.filter(device => targetDeviceIds.includes(device.deviceId)), [], nowEpochMillis, normalized, "explicit_target", "Explicit target device list controls ATT07 ownership.");
  }
  switch (occurrence.deliveryPolicy ?? "active_then_primary") {
    case "local_only": {
      const localId = normalized.localDeviceId ?? occurrence.sourceDeviceId;
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

export function routeStudyFlowAttentionDeliveryWithPolicy(
  occurrence: StudyFlowAttentionOccurrence,
  devices: StudyFlowAttentionDeviceCapability[],
  nowEpochMillis: number,
  focusPolicy: StudyFlowAttentionFocusPolicy,
  context: StudyFlowAttentionRoutingContext = {},
  ledgerEntry?: StudyFlowAttentionLedgerEntry | null,
  localClientKind: StudyFlowAttentionDeviceCapability["clientKind"] = "web",
): StudyFlowAttentionDeliveryRoute {
  const policyDecision = evaluateWebAttentionFocusPolicy(occurrence, focusPolicy, nowEpochMillis, ledgerEntry, localClientKind);
  if (policyDecision.outcome === "suppress_duplicate" || policyDecision.outcome === "defer_until_quiet_end") {
    const base = noRoute(occurrence, nowEpochMillis, devices.length, policyDecision.outcome === "suppress_duplicate" ? "ledger_already_handled" : "no_capable_device", {
      explanation: policyDecision.explanation,
      ledgerState: ledgerEntry?.state,
    });
    return {
      ...base,
      policyOutcome: policyDecision.outcome,
      diagnostics: { ...base.diagnostics, policyOutcome: policyDecision.outcome, deferredUntilEpochMillis: policyDecision.deferredUntilEpochMillis },
    };
  }
  let policyDevices = devices;
  if (policyDecision.outcome === "route_to_phone") policyDevices = devices.filter(device => device.clientKind === "android");
  if (policyDecision.outcome === "compact_only") policyDevices = devices
    .filter(device => device.deliverySurfaces.includes("widget_or_badge"));
  const normalizedContext = {
    ...context,
    maxMirrors: policyDecision.outcome === "escalate_mirror"
      ? Math.min(context.maxMirrors ?? 2, policyDecision.requestedMirrorCount)
      : context.maxMirrors,
  };
  const base = routeStudyFlowAttentionDelivery(occurrence, policyDevices, nowEpochMillis, normalizedContext, ledgerEntry);
  const owner = policyDecision.compactOnly && base.owner
    ? { ...base.owner, surface: "widget_or_badge" as StudyFlowAttentionDeliverySurface }
    : base.owner;
  let mirrors = policyDecision.compactOnly
    ? base.mirrors.map(mirror => ({ ...mirror, surface: "widget_or_badge" as StudyFlowAttentionDeliverySurface }))
    : base.mirrors;
  if (policyDecision.outcome === "escalate_mirror" && owner) {
    const normalized = normalizeContext(normalizedContext);
    const explicitTargets = new Set(occurrence.targetDeviceIds ?? []);
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
  const targetDeviceIds = [owner?.deviceId, ...mirrors.map(mirror => mirror.deviceId)]
    .filter((value): value is string => !!value);
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

export function createStudyFlowAttentionHandoffRequest(
  occurrence: StudyFlowAttentionOccurrence,
  fromDeviceId: string,
  toDeviceId: string,
  requestedAtEpochMillis: number,
  action: "move_to_device" | "open" | "continue" = "move_to_device",
): StudyFlowAttentionHandoffRequest {
  if (fromDeviceId === toDeviceId) throw new Error("Attention handoff requires two distinct devices");
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

function chooseRoute(
  occurrence: StudyFlowAttentionOccurrence,
  allCandidates: StudyFlowAttentionDeviceCapability[],
  candidates: StudyFlowAttentionDeviceCapability[],
  mirrorPool: StudyFlowAttentionDeviceCapability[],
  nowEpochMillis: number,
  context: Required<StudyFlowAttentionRoutingContext>,
  reason: StudyFlowAttentionRouteReason,
  explanation: string,
): StudyFlowAttentionDeliveryRoute {
  const ranked = [...candidates].sort((a, b) => compareDevice(a, b, occurrence, nowEpochMillis, context));
  const ownerDevice = ranked[0];
  if (!ownerDevice) return noRoute(occurrence, nowEpochMillis, allCandidates.length, "no_capable_device", { explanation });
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
    policy: occurrence.deliveryPolicy ?? "active_then_primary",
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

function noRoute(
  occurrence: StudyFlowAttentionOccurrence,
  nowEpochMillis: number,
  candidateCount: number,
  reason: StudyFlowAttentionRouteReason,
  options: { explanation: string; ledgerState?: StudyFlowAttentionDeliveryState },
): StudyFlowAttentionDeliveryRoute {
  return {
    occurrenceId: occurrence.occurrenceId,
    workspaceId: occurrence.workspaceId,
    policy: occurrence.deliveryPolicy ?? "active_then_primary",
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

function normalizeContext(context: StudyFlowAttentionRoutingContext): Required<StudyFlowAttentionRoutingContext> {
  return {
    localDeviceId: context.localDeviceId ?? "",
    primaryDeviceId: context.primaryDeviceId ?? "",
    foregroundDeviceId: context.foregroundDeviceId ?? "",
    designatedNotifierDeviceIds: context.designatedNotifierDeviceIds ?? [],
    mutedDeviceIds: context.mutedDeviceIds ?? [],
    maxMirrors: Math.max(0, Math.min(8, context.maxMirrors ?? DEFAULT_MIRRORS)),
    staleAfterMillis: Math.max(1, context.staleAfterMillis ?? DEFAULT_STALE_MS),
    allowTvForegroundOwners: context.allowTvForegroundOwners !== false,
  };
}

function bestSurface(device: StudyFlowAttentionDeviceCapability, now: number, context: Required<StudyFlowAttentionRoutingContext>): StudyFlowAttentionDeliverySurface | null {
  if (device.availability === "offline") return null;
  if (context.mutedDeviceIds.includes(device.deviceId)) return null;
  if (device.mutedUntilEpochMillis && device.mutedUntilEpochMillis > now) return null;
  if (device.lastSeenAtEpochMillis != null && now - device.lastSeenAtEpochMillis > context.staleAfterMillis) return null;
  const surfaces = device.deliverySurfaces;
  if (surfaces.includes("android_alarm")) return "android_alarm";
  if (surfaces.includes("web_push") && device.notificationPermissionGranted !== false) return "web_push";
  if (surfaces.includes("system_notification") && device.notificationPermissionGranted !== false) return "system_notification";
  if (surfaces.includes("desktop_tray") && device.foregroundActive) return "desktop_tray";
  if (surfaces.includes("foreground_banner") && device.foregroundActive) return "foreground_banner";
  if (surfaces.includes("widget_or_badge")) return "widget_or_badge";
  return null;
}

function canOwn(device: StudyFlowAttentionDeviceCapability, occurrence: StudyFlowAttentionOccurrence, now: number, context: Required<StudyFlowAttentionRoutingContext>): boolean {
  if (occurrence.expiresAtEpochMillis != null && now > occurrence.expiresAtEpochMillis) return false;
  const surface = bestSurface(device, now, context);
  if (!surface) return false;
  if (!context.allowTvForegroundOwners && isTv(device.clientKind) && surface === "foreground_banner") return false;
  return true;
}

function score(device: StudyFlowAttentionDeviceCapability, occurrence: StudyFlowAttentionOccurrence, now: number, context: Required<StudyFlowAttentionRoutingContext>): number {
  const surface = bestSurface(device, now, context);
  if (!surface) return Number.NEGATIVE_INFINITY;
  let value = surfaceScore(surface) + clientScore(device.clientKind) + priorityScore(occurrence.priority ?? "normal");
  if (device.deviceId === context.primaryDeviceId) value += 160;
  if (device.deviceId === context.localDeviceId) value += 25;
  if (device.foregroundActive || device.availability === "foreground") value += 90;
  if (device.designatedNotifier || context.designatedNotifierDeviceIds.includes(device.deviceId)) value += 120;
  if ((occurrence.targetDeviceIds ?? []).includes(device.deviceId)) value += 240;
  if (device.batteryConstrained) value -= 80;
  return value;
}

function surfaceScore(surface: StudyFlowAttentionDeliverySurface): number {
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

function clientScore(kind: StudyFlowAttentionDeviceCapability["clientKind"]): number {
  switch (kind) {
    case "android": return 80;
    case "desktop": return 70;
    case "web": return 50;
    case "android_tv": return 20;
    case "web_tv": return 10;
  }
}

function priorityScore(priority: StudyFlowAttentionPriority): number {
  switch (priority) {
    case "low": return -20;
    case "normal": return 0;
    case "high": return 40;
    case "time_sensitive": return 80;
  }
}

function compareDevice(a: StudyFlowAttentionDeviceCapability, b: StudyFlowAttentionDeviceCapability, occurrence: StudyFlowAttentionOccurrence, now: number, context: Required<StudyFlowAttentionRoutingContext>): number {
  const delta = score(b, occurrence, now, context) - score(a, occurrence, now, context);
  if (delta !== 0) return delta;
  const seen = (b.lastSeenAtEpochMillis ?? 0) - (a.lastSeenAtEpochMillis ?? 0);
  if (seen !== 0) return seen;
  return a.deviceId.localeCompare(b.deviceId);
}

function toTarget(device: StudyFlowAttentionDeviceCapability, occurrence: StudyFlowAttentionOccurrence, now: number, context: Required<StudyFlowAttentionRoutingContext>, role: StudyFlowAttentionDeliveryRole): StudyFlowAttentionDeliveryTarget {
  const surface = bestSurface(device, now, context);
  if (!surface) throw new Error("Eligible attention device lost its route surface");
  return {
    deviceId: device.deviceId,
    displayName: device.displayName,
    clientKind: device.clientKind,
    role,
    surface,
    score: score(device, occurrence, now, context),
  };
}

function preferredFallback(devices: StudyFlowAttentionDeviceCapability[], context: Required<StudyFlowAttentionRoutingContext>): StudyFlowAttentionDeviceCapability[] {
  const designated = devices.filter(device => device.designatedNotifier || context.designatedNotifierDeviceIds.includes(device.deviceId));
  return designated.length ? designated : devices;
}

function isTv(kind: StudyFlowAttentionDeviceCapability["clientKind"]): boolean {
  return kind === "android_tv" || kind === "web_tv";
}

function stableId(parts: string[]): string {
  const readable = parts.join(":").replace(/[^A-Za-z0-9._:-]/g, "-");
  if (readable.length > 0 && readable.length <= 128) return readable;
  let hash = 2166136261;
  for (const char of parts.join("\u001f")) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return `attn-${(hash >>> 0).toString(16).padStart(8, "0")}`;
}
