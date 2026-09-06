"use strict";

import { STATE_SCHEMA_VERSION, validateStudyState } from "./domain.js";

export const STORAGE_KEY = `studyflow.local.state.v${STATE_SCHEMA_VERSION}`;

function resolveBrowserStorage() {
    try {
        return globalThis.localStorage ?? null;
    } catch {
        return null;
    }
}

function result(status, extra = {}) {
    return { status, ...extra };
}

export function createBrowserRepository({ storage = resolveBrowserStorage(), key = STORAGE_KEY } = {}) {
    return {
        key,

        load() {
            if (!storage) return result("unavailable", { state: null, reason: "Browser local storage is unavailable." });
            let raw;
            try {
                raw = storage.getItem(key);
            } catch (error) {
                return result("unavailable", { state: null, reason: `Local storage read failed: ${error instanceof Error ? error.message : String(error)}` });
            }
            if (raw == null) return result("empty", { state: null, reason: null });

            let state;
            try {
                state = JSON.parse(raw);
            } catch {
                return result("invalid", { state: null, reason: "Saved local state is not valid JSON." });
            }
            const errors = validateStudyState(state);
            if (errors.length) {
                return result("invalid", { state: null, reason: `Saved local state failed validation: ${errors[0]}` });
            }
            return result("loaded", { state, reason: null });
        },

        save(state) {
            if (!storage) return result("unavailable", { reason: "Browser local storage is unavailable." });
            const errors = validateStudyState(state);
            if (errors.length) return result("invalid", { reason: `Refused to save invalid state: ${errors[0]}` });
            try {
                storage.setItem(key, JSON.stringify(state));
            } catch (error) {
                return result("unavailable", { reason: `Local storage write failed: ${error instanceof Error ? error.message : String(error)}` });
            }
            return result("saved", { reason: null });
        },

        clear() {
            if (!storage) return result("unavailable", { reason: "Browser local storage is unavailable." });
            try {
                storage.removeItem(key);
            } catch (error) {
                return result("unavailable", { reason: `Local storage clear failed: ${error instanceof Error ? error.message : String(error)}` });
            }
            return result("cleared", { reason: null });
        },
    };
}
