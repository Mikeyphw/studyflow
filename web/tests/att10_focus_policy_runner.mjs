import assert from "node:assert/strict";
import {
  defaultWebAttentionFocusPolicy,
  evaluateWebAttentionFocusPolicy,
  quietContains,
} from "../runtime/attention_focus_policy.js";
import {
  defaultAttentionSurfacesForClient,
  routeStudyFlowAttentionDeliveryWithPolicy,
} from "../runtime/attention_router.js";
import { projectWebAttentionToTv } from "../runtime/tv_attention.js";

const occurrence = (id, category = "planned_study", priority = "normal") => ({
  occurrenceId: `att10:${id}`,
  workspaceId: "workspace-main",
  category,
  priority,
  scheduledAtEpochMillis: Date.parse("2026-09-12T03:00:00Z"),
  originalScheduledAtEpochMillis: Date.parse("2026-09-12T03:00:00Z"),
  expiresAtEpochMillis: Date.parse("2026-09-13T03:00:00Z"),
  timezoneId: "America/Sao_Paulo",
  deliveryPolicy: "active_then_primary",
});
const defaults = defaultWebAttentionFocusPolicy("UTC");
assert.deepEqual(
  defaults.categoryPolicies.find(value => value.category === "due_review"),
  { category: "due_review", mode: "mirror_if_unhandled", bypassQuietHours: false, mirrorAfterMinutes: 30 },
);
assert.equal(defaults.categoryPolicies.find(value => value.category === "daily_summary")?.mode, "silent");
assert.equal(defaults.categoryPolicies.find(value => value.category === "exam_countdown")?.mode, "require_user_action");

const policy = defaultWebAttentionFocusPolicy("America/Sao_Paulo");
policy.profile = "sleep_mode";
policy.quietWindow = { enabled: true, startMinute: 22 * 60, endMinute: 7 * 60, timezoneId: "America/Sao_Paulo" };
const local0030 = Date.parse("2026-09-12T03:30:00Z"); // 00:30 in Sao Paulo
assert.equal(quietContains(policy.quietWindow, local0030, "America/Sao_Paulo"), true);
const sleep = evaluateWebAttentionFocusPolicy(occurrence("sleep"), policy, local0030, null, "web");
assert.equal(sleep.outcome, "defer_until_quiet_end");
assert(sleep.deferredUntilEpochMillis > local0030);

const minimal = defaultWebAttentionFocusPolicy("UTC");
minimal.profile = "minimal_tv";
minimal.quietWindow = null;
const devices = [
  { deviceId: "tv", displayName: "TV", clientKind: "web_tv", availability: "foreground", foregroundActive: true, deliverySurfaces: defaultAttentionSurfacesForClient("web_tv") },
  { deviceId: "phone", displayName: "Phone", clientKind: "android", availability: "online", primaryUserDevice: true, deliverySurfaces: defaultAttentionSurfacesForClient("android") },
];
const route = routeStudyFlowAttentionDeliveryWithPolicy(occurrence("tv"), devices, local0030, minimal, { localDeviceId: "tv", foregroundDeviceId: "tv", primaryDeviceId: "phone", allowTvForegroundOwners: true }, null, "web_tv");
assert.equal(route.policyOutcome, "route_to_phone");
assert.equal(route.owner?.deviceId, "phone");

const mirror = defaultWebAttentionFocusPolicy("UTC");
mirror.quietWindow = null;
mirror.categoryPolicies = mirror.categoryPolicies.map(value => value.category === "due_review" ? { ...value, mode: "mirror_if_unhandled", mirrorAfterMinutes: 1 } : value);
const review = { ...occurrence("review", "due_review"), scheduledAtEpochMillis: 1_000, originalScheduledAtEpochMillis: 1_000, expiresAtEpochMillis: 500_000 };
const escalated = evaluateWebAttentionFocusPolicy(review, mirror, 120_000, null, "web");
assert.equal(escalated.outcome, "escalate_mirror");
assert.equal(escalated.requestedMirrorCount, 1);
const primaryOnlyReview = { ...review, deliveryPolicy: "primary_device" };
const escalatedRoute = routeStudyFlowAttentionDeliveryWithPolicy(
  primaryOnlyReview,
  [
    { deviceId: "phone", displayName: "Phone", clientKind: "android", availability: "online", primaryUserDevice: true, deliverySurfaces: defaultAttentionSurfacesForClient("android") },
    { deviceId: "desktop", displayName: "Desktop", clientKind: "desktop", availability: "online", notificationPermissionGranted: true, deliverySurfaces: defaultAttentionSurfacesForClient("desktop") },
  ],
  120_000,
  mirror,
  { primaryDeviceId: "phone", maxMirrors: 3 },
);
assert.equal(escalatedRoute.owner?.deviceId, "phone");
assert.deepEqual(escalatedRoute.mirrors.map(value => value.deviceId), ["desktop"]);

const compact = defaultWebAttentionFocusPolicy("UTC");
compact.quietWindow = null;
const compactOccurrence = { ...occurrence("compact", "sync_attention"), deliveryPolicy: "primary_device" };
const compactRoute = routeStudyFlowAttentionDeliveryWithPolicy(
  compactOccurrence,
  [{ deviceId: "web", displayName: "Web", clientKind: "web", availability: "online", notificationPermissionGranted: false, primaryUserDevice: true, deliverySurfaces: defaultAttentionSurfacesForClient("web") }],
  local0030,
  compact,
  { primaryDeviceId: "web" },
);
assert.equal(compactRoute.policyOutcome, "compact_only");
assert.equal(compactRoute.owner?.surface, "widget_or_badge");

const tvSnapshot = projectWebAttentionToTv({
  preferences: { focusPolicy: minimal },
  capabilities: { notifications: false, serviceWorker: false },
  entries: [{ occurrence: review, state: "scheduled", updatedAtEpochMillis: 120_000, deliveryAttempts: 0, policyAudit: { profile: "minimal_tv", outcome: "route_to_phone", evaluatedAtEpochMillis: 120_000, explanation: "remote safe" } }],
  badgeCount: 0,
  lastScanAtEpochMillis: 120_000,
});
assert.equal(tvSnapshot.focusControlsReadOnly, true);
assert.equal(tvSnapshot.capabilities.closedBrowserDelivery, false);
assert.equal(tvSnapshot.focusProfile, "minimal_tv");
console.log("ATT10 Web focus policy tests passed");
