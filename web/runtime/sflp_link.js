"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StudyFlowAccountBackupReleaseRuntime = exports.StudyFlowShareImportConflictRuntime = exports.STUDYFLOW_CONFLICT_REVIEW_OPERATION = exports.STUDYFLOW_SHARE_IMPORT_INTENTS = exports.StudyFlowNotificationMediaRuntime = exports.StudyFlowBrowserAttentionLedgerStorage = exports.StudyFlowMemoryAttentionLedgerStorage = exports.StudyFlowAttentionLedgerSnapshotSchemaVersion = exports.StudyFlowControlInputRuntime = exports.StudyFlowWebPresentationProjectionController = exports.StudyFlowWebLiveSessionContinuityController = exports.SFLP15_PROJECTION_CONTROL_WIRE_NAME = exports.SFLP14_LIVE_CURSOR_WIRE_NAME = exports.StudyFlowWebActivityHandoffController = exports.StudyFlowWebTvConnectivityController = exports.StudyFlowWebLinkPeer = exports.StudyFlowWebManualDiscovery = exports.StudyFlowWebSocketTransportAdapter = exports.StudyFlowWebSocketRpcConnection = exports.StudyFlowWebPeerPersistence = exports.StudyFlowWebCryptoTrustKeyStore = exports.StudyFlowWebTerminalPeerError = exports.StudyFlowWebSyncIntegrityError = exports.StudyFlowWebRpcError = void 0;
exports.decodeStudyFlowPairingQr = decodeStudyFlowPairingQr;
exports.installStudyFlowWebSflpPeer = installStudyFlowWebSflpPeer;
const presence_js_1 = require("./presence.js");
const WEB_STORAGE_KEY = "studyflow.sflp.web.peer.v1";
const WEB_TV_STORAGE_KEY = "studyflow.sflp.web_tv.peer.v1";
const PAIRING_SCHEME_PREFIX = "studyflow://pair?data=";
const LINK_SYSTEM_WORKSPACE_ID = "link-system-v1";
const PAIRING_TTL_MILLIS = 120000;
const PROTOCOL_SUPPORT = { major: 1, minMinor: 0, maxMinor: 0 };
const PROTOCOL_VERSION = { major: 1, minor: 0 };
const WEB_CAPABILITIES = ["workspace.read", "workspace.write", "workspace.sync", "plan.read", "plan.write", "schedule.read", "schedule.write", "session.execute", "session.control", "session.present", "question.answer", "review.execute", "goal.read", "goal.complete", "library.read", "library.present", "document.read", "document.present", "writing.read", "writing.edit", "analytics.read", "backup.export", "backup.import", "device.control", "device.presentation_source", "text_input.provide", "keyboard.input", "touch.input", "clipboard.read", "clipboard.write", "share.receive", "import.preview", "conflict.review"];
const WEB_TV_CAPABILITIES = ["workspace.read", "workspace.sync", "schedule.read", "session.control", "session.present", "question.answer", "review.execute", "goal.read", "library.read", "library.present", "document.read", "document.present", "analytics.read", "device.control", "device.presentation_sink", "dpad.input", "audio.output", "video.output", "clipboard.read", "share.receive", "import.preview", "conflict.review"];
const CLIENT_KINDS = new Set(["android", "desktop", "web", "android_tv", "web_tv"]);
function requireSecureRandom() { const crypto = globalThis.crypto; if (!(crypto === null || crypto === void 0 ? void 0 : crypto.getRandomValues))
    throw new Error("Secure Web Crypto randomness is required for StudyFlow Link"); return crypto; }
function requireSubtleCrypto() { const subtle = requireSecureRandom().subtle; if (!subtle)
    throw new Error("Web Crypto SubtleCrypto is required for StudyFlow Link trust"); return subtle; }
function randomHex(bytes) { const buffer = new Uint8Array(bytes); requireSecureRandom().getRandomValues(buffer); return [...buffer].map(x => x.toString(16).padStart(2, "0")).join(""); }
function sixDigitCode() { const value = new Uint32Array(1); requireSecureRandom().getRandomValues(value); return String(value[0] % 1000000).padStart(6, "0"); }
function messageId(prefix) { return `${prefix}-${Date.now()}-${randomHex(6)}`; }
function storageKey(kind) { return kind === "web_tv" ? WEB_TV_STORAGE_KEY : WEB_STORAGE_KEY; }
function normalizeEndpoint(uri) { const text = uri.trim(); if (!text)
    throw new Error("SFLP endpoint cannot be blank"); if (text.startsWith("ws://") || text.startsWith("wss://"))
    return { kind: "websocket", uri: text, lastSeenAtEpochMillis: Date.now() }; if (text.startsWith("sflp+relay://"))
    return { kind: "relay", uri: text, lastSeenAtEpochMillis: Date.now() }; if (text.startsWith("sflp+tcp://"))
    return { kind: "manual", uri: text, lastSeenAtEpochMillis: Date.now() }; throw new Error("Unsupported SFLP endpoint scheme"); }
function capabilitiesFor(kind) { return kind === "web_tv" ? [...WEB_TV_CAPABILITIES] : [...WEB_CAPABILITIES]; }
function defaultState(kind) { if (kind !== "web" && kind !== "web_tv")
    throw new Error("Browser persistence supports web or web_tv identities only"); const suffix = randomHex(8); const prefix = kind === "web_tv" ? "device-web-tv" : "device-web"; return { identity: { deviceId: `${prefix}-${suffix}`, displayName: kind === "web_tv" ? "StudyFlow Web TV" : "StudyFlow Web", clientKind: kind, protocolSupport: PROTOCOL_SUPPORT, capabilities: capabilitiesFor(kind) }, trustedPeers: [], endpoints: {}, pendingQueue: [], journal: [], peerRetryStates: {}, verifiedRemoteFrontiers: {} }; }
function hexBytes(value) { if (value.length % 2 !== 0 || !/^[a-f0-9]+$/.test(value))
    throw new Error("Expected lower-case hex"); const out = new Uint8Array(value.length / 2); for (let i = 0; i < out.length; i++)
    out[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16); return out; }
function bytesHex(value) { const bytes = value instanceof ArrayBuffer ? new Uint8Array(value) : new Uint8Array(value.buffer, value.byteOffset, value.byteLength); return [...bytes].map(x => x.toString(16).padStart(2, "0")).join(""); }
function utf8(value) { return new TextEncoder().encode(value); }
function concatBytes(...parts) { const size = parts.reduce((sum, p) => sum + p.length, 0); const out = new Uint8Array(size); let offset = 0; for (const p of parts) {
    out.set(p, offset);
    offset += p.length;
} return out; }
function isProtocolSupported(version) { return version.major === PROTOCOL_SUPPORT.major && version.minor >= PROTOCOL_SUPPORT.minMinor && version.minor <= PROTOCOL_SUPPORT.maxMinor; }
function validateDescriptor(value) { if (!value || typeof value !== "object")
    throw new Error("Pairing host descriptor is missing"); const d = value; if (typeof d.deviceId !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(d.deviceId))
    throw new Error("Invalid pairing host device ID"); if (typeof d.displayName !== "string" || !d.displayName.trim() || d.displayName.length > 120)
    throw new Error("Invalid pairing host display name"); if (!CLIENT_KINDS.has(d.clientKind))
    throw new Error("Invalid pairing host client kind"); if (!d.protocolSupport || d.protocolSupport.major !== 1 || d.protocolSupport.minMinor > 0 || d.protocolSupport.maxMinor < 0)
    throw new Error("Pairing host has incompatible protocol support"); if (!Array.isArray(d.capabilities) || d.capabilities.some(cap => typeof cap !== "string" || !/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/.test(cap)))
    throw new Error("Invalid pairing host capabilities"); return d; }
function encodePairingQr(payload) { return PAIRING_SCHEME_PREFIX + encodeURIComponent(JSON.stringify(payload)); }
function decodeStudyFlowPairingQr(encoded) { var _a, _b; if (!encoded.startsWith(PAIRING_SCHEME_PREFIX))
    throw new Error("Unsupported StudyFlow pairing QR payload"); let parsed; try {
    parsed = JSON.parse(decodeURIComponent(encoded.slice(PAIRING_SCHEME_PREFIX.length)));
}
catch {
    throw new Error("Malformed StudyFlow pairing QR payload");
} if (parsed.version !== 2)
    throw new Error(`Unsupported pairing QR version ${String(parsed.version)}`); const host = validateDescriptor(parsed.host); if (typeof parsed.pairingId !== "string" || !/^pair-[A-Za-z0-9._:-]+$/.test(parsed.pairingId) || !/^[a-f0-9]{48}$/.test(String((_a = parsed.sessionNonce) !== null && _a !== void 0 ? _a : "")) || !/^[0-9]{6}$/.test(String((_b = parsed.pairingCode) !== null && _b !== void 0 ? _b : "")))
    throw new Error("Invalid StudyFlow pairing QR payload"); if (typeof parsed.createdAtEpochMillis !== "number" || typeof parsed.expiresAtEpochMillis !== "number" || parsed.createdAtEpochMillis < 0 || parsed.expiresAtEpochMillis <= parsed.createdAtEpochMillis)
    throw new Error("Invalid StudyFlow pairing QR expiry"); if (!Array.isArray(parsed.endpointHints) || parsed.endpointHints.length === 0 || parsed.endpointHints.some(h => typeof h !== "string" || !(h.startsWith("ws://") || h.startsWith("wss://") || h.startsWith("sflp+tcp://"))))
    throw new Error("Invalid StudyFlow pairing QR endpoints"); if (typeof parsed.hostKeyAgreementPublicKeyHex !== "string" || parsed.hostKeyAgreementPublicKeyHex.length < 160 || parsed.hostKeyAgreementPublicKeyHex.length > 1024 || parsed.hostKeyAgreementPublicKeyHex.length % 2 !== 0 || !/^[a-f0-9]+$/.test(parsed.hostKeyAgreementPublicKeyHex))
    throw new Error("Invalid pairing host key"); return { ...parsed, host }; }
function envelope(senderDeviceId, workspaceId, name, payload) { return { protocol: PROTOCOL_VERSION, messageId: messageId("web"), workspaceId, senderDeviceId, type: "request", name, sentAtEpochMillis: Date.now(), payload }; }
function frontierFromMap(value) { return { origins: Object.entries(value).filter(([, sequence]) => sequence > 0).sort(([a], [b]) => a.localeCompare(b)).map(([deviceId, contiguousSequence]) => ({ deviceId, contiguousSequence })) }; }
function frontierToMap(value) { if (!value || !Array.isArray(value.origins))
    throw new Error("Malformed SFLP frontier"); const result = {}; for (const origin of value.origins) {
    if (!origin || typeof origin.deviceId !== "string" || !origin.deviceId || !Number.isSafeInteger(origin.contiguousSequence) || origin.contiguousSequence < 0)
        throw new Error("Malformed SFLP frontier origin");
    if (Object.prototype.hasOwnProperty.call(result, origin.deviceId))
        throw new Error("SFLP frontier repeats an origin");
    result[origin.deviceId] = origin.contiguousSequence;
} return result; }
function frontierEquals(left, right) { var _a, _b; const keys = new Set([...Object.keys(left), ...Object.keys(right)]); for (const key of keys)
    if (((_a = left[key]) !== null && _a !== void 0 ? _a : 0) !== ((_b = right[key]) !== null && _b !== void 0 ? _b : 0))
        return false; return true; }
function frontierDominates(left, right) { return Object.entries(right).every(([deviceId, sequence]) => { var _a; return ((_a = left[deviceId]) !== null && _a !== void 0 ? _a : 0) >= sequence; }); }
function retryKey(peerDeviceId, workspaceId) { return `${peerDeviceId}|${workspaceId}`; }
function retryDelayMillis(attempts) { return Math.min(60000, 1000 * Math.pow(2, Math.max(0, Math.min(attempts - 1, 6)))); }
function validateJournalOperation(value, workspaceId) { if (!value || typeof value !== "object")
    throw new Error("Malformed SFLP journal operation"); const op = value; for (const field of ["workspaceId", "operationId", "originDeviceId", "entityId", "operationType"]) {
    if (typeof op[field] !== "string" || !op[field].trim())
        throw new Error(`Malformed SFLP journal operation ${field}`);
} if (workspaceId && op.workspaceId !== workspaceId)
    throw new Error("SFLP operation belongs to another workspace"); for (const field of ["originSequence", "entityRevision", "logicalTimestamp"]) {
    if (!Number.isSafeInteger(op[field]) || op[field] < 1)
        throw new Error(`Malformed SFLP journal operation ${field}`);
} if (!Number.isSafeInteger(op.occurredAtEpochMillis) || Number(op.occurredAtEpochMillis) < 0)
    throw new Error("Malformed SFLP journal operation timestamp"); if (op.kind !== "mutation" && op.kind !== "tombstone")
    throw new Error("Malformed SFLP journal operation kind"); if (!op.payload || typeof op.payload !== "object" || Array.isArray(op.payload))
    throw new Error("Malformed SFLP journal operation payload"); return op; }
function advanceFrontier(base, operations) { var _a, _b; const result = { ...base }; const sequences = new Map(); for (const operation of operations) {
    const set = (_a = sequences.get(operation.originDeviceId)) !== null && _a !== void 0 ? _a : new Set();
    set.add(operation.originSequence);
    sequences.set(operation.originDeviceId, set);
} for (const [deviceId, available] of sequences) {
    let contiguous = (_b = result[deviceId]) !== null && _b !== void 0 ? _b : 0;
    while (available.has(contiguous + 1))
        contiguous++;
    result[deviceId] = contiguous;
} return result; }
function validateSyncBatch(value, workspaceId, expectedBase, trustedOrigins) { if (!value || typeof value !== "object")
    throw new StudyFlowWebSyncIntegrityError("Malformed SFLP sync batch"); const batch = value; if (typeof batch.batchId !== "string" || !batch.batchId || batch.workspaceId !== workspaceId || !Array.isArray(batch.operations) || typeof batch.hasMore !== "boolean")
    throw new StudyFlowWebSyncIntegrityError("Malformed SFLP sync batch"); const base = frontierToMap(batch.baseFrontier); if (!frontierEquals(base, expectedBase))
    throw new StudyFlowWebSyncIntegrityError("SFLP sync batch base frontier is inconsistent"); const operations = batch.operations.map(operation => validateJournalOperation(operation, workspaceId)); const operationIds = new Set(); const originSequences = new Set(); for (const operation of operations) {
    if (!trustedOrigins.has(operation.originDeviceId))
        throw new StudyFlowWebSyncIntegrityError("SFLP operation claims an untrusted origin");
    if (operationIds.has(operation.operationId))
        throw new StudyFlowWebSyncIntegrityError("SFLP sync batch repeats an operation ID");
    operationIds.add(operation.operationId);
    const sequenceKey = `${operation.originDeviceId}:${operation.originSequence}`;
    if (originSequences.has(sequenceKey))
        throw new StudyFlowWebSyncIntegrityError("SFLP sync batch repeats an origin sequence");
    originSequences.add(sequenceKey);
} const through = frontierToMap(batch.throughFrontier); if (!frontierEquals(through, advanceFrontier(base, operations)))
    throw new StudyFlowWebSyncIntegrityError("SFLP sync batch declares a spoofed or gapped through frontier"); return batch; }
class StudyFlowWebRpcError extends Error {
    constructor(code, retryable, message) {
        super(`${code}: ${message}`);
        this.code = code;
        this.retryable = retryable;
        this.name = "StudyFlowWebRpcError";
    }
}
exports.StudyFlowWebRpcError = StudyFlowWebRpcError;
class StudyFlowWebSyncIntegrityError extends Error {
    constructor(message) { super(message); this.name = "StudyFlowWebSyncIntegrityError"; }
}
exports.StudyFlowWebSyncIntegrityError = StudyFlowWebSyncIntegrityError;
class StudyFlowWebTerminalPeerError extends Error {
    constructor(message) { super(message); this.name = "StudyFlowWebTerminalPeerError"; }
}
exports.StudyFlowWebTerminalPeerError = StudyFlowWebTerminalPeerError;
class StudyFlowWebCryptoTrustKeyStore {
    constructor(indexedDb = globalThis.indexedDB) {
        this.indexedDb = indexedDb;
        this.memory = new Map();
        this.dbPromise = null;
    }
    async importSecret(peerDeviceId, secretHex) { if (!/^[a-f0-9]{64}$/.test(secretHex))
        throw new Error("Invalid SFLP trust secret"); const key = await requireSubtleCrypto().importKey("raw", hexBytes(secretHex), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]); const keyId = `sflp-trust-${peerDeviceId}-${randomHex(8)}`; this.memory.set(keyId, key); const db = await this.db(); if (db)
        await this.put(db, keyId, key); return keyId; }
    async sign(keyId, message) { var _a; let key = this.memory.get(keyId); if (!key) {
        const db = await this.db();
        if (db)
            key = (_a = await this.get(db, keyId)) !== null && _a !== void 0 ? _a : undefined;
        if (key)
            this.memory.set(keyId, key);
    } if (!key)
        throw new Error("SFLP trust key is unavailable; pair this device again"); return bytesHex(await requireSubtleCrypto().sign("HMAC", key, message)); }
    async remove(keyId) { this.memory.delete(keyId); const db = await this.db(); if (db)
        await new Promise((resolve, reject) => { const tx = db.transaction("keys", "readwrite"); tx.objectStore("keys").delete(keyId); tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error); }); }
    async db() { if (!this.indexedDb)
        return null; if (!this.dbPromise)
        this.dbPromise = new Promise((resolve, reject) => { const request = this.indexedDb.open("studyflow-sflp-trust-v1", 1); request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains("keys"))
            request.result.createObjectStore("keys"); }; request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); }); return this.dbPromise; }
    put(db, keyId, key) { return new Promise((resolve, reject) => { const tx = db.transaction("keys", "readwrite"); tx.objectStore("keys").put(key, keyId); tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error); }); }
    get(db, keyId) { return new Promise((resolve, reject) => { const tx = db.transaction("keys", "readonly"); const request = tx.objectStore("keys").get(keyId); request.onsuccess = () => { var _a; return resolve((_a = request.result) !== null && _a !== void 0 ? _a : null); }; request.onerror = () => reject(request.error); }); }
}
exports.StudyFlowWebCryptoTrustKeyStore = StudyFlowWebCryptoTrustKeyStore;
async function pairingAad(parsed, peerDeviceId) { return utf8(["SFLP-PAIRING-V2", parsed.pairingId, parsed.host.deviceId, peerDeviceId, parsed.sessionNonce].join("|")); }
async function derivePairingAesKey(privateKey, hostPublicKeyHex, parsed, peerDeviceId) { const subtle = requireSubtleCrypto(); const hostPublic = await subtle.importKey("spki", hexBytes(hostPublicKeyHex), { name: "ECDH", namedCurve: "P-256" }, false, []); const shared = new Uint8Array(await subtle.deriveBits({ name: "ECDH", public: hostPublic }, privateKey, 256)); const salt = new Uint8Array(await subtle.digest("SHA-256", utf8(parsed.sessionNonce))); const saltKey = await subtle.importKey("raw", salt, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]); const prk = new Uint8Array(await subtle.sign("HMAC", saltKey, shared)); const prkKey = await subtle.importKey("raw", prk, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]); const okm = await subtle.sign("HMAC", prkKey, concatBytes(await pairingAad(parsed, peerDeviceId), new Uint8Array([1]))); return subtle.importKey("raw", okm, { name: "AES-GCM" }, false, ["decrypt"]); }
async function decryptPairingSecret(privateKey, grant, parsed, peerDeviceId) { if (!/^[a-f0-9]{24}$/.test(grant.initializationVectorHex) || !/^[a-f0-9]{96,}$/.test(grant.encryptedTrustSecretHex))
    throw new Error("Invalid encrypted pairing grant"); const key = await derivePairingAesKey(privateKey, parsed.hostKeyAgreementPublicKeyHex, parsed, peerDeviceId); const clear = await requireSubtleCrypto().decrypt({ name: "AES-GCM", iv: hexBytes(grant.initializationVectorHex), additionalData: await pairingAad(parsed, peerDeviceId), tagLength: 128 }, key, hexBytes(grant.encryptedTrustSecretHex)); const secret = new TextDecoder().decode(clear); if (!/^[a-f0-9]{64}$/.test(secret))
    throw new Error("Pairing grant decrypted an invalid trust secret"); return secret; }
async function reconnectProofHex(store, keyId, challenge) { const transcript = ["SFLP-RECONNECT-V1", challenge.challengerDeviceId, challenge.peerDeviceId, challenge.nonce, String(challenge.expiresAtEpochMillis)].join("|"); return store.sign(keyId, utf8(transcript)); }
class StudyFlowWebPeerPersistence {
    constructor(storage = globalThis.localStorage) {
        this.storage = storage;
    }
    load(kind) { var _a, _b; const key = storageKey(kind); const encoded = this.storage.getItem(key); if (!encoded)
        return defaultState(kind); try {
        const loaded = JSON.parse(encoded);
        if (!loaded.identity || loaded.identity.clientKind !== kind)
            throw new Error("Persisted SFLP identity kind is inconsistent");
        validateDescriptor(loaded.identity);
        if (!Array.isArray(loaded.trustedPeers) || !loaded.endpoints || typeof loaded.endpoints !== "object" || !Array.isArray(loaded.pendingQueue) || !Array.isArray(loaded.journal))
            throw new Error("Persisted SFLP snapshot shape is invalid");
        const trustedPeers = loaded.trustedPeers.map(record => { var _a, _b; const legacy = record; validateDescriptor(record.peer); return { peer: record.peer, state: legacy.trustSecret ? "revoked" : record.state, pairedAtEpochMillis: record.pairedAtEpochMillis, updatedAtEpochMillis: record.updatedAtEpochMillis, lastSeenAtEpochMillis: record.lastSeenAtEpochMillis, revokedAtEpochMillis: legacy.trustSecret ? ((_a = record.revokedAtEpochMillis) !== null && _a !== void 0 ? _a : Date.now()) : record.revokedAtEpochMillis, trustKeyId: legacy.trustSecret ? null : ((_b = record.trustKeyId) !== null && _b !== void 0 ? _b : null) }; });
        const journal = loaded.journal.map(operation => validateJournalOperation(operation));
        return { identity: { ...loaded.identity, clientKind: kind, protocolSupport: PROTOCOL_SUPPORT, capabilities: capabilitiesFor(kind) }, trustedPeers, endpoints: loaded.endpoints, pendingQueue: loaded.pendingQueue, journal, peerRetryStates: (_a = loaded.peerRetryStates) !== null && _a !== void 0 ? _a : {}, verifiedRemoteFrontiers: (_b = loaded.verifiedRemoteFrontiers) !== null && _b !== void 0 ? _b : {} };
    }
    catch (error) {
        this.quarantine(kind, key, encoded, error);
        return defaultState(kind);
    } }
    save(snapshot) { const sanitized = { ...snapshot, trustedPeers: snapshot.trustedPeers.map(record => ({ peer: record.peer, state: record.state, pairedAtEpochMillis: record.pairedAtEpochMillis, updatedAtEpochMillis: record.updatedAtEpochMillis, lastSeenAtEpochMillis: record.lastSeenAtEpochMillis, revokedAtEpochMillis: record.revokedAtEpochMillis, trustKeyId: record.trustKeyId })) }; this.storage.setItem(storageKey(snapshot.identity.clientKind), JSON.stringify(sanitized)); }
    clear(kind = "web") { this.storage.removeItem(storageKey(kind)); }
    corruptionRecords(kind = "web") { const raw = this.storage.getItem(`${storageKey(kind)}.corruption.v1`); if (!raw)
        return []; try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
    }
    catch {
        return [];
    } }
    quarantine(kind, key, encoded, error) { const observedAtEpochMillis = Date.now(); const quarantineReference = `${key}.quarantine.${observedAtEpochMillis}.${randomHex(6)}`; this.storage.setItem(quarantineReference, encoded); const record = { kind, observedAtEpochMillis, storageKey: key, quarantineReference, reason: (error instanceof Error ? error.message : String(error)).slice(0, 512) }; this.storage.setItem(`${key}.corruption.v1`, JSON.stringify([...this.corruptionRecords(kind), record].slice(-64))); this.storage.removeItem(key); }
}
exports.StudyFlowWebPeerPersistence = StudyFlowWebPeerPersistence;
function validateEnvelopeShape(value) { if (!value || typeof value !== "object")
    throw new Error("Malformed SFLP response envelope"); const response = value; if (!response.protocol || !isProtocolSupported(response.protocol))
    throw new Error("SFLP response protocol is incompatible"); if (typeof response.messageId !== "string" || typeof response.workspaceId !== "string" || typeof response.senderDeviceId !== "string" || typeof response.name !== "string" || typeof response.sentAtEpochMillis !== "number" || !response.payload || typeof response.payload !== "object" || !["response", "error"].includes(String(response.type)))
    throw new Error("Malformed SFLP response envelope"); return response; }
class StudyFlowWebSocketRpcConnection {
    constructor(socket) {
        this.socket = socket;
    }
    async exchange(request) { if (this.socket.readyState !== WebSocket.OPEN)
        throw new Error("SFLP WebSocket is not open"); return new Promise((resolve, reject) => { const cleanup = () => { this.socket.removeEventListener("message", onMessage); this.socket.removeEventListener("error", onError); this.socket.removeEventListener("close", onClose); }; const onError = () => { cleanup(); reject(new Error("SFLP WebSocket exchange failed")); }; const onClose = () => { cleanup(); reject(new Error("SFLP WebSocket closed before a response")); }; const onMessage = (event) => { var _a, _b; try {
        if (typeof event.data !== "string")
            return;
        const response = validateEnvelopeShape(JSON.parse(event.data));
        if (response.correlationId !== request.messageId)
            return;
        cleanup();
        if (response.protocol.major !== request.protocol.major || response.protocol.minor !== request.protocol.minor)
            throw new Error("SFLP response protocol mismatch");
        if (response.type === "error") {
            const code = String((_a = response.payload.code) !== null && _a !== void 0 ? _a : "link.error");
            const message = String((_b = response.payload.message) !== null && _b !== void 0 ? _b : "SFLP request failed");
            throw new StudyFlowWebRpcError(code, response.payload.retryable === true, message);
        }
        resolve(response);
    }
    catch (error) {
        cleanup();
        reject(error);
    } }; this.socket.addEventListener("message", onMessage); this.socket.addEventListener("error", onError, { once: true }); this.socket.addEventListener("close", onClose, { once: true }); this.socket.send(JSON.stringify(request)); }); }
    close() { this.socket.close(); }
}
exports.StudyFlowWebSocketRpcConnection = StudyFlowWebSocketRpcConnection;
class StudyFlowWebSocketTransportAdapter {
    constructor() {
        this.supportsListen = false;
        this.supportsClientConnect = true;
    }
    async open(endpoint) { if (endpoint.kind !== "websocket")
        throw new Error("Web SFLP peers can connect only to ws/wss endpoints directly"); return new Promise((resolve, reject) => { const socket = new WebSocket(endpoint.uri, "studyflow.sflp.v1"); socket.onopen = () => resolve(socket); socket.onerror = () => reject(new Error("SFLP WebSocket connection failed")); }); }
    async connect(endpoint) { return new StudyFlowWebSocketRpcConnection(await this.open(endpoint)); }
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
    constructor(persistence = new StudyFlowWebPeerPersistence(), kind = "web", transport = new StudyFlowWebSocketTransportAdapter(), trustKeys = new StudyFlowWebCryptoTrustKeyStore()) {
        this.persistence = persistence;
        this.trustKeys = trustKeys;
        this.discovery = new StudyFlowWebManualDiscovery();
        this.snapshot = this.persistence.load(kind);
        this.persistence.save(this.snapshot);
        this.transport = transport;
        this.presence = new presence_js_1.StudyFlowWebPresenceRuntime(this.identity);
    }
    get identity() { return this.snapshot.identity; }
    get trustedPeers() { return [...this.snapshot.trustedPeers]; }
    get pendingOutgoingOperations() { return [...this.snapshot.pendingQueue]; }
    get journal() { return [...this.snapshot.journal]; }
    /** Browser receiver metadata is not a native trust grant; native trust is established only through pairFromQr(). */
    createPairingOffer(endpointHints = this.localEndpointHints()) { const pairingCode = sixDigitCode(); const expiresAtEpochMillis = Date.now() + PAIRING_TTL_MILLIS; const qrPayload = JSON.stringify({ type: "studyflow.sflp.web.receiver.v2", pairingCode, expiresAtEpochMillis, descriptor: this.identity, endpointHints }); return { pairingCode, expiresAtEpochMillis, descriptor: this.identity, endpointHints, qrPayload }; }
    async pairFromQr(qrPayload) { const parsed = decodeStudyFlowPairingQr(qrPayload); const now = Date.now(); if (now < parsed.createdAtEpochMillis - 30000 || now >= parsed.expiresAtEpochMillis)
        throw new Error("Pairing QR is expired or not yet valid"); if (parsed.host.deviceId === this.identity.deviceId)
        throw new Error("Cannot pair a device to itself"); const endpoint = parsed.endpointHints.map(normalizeEndpoint).find(item => item.kind === "websocket"); if (!endpoint)
        throw new Error("Pairing QR does not contain a browser-compatible ws/wss endpoint"); const subtle = requireSubtleCrypto(); const clientKeys = await subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]); const clientPublicKeyHex = bytesHex(await subtle.exportKey("spki", clientKeys.publicKey)); const connection = await this.transport.connect(endpoint); try {
        const request = envelope(this.identity.deviceId, LINK_SYSTEM_WORKSPACE_ID, "link.pair", { pairingId: parsed.pairingId, sessionNonce: parsed.sessionNonce, pairingCode: parsed.pairingCode, peer: this.identity, requestedAtEpochMillis: now, clientKeyAgreementPublicKeyHex: clientPublicKeyHex });
        const response = await connection.exchange(request);
        if (response.type !== "response" || response.name !== "link.pairing_grant" || response.senderDeviceId !== parsed.host.deviceId || response.workspaceId !== LINK_SYSTEM_WORKSPACE_ID)
            throw new Error("Invalid native SFLP pairing response envelope");
        const grant = response.payload;
        validateDescriptor(grant.host);
        if (grant.pairingId !== parsed.pairingId || grant.peerDeviceId !== this.identity.deviceId || grant.host.deviceId !== parsed.host.deviceId || grant.hostKeyAgreementPublicKeyHex !== parsed.hostKeyAgreementPublicKeyHex)
            throw new Error("Invalid native SFLP pairing grant");
        const secret = await decryptPairingSecret(clientKeys.privateKey, grant, parsed, this.identity.deviceId);
        const trustKeyId = await this.trustKeys.importSecret(grant.host.deviceId, secret);
        const record = { peer: grant.host, state: "trusted", pairedAtEpochMillis: grant.pairedAtEpochMillis, updatedAtEpochMillis: now, lastSeenAtEpochMillis: now, revokedAtEpochMillis: null, trustKeyId };
        this.snapshot.trustedPeers = [record, ...this.snapshot.trustedPeers.filter(item => item.peer.deviceId !== record.peer.deviceId)];
        for (const hint of parsed.endpointHints)
            this.rememberManualEndpoint(record.peer.deviceId, hint);
        this.persistence.save(this.snapshot);
        return record;
    }
    finally {
        connection.close();
    } }
    rememberManualEndpoint(peerDeviceId, uri) { var _a; const endpoint = this.discovery.remember(peerDeviceId, uri); const existing = (_a = this.snapshot.endpoints[peerDeviceId]) !== null && _a !== void 0 ? _a : []; this.snapshot.endpoints[peerDeviceId] = [endpoint, ...existing.filter(item => item.uri !== endpoint.uri)]; this.persistence.save(this.snapshot); return endpoint; }
    appendLocalMutation(workspaceId, entityId, operationType, payload) { const sequence = this.snapshot.journal.filter(op => op.workspaceId === workspaceId && op.originDeviceId === this.identity.deviceId).reduce((max, op) => Math.max(max, op.originSequence), 0) + 1; const entityRevision = this.snapshot.journal.filter(op => op.workspaceId === workspaceId && op.entityId === entityId).reduce((max, op) => Math.max(max, op.entityRevision), 0) + 1; const logicalTimestamp = this.snapshot.journal.filter(op => op.workspaceId === workspaceId).reduce((max, op) => { var _a; return Math.max(max, (_a = op.logicalTimestamp) !== null && _a !== void 0 ? _a : 0); }, 0) + 1; const occurredAtEpochMillis = Date.now(); const operation = { workspaceId, operationId: `op-${this.identity.deviceId}-${sequence}`, originDeviceId: this.identity.deviceId, originSequence: sequence, entityId, entityRevision, logicalTimestamp, operationType, kind: "mutation", occurredAtEpochMillis, payload }; this.snapshot.journal = [...this.snapshot.journal, operation]; for (const peer of this.snapshot.trustedPeers.filter(p => p.state === "trusted")) {
        this.snapshot.pendingQueue.push({ peerDeviceId: peer.peer.deviceId, workspaceId, operationId: operation.operationId, attempts: 0, nextAttemptEpochMillis: Date.now() });
    } this.persistence.save(this.snapshot); return operation; }
    applyRemoteOperations(operations, workspaceId, sourcePeerDeviceId) { const trustedOrigins = new Set(this.snapshot.trustedPeers.filter(item => item.state === "trusted").map(item => item.peer.deviceId)); trustedOrigins.add(this.identity.deviceId); if (sourcePeerDeviceId && !trustedOrigins.has(sourcePeerDeviceId))
        throw new StudyFlowWebSyncIntegrityError("SFLP operation source peer is not trusted"); const byId = new Map(this.snapshot.journal.map(op => [op.operationId, op])); const byOriginSequence = new Map(this.snapshot.journal.map(op => [`${op.originDeviceId}:${op.originSequence}`, op])); let added = 0; for (const raw of operations) {
        const operation = validateJournalOperation(raw, workspaceId);
        if (!trustedOrigins.has(operation.originDeviceId))
            throw new StudyFlowWebSyncIntegrityError("SFLP operation claims an untrusted origin");
        const existingById = byId.get(operation.operationId);
        if (existingById) {
            if (JSON.stringify(existingById) !== JSON.stringify(operation))
                throw new StudyFlowWebSyncIntegrityError("SFLP operation ID was reused with different content");
            continue;
        }
        const sequenceKey = `${operation.originDeviceId}:${operation.originSequence}`;
        const existingSequence = byOriginSequence.get(sequenceKey);
        if (existingSequence && existingSequence.operationId !== operation.operationId)
            throw new StudyFlowWebSyncIntegrityError("SFLP origin sequence was reused by another operation");
        byId.set(operation.operationId, operation);
        byOriginSequence.set(sequenceKey, operation);
        this.snapshot.journal.push(operation);
        added++;
    } if (added)
        this.persistence.save(this.snapshot); return added; }
    frontier(workspaceId) { var _a; const grouped = new Map(); for (const op of this.snapshot.journal.filter(item => item.workspaceId === workspaceId)) {
        const values = (_a = grouped.get(op.originDeviceId)) !== null && _a !== void 0 ? _a : [];
        values.push(op.originSequence);
        grouped.set(op.originDeviceId, values);
    } const out = {}; for (const [deviceId, sequences] of grouped) {
        const set = new Set(sequences);
        let contiguous = 0;
        while (set.has(contiguous + 1))
            contiguous++;
        if (contiguous > 0)
            out[deviceId] = contiguous;
    } return out; }
    operationsAfter(workspaceId, frontier, limit = 100) { return this.snapshot.journal.filter(op => { var _a; return op.workspaceId === workspaceId && op.originSequence > ((_a = frontier[op.originDeviceId]) !== null && _a !== void 0 ? _a : 0); }).sort((a, b) => a.originDeviceId.localeCompare(b.originDeviceId) || a.originSequence - b.originSequence).slice(0, limit); }
    acknowledge(peerDeviceId, workspaceId, operationIds) { const acknowledged = new Set(operationIds); if (acknowledged.size === 0)
        return; this.snapshot.pendingQueue = this.snapshot.pendingQueue.filter(entry => entry.peerDeviceId !== peerDeviceId || entry.workspaceId !== workspaceId || !acknowledged.has(entry.operationId)); this.persistence.save(this.snapshot); }
    peerRetry(peerDeviceId, workspaceId) { return this.snapshot.peerRetryStates[retryKey(peerDeviceId, workspaceId)]; }
    recordPeerFailure(peerDeviceId, workspaceId, now, failure, attemptedOperationIds) { var _a; const key = retryKey(peerDeviceId, workspaceId); const previous = this.snapshot.peerRetryStates[key]; const attempts = ((_a = previous === null || previous === void 0 ? void 0 : previous.attempts) !== null && _a !== void 0 ? _a : 0) + 1; const state = { peerDeviceId, workspaceId, attempts, lastAttemptEpochMillis: now, nextAttemptEpochMillis: now + retryDelayMillis(attempts), lastFailure: failure.slice(0, 512) }; this.snapshot.peerRetryStates[key] = state; if (attemptedOperationIds.size) {
        this.snapshot.pendingQueue = this.snapshot.pendingQueue.map(entry => entry.peerDeviceId === peerDeviceId && entry.workspaceId === workspaceId && attemptedOperationIds.has(entry.operationId) ? { ...entry, attempts: entry.attempts + 1, lastAttemptEpochMillis: now, nextAttemptEpochMillis: state.nextAttemptEpochMillis, lastFailure: state.lastFailure } : entry);
    } this.persistence.save(this.snapshot); return state; }
    clearPeerRetry(peerDeviceId, workspaceId) { const key = retryKey(peerDeviceId, workspaceId); if (this.snapshot.peerRetryStates[key]) {
        delete this.snapshot.peerRetryStates[key];
        this.persistence.save(this.snapshot);
    } }
    verifiedRemoteFrontier(peerDeviceId, workspaceId) { var _a; return { ...((_a = this.snapshot.verifiedRemoteFrontiers[retryKey(peerDeviceId, workspaceId)]) !== null && _a !== void 0 ? _a : {}) }; }
    saveVerifiedRemoteFrontier(peerDeviceId, workspaceId, frontier) { this.snapshot.verifiedRemoteFrontiers[retryKey(peerDeviceId, workspaceId)] = { ...frontier }; this.persistence.save(this.snapshot); }
    async reconcileWith(peerDeviceId, workspaceId) { const now = Date.now(); const retry = this.peerRetry(peerDeviceId, workspaceId); if (retry && retry.nextAttemptEpochMillis > now)
        return { pulled: 0, pushed: 0, status: "deferred", failure: retry.lastFailure, retryAtEpochMillis: retry.nextAttemptEpochMillis }; const attemptedOperationIds = new Set(); let pulled = 0; let pushed = 0; try {
        const result = await this.withAuthenticatedConnection(peerDeviceId, async (connection) => { var _a; const trustedOrigins = new Set(this.snapshot.trustedPeers.filter(item => item.state === "trusted").map(item => item.peer.deviceId)); trustedOrigins.add(this.identity.deviceId); const initialFrontier = this.frontier(workspaceId); const frontierResponse = await connection.exchange(envelope(this.identity.deviceId, workspaceId, "sync.frontier", { knownFrontier: frontierFromMap(initialFrontier) })); if (frontierResponse.name !== "sync.frontier" || frontierResponse.senderDeviceId !== peerDeviceId)
            throw new StudyFlowWebSyncIntegrityError(`Unexpected frontier response ${frontierResponse.name}`); const advertisedRemote = frontierToMap(frontierResponse.payload.currentFrontier); let hasMore = true; let pullRounds = 0; while (hasMore) {
            if (++pullRounds > 256)
                throw new StudyFlowWebSyncIntegrityError("SFLP pull exceeded the bounded continuation limit");
            const known = this.frontier(workspaceId);
            const pull = await connection.exchange(envelope(this.identity.deviceId, workspaceId, "sync.pull", { workspaceId, knownFrontier: frontierFromMap(known), maxOperations: 100 }));
            if (pull.name !== "sync.operations" || pull.senderDeviceId !== peerDeviceId)
                throw new StudyFlowWebSyncIntegrityError(`Unexpected pull response ${pull.name}`);
            const batch = validateSyncBatch(pull.payload, workspaceId, known, trustedOrigins);
            if (batch.hasMore && batch.operations.length === 0)
                throw new StudyFlowWebSyncIntegrityError("SFLP peer declared continuation without operations");
            pulled += this.applyRemoteOperations(batch.operations, workspaceId, peerDeviceId);
            hasMore = batch.hasMore;
        } const verifiedBase = this.verifiedRemoteFrontier(peerDeviceId, workspaceId); for (const [origin, sequence] of Object.entries(verifiedBase)) {
            if (sequence > ((_a = advertisedRemote[origin]) !== null && _a !== void 0 ? _a : 0))
                throw new StudyFlowWebSyncIntegrityError("SFLP peer regressed below its verified frontier");
        } const outgoing = this.operationsAfter(workspaceId, verifiedBase, 1000); if (outgoing.length) {
            outgoing.forEach(operation => attemptedOperationIds.add(operation.operationId));
            const through = advanceFrontier(verifiedBase, outgoing);
            const batch = { batchId: messageId("web-batch"), workspaceId, baseFrontier: frontierFromMap(verifiedBase), operations: outgoing, throughFrontier: frontierFromMap(through), hasMore: false };
            const apply = await connection.exchange(envelope(this.identity.deviceId, workspaceId, "sync.apply", batch));
            if (apply.name !== "sync.apply" || apply.senderDeviceId !== peerDeviceId)
                throw new StudyFlowWebSyncIntegrityError(`Unexpected apply response ${apply.name}`);
            const applyPayload = apply.payload;
            const resulting = frontierToMap(applyPayload.resultingFrontier);
            if (!frontierDominates(resulting, through))
                throw new StudyFlowWebSyncIntegrityError("SFLP apply response did not cover the accepted batch");
            pushed = outgoing.length;
            this.acknowledge(peerDeviceId, workspaceId, attemptedOperationIds);
            this.saveVerifiedRemoteFrontier(peerDeviceId, workspaceId, through);
        } return { pulled, pushed, status: "complete" }; });
        this.clearPeerRetry(peerDeviceId, workspaceId);
        return result;
    }
    catch (error) {
        const message = (error instanceof Error ? error.message : String(error)).slice(0, 512);
        if (error instanceof StudyFlowWebSyncIntegrityError || error instanceof StudyFlowWebTerminalPeerError || (error instanceof StudyFlowWebRpcError && !error.retryable))
            return { pulled, pushed, status: "terminal_failure", failure: message };
        const state = this.recordPeerFailure(peerDeviceId, workspaceId, Date.now(), message, attemptedOperationIds);
        return { pulled, pushed, status: "interrupted", failure: message, retryAtEpochMillis: state.nextAttemptEpochMillis };
    } }
    async sendRoutedRpc(peerDeviceId, workspaceId, name, payload) { return this.withAuthenticatedConnection(peerDeviceId, async (connection) => { const response = await connection.exchange(envelope(this.identity.deviceId, workspaceId, name, payload)); if (response.name !== "link.rpc_accepted" || response.senderDeviceId !== peerDeviceId)
        throw new Error(`Unexpected routed RPC response ${response.name}`); return response.payload; }); }
    updatePresence(workspaceId, availability, activityHint, visibilityCapabilities = []) { return this.presence.announce(workspaceId, availability, activityHint, visibilityCapabilities); }
    presenceViews(workspaceId) { return this.presence.views(workspaceId, this.identity); }
    presenceExchangeFor(peerDeviceId, workspaceId) { var _a; const peer = (_a = this.snapshot.trustedPeers.find(item => item.state === "trusted" && item.peer.deviceId === peerDeviceId)) === null || _a === void 0 ? void 0 : _a.peer; if (!peer)
        throw new Error("Presence target is not trusted"); return this.presence.exchangeForPeer(workspaceId, peer); }
    acceptPresenceExchange(peerDeviceId, workspaceId, exchange) { const trusted = new Set(this.snapshot.trustedPeers.filter(item => item.state === "trusted").map(item => item.peer.deviceId)); if (!trusted.has(peerDeviceId))
        throw new Error("Presence sender is not trusted"); return this.presence.acceptExchange(workspaceId, peerDeviceId, exchange, trusted); }
    revoke(peerDeviceId) { this.presence.forgetPeer(peerDeviceId); const now = Date.now(); let revoked = null; this.snapshot.trustedPeers = this.snapshot.trustedPeers.map(record => { if (record.peer.deviceId !== peerDeviceId)
        return record; if (record.trustKeyId)
        void this.trustKeys.remove(record.trustKeyId); revoked = { ...record, state: "revoked", trustKeyId: null, updatedAtEpochMillis: now, revokedAtEpochMillis: now }; return revoked; }); this.persistence.save(this.snapshot); return revoked; }
    forget(peerDeviceId) { this.presence.forgetPeer(peerDeviceId); const record = this.snapshot.trustedPeers.find(item => item.peer.deviceId === peerDeviceId); if (record === null || record === void 0 ? void 0 : record.trustKeyId)
        void this.trustKeys.remove(record.trustKeyId); const before = this.snapshot.trustedPeers.length; this.snapshot.trustedPeers = this.snapshot.trustedPeers.filter(item => item.peer.deviceId !== peerDeviceId); delete this.snapshot.endpoints[peerDeviceId]; this.snapshot.pendingQueue = this.snapshot.pendingQueue.filter(item => item.peerDeviceId !== peerDeviceId); for (const key of Object.keys(this.snapshot.peerRetryStates))
        if (key.startsWith(`${peerDeviceId}|`))
            delete this.snapshot.peerRetryStates[key]; for (const key of Object.keys(this.snapshot.verifiedRemoteFrontiers))
        if (key.startsWith(`${peerDeviceId}|`))
            delete this.snapshot.verifiedRemoteFrontiers[key]; this.persistence.save(this.snapshot); return before !== this.snapshot.trustedPeers.length; }
    snapshotForDebug() { return JSON.parse(JSON.stringify(this.snapshot)); }
    endpointFor(peerDeviceId) { var _a; const endpoint = ((_a = this.snapshot.endpoints[peerDeviceId]) !== null && _a !== void 0 ? _a : []).find(item => item.kind === "websocket"); if (!endpoint)
        throw new Error(`No ws/wss endpoint remembered for ${peerDeviceId}`); return endpoint; }
    trusted(peerDeviceId) { const record = this.snapshot.trustedPeers.find(item => item.peer.deviceId === peerDeviceId && item.state === "trusted"); if (!(record === null || record === void 0 ? void 0 : record.trustKeyId))
        throw new Error(`Peer ${peerDeviceId} is not trusted or its secure key is unavailable`); return record; }
    async withAuthenticatedConnection(peerDeviceId, block) { const record = this.trusted(peerDeviceId); const connection = await this.transport.connect(this.endpointFor(peerDeviceId)); try {
        const challengeResponse = await connection.exchange(envelope(this.identity.deviceId, LINK_SYSTEM_WORKSPACE_ID, "link.reconnect_challenge", { peerDeviceId: this.identity.deviceId }));
        if (challengeResponse.name !== "link.reconnect_challenge" || challengeResponse.senderDeviceId !== record.peer.deviceId)
            throw new StudyFlowWebTerminalPeerError(`Unexpected reconnect challenge ${challengeResponse.name}`);
        const challenge = challengeResponse.payload;
        if (challenge.challengerDeviceId !== record.peer.deviceId || challenge.peerDeviceId !== this.identity.deviceId || challenge.expiresAtEpochMillis <= Date.now())
            throw new StudyFlowWebTerminalPeerError("Reconnect challenge identity/expiry mismatch");
        const proofHex = await reconnectProofHex(this.trustKeys, record.trustKeyId, challenge);
        const accepted = await connection.exchange(envelope(this.identity.deviceId, LINK_SYSTEM_WORKSPACE_ID, "link.reconnect_proof", { proverDeviceId: this.identity.deviceId, nonce: challenge.nonce, proofHex }));
        if (accepted.name !== "link.reconnect_accepted" || accepted.senderDeviceId !== record.peer.deviceId)
            throw new StudyFlowWebTerminalPeerError(`Unexpected reconnect response ${accepted.name}`);
        return await block(connection);
    }
    finally {
        connection.close();
    } }
    localEndpointHints() { var _a, _b, _c, _d; return ((_b = (_a = globalThis.location) === null || _a === void 0 ? void 0 : _a.origin) === null || _b === void 0 ? void 0 : _b.startsWith("https")) ? [globalThis.location.origin.replace(/^https/, "wss") + "/sflp"] : ((_c = globalThis.location) === null || _c === void 0 ? void 0 : _c.hostname) === "127.0.0.1" || ((_d = globalThis.location) === null || _d === void 0 ? void 0 : _d.hostname) === "localhost" ? [globalThis.location.origin.replace(/^http/, "ws") + "/sflp"] : []; }
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
    constructor(peer, clock = () => Date.now()) {
        this.peer = peer;
        this.clock = clock;
        this.leases = new Map();
        this.inputDelegations = new Map();
        this.consumedDelegations = new Set();
        this.appliedControlMessages = new Set();
        this.appliedInputMessages = new Set();
        this.lastControlSequence = new Map();
        this.lastInputSequence = new Map();
    }
    rememberControlLease(lease) {
        if (lease.targetDeviceId !== this.peer.deviceId)
            throw new Error('Lease targets a different device');
        if (!lease.sessionId || !lease.workspaceId || !lease.controllerDeviceId || !lease.leaseToken)
            throw new Error('Malformed control lease');
        if (lease.expiresAtEpochMillis <= lease.grantedAtEpochMillis || lease.expiresAtEpochMillis <= this.clock())
            throw new Error('Expired control lease');
        if (!Array.isArray(lease.allowedCommands) || lease.allowedCommands.length === 0)
            throw new Error('Control lease has no allowed commands');
        this.leases.set(lease.sessionId, { ...lease, allowedCommands: [...lease.allowedCommands] });
        return lease;
    }
    applyControlCommand(command) {
        var _a;
        if (this.appliedControlMessages.has(command.messageId))
            return { status: 'duplicate', acknowledgedSequence: command.sequence };
        const lease = this.leases.get(command.sessionId);
        if (!lease)
            return { status: 'rejected', reason: 'unknown_lease' };
        if (lease.expiresAtEpochMillis < this.clock())
            return { status: 'rejected', reason: 'lease_expired' };
        if (command.workspaceId !== lease.workspaceId)
            return { status: 'rejected', reason: 'workspace_mismatch' };
        if (command.targetDeviceId !== this.peer.deviceId || command.targetDeviceId !== lease.targetDeviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        if (command.sourceDeviceId !== lease.controllerDeviceId)
            return { status: 'rejected', reason: 'wrong_controller' };
        if (command.leaseToken !== lease.leaseToken)
            return { status: 'rejected', reason: 'bad_lease_token' };
        if (!lease.allowedCommands.includes(command.kind))
            return { status: 'rejected', reason: 'command_not_allowed' };
        const last = (_a = this.lastControlSequence.get(command.sessionId)) !== null && _a !== void 0 ? _a : 0;
        if (!Number.isSafeInteger(command.sequence) || command.sequence <= last)
            return { status: 'rejected', reason: 'stale_sequence' };
        this.appliedControlMessages.add(command.messageId);
        this.lastControlSequence.set(command.sessionId, command.sequence);
        return { status: 'accepted', acknowledgedSequence: command.sequence };
    }
    acceptInputDelegation(offer) {
        var _a;
        if (this.consumedDelegations.has(offer.delegationId))
            return { status: 'duplicate', delegationId: offer.delegationId };
        if (offer.targetDeviceId !== this.peer.deviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        if (!offer.sourceDeviceId || offer.sourceDeviceId === this.peer.deviceId)
            return { status: 'rejected', reason: 'wrong_source' };
        if (offer.expiresAtEpochMillis <= offer.createdAtEpochMillis || offer.expiresAtEpochMillis < this.clock())
            return { status: 'rejected', reason: 'expired' };
        if (!/^[a-f0-9]{32,128}$/.test(offer.oneTimeNonceHex))
            return { status: 'rejected', reason: 'invalid_nonce' };
        if (!Array.isArray(offer.acceptedInputCapabilities) || offer.acceptedInputCapabilities.length === 0)
            return { status: 'rejected', reason: 'missing_input_capability' };
        const localCaps = new Set((_a = this.peer.capabilities) !== null && _a !== void 0 ? _a : []);
        if (localCaps.size > 0 && offer.acceptedInputCapabilities.some(capability => !localCaps.has(capability)))
            return { status: 'rejected', reason: 'missing_input_capability' };
        this.consumedDelegations.add(offer.delegationId);
        this.inputDelegations.set(offer.delegationId, { ...offer, acceptedInputCapabilities: [...offer.acceptedInputCapabilities] });
        return { status: 'accepted', delegationId: offer.delegationId };
    }
    applyInputEvent(event) {
        var _a;
        if (this.appliedInputMessages.has(event.messageId))
            return { status: 'duplicate', acknowledgedSequence: event.sequence };
        const offer = this.inputDelegations.get(event.delegationId);
        if (!offer)
            return { status: 'rejected', reason: 'unknown_delegation' };
        if (offer.expiresAtEpochMillis < this.clock())
            return { status: 'rejected', reason: 'delegation_expired' };
        if (event.workspaceId !== offer.workspaceId)
            return { status: 'rejected', reason: 'workspace_mismatch' };
        if (event.sourceDeviceId !== offer.sourceDeviceId)
            return { status: 'rejected', reason: 'wrong_source' };
        if (event.targetDeviceId !== this.peer.deviceId || event.targetDeviceId !== offer.targetDeviceId)
            return { status: 'rejected', reason: 'wrong_target' };
        const last = (_a = this.lastInputSequence.get(event.delegationId)) !== null && _a !== void 0 ? _a : 0;
        if (!Number.isSafeInteger(event.sequence) || event.sequence <= last)
            return { status: 'rejected', reason: 'stale_sequence' };
        this.appliedInputMessages.add(event.messageId);
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
            policyAudit: existing === null || existing === void 0 ? void 0 : existing.policyAudit,
            routeAudit: existing === null || existing === void 0 ? void 0 : existing.routeAudit,
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
// Keep the SFLP20/SFLP21 wire vocabulary present at runtime as well as in erased
// TypeScript unions. Release-contract checks intentionally audit compiled runtime
// semantics rather than treating source-only type declarations as delivery proof.
exports.STUDYFLOW_SHARE_IMPORT_INTENTS = [
    'clipboard_preview',
    'clipboard_apply',
    'share_preview',
    'share_import',
];
exports.STUDYFLOW_CONFLICT_REVIEW_OPERATION = 'conflict.review';
class StudyFlowShareImportConflictRuntime {
    constructor(peer) {
        this.peer = peer;
        this.imports = new Map();
        this.conflictReviews = new Map();
    }
    localClientKind() { var _a, _b; return (_a = this.peer.clientKind) !== null && _a !== void 0 ? _a : (_b = this.peer.identity) === null || _b === void 0 ? void 0 : _b.clientKind; }
    isReadOnlyTv() { return this.localClientKind() === 'android_tv' || this.localClientKind() === 'web_tv'; }
    applyShareImportDelivery(delivery) {
        var _a, _b;
        const localId = (_a = this.peer.deviceId) !== null && _a !== void 0 ? _a : (_b = this.peer.identity) === null || _b === void 0 ? void 0 : _b.deviceId;
        if (delivery.targetDeviceId !== localId)
            return { status: 'rejected', reason: 'wrong_target' };
        if (this.isReadOnlyTv() && (delivery.offer.intent === 'clipboard_apply' || delivery.offer.intent === 'share_import')) {
            return { status: 'rejected', reason: 'tv_write_forbidden' };
        }
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
        if (this.isReadOnlyTv())
            return { status: 'rejected', reason: 'tv_write_forbidden' };
        if (!this.conflictReviews.has(reviewId))
            return { status: 'rejected', reason: 'unknown_review' };
        this.conflictReviews.delete(reviewId);
        return { status: 'resolved', decision, operation: exports.STUDYFLOW_CONFLICT_REVIEW_OPERATION };
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
