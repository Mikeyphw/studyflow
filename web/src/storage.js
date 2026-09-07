"use strict";

import { STATE_SCHEMA_VERSION, migrateStudyState, validateStudyState } from "./domain.js";

export const STORAGE_KEY = `studyflow.local.state.v${STATE_SCHEMA_VERSION}`;
export const LEGACY_STORAGE_KEYS = Object.freeze(["studyflow.local.state.v2", "studyflow.local.state.v1"]);

function resolveBrowserStorage() {
    try { return globalThis.localStorage ?? null; } catch { return null; }
}
function result(status, extra = {}) { return { status, ...extra }; }
function parseState(raw, now) {
    let state;
    try { state = JSON.parse(raw); } catch { return result("invalid", { state: null, reason: "Saved local state is not valid JSON." }); }
    const migrated = migrateStudyState(state, { now });
    const errors = validateStudyState(migrated);
    if (errors.length) return result("invalid", { state: null, reason: `Saved local state failed validation: ${errors[0]}` });
    return result(state.schemaVersion === STATE_SCHEMA_VERSION ? "loaded" : "migrated", { state: migrated, reason: null });
}

export function createBrowserRepository({ storage = resolveBrowserStorage(), key = STORAGE_KEY, legacyKeys = LEGACY_STORAGE_KEYS, now = () => new Date().toISOString() } = {}) {
    return {
        key,
        load() {
            if (!storage) return result("unavailable", { state: null, reason: "Browser local storage is unavailable." });
            try {
                const raw = storage.getItem(key);
                if (raw != null) return parseState(raw, now());
                for (const legacyKey of legacyKeys) {
                    if (legacyKey === key) continue;
                    const legacyRaw = storage.getItem(legacyKey);
                    if (legacyRaw == null) continue;
                    const parsed = parseState(legacyRaw, now());
                    if (parsed.state) return { ...parsed, status: "migrated", legacyKey };
                    return parsed;
                }
                return result("empty", { state: null, reason: null });
            } catch (error) {
                return result("unavailable", { state: null, reason: `Local storage read failed: ${error instanceof Error ? error.message : String(error)}` });
            }
        },
        save(state) {
            if (!storage) return result("unavailable", { reason: "Browser local storage is unavailable." });
            const errors = validateStudyState(state);
            if (errors.length) return result("invalid", { reason: `Refused to save invalid state: ${errors[0]}` });
            try { storage.setItem(key, JSON.stringify(state)); }
            catch (error) { return result("unavailable", { reason: `Local storage write failed: ${error instanceof Error ? error.message : String(error)}` }); }
            return result("saved", { reason: null });
        },
        clear() {
            if (!storage) return result("unavailable", { reason: "Browser local storage is unavailable." });
            try {
                storage.removeItem(key);
                for (const legacyKey of legacyKeys) storage.removeItem(legacyKey);
            } catch (error) { return result("unavailable", { reason: `Local storage clear failed: ${error instanceof Error ? error.message : String(error)}` }); }
            return result("cleared", { reason: null });
        },
    };
}
