"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TV_PAIRING_TTL_SECONDS = exports.TV_PAIRING_CODE_LENGTH = exports.TV_CONTROL_PROTOCOL_VERSION = void 0;
exports.isStudyFlowTvControlCommand = isStudyFlowTvControlCommand;
exports.isStudyFlowTvCastState = isStudyFlowTvCastState;
exports.installTvControlReceiver = installTvControlReceiver;
exports.TV_CONTROL_PROTOCOL_VERSION = 1;
exports.TV_PAIRING_CODE_LENGTH = 6;
exports.TV_PAIRING_TTL_SECONDS = 120;
const SURFACES = new Set(["HOME", "TODAY", "STUDY_SESSION", "QUESTION_SET", "REVIEW", "MOCK_EXAM", "PROGRESS", "SCHEDULE", "LIBRARY", "SETTINGS"]);
const ACTIONS = new Set(["OPEN", "START", "PAUSE", "RESUME", "NEXT", "PREVIOUS", "SELECT_ANSWER", "SHOW_EXPLANATION", "MARK_FOR_REVIEW", "SUBMIT", "REVIEW_RESULTS", "COMPLETE", "BACK", "STOP_CAST"]);
const CASTABLE_SURFACES = new Set(["STUDY_SESSION", "QUESTION_SET", "REVIEW", "MOCK_EXAM", "PROGRESS", "SCHEDULE", "LIBRARY"]);
function isStudyFlowTvControlCommand(value) {
    if (!value || typeof value !== "object")
        return false;
    const v = value;
    return typeof v.commandId === "string" && v.commandId.length > 0 && v.protocolVersion === exports.TV_CONTROL_PROTOCOL_VERSION && SURFACES.has(v.surface) && ACTIONS.has(v.action) && typeof v.issuedAtEpochMillis === "number" && v.requiresUserPresence === true;
}
function isStudyFlowTvCastState(value) {
    if (!value || typeof value !== "object")
        return false;
    const v = value;
    const progress = v.progressPercent;
    return typeof v.castId === "string" && v.castId.length > 0 && v.protocolVersion === exports.TV_CONTROL_PROTOCOL_VERSION && CASTABLE_SURFACES.has(v.surface) && typeof v.title === "string" && v.title.trim().length > 0 && typeof v.updatedAtEpochMillis === "number" && (progress == null || (typeof progress === "number" && progress >= 0 && progress <= 100));
}
function dispatchEnvelope(envelope) {
    if (envelope.type === "command" && isStudyFlowTvControlCommand(envelope.payload))
        globalThis.dispatchEvent(new CustomEvent("studyflow:tv-control", { detail: envelope.payload }));
    if (envelope.type === "cast" && isStudyFlowTvCastState(envelope.payload))
        globalThis.dispatchEvent(new CustomEvent("studyflow:tv-cast", { detail: envelope.payload }));
}
function installTvControlReceiver(enabled, deviceName = "StudyFlow Browser TV") {
    let snapshot = { state: enabled ? "connecting" : "disabled", pairingCode: null, expiresAtEpochMillis: null, deviceName, transport: "none", message: enabled ? "Starting TV control receiver." : "TV control receiver disabled." };
    const listeners = new Set();
    let disposed = false;
    let source = null;
    let abort = null;
    let registration = null;
    let pollCursor = 0;
    let refreshTimer = null;
    const publish = (next) => { snapshot = { ...snapshot, ...next }; for (const listener of listeners)
        listener(); };
    const endpoint = (path) => path;
    const register = async () => {
        try {
            const response = await fetch(endpoint("/api/tv/receiver"), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ deviceName }) });
            if (!response.ok)
                throw new Error(`TV relay registration failed: HTTP ${response.status}`);
            registration = await response.json();
            if (disposed)
                return;
            publish({ state: registration.paired ? "paired" : "pairing", pairingCode: registration.pairingCode, expiresAtEpochMillis: registration.expiresAtEpochMillis, message: registration.paired ? "Phone paired." : "Enter this code on the phone remote." });
            if (refreshTimer != null)
                clearTimeout(refreshTimer);
            refreshTimer = globalThis.setTimeout(() => { if (!disposed && snapshot.state === "pairing") {
                source === null || source === void 0 ? void 0 : source.close();
                abort === null || abort === void 0 ? void 0 : abort.abort();
                registration = null;
                pollCursor = 0;
                void register();
            } }, Math.max(1000, registration.expiresAtEpochMillis - Date.now() + 250));
            connectEvents();
        }
        catch (error) {
            if (!disposed)
                publish({ state: "error", message: error instanceof Error ? error.message : String(error) });
        }
    };
    const receive = (envelope) => {
        var _a;
        pollCursor = Math.max(pollCursor, Number(envelope.sequence) || 0);
        if (envelope.type === "pairing") {
            const p = envelope.payload;
            const state = String((_a = p === null || p === void 0 ? void 0 : p.state) !== null && _a !== void 0 ? _a : "");
            if (state === "PAIRED") {
                if (refreshTimer != null) {
                    clearTimeout(refreshTimer);
                    refreshTimer = null;
                }
                publish({ state: "paired", message: "Phone paired and ready." });
            }
            else if (state === "REVOKED") {
                publish({ state: "revoked", message: "Phone pairing revoked. Creating a new pairing code." });
                source === null || source === void 0 ? void 0 : source.close();
                abort === null || abort === void 0 ? void 0 : abort.abort();
                registration = null;
                pollCursor = 0;
                globalThis.setTimeout(() => { if (!disposed)
                    void register(); }, 250);
            }
        }
        dispatchEnvelope(envelope);
    };
    const startPoll = () => { if (!registration || disposed)
        return; publish({ transport: "poll" }); abort = new AbortController(); const loop = async () => { while (!disposed && registration) {
        try {
            const url = `/api/tv/poll?receiverId=${encodeURIComponent(registration.receiverId)}&nonce=${encodeURIComponent(registration.sessionNonce)}&after=${pollCursor}`;
            const res = await fetch(url, { cache: "no-store", signal: abort === null || abort === void 0 ? void 0 : abort.signal });
            if (!res.ok)
                throw new Error(`poll HTTP ${res.status}`);
            const body = await res.json();
            for (const event of body.events || [])
                receive(event);
        }
        catch (error) {
            if (disposed)
                return;
            publish({ state: "error", message: `TV relay polling failed: ${error instanceof Error ? error.message : String(error)}` });
            await new Promise(resolve => setTimeout(resolve, 1500));
        }
    } }; void loop(); };
    const connectEvents = () => { if (!registration || disposed)
        return; if (typeof EventSource === "undefined") {
        startPoll();
        return;
    } publish({ transport: "sse" }); const url = `/api/tv/events?receiverId=${encodeURIComponent(registration.receiverId)}&nonce=${encodeURIComponent(registration.sessionNonce)}`; source = new EventSource(url); source.onmessage = (event) => { try {
        receive(JSON.parse(event.data));
    }
    catch { } }; source.onerror = () => { source === null || source === void 0 ? void 0 : source.close(); source = null; if (!disposed)
        startPoll(); }; };
    if (enabled)
        void register();
    return { getSnapshot: () => snapshot, subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener); }, dispose: () => { disposed = true; source === null || source === void 0 ? void 0 : source.close(); abort === null || abort === void 0 ? void 0 : abort.abort(); if (refreshTimer != null)
            clearTimeout(refreshTimer); listeners.clear(); } };
}
