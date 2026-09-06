"use strict";

import {
    createActivity,
    createInitialStudyState,
    validateStudyState,
} from "./domain.js";

function cloneState(state) {
    return JSON.parse(JSON.stringify(state));
}

function persistenceMessage(status, reason = null) {
    if (status === "loaded") return "Local state loaded from this browser.";
    if (status === "saved") return "Saved locally on this device.";
    if (status === "empty") return "Local state initialized on this device.";
    if (status === "invalid") return reason ?? "Saved local state was invalid; using a safe in-memory baseline.";
    if (status === "unavailable") return reason ?? "Local persistence is unavailable; changes remain in memory only.";
    if (status === "cleared") return "Local state reset to the StudyFlow sample baseline.";
    return reason ?? "Local state ready.";
}

export function createStudyStore({ repository, initialStateFactory = createInitialStudyState, now = () => new Date().toISOString() } = {}) {
    if (!repository || typeof repository.load !== "function" || typeof repository.save !== "function") {
        throw new TypeError("createStudyStore requires a repository with load/save methods");
    }

    const loaded = repository.load();
    let state = loaded.state ? cloneState(loaded.state) : initialStateFactory({ now: now() });
    let persistence = {
        status: loaded.status,
        message: persistenceMessage(loaded.status, loaded.reason),
        durable: loaded.status !== "unavailable" && loaded.status !== "invalid",
    };
    const listeners = new Set();

    if (!loaded.state && loaded.status === "empty") {
        const seeded = repository.save(state);
        persistence = {
            status: seeded.status === "saved" ? "saved" : seeded.status,
            message: seeded.status === "saved"
                ? "Local state initialized and saved on this device."
                : persistenceMessage(seeded.status, seeded.reason),
            durable: seeded.status === "saved",
        };
    }

    function notify(reason) {
        for (const listener of listeners) listener(state, persistence, reason);
    }

    function commit(nextState, reason) {
        const errors = validateStudyState(nextState);
        if (errors.length) throw new TypeError(`Invalid StudyFlow state: ${errors[0]}`);
        state = {
            ...cloneState(nextState),
            revision: state.revision + 1,
        };
        const saved = repository.save(state);
        persistence = {
            status: saved.status,
            message: persistenceMessage(saved.status, saved.reason),
            durable: saved.status === "saved",
        };
        notify(reason);
        return state;
    }

    return {
        getState() {
            return state;
        },

        getSnapshot() {
            return cloneState(state);
        },

        getPersistenceStatus() {
            return { ...persistence };
        },

        subscribe(listener) {
            if (typeof listener !== "function") throw new TypeError("listener must be a function");
            listeners.add(listener);
            return () => listeners.delete(listener);
        },

        addActivity(input) {
            if (!state.activePlanId) throw new TypeError("Cannot add an activity without an active plan");
            const activity = createActivity({
                ...input,
                planId: input.planId ?? state.activePlanId,
                createdAt: input.createdAt ?? now(),
                updatedAt: input.updatedAt ?? now(),
            });
            commit({ ...state, activities: [...state.activities, activity] }, "activity-added");
            return activity;
        },

        replaceState(nextState, reason = "state-replaced") {
            return commit(nextState, reason);
        },

        resetLocalState() {
            const cleared = typeof repository.clear === "function" ? repository.clear() : { status: "cleared" };
            state = initialStateFactory({ now: now() });
            const saved = repository.save(state);
            const status = saved.status === "saved" ? "cleared" : saved.status;
            persistence = {
                status,
                message: saved.status === "saved"
                    ? persistenceMessage("cleared")
                    : persistenceMessage(saved.status, saved.reason ?? cleared.reason),
                durable: saved.status === "saved",
            };
            notify("state-reset");
            return state;
        },
    };
}
