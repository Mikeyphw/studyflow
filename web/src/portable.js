"use strict";

import { ENTITY_COLLECTIONS, migrateStudyState, validateStudyState } from "./domain.js";

export const PORTABLE_FORMAT = "studyflow-local-package";
export const PORTABLE_FORMAT_VERSION = 1;
export const MAX_PORTABLE_BYTES = 5 * 1024 * 1024;

function clone(value) { return JSON.parse(JSON.stringify(value)); }
function counts(state) { return Object.fromEntries(ENTITY_COLLECTIONS.map((name) => [name, Array.isArray(state[name]) ? state[name].length : 0])); }
function byId(items) { return new Map((items ?? []).map((item) => [item.id, item])); }
function same(a,b) { return JSON.stringify(a) === JSON.stringify(b); }

export function createPortablePackage(state, { now = () => new Date().toISOString() } = {}) {
    const errors = validateStudyState(state); if (errors.length) throw new TypeError(`Cannot export invalid StudyFlow state: ${errors[0]}`);
    const exportedAt = now();
    return { format: PORTABLE_FORMAT, formatVersion: PORTABLE_FORMAT_VERSION, exportedAt, stateSchemaVersion: state.schemaVersion, manifest: { activePlanId: state.activePlanId, counts: counts(state) }, payload: clone(state) };
}

export function serializePortablePackage(packageObject) { return JSON.stringify(packageObject, null, 2) + "\n"; }

export function parsePortablePackage(text, { now = () => new Date().toISOString() } = {}) {
    if (typeof text !== "string") throw new TypeError("Portable package must be JSON text");
    if (new TextEncoder().encode(text).length > MAX_PORTABLE_BYTES) throw new TypeError("Portable package exceeds the 5 MiB local import limit");
    let parsed; try { parsed = JSON.parse(text); } catch { throw new TypeError("Portable package is not valid JSON"); }
    if (!parsed || parsed.format !== PORTABLE_FORMAT) throw new TypeError("Not a StudyFlow Local portable package");
    if (parsed.formatVersion !== PORTABLE_FORMAT_VERSION) throw new TypeError(`Unsupported portable package version: ${parsed.formatVersion}`);
    if (!parsed.payload || typeof parsed.payload !== "object" || Array.isArray(parsed.payload)) throw new TypeError("Portable package payload is missing");
    const candidate = migrateStudyState(clone(parsed.payload), { now: now() });
    const errors = validateStudyState(candidate); if (errors.length) throw new TypeError(`Portable package state is invalid: ${errors[0]}`);
    const actualCounts = counts(candidate);
    if (parsed.manifest?.counts && !same(parsed.manifest.counts, actualCounts)) throw new TypeError("Portable package manifest counts do not match its payload");
    return { ...parsed, payload: candidate, manifest: { activePlanId: candidate.activePlanId, counts: actualCounts } };
}

export function buildPortableImportPreview(currentState, packageObject) {
    const nextState = clone(packageObject.payload);
    const collections = {};
    let added=0, updated=0, removed=0, unchanged=0;
    for (const name of ENTITY_COLLECTIONS) {
        const before = byId(currentState[name]), after = byId(nextState[name]);
        let a=0,u=0,r=0,sameCount=0;
        for (const [id,item] of after) { if (!before.has(id)) a++; else if (!same(before.get(id),item)) u++; else sameCount++; }
        for (const id of before.keys()) if (!after.has(id)) r++;
        collections[name] = { add:a, update:u, remove:r, unchanged:sameCount };
        added+=a; updated+=u; removed+=r; unchanged+=sameCount;
    }
    return { baseRevision: currentState.revision, generatedAt: new Date().toISOString(), packageExportedAt: packageObject.exportedAt ?? null, sourceActivePlanId: nextState.activePlanId, summary: { add:added, update:updated, remove:removed, unchanged }, collections, nextState };
}

export function portablePackageFilename(state, at = new Date()) {
    const plan = state.plans.find((item) => item.id === state.activePlanId); const stem = String(plan?.name ?? "studyflow").toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"").slice(0,48) || "studyflow";
    const stamp = at.toISOString().replace(/[:.]/g,"-"); return `${stem}-${stamp}.studyflow.json`;
}
