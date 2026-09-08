"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.BB_STUDY_PHASE_SUGGESTIONS = exports.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS = exports.REFERENCE_POSSIBLY_STALE_AFTER_DAYS = void 0;
exports.normalizeStudyPhaseDefinitions = normalizeStudyPhaseDefinitions;
exports.validateStudyPhaseDefinitions = validateStudyPhaseDefinitions;
exports.deriveActiveStudyPhase = deriveActiveStudyPhase;
exports.cloneBbStudyPhaseSuggestions = cloneBbStudyPhaseSuggestions;
exports.derivePreEditalCockpit = derivePreEditalCockpit;
exports.decodeReferenceProvenance = decodeReferenceProvenance;
exports.encodeReferenceProvenance = encodeReferenceProvenance;
exports.deriveMaterialReferenceFreshness = deriveMaterialReferenceFreshness;
const promise_cleanup_1 = require("./promise_cleanup");
const DAY_MS = 86400000;
const PHASE_KINDS = new Set(["foundation", "expansion", "consolidation", "questions-reviews", "custom"]);
const ORIGIN_PREFIX = "studyflow:reference-origin=";
const VERIFIED_PREFIX = "studyflow:reference-verified-on=";
const SUPERSEDES_PREFIX = "studyflow:reference-supersedes=";
exports.REFERENCE_POSSIBLY_STALE_AFTER_DAYS = 90;
exports.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS = 365;
const cleanText = (value) => { const text = String(value !== null && value !== void 0 ? value : "").trim(); return text || null; };
const dateOnly = (value) => { const text = cleanText(value); return text && /^\d{4}-\d{2}-\d{2}$/.test(text) && Number.isFinite(Date.parse(`${text}T00:00:00Z`)) ? text : null; };
const dayFromDateLike = (value) => { if (value instanceof Date)
    return Number.isNaN(value.getTime()) ? null : value.toISOString().slice(0, 10); const canonical = dateOnly(value); if (canonical)
    return canonical; const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString().slice(0, 10); };
const nonNegativeInt = (value) => { if (value == null || value === "")
    return null; const n = Number(value); return Number.isInteger(n) && n >= 0 ? n : null; };
const pct = (value) => { const n = Number(value); return Number.isInteger(n) && n >= 0 && n <= 100 ? n : 0; };
const clone = (value) => JSON.parse(JSON.stringify(value));
exports.BB_STUDY_PHASE_SUGGESTIONS = Object.freeze([
    { id: "bb-foundation-2026", kind: "foundation", label: "Foundation", startsOn: "2026-09-07", endsOn: "2026-09-30", theoryRatioPercent: 60, questionRatioPercent: 25, reviewRatioPercent: 15, questionGoal: 300, mockExamGoal: 3, allocationIsStudyFlowSuggested: true },
    { id: "bb-expansion-2026", kind: "expansion", label: "Expansion", startsOn: "2026-10-01", endsOn: "2026-10-31", theoryRatioPercent: 45, questionRatioPercent: 40, reviewRatioPercent: 15, questionGoal: 500, mockExamGoal: 5, allocationIsStudyFlowSuggested: true },
    { id: "bb-consolidation-2026", kind: "consolidation", label: "Consolidation", startsOn: "2026-11-01", endsOn: "2026-11-30", theoryRatioPercent: 25, questionRatioPercent: 55, reviewRatioPercent: 20, questionGoal: 700, mockExamGoal: 4, allocationIsStudyFlowSuggested: true },
    { id: "bb-questions-reviews-2026", kind: "questions-reviews", label: "Questions & reviews", startsOn: "2026-12-01", endsOn: "2026-12-31", theoryRatioPercent: 10, questionRatioPercent: 65, reviewRatioPercent: 25, questionGoal: 1000, mockExamGoal: 4, allocationIsStudyFlowSuggested: true },
]);
function normalizeStudyPhaseDefinitions(input) {
    if (!Array.isArray(input))
        return [];
    return input.map((raw, index) => {
        var _a, _b, _c, _d;
        const kind = PHASE_KINDS.has(String(raw === null || raw === void 0 ? void 0 : raw.kind)) ? String(raw.kind) : "custom";
        const startsOn = (_a = dateOnly(raw === null || raw === void 0 ? void 0 : raw.startsOn)) !== null && _a !== void 0 ? _a : "1970-01-01";
        const endsOn = (_b = dateOnly(raw === null || raw === void 0 ? void 0 : raw.endsOn)) !== null && _b !== void 0 ? _b : startsOn;
        return {
            id: (_c = cleanText(raw === null || raw === void 0 ? void 0 : raw.id)) !== null && _c !== void 0 ? _c : `phase-${index + 1}`,
            kind,
            label: (_d = cleanText(raw === null || raw === void 0 ? void 0 : raw.label)) !== null && _d !== void 0 ? _d : kind.replace(/-/g, " "),
            startsOn, endsOn,
            theoryRatioPercent: pct(raw === null || raw === void 0 ? void 0 : raw.theoryRatioPercent),
            questionRatioPercent: pct(raw === null || raw === void 0 ? void 0 : raw.questionRatioPercent),
            reviewRatioPercent: pct(raw === null || raw === void 0 ? void 0 : raw.reviewRatioPercent),
            questionGoal: nonNegativeInt(raw === null || raw === void 0 ? void 0 : raw.questionGoal),
            mockExamGoal: nonNegativeInt(raw === null || raw === void 0 ? void 0 : raw.mockExamGoal),
            allocationIsStudyFlowSuggested: Boolean(raw === null || raw === void 0 ? void 0 : raw.allocationIsStudyFlowSuggested),
        };
    });
}
function validateStudyPhaseDefinitions(input) {
    const phases = normalizeStudyPhaseDefinitions(input);
    const errors = [];
    const raw = Array.isArray(input) ? input : [];
    if (raw.length !== phases.length)
        return ["study phases must be an array"];
    const ids = new Set(), kinds = new Set();
    for (const phase of phases) {
        if (ids.has(phase.id))
            errors.push(`study phase id must be unique: ${phase.id}`);
        ids.add(phase.id);
        if (kinds.has(phase.kind))
            errors.push(`study phase kind may appear only once: ${phase.kind}`);
        kinds.add(phase.kind);
        if (phase.endsOn < phase.startsOn)
            errors.push(`study phase ${phase.label} ends before it starts`);
        if (phase.theoryRatioPercent + phase.questionRatioPercent + phase.reviewRatioPercent !== 100)
            errors.push(`study phase ${phase.label} allocation must total 100%`);
    }
    const ordered = [...phases].sort((a, b) => a.startsOn.localeCompare(b.startsOn) || a.id.localeCompare(b.id));
    for (let i = 1; i < ordered.length; i++)
        if (ordered[i].startsOn <= ordered[i - 1].endsOn)
            errors.push(`study phases cannot overlap: ${ordered[i - 1].label} / ${ordered[i].label}`);
    return errors;
}
function deriveActiveStudyPhase(plan, date = new Date()) {
    var _a, _b;
    if (plan.mode !== "EXAM_PLAN")
        return null;
    const day = dayFromDateLike(date);
    if (!day)
        return null;
    return (_b = (_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.phaseDefinitions.find(phase => phase.startsOn <= day && day <= phase.endsOn)) !== null && _b !== void 0 ? _b : null;
}
function cloneBbStudyPhaseSuggestions() { return clone(exports.BB_STUDY_PHASE_SUGGESTIONS); }
function derivePreEditalCockpit(plan, at = new Date()) {
    var _a, _b, _c;
    if (plan.mode !== "EXAM_PLAN" || !plan.configuration.publicExam)
        return null;
    const cfg = plan.configuration.publicExam, target = cfg.target;
    const now = at instanceof Date ? at : new Date(at);
    if (Number.isNaN(now.getTime()))
        throw new TypeError("Invalid cockpit timestamp");
    const ordered = [...cfg.noticeRevisions].sort((a, b) => a.acceptedAt.localeCompare(b.acceptedAt) || a.id.localeCompare(b.id));
    const latest = ordered.length ? ordered[ordered.length - 1] : null;
    const resolved = (0, promise_cleanup_1.resolvePublicExamDate)(plan);
    const resolvedDate = { date: resolved.date, source: resolved.source, isConfirmed: resolved.official };
    const referenceLabel = (_c = (_b = (_a = latest === null || latest === void 0 ? void 0 : latest.reference) !== null && _a !== void 0 ? _a : latest === null || latest === void 0 ? void 0 : latest.id) !== null && _b !== void 0 ? _b : target.editalRef) !== null && _c !== void 0 ? _c : null;
    const referenceAgeDays = (latest === null || latest === void 0 ? void 0 : latest.publishedAt) ? Math.max(0, Math.floor((Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()) - Date.parse(`${latest.publishedAt}T00:00:00Z`)) / DAY_MS)) : null;
    const staleReferenceDays = Math.max(1, Math.trunc(cfg.cockpitStaleReferenceDays || exports.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS));
    if (resolvedDate.isConfirmed)
        return { preEdital: false, officialCountdownDays: null, resolvedDate, latestRevision: latest, referenceLabel, referenceAgeDays, issues: [], staleReferenceDays };
    const issues = ["no-confirmed-exam-date"];
    if (!latest && !target.editalRef)
        issues.push("no-reference-notice");
    if ((latest && !latest.publishedAt) || (!latest && Boolean(target.editalRef)))
        issues.push("reference-publication-date-unknown");
    if (referenceAgeDays != null && referenceAgeDays > staleReferenceDays)
        issues.push("stale-reference-notice");
    return { preEdital: true, officialCountdownDays: null, resolvedDate, latestRevision: latest, referenceLabel, referenceAgeDays, issues, staleReferenceDays };
}
function managedReferenceTag(tag) { return tag.startsWith(ORIGIN_PREFIX) || tag.startsWith(VERIFIED_PREFIX) || tag.startsWith(SUPERSEDES_PREFIX); }
function valueFor(tags, prefix) { var _a, _b; return (_b = (_a = tags.find(tag => tag.startsWith(prefix))) === null || _a === void 0 ? void 0 : _a.slice(prefix.length)) !== null && _b !== void 0 ? _b : null; }
function decodeReferenceProvenance(materialOrTags) {
    const tags = Array.isArray(materialOrTags) ? materialOrTags : materialOrTags.tags;
    const originRaw = valueFor(tags, ORIGIN_PREFIX);
    const verifiedOn = dateOnly(valueFor(tags, VERIFIED_PREFIX));
    const supersedesMaterialId = cleanText(valueFor(tags, SUPERSEDES_PREFIX));
    return { origin: originRaw === "EDITAL_2022" ? originRaw : null, verifiedOn, supersedesMaterialId };
}
function encodeReferenceProvenance(tags, provenance) {
    const next = tags.filter(tag => !managedReferenceTag(tag));
    if (provenance.origin)
        next.push(`${ORIGIN_PREFIX}${provenance.origin}`);
    if (provenance.verifiedOn)
        next.push(`${VERIFIED_PREFIX}${provenance.verifiedOn}`);
    if (provenance.supersedesMaterialId)
        next.push(`${SUPERSEDES_PREFIX}${provenance.supersedesMaterialId}`);
    return [...new Set(next)];
}
function deriveMaterialReferenceFreshness(material, today = new Date()) {
    const provenance = decodeReferenceProvenance(material);
    if (!provenance.origin)
        return { status: "untracked", ageDays: null, provenance };
    if (!provenance.verifiedOn)
        return { status: "needs-verification", ageDays: null, provenance };
    const now = today instanceof Date ? today : new Date(today);
    if (Number.isNaN(now.getTime()))
        throw new TypeError("Invalid reference freshness date");
    const day = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
    const ageDays = Math.max(0, Math.floor((day - Date.parse(`${provenance.verifiedOn}T00:00:00Z`)) / DAY_MS));
    return { status: ageDays > exports.REFERENCE_POSSIBLY_STALE_AFTER_DAYS ? "possibly-stale" : "current", ageDays, provenance };
}
