import assert from "node:assert/strict";
import {
  defaultWebCompactCompanionProfiles,
  projectAttentionRouteToCompactCompanions,
  projectWebAttentionToCompactCompanions,
} from "../runtime/compact_attention.js";

const occurrence = {
  occurrenceId: "att08:review",
  workspaceId: "workspace-main",
  category: "due_review",
  priority: "high",
  scheduledAtEpochMillis: 9000,
  deliveryPolicy: "primary_device",
  actions: [{ kind: "review_now" }, { kind: "snooze" }, { kind: "move_to_device" }],
  payload: { title: "Mock exam law review", body: "Review flashcards now" },
};
const entry = { occurrence, state: "scheduled", updatedAtEpochMillis: 9500, deliveryAttempts: 0 };
const route = {
  occurrenceId: occurrence.occurrenceId,
  workspaceId: occurrence.workspaceId,
  policy: "primary_device",
  reason: "primary_device",
  decidedAtEpochMillis: 10000,
  owner: { deviceId: "phone", displayName: "Phone", clientKind: "android", role: "owner", surface: "android_alarm", score: 1200 },
  mirrors: [],
  diagnostics: { candidateCount: 1, eligibleCount: 1, suppressedDeviceIds: [], explanation: "test" },
  deliverable: true,
  targetDeviceIds: ["phone"],
};

const projection = projectAttentionRouteToCompactCompanions(entry, route, [
  { companionId: "watch", displayName: "Watch", parentDeviceId: "phone", formFactor: "wearable", availability: "connected", maxActions: 3, canHandoff: true, canVibrate: true, canShowSensitiveText: false, lastSeenAtEpochMillis: 9950 },
  { companionId: "desktop-mini", displayName: "Desktop mini", parentDeviceId: "desktop", formFactor: "desktop_mini", availability: "connected", maxActions: 3, canHandoff: true, canVibrate: false, canShowSensitiveText: true, lastSeenAtEpochMillis: 9950 },
], 10000);
assert.equal(projection.deliverable, true);
assert.equal(projection.cards.length, 1);
assert.equal(projection.cards[0].title, "Review due");
assert.equal(projection.cards[0].sensitiveTextHidden, true);
assert.deepEqual(projection.cards[0].actions.map(action => action.kind), ["review_now", "snooze", "move_to_device"]);
assert.equal(projection.diagnostics.suppressedCompanionIds[0], "desktop-mini");

const handled = projectAttentionRouteToCompactCompanions({ ...entry, state: "acknowledged" }, route, defaultWebCompactCompanionProfiles("phone", 10000), 10000);
assert.equal(handled.deliverable, false);
assert.match(handled.diagnostics.explanation, /suppress replay/);

const snapshotProjection = projectWebAttentionToCompactCompanions({
  permission: "granted",
  preferences: {},
  capabilities: { notifications: true, serviceWorker: true, push: true, badge: true, persistentStorage: false, installPrompt: false, secureContext: true },
  backgroundDelivery: "push-ready",
  pushSubscription: null,
  entries: [entry],
  upcomingCount: 1,
  recentCount: 1,
  badgeCount: 1,
  lastScanAtEpochMillis: 10000,
  running: true,
  routeSummary: { localDeviceId: "phone", deliverableCount: 1, ownedCount: 1, mirroredCount: 0, suppressedCount: 0, routes: [route] },
});
assert.equal(snapshotProjection.cards[0].companionId, "web-compact-badge");
assert.equal(snapshotProjection.cards[1].companionId, "web-compact-panel");
console.log("ATT08 Web compact companion attention tests passed");
