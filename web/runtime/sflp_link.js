"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StudyFlowWebLinkPeer = exports.StudyFlowWebManualDiscovery = exports.StudyFlowWebSocketTransportAdapter = exports.StudyFlowWebPeerPersistence = void 0;
exports.installStudyFlowWebSflpPeer = installStudyFlowWebSflpPeer;
const STORAGE_KEY = "studyflow.sflp.web.peer.v1";
const PAIRING_TTL_MILLIS = 120000;
const TRUST_SECRET_HEX_LENGTH = 64;
const PROTOCOL_SUPPORT = { major: 1, minMinor: 0, maxMinor: 0 };
const WEB_CAPABILITIES = ["workspace.read", "workspace.write", "workspace.sync", "plan.read", "plan.write", "schedule.read", "schedule.write", "session.execute", "session.control", "session.present", "question.answer", "review.execute", "goal.read", "goal.complete", "library.read", "library.present", "document.read", "document.present", "writing.read", "writing.edit", "analytics.read", "backup.export", "backup.import", "device.control", "device.presentation_source", "text_input.provide", "keyboard.input", "touch.input"];
const WEB_TV_CAPABILITIES = ["workspace.read", "workspace.sync", "schedule.read", "session.control", "session.present", "question.answer", "review.execute", "goal.read", "library.read", "library.present", "document.read", "document.present", "analytics.read", "device.control", "device.presentation_sink", "dpad.input", "audio.output", "video.output"];
function randomHex(bytes) { var _a, _b; const buffer = new Uint8Array(bytes); (_b = (_a = globalThis.crypto) === null || _a === void 0 ? void 0 : _a.getRandomValues) === null || _b === void 0 ? void 0 : _b.call(_a, buffer); return [...buffer].map(x => x.toString(16).padStart(2, "0")).join("") || Math.random().toString(16).slice(2).padEnd(bytes * 2, "0").slice(0, bytes * 2); }
function sixDigitCode() { return String(Math.floor(Math.random() * 900000) + 100000); }
function normalizeEndpoint(uri) { const text = uri.trim(); if (!text)
    throw new Error("SFLP endpoint cannot be blank"); if (text.startsWith("ws://") || text.startsWith("wss://"))
    return { kind: "websocket", uri: text, lastSeenAtEpochMillis: Date.now() }; if (text.startsWith("sflp+relay://"))
    return { kind: "relay", uri: text, lastSeenAtEpochMillis: Date.now() }; return { kind: "manual", uri: text, lastSeenAtEpochMillis: Date.now() }; }
function defaultState(kind) { const suffix = randomHex(8); return { identity: { deviceId: `device-web-${suffix}`, displayName: kind === "web_tv" ? "StudyFlow Web TV" : "StudyFlow Web", clientKind: kind, protocolSupport: PROTOCOL_SUPPORT, capabilities: kind === "web_tv" ? WEB_TV_CAPABILITIES : WEB_CAPABILITIES }, trustedPeers: [], endpoints: {}, pendingQueue: [], journal: [] }; }
function safeParse(text) { if (!text)
    return null; try {
    return JSON.parse(text);
}
catch {
    return null;
} }
class StudyFlowWebPeerPersistence {
    constructor(storage = globalThis.localStorage) {
        this.storage = storage;
    }
    load(kind) { const loaded = safeParse(this.storage.getItem(STORAGE_KEY)); if (!loaded)
        return defaultState(kind); const refreshed = { ...loaded, identity: { ...loaded.identity, clientKind: kind, capabilities: kind === "web_tv" ? WEB_TV_CAPABILITIES : WEB_CAPABILITIES } }; return refreshed; }
    save(snapshot) { this.storage.setItem(STORAGE_KEY, JSON.stringify(snapshot)); }
    clear() { this.storage.removeItem(STORAGE_KEY); }
}
exports.StudyFlowWebPeerPersistence = StudyFlowWebPeerPersistence;
class StudyFlowWebSocketTransportAdapter {
    constructor() {
        this.supportsListen = false;
        this.supportsClientConnect = true;
    }
    async open(endpoint) { if (endpoint.kind !== "websocket")
        throw new Error("Web SFLP peers can connect only to ws/wss endpoints directly"); return new Promise((resolve, reject) => { const socket = new WebSocket(endpoint.uri); socket.onopen = () => resolve(socket); socket.onerror = () => reject(new Error("SFLP WebSocket connection failed")); }); }
}
exports.StudyFlowWebSocketTransportAdapter = StudyFlowWebSocketTransportAdapter;
class StudyFlowWebManualDiscovery {
    constructor() {
        this.endpoints = {};
    }
    remember(peerDeviceId, uri) { var _a; const endpoint = normalizeEndpoint(uri); const existing = (_a = this.endpoints[peerDeviceId]) !== null && _a !== void 0 ? _a : []; this.endpoints[peerDeviceId] = [endpoint, ...existing.filter(item => item.uri !== endpoint.uri)]; return endpoint; }
    discovered(peerDeviceId) { var _a; return [...((_a = this.endpoints[peerDeviceId]) !== null && _a !== void 0 ? _a : [])]; }
}
exports.StudyFlowWebManualDiscovery = StudyFlowWebManualDiscovery;
class StudyFlowWebLinkPeer {
    constructor(persistence = new StudyFlowWebPeerPersistence(), kind = "web") {
        this.persistence = persistence;
        this.discovery = new StudyFlowWebManualDiscovery();
        this.transport = new StudyFlowWebSocketTransportAdapter();
        this.snapshot = this.persistence.load(kind);
        this.persistence.save(this.snapshot);
    }
    get identity() { return this.snapshot.identity; }
    get trustedPeers() { return [...this.snapshot.trustedPeers]; }
    get pendingOutgoingOperations() { return [...this.snapshot.pendingQueue]; }
    get journal() { return [...this.snapshot.journal]; }
    createPairingOffer(endpointHints = this.localEndpointHints()) { const pairingCode = sixDigitCode(); const expiresAtEpochMillis = Date.now() + PAIRING_TTL_MILLIS; const offer = { pairingCode, expiresAtEpochMillis, descriptor: this.identity, endpointHints }; const qrPayload = JSON.stringify({ type: "studyflow.sflp.pairing.v1", ...offer }); return { ...offer, qrPayload }; }
    pairFromQr(qrPayload) { var _a; const parsed = JSON.parse(qrPayload); const trustSecret = randomHex(TRUST_SECRET_HEX_LENGTH / 2); const now = Date.now(); const record = { peer: parsed.descriptor, state: "trusted", pairedAtEpochMillis: now, updatedAtEpochMillis: now, lastSeenAtEpochMillis: now, revokedAtEpochMillis: null, trustSecret }; this.snapshot.trustedPeers = [record, ...this.snapshot.trustedPeers.filter(item => item.peer.deviceId !== record.peer.deviceId)]; for (const hint of (_a = parsed.endpointHints) !== null && _a !== void 0 ? _a : [])
        this.rememberManualEndpoint(record.peer.deviceId, hint); this.persistence.save(this.snapshot); return record; }
    rememberManualEndpoint(peerDeviceId, uri) { var _a; const endpoint = this.discovery.remember(peerDeviceId, uri); const existing = (_a = this.snapshot.endpoints[peerDeviceId]) !== null && _a !== void 0 ? _a : []; this.snapshot.endpoints[peerDeviceId] = [endpoint, ...existing.filter(item => item.uri !== endpoint.uri)]; this.persistence.save(this.snapshot); return endpoint; }
    appendLocalMutation(workspaceId, entityId, operationType, payload) { const mine = this.snapshot.journal.filter(op => op.originDeviceId === this.identity.deviceId); const sequence = mine.reduce((max, op) => Math.max(max, op.originSequence), 0) + 1; const operation = { workspaceId, operationId: `op-${this.identity.deviceId}-${sequence}`, originDeviceId: this.identity.deviceId, originSequence: sequence, entityId, entityRevision: sequence, operationType, payload, createdAtEpochMillis: Date.now() }; this.snapshot.journal = [...this.snapshot.journal, operation]; for (const peer of this.snapshot.trustedPeers.filter(p => p.state === "trusted")) {
        this.snapshot.pendingQueue.push({ peerDeviceId: peer.peer.deviceId, workspaceId, operationId: operation.operationId, attempts: 0, nextAttemptEpochMillis: Date.now() });
    } this.persistence.save(this.snapshot); return operation; }
    applyRemoteOperations(operations) { const seen = new Set(this.snapshot.journal.map(op => op.operationId)); let added = 0; for (const operation of operations) {
        if (seen.has(operation.operationId))
            continue;
        seen.add(operation.operationId);
        this.snapshot.journal.push(operation);
        added++;
    } if (added)
        this.persistence.save(this.snapshot); return added; }
    frontier(workspaceId) { var _a; const out = {}; for (const op of this.snapshot.journal.filter(item => item.workspaceId === workspaceId)) {
        out[op.originDeviceId] = Math.max((_a = out[op.originDeviceId]) !== null && _a !== void 0 ? _a : 0, op.originSequence);
    } return out; }
    operationsAfter(workspaceId, frontier, limit = 100) { return this.snapshot.journal.filter(op => { var _a; return op.workspaceId === workspaceId && op.originSequence > ((_a = frontier[op.originDeviceId]) !== null && _a !== void 0 ? _a : 0); }).sort((a, b) => a.originDeviceId.localeCompare(b.originDeviceId) || a.originSequence - b.originSequence).slice(0, limit); }
    acknowledge(peerDeviceId, workspaceId, frontier) { this.snapshot.pendingQueue = this.snapshot.pendingQueue.filter(entry => { var _a; if (entry.peerDeviceId !== peerDeviceId || entry.workspaceId !== workspaceId)
        return true; const op = this.snapshot.journal.find(item => item.operationId === entry.operationId); return !op || op.originSequence > ((_a = frontier[op.originDeviceId]) !== null && _a !== void 0 ? _a : 0); }); this.persistence.save(this.snapshot); }
    revoke(peerDeviceId) { const now = Date.now(); let revoked = null; this.snapshot.trustedPeers = this.snapshot.trustedPeers.map(record => { if (record.peer.deviceId !== peerDeviceId)
        return record; revoked = { ...record, state: "revoked", trustSecret: null, updatedAtEpochMillis: now, revokedAtEpochMillis: now }; return revoked; }); this.persistence.save(this.snapshot); return revoked; }
    forget(peerDeviceId) { const before = this.snapshot.trustedPeers.length; this.snapshot.trustedPeers = this.snapshot.trustedPeers.filter(item => item.peer.deviceId !== peerDeviceId); delete this.snapshot.endpoints[peerDeviceId]; this.snapshot.pendingQueue = this.snapshot.pendingQueue.filter(item => item.peerDeviceId !== peerDeviceId); this.persistence.save(this.snapshot); return before !== this.snapshot.trustedPeers.length; }
    snapshotForDebug() { return JSON.parse(JSON.stringify(this.snapshot)); }
    localEndpointHints() { var _a, _b; return ((_b = (_a = globalThis.location) === null || _a === void 0 ? void 0 : _a.origin) === null || _b === void 0 ? void 0 : _b.startsWith("http")) ? [globalThis.location.origin.replace(/^http/, "ws") + "/sflp"] : []; }
}
exports.StudyFlowWebLinkPeer = StudyFlowWebLinkPeer;
function installStudyFlowWebSflpPeer(kind = "web") { if (typeof globalThis.localStorage === "undefined")
    return null; const peer = new StudyFlowWebLinkPeer(new StudyFlowWebPeerPersistence(globalThis.localStorage), kind); globalThis.studyFlowSflpPeer = peer; globalThis.dispatchEvent(new CustomEvent("studyflow:sflp-peer-ready", { detail: { deviceId: peer.identity.deviceId, clientKind: peer.identity.clientKind } })); return peer; }
