"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StudyFlowAccountBackupReleaseRuntime = exports.StudyFlowShareImportConflictRuntime = exports.StudyFlowNotificationMediaRuntime = exports.StudyFlowBrowserAttentionLedgerStorage = exports.StudyFlowMemoryAttentionLedgerStorage = exports.StudyFlowAttentionLedgerSnapshotSchemaVersion = exports.StudyFlowControlInputRuntime = exports.StudyFlowWebPresentationProjectionController = exports.StudyFlowWebLiveSessionContinuityController = exports.SFLP15_PROJECTION_CONTROL_WIRE_NAME = exports.SFLP14_LIVE_CURSOR_WIRE_NAME = exports.StudyFlowWebActivityHandoffController = exports.StudyFlowWebTvConnectivityController = exports.StudyFlowWebLinkPeer = exports.StudyFlowWebManualDiscovery = exports.StudyFlowWebSocketTransportAdapter = exports.StudyFlowWebPeerPersistence = void 0;
exports.installStudyFlowWebSflpPeer = installStudyFlowWebSflpPeer;
const presence_js_1 = require("./presence.js");
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
        this.presence = new presence_js_1.StudyFlowWebPresenceRuntime(this.identity);
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
    updatePresence(workspaceId, availability, activityHint, visibilityCapabilities = []) { return this.presence.announce(workspaceId, availability, activityHint, visibilityCapabilities); }
    presenceViews(workspaceId) { return this.presence.views(workspaceId, this.identity); }
    presenceExchangeFor(peerDeviceId, workspaceId) { var _a; const peer = (_a = this.snapshot.trustedPeers.find(item => item.state === "trusted" && item.peer.deviceId === peerDeviceId)) === null || _a === void 0 ? void 0 : _a.peer; if (!peer)
        throw new Error("Presence target is not trusted"); return this.presence.exchangeForPeer(workspaceId, peer); }
    acceptPresenceExchange(peerDeviceId, workspaceId, exchange) { const trusted = new Set(this.snapshot.trustedPeers.filter(item => item.state === "trusted").map(item => item.peer.deviceId)); if (!trusted.has(peerDeviceId))
        throw new Error("Presence sender is not trusted"); return this.presence.acceptExchange(workspaceId, peerDeviceId, exchange, trusted); }
    revoke(peerDeviceId) { this.presence.forgetPeer(peerDeviceId); const now = Date.now(); let revoked = null; this.snapshot.trustedPeers = this.snapshot.trustedPeers.map(record => { if (record.peer.deviceId !== peerDeviceId)
        return record; revoked = { ...record, state: "revoked", trustSecret: null, updatedAtEpochMillis: now, revokedAtEpochMillis: now }; return revoked; }); this.persistence.save(this.snapshot); return revoked; }
    forget(peerDeviceId) { this.presence.forgetPeer(peerDeviceId); const before = this.snapshot.trustedPeers.length; this.snapshot.trustedPeers = this.snapshot.trustedPeers.filter(item => item.peer.deviceId !== peerDeviceId); delete this.snapshot.endpoints[peerDeviceId]; this.snapshot.pendingQueue = this.snapshot.pendingQueue.filter(item => item.peerDeviceId !== peerDeviceId); this.persistence.save(this.snapshot); return before !== this.snapshot.trustedPeers.length; }
    snapshotForDebug() { return JSON.parse(JSON.stringify(this.snapshot)); }
    localEndpointHints() { var _a, _b; return ((_b = (_a = globalThis.location) === null || _a === void 0 ? void 0 : _a.origin) === null || _b === void 0 ? void 0 : _b.startsWith("http")) ? [globalThis.location.origin.replace(/^http/, "ws") + "/sflp"] : []; }
}
exports.StudyFlowWebLinkPeer = StudyFlowWebLinkPeer;
class StudyFlowWebTvConnectivityController {
    constructor(peer) {
        this.peer = peer;
        if (peer.identity.clientKind !== "web_tv")
            throw new Error("SFLP12 requires a web_tv peer");
    }
    createReceiverOffer(workspaceId, endpointHints = this.peer.createPairingOffer().endpointHints) { const pairing = this.peer.createPairingOffer(endpointHints); const createdAtEpochMillis = Date.now(); const offer = { type: "studyflow.sflp.web_tv.receiver.v1", receiver: this.peer.identity, workspaceId, pairingCode: pairing.pairingCode, endpointHints, createdAtEpochMillis, expiresAtEpochMillis: pairing.expiresAtEpochMillis, qrPayload: "" }; offer.qrPayload = JSON.stringify({ ...offer, qrPayload: undefined }); return offer; }
    acceptRemoteInput(command) { return command.targetDeviceId === this.peer.identity.deviceId && ["dpad_up", "dpad_down", "dpad_left", "dpad_right", "select", "back", "text"].includes(command.kind); }
    acceptProjection(snapshot) { return snapshot.kind === "presentation_projection" && snapshot.continuityMode === "presentation_sink" && snapshot.requiredCapabilities.includes("device.presentation_sink") && snapshot.requiredCapabilities.includes("dpad.input"); }
}
exports.StudyFlowWebTvConnectivityController = StudyFlowWebTvConnectivityController;
class StudyFlowWebActivityHandoffController {
    constructor(peer) {
        this.peer = peer;
        this.consumed = new Set();
    }
    createOffer(targetDeviceId, activity, ttlMillis = 90000) { const now = Date.now(); if (activity.requiredCapabilities.length === 0)
        throw new Error("Handoff requires capability declarations"); return { handoffId: `handoff-${this.peer.identity.deviceId}-${now}`, sourceDevice: this.peer.identity, targetDeviceId, activity, createdAtEpochMillis: now, expiresAtEpochMillis: now + ttlMillis, oneTimeNonceHex: randomHex(16) }; }
    acceptOffer(offer) { if (this.consumed.has(offer.handoffId))
        return false; if (offer.expiresAtEpochMillis < Date.now())
        return false; if (offer.targetDeviceId && offer.targetDeviceId !== this.peer.identity.deviceId)
        return false; const capabilities = new Set(this.peer.identity.capabilities); if (offer.activity.requiredCapabilities.some(capability => !capabilities.has(capability)))
        return false; if ((this.peer.identity.clientKind === "web_tv" || this.peer.identity.clientKind === "android_tv") && offer.activity.requiredCapabilities.some(capability => capability.endsWith(".write") || capability === "writing.edit" || capability === "library.import"))
        return false; this.consumed.add(offer.handoffId); return true; }
    resumePayload(offer) { return { type: "studyflow.sflp.activity_handoff.v1", handoffId: offer.handoffId, workspaceId: offer.activity.workspaceId, activityId: offer.activity.activityId, cursor: offer.activity.cursor }; }
}
exports.StudyFlowWebActivityHandoffController = StudyFlowWebActivityHandoffController;
function installStudyFlowWebSflpPeer(kind = "web") { if (typeof globalThis.localStorage === "undefined")
    return null; const peer = new StudyFlowWebLinkPeer(new StudyFlowWebPeerPersistence(globalThis.localStorage), kind); globalThis.studyFlowSflpPeer = peer; globalThis.dispatchEvent(new CustomEvent("studyflow:sflp-peer-ready", { detail: { deviceId: peer.identity.deviceId, clientKind: peer.identity.clientKind } })); return peer; }
exports.SFLP14_LIVE_CURSOR_WIRE_NAME = "session.live_cursor";
exports.SFLP15_PROJECTION_CONTROL_WIRE_NAME = "presentation.projection_control";
class StudyFlowWebLiveSessionContinuityController {
    constructor(snapshot) {
        this.snapshot = snapshot;
        this.applied = new Set();
    }
    current() { return JSON.parse(JSON.stringify(this.snapshot)); }
    applyEvent(event) { var _a; if (this.applied.has(event.eventId))
        return "duplicate"; if (event.sessionEpoch < this.snapshot.sessionEpoch)
        return "stale_epoch"; if (event.sessionEpoch === this.snapshot.sessionEpoch && event.sequence <= this.snapshot.lastSequence)
        return "stale_sequence"; if (this.snapshot.state === "completed")
        return "already_completed"; this.snapshot = { ...this.snapshot, state: event.kind === "paused" ? "paused" : event.kind === "completed" ? "completed" : "running", cursor: event.cursor, lastSequence: event.sequence, sessionEpoch: event.sessionEpoch, completionOperationId: (_a = event.completionOperationId) !== null && _a !== void 0 ? _a : this.snapshot.completionOperationId }; this.applied.add(event.eventId); return "applied"; }
    pause(sourceDeviceId) { return this.event(sourceDeviceId, "paused"); }
    resume(sourceDeviceId) { return this.event(sourceDeviceId, "resumed"); }
    complete(sourceDeviceId, completionOperationId) { return this.event(sourceDeviceId, "completed", completionOperationId); }
    event(sourceDeviceId, kind, completionOperationId) { return { eventId: `live-${kind}-${Date.now()}`, sessionId: this.snapshot.sessionId, workspaceId: this.snapshot.workspaceId, sourceDeviceId, sessionEpoch: this.snapshot.sessionEpoch, sequence: this.snapshot.lastSequence + 1, kind, cursor: this.snapshot.cursor, completionOperationId }; }
}
exports.StudyFlowWebLiveSessionContinuityController = StudyFlowWebLiveSessionContinuityController;
class StudyFlowWebPresentationProjectionController {
    constructor(peer) {
        this.peer = peer;
        this.accepted = new Set();
        this.frames = {};
    }
    createProjectionOffer(workspaceId, title, contentHash, targetDeviceId) { const now = Date.now(); return { projectionId: `projection-${this.peer.identity.deviceId}-${now}`, workspaceId, sourceDevice: this.peer.identity, targetDeviceId, contentKind: "document", title, contentHash, requiredCapabilities: ["workspace.read", "device.presentation_sink", "video.output"], createdAtEpochMillis: now, expiresAtEpochMillis: now + 120000 }; }
    acceptProjectionOffer(offer) { if (offer.expiresAtEpochMillis < Date.now())
        return false; if (offer.targetDeviceId && offer.targetDeviceId !== this.peer.identity.deviceId)
        return false; const caps = new Set(this.peer.identity.capabilities); if (offer.requiredCapabilities.some(capability => !caps.has(capability)))
        return false; if (offer.requiredCapabilities.some(capability => capability.endsWith(".write") || capability === "library.import" || capability === "writing.edit"))
        return false; this.accepted.add(offer.projectionId); return true; }
    applyProjectionFrame(frame) { if (!this.accepted.has(frame.projectionId))
        return false; const current = this.frames[frame.projectionId]; if (current && frame.sequence <= current.sequence)
        return false; this.frames[frame.projectionId] = frame; return true; }
    acknowledgeProjectionFrame(projectionId) { const frame = this.frames[projectionId]; if (!frame)
        return null; return { projectionId, sinkDeviceId: this.peer.identity.deviceId, acknowledgedFrameId: frame.frameId, acknowledgedSequence: frame.sequence, contentHash: frame.contentHash, acknowledgedAtEpochMillis: Date.now() }; }
    createProjectionCommand(projectionId, targetDeviceId, kind) { return { type: "presentation.projection_control", commandId: `projection-command-${Date.now()}`, projectionId, sourceDeviceId: this.peer.identity.deviceId, targetDeviceId, kind, issuedAtEpochMillis: Date.now() }; }
}
exports.StudyFlowWebPresentationProjectionController = StudyFlowWebPresentationProjectionController;
class StudyFlowControlInputRuntime {
    constructor(peer) {
        this.peer = peer;
        this.leases = new Map();
        this.inputDelegations = new Map();
        this.lastControlSequence = new Map();
        this.lastInputSequence = new Map();
    }
    rememberControlLease(lease) {
        this.leases.set(lease.sessionId, lease);
        return lease;
    }
    applyControlCommand(command) {
        var _a;
        const lease = this.leases.get(command.sessionId);
        if (!lease)
            return { status: 'rejected', reason: 'unknown_lease' };
        if (command.targetDeviceId !== this.peer.deviceId) {
            return { status: 'rejected', reason: 'wrong_target' };
        }
        if (command.leaseToken !== lease.leaseToken) {
            return { status: 'rejected', reason: 'bad_lease_token' };
        }
        const last = (_a = this.lastControlSequence.get(command.sessionId)) !== null && _a !== void 0 ? _a : 0;
        if (command.sequence <= last)
            return { status: 'rejected', reason: 'stale_sequence' };
        this.lastControlSequence.set(command.sessionId, command.sequence);
        return { status: 'accepted', acknowledgedSequence: command.sequence };
    }
    acceptInputDelegation(offer) {
        if (offer.targetDeviceId !== this.peer.deviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        this.inputDelegations.set(offer.delegationId, offer);
        return { status: 'accepted', delegationId: offer.delegationId };
    }
    applyInputEvent(event) {
        var _a;
        if (!this.inputDelegations.has(event.delegationId))
            return { status: 'rejected', reason: 'unknown_delegation' };
        if (event.targetDeviceId !== this.peer.deviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        const last = (_a = this.lastInputSequence.get(event.delegationId)) !== null && _a !== void 0 ? _a : 0;
        if (event.sequence <= last)
            return { status: 'rejected', reason: 'stale_sequence' };
        this.lastInputSequence.set(event.delegationId, event.sequence);
        return { status: 'accepted', acknowledgedSequence: event.sequence };
    }
}
exports.StudyFlowControlInputRuntime = StudyFlowControlInputRuntime;
exports.StudyFlowAttentionLedgerSnapshotSchemaVersion = 1;
class StudyFlowMemoryAttentionLedgerStorage {
    constructor(initialEncodedSnapshot = null) { this.encoded = initialEncodedSnapshot; }
    readEncodedSnapshot() { return this.encoded; }
    writeEncodedSnapshot(encoded) { this.encoded = encoded; }
}
exports.StudyFlowMemoryAttentionLedgerStorage = StudyFlowMemoryAttentionLedgerStorage;
class StudyFlowBrowserAttentionLedgerStorage {
    constructor(key) {
        this.key = key;
        this.fallback = new StudyFlowMemoryAttentionLedgerStorage();
    }
    readEncodedSnapshot() {
        var _a, _b;
        try {
            return (_b = (_a = globalThis.localStorage) === null || _a === void 0 ? void 0 : _a.getItem(this.key)) !== null && _b !== void 0 ? _b : this.fallback.readEncodedSnapshot();
        }
        catch {
            return this.fallback.readEncodedSnapshot();
        }
    }
    writeEncodedSnapshot(encoded) {
        try {
            if (globalThis.localStorage)
                globalThis.localStorage.setItem(this.key, encoded);
            else
                this.fallback.writeEncodedSnapshot(encoded);
        }
        catch {
            this.fallback.writeEncodedSnapshot(encoded);
        }
    }
}
exports.StudyFlowBrowserAttentionLedgerStorage = StudyFlowBrowserAttentionLedgerStorage;
function emptyAttentionLedger() {
    return { schemaVersion: 1, entries: [], lastSequenceBySource: {}, legacyDeliveredOccurrenceIds: [] };
}
function validAttentionLedger(value) {
    var _a;
    if (!value || typeof value !== 'object')
        return false;
    const candidate = value;
    return candidate.schemaVersion === 1 && Array.isArray(candidate.entries) &&
        !!candidate.lastSequenceBySource && typeof candidate.lastSequenceBySource === 'object' &&
        Array.isArray((_a = candidate.legacyDeliveredOccurrenceIds) !== null && _a !== void 0 ? _a : []);
}
function stableAttentionOccurrenceId(parts) {
    const readable = parts.join(':').replace(/[^A-Za-z0-9._:-]/g, '-');
    if (readable.length > 0 && readable.length <= 128)
        return readable;
    let hash = 2166136261;
    for (const char of parts.join('\u001f')) {
        hash ^= char.charCodeAt(0);
        hash = Math.imul(hash, 16777619);
    }
    return `attn-${(hash >>> 0).toString(16).padStart(8, '0')}`;
}
class StudyFlowNotificationMediaRuntime {
    constructor(peer, attentionStorage = new StudyFlowBrowserAttentionLedgerStorage(`studyflow.attention.v1.${peer.deviceId}`)) {
        this.peer = peer;
        this.attentionStorage = attentionStorage;
        this.materialStreams = new Map();
        this.materialChunks = new Map();
        this.attentionLedger = this.loadAttentionLedger();
    }
    applyNotificationDelivery(delivery) {
        var _a, _b, _c;
        if (delivery.targetDeviceId !== this.peer.deviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        if (delivery.notification.expiresAtEpochMillis < Date.now())
            return { status: 'expired', reason: 'expired' };
        const occurrence = (_a = delivery.notification.attentionOccurrence) !== null && _a !== void 0 ? _a : this.legacyNotificationOccurrence(delivery);
        const existing = this.attentionLedger.entries.find(entry => entry.occurrence.occurrenceId === occurrence.occurrenceId);
        if (existing && !['planned', 'scheduled', 'delivery_pending'].includes(existing.state)) {
            return { status: 'duplicate', acknowledgedSequence: delivery.sequence };
        }
        if (this.attentionLedger.legacyDeliveredOccurrenceIds.includes(occurrence.occurrenceId)) {
            return { status: 'duplicate', acknowledgedSequence: delivery.sequence };
        }
        const last = (_b = this.attentionLedger.lastSequenceBySource[delivery.sourceDeviceId]) !== null && _b !== void 0 ? _b : 0;
        if (delivery.sequence <= last)
            return { status: 'rejected', reason: 'stale_sequence' };
        const now = Date.now();
        const entry = {
            occurrence,
            state: 'delivered',
            updatedAtEpochMillis: now,
            deliveredAtEpochMillis: now,
            sourceDeviceId: delivery.sourceDeviceId,
            targetDeviceId: delivery.targetDeviceId,
            sequence: delivery.sequence,
            deliveryAttempts: ((_c = existing === null || existing === void 0 ? void 0 : existing.deliveryAttempts) !== null && _c !== void 0 ? _c : 0) + 1,
        };
        this.attentionLedger = {
            ...this.attentionLedger,
            entries: [...this.attentionLedger.entries.filter(value => value.occurrence.occurrenceId !== occurrence.occurrenceId), entry].slice(-512),
            lastSequenceBySource: { ...this.attentionLedger.lastSequenceBySource, [delivery.sourceDeviceId]: delivery.sequence },
        };
        this.persistAttentionLedger();
        return { status: 'accepted', acknowledgedSequence: delivery.sequence };
    }
    scheduleChangeOperation(change) {
        const operationType = change.changeKind === 'reminder_cancelled'
            ? 'schedule.reminder_cancelled'
            : change.changeKind === 'activity_moved'
                ? 'schedule.activity_moved'
                : change.changeKind === 'quiet_hours_changed'
                    ? 'notification.quiet_hours_changed'
                    : change.changeKind === 'notification_policy_changed'
                        ? 'notification.policy_changed'
                        : 'schedule.reminder_upserted';
        return {
            operationId: `op-${change.changeId}`,
            workspaceId: change.workspaceId,
            originDeviceId: change.sourceDeviceId,
            originSequence: change.sequence,
            entityId: change.scheduleEntityId,
            operationType,
            payload: {
                notificationScheduleSync: 'sflp18',
                attentionSchema: 'v1',
                changeKind: change.changeKind,
                scheduleSyncId: change.changeId,
                attentionScheduleChange: { ...change },
            },
        };
    }
    canonicalScheduleOccurrence(change) {
        if (change.attentionOccurrence)
            return change.attentionOccurrence;
        if (change.reminderAtEpochMillis === undefined)
            return null;
        return {
            occurrenceId: stableAttentionOccurrenceId(['schedule', change.scheduleEntityId, String(change.reminderAtEpochMillis)]),
            workspaceId: change.workspaceId,
            scheduleEntityId: change.scheduleEntityId,
            category: 'planned_study',
            priority: 'normal',
            scheduledAtEpochMillis: change.reminderAtEpochMillis,
            originalScheduledAtEpochMillis: change.reminderAtEpochMillis,
            quietHours: change.quietHoursStartMinute !== undefined && change.quietHoursEndMinute !== undefined
                ? { startMinute: change.quietHoursStartMinute, endMinute: change.quietHoursEndMinute }
                : undefined,
            deliveryPolicy: 'active_then_primary',
            sourceDeviceId: change.sourceDeviceId,
            payload: change.payload,
        };
    }
    attentionDiagnostics() {
        const terminal = new Set(['completed', 'dismissed', 'expired', 'cancelled']);
        const terminalCount = this.attentionLedger.entries.filter(entry => terminal.has(entry.state)).length;
        return {
            schemaVersion: this.attentionLedger.schemaVersion,
            entryCount: this.attentionLedger.entries.length,
            activeCount: this.attentionLedger.entries.length - terminalCount,
            terminalCount,
            sourceSequenceCount: Object.keys(this.attentionLedger.lastSequenceBySource).length,
            legacyDeliveredAliasCount: this.attentionLedger.legacyDeliveredOccurrenceIds.length,
            lastUpdatedAtEpochMillis: this.attentionLedger.entries.reduce((latest, entry) => latest === null ? entry.updatedAtEpochMillis : Math.max(latest, entry.updatedAtEpochMillis), null),
        };
    }
    acceptMaterialStream(offer) {
        if (offer.expiresAtEpochMillis < Date.now())
            return false;
        if (offer.targetDeviceId && offer.targetDeviceId !== this.peer.deviceId)
            return false;
        const capabilities = new Set(this.peer.capabilities);
        if (offer.requiredCapabilities.some(capability => capability.endsWith('.write') || capability === 'library.import'))
            return false;
        if (offer.requiredCapabilities.some(capability => !capabilities.has(capability)))
            return false;
        this.materialStreams.set(offer.streamId, offer);
        this.materialChunks.set(offer.streamId, new Set());
        return true;
    }
    applyMaterialChunk(chunk) {
        var _a;
        const offer = this.materialStreams.get(chunk.streamId);
        if (!offer)
            return { status: 'rejected', reason: 'unknown_stream' };
        if (chunk.targetDeviceId !== this.peer.deviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        if (chunk.byteOffset !== chunk.chunkIndex * offer.chunkSize)
            return { status: 'rejected', reason: 'unexpected_offset' };
        const chunks = (_a = this.materialChunks.get(chunk.streamId)) !== null && _a !== void 0 ? _a : new Set();
        if (chunks.has(chunk.chunkIndex))
            return { status: 'duplicate', acknowledgedChunkIndex: chunk.chunkIndex };
        chunks.add(chunk.chunkIndex);
        this.materialChunks.set(chunk.streamId, chunks);
        return { status: chunk.finalChunk ? 'complete' : 'accepted', acknowledgedChunkIndex: chunk.chunkIndex };
    }
    legacyNotificationOccurrence(delivery) {
        var _a, _b, _c, _d, _e, _f;
        const workspaceId = (_b = (_a = delivery.notification.route) === null || _a === void 0 ? void 0 : _a.workspaceId) !== null && _b !== void 0 ? _b : 'workspace-main';
        const category = delivery.notification.kind === 'review_due'
            ? 'due_review'
            : delivery.notification.kind === 'study_due'
                ? 'planned_study'
                : delivery.notification.kind === 'session_resume'
                    ? 'session_ending'
                    : 'sync_attention';
        return {
            occurrenceId: stableAttentionOccurrenceId(['notification', delivery.notification.notificationId]),
            workspaceId,
            scheduleEntityId: delivery.notification.scheduleActivityId,
            category,
            priority: 'normal',
            scheduledAtEpochMillis: (_d = (_c = delivery.notification.dueAtEpochMillis) !== null && _c !== void 0 ? _c : delivery.notification.createdAtEpochMillis) !== null && _d !== void 0 ? _d : Date.now(),
            originalScheduledAtEpochMillis: (_f = (_e = delivery.notification.dueAtEpochMillis) !== null && _e !== void 0 ? _e : delivery.notification.createdAtEpochMillis) !== null && _f !== void 0 ? _f : Date.now(),
            expiresAtEpochMillis: delivery.notification.expiresAtEpochMillis,
            deliveryPolicy: 'active_then_primary',
            sourceDeviceId: delivery.sourceDeviceId,
            targetDeviceIds: [delivery.targetDeviceId],
            payload: delivery.notification.payload,
        };
    }
    loadAttentionLedger() {
        const encoded = this.attentionStorage.readEncodedSnapshot();
        if (!encoded)
            return emptyAttentionLedger();
        try {
            const decoded = JSON.parse(encoded);
            return validAttentionLedger(decoded) ? decoded : emptyAttentionLedger();
        }
        catch {
            return emptyAttentionLedger();
        }
    }
    persistAttentionLedger() {
        this.attentionStorage.writeEncodedSnapshot(JSON.stringify(this.attentionLedger));
    }
}
exports.StudyFlowNotificationMediaRuntime = StudyFlowNotificationMediaRuntime;
class StudyFlowShareImportConflictRuntime {
    constructor(peer) {
        this.peer = peer;
        this.imports = new Map();
        this.conflictReviews = new Map();
    }
    applyShareImportDelivery(delivery) {
        var _a, _b;
        const localId = (_a = this.peer.deviceId) !== null && _a !== void 0 ? _a : (_b = this.peer.identity) === null || _b === void 0 ? void 0 : _b.deviceId;
        if (delivery.targetDeviceId !== localId)
            return { status: 'rejected', reason: 'wrong_target' };
        if (this.imports.has(delivery.offer.importId))
            return { status: 'duplicate' };
        this.imports.set(delivery.offer.importId, delivery.offer);
        return { status: 'accepted' };
    }
    openConflictReview(review) {
        this.conflictReviews.set(review.reviewId, review);
        return true;
    }
    resolveConflict(reviewId, decision) {
        if (!this.conflictReviews.has(reviewId))
            return { status: 'rejected', reason: 'unknown_review' };
        this.conflictReviews.delete(reviewId);
        return { status: 'resolved', decision };
    }
}
exports.StudyFlowShareImportConflictRuntime = StudyFlowShareImportConflictRuntime;
class StudyFlowAccountBackupReleaseRuntime {
    constructor(peer) {
        this.peer = peer;
        this.handoffs = new Set();
        this.restores = new Set();
    }
    localDeviceId() { var _a, _b; return (_a = this.peer.deviceId) !== null && _a !== void 0 ? _a : (_b = this.peer.identity) === null || _b === void 0 ? void 0 : _b.deviceId; }
    localCapabilities() { var _a, _b, _c; return new Set((_c = (_a = this.peer.capabilities) !== null && _a !== void 0 ? _a : (_b = this.peer.identity) === null || _b === void 0 ? void 0 : _b.capabilities) !== null && _c !== void 0 ? _c : []); }
    localClientKind() { var _a, _b; return (_a = this.peer.clientKind) !== null && _a !== void 0 ? _a : (_b = this.peer.identity) === null || _b === void 0 ? void 0 : _b.clientKind; }
    applyAccountHandoff(delivery) {
        if (delivery.targetDeviceId !== this.localDeviceId())
            return { status: 'rejected', reason: 'wrong_target' };
        if (delivery.offer.expiresAtEpochMillis < Date.now())
            return { status: 'expired', reason: 'expired' };
        if (this.handoffs.has(delivery.offer.handoffId))
            return { status: 'duplicate', acknowledgedSequence: delivery.sequence };
        const caps = this.localCapabilities();
        if (delivery.offer.requiredCapabilities.some(capability => !caps.has(capability)))
            return { status: 'rejected', reason: 'missing_capability' };
        const kind = this.localClientKind();
        if ((kind === 'android_tv' || kind === 'web_tv') && delivery.offer.purpose !== 'audit')
            return { status: 'rejected', reason: 'tv_account_handoff_forbidden' };
        this.handoffs.add(delivery.offer.handoffId);
        return { status: 'accepted', handoffId: delivery.offer.handoffId, acknowledgedSequence: delivery.sequence };
    }
    acceptBackupRestore(plan) {
        if (plan.targetDeviceId !== this.localDeviceId())
            return { status: 'rejected', reason: 'wrong_target' };
        if (plan.bundle.expiresAtEpochMillis && plan.bundle.expiresAtEpochMillis < Date.now())
            return { status: 'expired', reason: 'expired' };
        if (this.restores.has(plan.restoreId))
            return { status: 'duplicate', acknowledgedSequence: plan.sequence };
        const kind = this.localClientKind();
        if ((kind === 'android_tv' || kind === 'web_tv') && plan.mode !== 'verify_only')
            return { status: 'rejected', reason: 'tv_restore_forbidden' };
        const caps = this.localCapabilities();
        if (plan.bundle.requiredRestoreCapabilities.some(capability => !caps.has(capability)))
            return { status: 'rejected', reason: 'missing_capability' };
        this.restores.add(plan.restoreId);
        return { status: 'accepted', restoreId: plan.restoreId, acknowledgedSequence: plan.sequence };
    }
    finalReleaseValidationTaskNames() {
        return [
            'verifySflp22Sflp23AccountBackupValidation', 'verifySflp20Sflp21ShareImportConflict',
            'verifySflp18Sflp19NotificationMediaSync', 'verifySflp16Sflp17ControlInputDelegation',
            'verifySflp14Sflp15LiveSessionProjection', 'verifySflp12Sflp13WebTvHandoff',
            'verifySflp10Sflp11WebTvGeneralization', 'verifySflp08Sflp09AndroidDesktopIntegration',
            'verifySflp06Sflp07TopologyOfflineQueue', 'verifySflp04Sflp05IdentityTransport',
            'verifySflp02Sflp03JournalReconciliation', 'verifySflp01ProtocolBaseline',
            ':core:link:test', ':app-tv:testDebugUnitTest', ':desktopApp:test', ':app:testReleaseUnitTest',
        ];
    }
}
exports.StudyFlowAccountBackupReleaseRuntime = StudyFlowAccountBackupReleaseRuntime;
