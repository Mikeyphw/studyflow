"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StudyFlowWebPresenceRuntime = exports.PRESENCE01_DEFAULT_POLICY = void 0;
exports.PRESENCE01_DEFAULT_POLICY = { staleAfterMillis: 45000, expiryMillis: 180000 };
const MAX_RECORDS = 128;
function randomToken(prefix) {
    var _a, _b;
    const bytes = new Uint8Array(16);
    (_b = (_a = globalThis.crypto) === null || _a === void 0 ? void 0 : _a.getRandomValues) === null || _b === void 0 ? void 0 : _b.call(_a, bytes);
    const suffix = [...bytes].map(value => value.toString(16).padStart(2, "0")).join("") || Math.random().toString(16).slice(2);
    return `${prefix}-${suffix}`;
}
function capabilitiesAllow(required, peer) { const caps = new Set(peer.capabilities); return required.every(cap => caps.has(cap)); }
function key(record) { return `${record.workspaceId}|${record.sourceDevice.deviceId}|${record.instanceId}`; }
function clone(value) { return JSON.parse(JSON.stringify(value)); }
class StudyFlowWebPresenceRuntime {
    constructor(localDevice, now = () => Date.now(), policy = exports.PRESENCE01_DEFAULT_POLICY) {
        this.localDevice = localDevice;
        this.now = now;
        this.policy = policy;
        this.records = new Map();
        this.localState = new Map();
        this.instanceId = randomToken("instance");
        this.sessionId = randomToken("session");
        if (policy.staleAfterMillis <= 0 || policy.expiryMillis <= policy.staleAfterMillis)
            throw new Error("Invalid presence expiry policy");
        this.sessionStartedAtEpochMillis = this.now();
    }
    announce(workspaceId, availability, activityHint, visibilityCapabilities = []) {
        var _a;
        if (!workspaceId.trim())
            throw new Error("Presence workspace must not be blank");
        if (activityHint && activityHint.requiredCapabilities.length === 0)
            throw new Error("Presence activity hint requires capabilities");
        const previous = this.localState.get(workspaceId);
        const state = { sequence: ((_a = previous === null || previous === void 0 ? void 0 : previous.sequence) !== null && _a !== void 0 ? _a : 0) + 1, availability, activityHint, visibilityCapabilities: [...new Set(visibilityCapabilities)] };
        this.localState.set(workspaceId, state);
        const record = { workspaceId, sourceDevice: this.localDevice, instanceId: this.instanceId, sessionId: this.sessionId, sessionStartedAtEpochMillis: this.sessionStartedAtEpochMillis, sequence: state.sequence, availability, observedAtEpochMillis: this.now(), activityHint, visibilityCapabilities: state.visibilityCapabilities, trace: { sourceDeviceId: this.localDevice.deviceId, visitedDeviceIds: [this.localDevice.deviceId], hopLimit: 8 } };
        this.records.set(key(record), clone(record));
        return clone(record);
    }
    ensureOnline(workspaceId) {
        var _a, _b;
        const current = this.localState.get(workspaceId);
        return this.announce(workspaceId, (_a = current === null || current === void 0 ? void 0 : current.availability) !== null && _a !== void 0 ? _a : "online", current === null || current === void 0 ? void 0 : current.activityHint, (_b = current === null || current === void 0 ? void 0 : current.visibilityCapabilities) !== null && _b !== void 0 ? _b : []);
    }
    ingest(record, authenticatedPeerDeviceId, trustedSourceDeviceIds, nowEpochMillis = this.now()) {
        this.expire(nowEpochMillis);
        if (nowEpochMillis - record.observedAtEpochMillis > this.policy.expiryMillis)
            return "expired";
        if (record.sourceDevice.deviceId === this.localDevice.deviceId || record.trace.visitedDeviceIds.includes(this.localDevice.deviceId))
            return "loop";
        if (record.trace.visitedDeviceIds[record.trace.visitedDeviceIds.length - 1] !== authenticatedPeerDeviceId)
            return "untrusted";
        if (!trustedSourceDeviceIds.has(record.sourceDevice.deviceId))
            return "untrusted";
        const current = this.records.get(key(record));
        if (current) {
            if (current.sessionId === record.sessionId) {
                if (record.sequence === current.sequence)
                    return "duplicate";
                if (record.sequence < current.sequence)
                    return "stale";
            }
            else if (record.sessionStartedAtEpochMillis < current.sessionStartedAtEpochMillis ||
                (record.sessionStartedAtEpochMillis === current.sessionStartedAtEpochMillis && record.sessionId <= current.sessionId))
                return "stale";
        }
        if (record.trace.visitedDeviceIds.length >= record.trace.hopLimit)
            return "stale";
        const relayed = clone(record);
        relayed.trace.visitedDeviceIds = [...relayed.trace.visitedDeviceIds, this.localDevice.deviceId];
        this.records.set(key(relayed), relayed);
        return "accepted";
    }
    recordsForPeer(workspaceId, peer, nowEpochMillis = this.now()) {
        this.expire(nowEpochMillis);
        return [...this.records.values()]
            .filter(record => record.workspaceId === workspaceId)
            .filter(record => record.sourceDevice.deviceId !== peer.deviceId)
            .filter(record => !record.trace.visitedDeviceIds.includes(peer.deviceId))
            .filter(record => capabilitiesAllow(record.visibilityCapabilities, peer))
            .sort((a, b) => a.sourceDevice.deviceId.localeCompare(b.sourceDevice.deviceId) || a.instanceId.localeCompare(b.instanceId))
            .slice(0, MAX_RECORDS).map(clone);
    }
    exchangeForPeer(workspaceId, peer, nowEpochMillis = this.now()) {
        this.ensureOnline(workspaceId);
        return { records: this.recordsForPeer(workspaceId, peer, nowEpochMillis) };
    }
    acceptExchange(workspaceId, authenticatedPeerDeviceId, exchange, trustedSourceDeviceIds, nowEpochMillis = this.now()) {
        if (exchange.records.length > MAX_RECORDS)
            throw new Error("Presence exchange is too large");
        return exchange.records.filter(record => record.workspaceId === workspaceId).map(record => this.ingest(record, authenticatedPeerDeviceId, trustedSourceDeviceIds, nowEpochMillis));
    }
    views(workspaceId, viewer = this.localDevice, nowEpochMillis = this.now()) {
        this.expire(nowEpochMillis);
        return [...this.records.values()].filter(record => record.workspaceId === workspaceId && capabilitiesAllow(record.visibilityCapabilities, viewer)).map(record => ({
            workspaceId: record.workspaceId,
            sourceDevice: clone(record.sourceDevice),
            instanceId: record.instanceId,
            status: this.status(record, nowEpochMillis),
            observedAtEpochMillis: record.observedAtEpochMillis,
            activityHint: record.activityHint && capabilitiesAllow(record.activityHint.requiredCapabilities, viewer) ? clone(record.activityHint) : undefined,
        })).sort((a, b) => a.sourceDevice.deviceId.localeCompare(b.sourceDevice.deviceId) || a.instanceId.localeCompare(b.instanceId));
    }
    forgetPeer(deviceId) { for (const [entryKey, record] of this.records)
        if (record.sourceDevice.deviceId === deviceId)
            this.records.delete(entryKey); }
    status(record, nowEpochMillis) {
        if (record.availability === "offline")
            return "offline";
        if (nowEpochMillis - record.observedAtEpochMillis > this.policy.staleAfterMillis)
            return "stale";
        return record.availability;
    }
    expire(nowEpochMillis) { for (const [entryKey, record] of this.records)
        if (nowEpochMillis - record.observedAtEpochMillis > this.policy.expiryMillis)
            this.records.delete(entryKey); }
}
exports.StudyFlowWebPresenceRuntime = StudyFlowWebPresenceRuntime;
