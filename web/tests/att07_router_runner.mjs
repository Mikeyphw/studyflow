import assert from "node:assert/strict";
import {
  attentionDeviceFromWebPeer,
  createStudyFlowAttentionHandoffRequest,
  defaultAttentionSurfacesForClient,
  routeStudyFlowAttentionDelivery,
} from "../runtime/attention_router.js";

const occurrence = (id, policy = "active_then_primary", targetDeviceIds = []) => ({
  occurrenceId: `att07:${id}`,
  workspaceId: "workspace-main",
  category: "due_review",
  priority: "high",
  scheduledAtEpochMillis: 9000,
  originalScheduledAtEpochMillis: 9000,
  expiresAtEpochMillis: 20000,
  deliveryPolicy: policy,
  sourceDeviceId: "source",
  targetDeviceIds,
  actions: [{ kind: "open" }, { kind: "snooze" }, { kind: "move_to_device" }],
  payload: { title: "Review ready" },
});
const device = (deviceId, clientKind, extra = {}) => ({
  deviceId,
  displayName: deviceId,
  clientKind,
  availability: extra.foregroundActive ? "foreground" : "online",
  deliverySurfaces: defaultAttentionSurfacesForClient(clientKind),
  ...extra,
});

const active = routeStudyFlowAttentionDelivery(
  occurrence("active"),
  [device("phone", "android", { primaryUserDevice: true }), device("desktop", "desktop", { foregroundActive: true })],
  10000,
  { primaryDeviceId: "phone", foregroundDeviceId: "desktop" },
);
assert.equal(active.owner.deviceId, "desktop");
assert.equal(active.reason, "active_foreground");
assert.equal(active.deliverable, true);
assert.deepEqual(active.mirrors.map(item => item.deviceId), ["phone"]);

const pair = routeStudyFlowAttentionDelivery(
  occurrence("pair", "phone_and_desktop"),
  [device("phone", "android", { primaryUserDevice: true }), device("desktop", "desktop"), device("tv", "android_tv", { foregroundActive: true })],
  10000,
  { primaryDeviceId: "phone", maxMirrors: 3 },
);
assert.equal(pair.owner.deviceId, "phone");
assert.deepEqual(pair.mirrors.map(item => item.deviceId), ["desktop"]);
assert.equal(new Set(pair.targetDeviceIds).size, pair.targetDeviceIds.length);

const explicit = routeStudyFlowAttentionDelivery(
  occurrence("explicit", "all_capable", ["tv"]),
  [device("phone", "android"), device("tv", "android_tv", { foregroundActive: true })],
  10000,
);
assert.equal(explicit.owner.deviceId, "tv");
assert.equal(explicit.owner.surface, "foreground_banner");
assert.equal(explicit.reason, "explicit_target");

const suppressed = routeStudyFlowAttentionDelivery(
  occurrence("handled"),
  [device("phone", "android")],
  10000,
  {},
  { occurrence: occurrence("handled"), state: "acknowledged", updatedAtEpochMillis: 9000, targetDeviceId: "phone" },
);
assert.equal(suppressed.deliverable, false);
assert.equal(suppressed.reason, "ledger_already_handled");
assert.equal(suppressed.diagnostics.ledgerState, "acknowledged");

const none = routeStudyFlowAttentionDelivery(
  occurrence("none", "all_capable"),
  [
    device("web", "web", { lastSeenAtEpochMillis: 1000 }),
    device("phone", "android", { mutedUntilEpochMillis: 20000 }),
    device("tv", "web_tv", { foregroundActive: false }),
  ],
  10000,
  { staleAfterMillis: 1000 },
);
assert.equal(none.deliverable, false);
assert.equal(none.reason, "no_capable_device");

const peerDevice = attentionDeviceFromWebPeer({
  identity: {
    deviceId: "web-peer",
    displayName: "Web peer",
    clientKind: "web",
    protocolSupport: { major: 1, minMinor: 0, maxMinor: 0 },
    capabilities: ["workspace.sync"],
  },
}, { notificationPermissionGranted: true });
assert.equal(peerDevice.deviceId, "web-peer");
assert(peerDevice.deliverySurfaces.includes("web_push"));

const handoff = createStudyFlowAttentionHandoffRequest(occurrence("handoff"), "tv", "phone", 10000);
assert.equal(handoff.fromDeviceId, "tv");
assert.equal(handoff.toDeviceId, "phone");
assert.equal(handoff.action, "move_to_device");
console.log("ATT07 Web attention router tests passed");
