"use strict";

import type { StudyFlowAttentionCategory, StudyFlowAttentionLedgerEntry } from "./sflp_link";
import type { StudyFlowWebAttentionRuntime, StudyFlowWebAttentionSnapshot } from "./attention";
import type { StudyFlowPwaCapabilities } from "./pwa";

export type StudyFlowWebTvAttentionAction = "open" | "continue" | "snooze" | "dismiss" | "move_to_phone";
export type StudyFlowWebTvAttentionDeliveryMode = "foreground-only" | "designated-notifier-fallback";

export interface StudyFlowWebTvAttentionCapabilities {
  foregroundBanners: true;
  systemNotifications: false;
  backgroundAlarms: false;
  closedBrowserDelivery: false;
  notificationApiAvailable: boolean;
  serviceWorkerAvailable: boolean;
  designatedNotifierFallback: true;
  deliveryMode: StudyFlowWebTvAttentionDeliveryMode;
}

export interface StudyFlowWebTvAttentionBanner {
  occurrenceId: string;
  category: StudyFlowAttentionCategory;
  title: string;
  body: string;
  routeHash: string;
  actions: StudyFlowWebTvAttentionAction[];
  updatedAtEpochMillis: number;
}

export interface StudyFlowWebTvAttentionSnapshot {
  capabilities: StudyFlowWebTvAttentionCapabilities;
  activeBanner: StudyFlowWebTvAttentionBanner | null;
  recentBanners: StudyFlowWebTvAttentionBanner[];
  badgeCount: number;
  lastScanAtEpochMillis: number | null;
  focusProfile: string;
  focusOutcomes: string[];
  focusControlsReadOnly: true;
  source: "att10-web-runtime";
}

const TV_ACTION_LIMIT = 3;
const ACTIVE_STATES = new Set(["planned", "scheduled", "delivery_pending", "delivered", "acknowledged", "snoozed", "missed"]);

export function webTvAttentionCapabilities(capabilities: Pick<StudyFlowPwaCapabilities, "notifications" | "serviceWorker">): StudyFlowWebTvAttentionCapabilities {
  return {
    foregroundBanners: true,
    systemNotifications: false,
    backgroundAlarms: false,
    closedBrowserDelivery: false,
    notificationApiAvailable: capabilities.notifications,
    serviceWorkerAvailable: capabilities.serviceWorker,
    designatedNotifierFallback: true,
    deliveryMode: "foreground-only",
  };
}

export function webTvAttentionRouteForCategory(category: StudyFlowAttentionCategory): string {
  switch (category) {
    case "due_review": return "#reviews";
    case "planned_study":
    case "session_ending":
    case "session_completed": return "#session";
    case "exam_countdown": return "#progress";
    case "schedule_change": return "#schedule";
    default: return "#today";
  }
}

export function webTvAttentionActionsForCategory(category: StudyFlowAttentionCategory): StudyFlowWebTvAttentionAction[] {
  const actions: StudyFlowWebTvAttentionAction[] = category === "due_review"
    ? ["open", "snooze", "move_to_phone"]
    : category === "planned_study" || category === "session_ending" || category === "session_completed"
      ? ["continue", "snooze", "move_to_phone"]
      : ["open", "dismiss", "move_to_phone"];
  return actions.slice(0, TV_ACTION_LIMIT);
}

export function bannerFromWebAttentionEntry(entry: StudyFlowAttentionLedgerEntry): StudyFlowWebTvAttentionBanner {
  const payload = entry.occurrence.payload ?? {};
  const title = typeof payload.title === "string" ? payload.title : `StudyFlow ${entry.occurrence.category.replace(/_/g, " ")}`;
  const body = typeof payload.body === "string" ? payload.body : "Use the TV foreground banner, or continue this reminder on your designated phone/desktop/Web notifier.";
  const routeHash = typeof payload.routeHash === "string" ? payload.routeHash : webTvAttentionRouteForCategory(entry.occurrence.category);
  return {
    occurrenceId: entry.occurrence.occurrenceId,
    category: entry.occurrence.category,
    title,
    body,
    routeHash,
    actions: webTvAttentionActionsForCategory(entry.occurrence.category),
    updatedAtEpochMillis: entry.updatedAtEpochMillis,
  };
}

export function projectWebAttentionToTv(snapshot: StudyFlowWebAttentionSnapshot): StudyFlowWebTvAttentionSnapshot {
  const recentBanners = snapshot.entries
    .filter(entry => ACTIVE_STATES.has(entry.state))
    .slice(0, 8)
    .map(bannerFromWebAttentionEntry);
  return {
    capabilities: webTvAttentionCapabilities(snapshot.capabilities),
    activeBanner: recentBanners[0] ?? null,
    recentBanners,
    badgeCount: snapshot.badgeCount,
    lastScanAtEpochMillis: snapshot.lastScanAtEpochMillis,
    focusProfile: snapshot.preferences?.focusPolicy?.profile ?? "balanced",
    focusOutcomes: snapshot.entries.map(entry => entry.policyAudit?.outcome).filter((value): value is string => !!value).slice(0, 8),
    focusControlsReadOnly: true,
    source: "att10-web-runtime",
  };
}

export class StudyFlowWebTvAttentionRuntime {
  constructor(private readonly webRuntime: StudyFlowWebAttentionRuntime) {}

  snapshot(): StudyFlowWebTvAttentionSnapshot {
    return projectWebAttentionToTv(this.webRuntime.snapshot());
  }

  applyAction(occurrenceId: string, action: StudyFlowWebTvAttentionAction): void {
    const mapped = action === "move_to_phone" ? "open" : action === "continue" ? "open" : action;
    void this.webRuntime.applyAction(occurrenceId, mapped);
  }
}

export function installStudyFlowWebTvAttentionBridge(runtime: StudyFlowWebTvAttentionRuntime): () => void {
  if (typeof document === "undefined") return () => {};
  const root = document.documentElement;
  const render = () => {
    const snapshot = runtime.snapshot();
    root.dataset.studyflowTvAttention = snapshot.capabilities.deliveryMode;
    root.dataset.studyflowTvAttentionFallback = snapshot.capabilities.designatedNotifierFallback ? "designated-notifier" : "none";
    root.dataset.studyflowTvAttentionBadge = String(snapshot.badgeCount);
    root.dataset.studyflowTvAttentionFocusProfile = snapshot.focusProfile;
    root.dataset.studyflowTvAttentionFocusControls = snapshot.focusControlsReadOnly ? "remote-safe-read-only" : "local";
    globalThis.dispatchEvent?.(new CustomEvent("studyflow:tv-attention-snapshot", { detail: snapshot }));
  };
  const onAttention = () => render();
  render();
  globalThis.addEventListener?.("studyflow:attention-snapshot", onAttention as EventListener);
  globalThis.addEventListener?.("studyflow:attention-drain", onAttention as EventListener);
  return () => {
    globalThis.removeEventListener?.("studyflow:attention-snapshot", onAttention as EventListener);
    globalThis.removeEventListener?.("studyflow:attention-drain", onAttention as EventListener);
    delete root.dataset.studyflowTvAttention;
    delete root.dataset.studyflowTvAttentionFallback;
    delete root.dataset.studyflowTvAttentionBadge;
    delete root.dataset.studyflowTvAttentionFocusProfile;
    delete root.dataset.studyflowTvAttentionFocusControls;
  };
}
