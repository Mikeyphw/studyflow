"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.STUDYFLOW_LOCAL_RELEASE = void 0;
exports.evaluateReleaseReadiness = evaluateReleaseReadiness;
const domain_1 = require("./domain");
const indexeddb_1 = require("./indexeddb");
const portable_1 = require("./portable");
exports.STUDYFLOW_LOCAL_RELEASE = Object.freeze({
    product: "StudyFlow Local",
    version: "1.0.0",
    stateSchema: domain_1.STATE_SCHEMA_VERSION,
    indexedDbSchema: indexeddb_1.STUDYFLOW_DB_VERSION,
    portableFormat: portable_1.PORTABLE_FORMAT_VERSION,
    channel: "local-first",
    releaseSeal: "parity-closure",
    runtimeArtifact: "self-contained-dist",
});
function evaluateReleaseReadiness(state, persistenceBackend) {
    const errors = (0, domain_1.validateStudyState)(state);
    if (errors.length)
        return { status: "blocked", version: exports.STUDYFLOW_LOCAL_RELEASE.version, reasons: [`State validation failed: ${errors[0]}`] };
    const reasons = [];
    if (!state.activePlanId)
        reasons.push("No active plan is selected.");
    if (persistenceBackend !== "indexeddb")
        reasons.push("IndexedDB is unavailable; structured state is running in compatibility storage and local attachment bytes are disabled.");
    return { status: reasons.length ? "degraded" : "ready", version: exports.STUDYFLOW_LOCAL_RELEASE.version, reasons: reasons.length ? reasons : ["Local state, schema and release contracts are valid."] };
}
