import assert from "node:assert/strict";
import {
  analyzeWebAttentionReliability,
  exportWebAttentionReliabilityCsv,
  exportWebAttentionReliabilityJson,
  routeAuditFromDeliveryRoute,
} from "../runtime/attention_reliability.js";
import { routeStudyFlowAttentionDeliveryWithPolicy } from "../runtime/attention_router.js";
import { defaultWebAttentionFocusPolicy } from "../runtime/attention_focus_policy.js";

const occurrence = {
  occurrenceId: "att11:review",
  workspaceId: "workspace-main",
  category: "due_review",
  priority: "normal",
  scheduledAtEpochMillis: 1_000,
  originalScheduledAtEpochMillis: 1_000,
  expiresAtEpochMillis: 1_000_000,
  deliveryPolicy: "active_then_primary",
};
const webNoPush = {
  deviceId: "web",
  displayName: "Web",
  clientKind: "web",
  availability: "foreground",
  foregroundActive: true,
  notificationPermissionGranted: true,
  primaryUserDevice: true,
  designatedNotifier: true,
  deliverySurfaces: ["system_notification", "widget_or_badge"],
  lastSeenAtEpochMillis: 120_000,
};
const route = routeStudyFlowAttentionDeliveryWithPolicy(
  occurrence,
  [webNoPush],
  120_000,
  defaultWebAttentionFocusPolicy("UTC"),
  { localDeviceId: "web", primaryDeviceId: "web" },
);
const audit = routeAuditFromDeliveryRoute(route);
assert.equal(audit.owner?.deviceId, "web");
const ledger = {
  schemaVersion: 1,
  entries: [{
    occurrence,
    state: "dismissed",
    updatedAtEpochMillis: 130_000,
    deliveredAtEpochMillis: 120_000,
    acknowledgedAtEpochMillis: 125_000,
    deliveryAttempts: 1,
    policyAudit: { profile: "balanced", outcome: "escalate_mirror", evaluatedAtEpochMillis: 120_000, explanation: "test" },
    routeAudit: audit,
  }],
  lastSequenceBySource: {},
  legacyDeliveredOccurrenceIds: [],
};
const snapshot = analyzeWebAttentionReliability(ledger, [webNoPush], 130_000, "workspace-main");
assert.equal(snapshot.analytics.totalCount, 1);
assert.equal(snapshot.analytics.dismissedCount, 1);
assert.equal(snapshot.analytics.escalatedMirrorCount, 1);
assert.equal(snapshot.analytics.auditedRouteCount, 1);
assert.equal(snapshot.platforms[0].status, "foreground_only");
assert.equal(snapshot.platforms[0].truthfulBackgroundDelivery, false);
const pushReady = analyzeWebAttentionReliability(ledger, [{ ...webNoPush, deliverySurfaces: ["web_push", "system_notification", "widget_or_badge"] }], 130_000);
assert.equal(pushReady.platforms[0].status, "ready");
assert.equal(pushReady.platforms[0].truthfulBackgroundDelivery, true);
assert.match(exportWebAttentionReliabilityJson(snapshot), /"routeAudits"/);
assert.match(exportWebAttentionReliabilityCsv(snapshot), /occurrence_id,workspace_id/);
assert.match(exportWebAttentionReliabilityCsv(snapshot), /att11:review/);
console.log("ATT11 Web reliability evidence tests passed");
