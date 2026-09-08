"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.filterPracticeQuestions = exports.PUBLIC_EXAM_STARTER_TEMPLATES = exports.validateStudyPhaseDefinitions = exports.normalizeStudyPhaseDefinitions = exports.encodeReferenceProvenance = exports.derivePreEditalCockpit = exports.deriveMaterialReferenceFreshness = exports.deriveActiveStudyPhase = exports.decodeReferenceProvenance = exports.cloneBbStudyPhaseSuggestions = exports.REFERENCE_POSSIBLY_STALE_AFTER_DAYS = exports.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS = exports.BB_STUDY_PHASE_SUGGESTIONS = void 0;
exports.defaultPlanConfiguration = defaultPlanConfiguration;
exports.normalizePlanConfiguration = normalizePlanConfiguration;
exports.validatePlanConfiguration = validatePlanConfiguration;
exports.normalizeMockBlueprint = normalizeMockBlueprint;
exports.normalizeQuestionMetadata = normalizeQuestionMetadata;
exports.parseExamNoticeText = parseExamNoticeText;
exports.buildExamNoticePreview = buildExamNoticePreview;
exports.deriveFinalStretchGuidance = deriveFinalStretchGuidance;
exports.filterExamQuestions = filterExamQuestions;
exports.buildWeaknessDrill = buildWeaknessDrill;
exports.derivePublicExamNextActions = derivePublicExamNextActions;
exports.deriveEditalMatrix = deriveEditalMatrix;
exports.previewMockExam = previewMockExam;
exports.buildBbReferenceMockBlueprint = buildBbReferenceMockBlueprint;
exports.previewBbReferenceMockExam = previewBbReferenceMockExam;
exports.scoreMockExam = scoreMockExam;
exports.derivePublicExamDashboard = derivePublicExamDashboard;
exports.createProgrammingExercise = createProgrammingExercise;
exports.createLanguageExpression = createLanguageExpression;
exports.createReadingItem = createReadingItem;
exports.createGeneralSkillDrill = createGeneralSkillDrill;
exports.deriveModeAnalytics = deriveModeAnalytics;
exports.tokenizeCode = tokenizeCode;
exports.assessProgrammingAttempt = assessProgrammingAttempt;
exports.buildLanguageReviewPrompt = buildLanguageReviewPrompt;
const schedule_1 = require("./schedule");
const exam_foundation_1 = require("./exam_foundation");
const task_workflows_1 = require("./task_workflows");
const promise_cleanup_1 = require("./promise_cleanup");
var exam_foundation_2 = require("./exam_foundation");
Object.defineProperty(exports, "BB_STUDY_PHASE_SUGGESTIONS", { enumerable: true, get: function () { return exam_foundation_2.BB_STUDY_PHASE_SUGGESTIONS; } });
Object.defineProperty(exports, "PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS", { enumerable: true, get: function () { return exam_foundation_2.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS; } });
Object.defineProperty(exports, "REFERENCE_POSSIBLY_STALE_AFTER_DAYS", { enumerable: true, get: function () { return exam_foundation_2.REFERENCE_POSSIBLY_STALE_AFTER_DAYS; } });
Object.defineProperty(exports, "cloneBbStudyPhaseSuggestions", { enumerable: true, get: function () { return exam_foundation_2.cloneBbStudyPhaseSuggestions; } });
Object.defineProperty(exports, "decodeReferenceProvenance", { enumerable: true, get: function () { return exam_foundation_2.decodeReferenceProvenance; } });
Object.defineProperty(exports, "deriveActiveStudyPhase", { enumerable: true, get: function () { return exam_foundation_2.deriveActiveStudyPhase; } });
Object.defineProperty(exports, "deriveMaterialReferenceFreshness", { enumerable: true, get: function () { return exam_foundation_2.deriveMaterialReferenceFreshness; } });
Object.defineProperty(exports, "derivePreEditalCockpit", { enumerable: true, get: function () { return exam_foundation_2.derivePreEditalCockpit; } });
Object.defineProperty(exports, "encodeReferenceProvenance", { enumerable: true, get: function () { return exam_foundation_2.encodeReferenceProvenance; } });
Object.defineProperty(exports, "normalizeStudyPhaseDefinitions", { enumerable: true, get: function () { return exam_foundation_2.normalizeStudyPhaseDefinitions; } });
Object.defineProperty(exports, "validateStudyPhaseDefinitions", { enumerable: true, get: function () { return exam_foundation_2.validateStudyPhaseDefinitions; } });
const STUDY_MODES = ["EXAM_PLAN", "PROGRAMMING_COURSE", "LANGUAGE_DECK", "READING_STUDY", "GENERAL_SKILL"];
const LANGUAGE_DIRECTIONS = ["recognition", "production", "fill-blank", "natural-usage", "mistake-spotting", "sentence-creation"];
const EXAM_SCORING = ["no-penalty", "fixed-negative", "right-minus-wrong", "weighted"];
const clone = (value) => JSON.parse(JSON.stringify(value));
const text = (value) => { const v = String(value !== null && value !== void 0 ? value : "").trim(); return v || null; };
const n = (value) => { if (value == null || value === "")
    return null; const v = Number(value); return Number.isFinite(v) ? v : null; };
const bool = (value) => Boolean(value);
const arr = (value) => Array.isArray(value) ? value : [];
const id = (prefix) => { var _a, _b, _c; return `${prefix}-${(_c = (_b = (_a = globalThis.crypto) === null || _a === void 0 ? void 0 : _a.randomUUID) === null || _b === void 0 ? void 0 : _b.call(_a)) !== null && _c !== void 0 ? _c : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`}`; };
const iso = (value) => { const v = text(value); if (!v)
    return null; const ms = Date.parse(v); return Number.isFinite(ms) ? new Date(ms).toISOString() : null; };
const dateOnly = (value) => { const v = text(value); if (!v)
    return null; const m = v.match(/^\d{4}-\d{2}-\d{2}$/); return m ? v : null; };
const uniqueStrings = (value) => [...new Set(arr(value).map(x => String(x !== null && x !== void 0 ? x : "").trim()).filter(Boolean))];
function defaultPlanConfiguration(mode = "GENERAL_SKILL") {
    const base = { version: 1, schedule: (0, schedule_1.defaultScheduleConfiguration)(), practice: { savedSets: [], weeklyQuestionQuota: null } };
    if (mode === "EXAM_PLAN")
        base.publicExam = {
            target: { organization: null, role: null, board: null, editalRef: null, dateStatus: "unknown", examDate: null, openings: null, estimatedCutoff: null },
            stages: [], noticeRevisions: [], reviewIntervalsDays: [1, 7, 30], legislation: [], studyPhase: "pre-edital", phaseDefinitions: [], cockpitStaleReferenceDays: exam_foundation_1.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS, bbReferencePreset: null, mockBlueprint: { scoring: "no-penalty", negativePoints: 0, totalQuestions: 20, durationMinutes: 60, sections: [], examBoard: null, sourceFilter: null, requiredTags: [], includeDisputed: false, minimumOverallScorePercent: null }
        };
    if (mode === "PROGRAMMING_COURSE")
        base.programming = { language: "TypeScript", level: "beginner", runtime: "browser-only", exercises: [], projects: [] };
    if (mode === "LANGUAGE_DECK")
        base.language = { language: "English", nativeLanguage: null, expressions: [], reviewDirections: [...LANGUAGE_DIRECTIONS] };
    if (mode === "READING_STUDY")
        base.reading = { items: [] };
    if (mode === "GENERAL_SKILL")
        base.general = { drills: [] };
    return base;
}
function normalizeExamTarget(input) {
    const x = (input && typeof input === "object" ? input : {});
    const dateStatus = ["confirmed", "expected", "unknown"].includes(String(x.dateStatus)) ? String(x.dateStatus) : "unknown";
    const openings = n(x.openings);
    const estimatedCutoff = n(x.estimatedCutoff);
    return { organization: text(x.organization), role: text(x.role), board: text(x.board), editalRef: text(x.editalRef), dateStatus, examDate: dateOnly(x.examDate), openings: openings == null ? null : Math.max(0, Math.trunc(openings)), estimatedCutoff: estimatedCutoff == null ? null : Math.max(0, Math.min(100, estimatedCutoff)) };
}
function normalizePlanConfiguration(input, mode) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m;
    const raw = (input && typeof input === "object" && !Array.isArray(input) ? clone(input) : {});
    const out = { version: 1, schedule: (0, schedule_1.normalizeScheduleConfiguration)(raw.schedule), practice: { savedSets: arr((_a = raw.practice) === null || _a === void 0 ? void 0 : _a.savedSets).map((x, i) => { var _a, _b, _c; return ({ id: (_a = text(x === null || x === void 0 ? void 0 : x.id)) !== null && _a !== void 0 ? _a : `practice-set-${i + 1}`, name: (_b = text(x === null || x === void 0 ? void 0 : x.name)) !== null && _b !== void 0 ? _b : `Set ${i + 1}`, questionIds: uniqueStrings(x === null || x === void 0 ? void 0 : x.questionIds), filter: ((x === null || x === void 0 ? void 0 : x.filter) && typeof x.filter === "object" ? x.filter : {}), createdAt: (_c = iso(x === null || x === void 0 ? void 0 : x.createdAt)) !== null && _c !== void 0 ? _c : new Date(0).toISOString() }); }), weeklyQuestionQuota: n((_b = raw.practice) === null || _b === void 0 ? void 0 : _b.weeklyQuestionQuota) == null ? null : Math.max(1, Math.trunc(n((_c = raw.practice) === null || _c === void 0 ? void 0 : _c.weeklyQuestionQuota))) } };
    if (mode === "EXAM_PLAN") {
        const d = defaultPlanConfiguration(mode).publicExam;
        const p = (_d = raw.publicExam) !== null && _d !== void 0 ? _d : {};
        out.publicExam = {
            target: normalizeExamTarget(p.target),
            stages: arr(p.stages).map((s, i) => { var _a, _b; return ({ id: (_a = text(s === null || s === void 0 ? void 0 : s.id)) !== null && _a !== void 0 ? _a : `stage-${i + 1}`, name: (_b = text(s === null || s === void 0 ? void 0 : s.name)) !== null && _b !== void 0 ? _b : `Stage ${i + 1}`, date: dateOnly(s === null || s === void 0 ? void 0 : s.date), status: ["planned", "completed", "cancelled"].includes(String(s === null || s === void 0 ? void 0 : s.status)) ? s.status : "planned", type: ["objective", "essay", "other"].includes(String(s === null || s === void 0 ? void 0 : s.type)) ? s.type : "other", weight: n(s === null || s === void 0 ? void 0 : s.weight), minimumScorePercent: n(s === null || s === void 0 ? void 0 : s.minimumScorePercent) == null ? null : Math.max(0, Math.min(100, n(s === null || s === void 0 ? void 0 : s.minimumScorePercent))), referenceQuestionCount: n(s === null || s === void 0 ? void 0 : s.referenceQuestionCount) == null ? null : Math.max(0, Math.trunc(n(s === null || s === void 0 ? void 0 : s.referenceQuestionCount))) }); }),
            noticeRevisions: arr(p.noticeRevisions).map((r, i) => { var _a, _b, _c, _d, _e, _f, _g; return ({ id: (_a = text(r === null || r === void 0 ? void 0 : r.id)) !== null && _a !== void 0 ? _a : `notice-${i + 1}`, acceptedAt: (_b = iso(r === null || r === void 0 ? void 0 : r.acceptedAt)) !== null && _b !== void 0 ? _b : new Date(0).toISOString(), sourceHash: (_c = text(r === null || r === void 0 ? void 0 : r.sourceHash)) !== null && _c !== void 0 ? _c : `legacy-${i + 1}`, added: Math.max(0, Math.trunc((_d = n(r === null || r === void 0 ? void 0 : r.added)) !== null && _d !== void 0 ? _d : 0)), archived: Math.max(0, Math.trunc((_e = n(r === null || r === void 0 ? void 0 : r.archived)) !== null && _e !== void 0 ? _e : 0)), reactivated: Math.max(0, Math.trunc((_f = n(r === null || r === void 0 ? void 0 : r.reactivated)) !== null && _f !== void 0 ? _f : 0)), unchanged: Math.max(0, Math.trunc((_g = n(r === null || r === void 0 ? void 0 : r.unchanged)) !== null && _g !== void 0 ? _g : 0)), previousRevisionId: text(r === null || r === void 0 ? void 0 : r.previousRevisionId), syllabusFingerprint: text(r === null || r === void 0 ? void 0 : r.syllabusFingerprint), reference: text(r === null || r === void 0 ? void 0 : r.reference), publishedAt: dateOnly(r === null || r === void 0 ? void 0 : r.publishedAt) }); }),
            reviewIntervalsDays: arr(p.reviewIntervalsDays).map(Number).filter(v => Number.isInteger(v) && v > 0 && v <= 365).slice(0, 8),
            legislation: arr(p.legislation).map((d, i) => { var _a, _b; return ({ id: (_a = text(d === null || d === void 0 ? void 0 : d.id)) !== null && _a !== void 0 ? _a : `law-${i + 1}`, title: (_b = text(d === null || d === void 0 ? void 0 : d.title)) !== null && _b !== void 0 ? _b : `Legislation ${i + 1}`, reference: text(d === null || d === void 0 ? void 0 : d.reference), notes: text(d === null || d === void 0 ? void 0 : d.notes), sourceUpdatedAt: iso(d === null || d === void 0 ? void 0 : d.sourceUpdatedAt), verifiedAt: iso(d === null || d === void 0 ? void 0 : d.verifiedAt), provisions: arr(d === null || d === void 0 ? void 0 : d.provisions).map((q, j) => { var _a, _b, _c; return ({ id: (_a = text(q === null || q === void 0 ? void 0 : q.id)) !== null && _a !== void 0 ? _a : `provision-${i + 1}-${j + 1}`, label: (_b = text(q === null || q === void 0 ? void 0 : q.label)) !== null && _b !== void 0 ? _b : `Provision ${j + 1}`, text: (_c = text(q === null || q === void 0 ? void 0 : q.text)) !== null && _c !== void 0 ? _c : "", status: ["unseen", "studying", "reviewing", "known"].includes(String(q === null || q === void 0 ? void 0 : q.status)) ? q.status : "unseen" }); }), evidenceLinks: arr(d === null || d === void 0 ? void 0 : d.evidenceLinks).map((l, j) => { var _a, _b, _c; return ({ id: (_a = text(l === null || l === void 0 ? void 0 : l.id)) !== null && _a !== void 0 ? _a : `law-link-${i + 1}-${j + 1}`, provisionId: (_b = text(l === null || l === void 0 ? void 0 : l.provisionId)) !== null && _b !== void 0 ? _b : "", questionId: text(l === null || l === void 0 ? void 0 : l.questionId), errorId: text(l === null || l === void 0 ? void 0 : l.errorId), reviewId: text(l === null || l === void 0 ? void 0 : l.reviewId), note: text(l === null || l === void 0 ? void 0 : l.note), createdAt: (_c = iso(l === null || l === void 0 ? void 0 : l.createdAt)) !== null && _c !== void 0 ? _c : new Date(0).toISOString() }); }) }); }),
            studyPhase: ["pre-edital", "post-edital", "final-stretch"].includes(String(p.studyPhase)) ? p.studyPhase : (arr(p.noticeRevisions).length ? "post-edital" : "pre-edital"),
            phaseDefinitions: (0, exam_foundation_1.normalizeStudyPhaseDefinitions)(p.phaseDefinitions),
            cockpitStaleReferenceDays: Number.isInteger(Number(p.cockpitStaleReferenceDays)) && Number(p.cockpitStaleReferenceDays) > 0 ? Math.trunc(Number(p.cockpitStaleReferenceDays)) : exam_foundation_1.PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS,
            bbReferencePreset: ((_e = p.bbReferencePreset) === null || _e === void 0 ? void 0 : _e.id) === "BB_AGENTE_COMERCIAL_PRE_EDITAL" ? (0, task_workflows_1.createBbAgenteComercialReferencePreset)() : null,
            mockBlueprint: normalizeMockBlueprint((_f = p.mockBlueprint) !== null && _f !== void 0 ? _f : d.mockBlueprint),
        };
        if (!out.publicExam.reviewIntervalsDays.length)
            out.publicExam.reviewIntervalsDays = [1, 7, 30];
    }
    if (mode === "PROGRAMMING_COURSE") {
        const p = (_g = raw.programming) !== null && _g !== void 0 ? _g : {};
        out.programming = { language: (_h = text(p.language)) !== null && _h !== void 0 ? _h : "TypeScript", level: ["beginner", "intermediate", "advanced"].includes(String(p.level)) ? p.level : "beginner", runtime: "browser-only",
            exercises: arr(p.exercises).map((e, i) => normalizeProgrammingExercise(e, i)), projects: arr(p.projects).map((q, i) => { var _a, _b; return ({ id: (_a = text(q === null || q === void 0 ? void 0 : q.id)) !== null && _a !== void 0 ? _a : `project-${i + 1}`, title: (_b = text(q === null || q === void 0 ? void 0 : q.title)) !== null && _b !== void 0 ? _b : `Project ${i + 1}`, status: ["planned", "active", "completed"].includes(String(q === null || q === void 0 ? void 0 : q.status)) ? q.status : "planned", checkpoints: uniqueStrings(q === null || q === void 0 ? void 0 : q.checkpoints), completedCheckpoints: uniqueStrings(q === null || q === void 0 ? void 0 : q.completedCheckpoints).filter((x) => uniqueStrings(q === null || q === void 0 ? void 0 : q.checkpoints).includes(x)), deliverables: uniqueStrings(q === null || q === void 0 ? void 0 : q.deliverables) }); }) };
    }
    if (mode === "LANGUAGE_DECK") {
        const p = (_j = raw.language) !== null && _j !== void 0 ? _j : {};
        out.language = { language: (_k = text(p.language)) !== null && _k !== void 0 ? _k : "English", nativeLanguage: text(p.nativeLanguage), expressions: arr(p.expressions).map((e, i) => normalizeLanguageExpression(e, i)), reviewDirections: arr(p.reviewDirections).map(String).filter(x => LANGUAGE_DIRECTIONS.includes(x)) };
        if (!out.language.reviewDirections.length)
            out.language.reviewDirections = [...LANGUAGE_DIRECTIONS];
    }
    if (mode === "READING_STUDY") {
        const p = (_l = raw.reading) !== null && _l !== void 0 ? _l : {};
        out.reading = { items: arr(p.items).map((r, i) => normalizeReadingItem(r, i)) };
    }
    if (mode === "GENERAL_SKILL") {
        const p = (_m = raw.general) !== null && _m !== void 0 ? _m : {};
        out.general = { drills: arr(p.drills).map((d, i) => normalizeGeneralDrill(d, i)) };
    }
    return out;
}
function validatePlanConfiguration(config, mode) {
    const errors = [];
    if (!STUDY_MODES.includes(mode))
        errors.push(`unsupported study mode: ${mode}`);
    if (config.version !== 1)
        errors.push("plan configuration version must be 1");
    errors.push(...(0, schedule_1.validateScheduleConfiguration)(config.schedule));
    if (mode === "EXAM_PLAN") {
        const p = config.publicExam;
        if (!p)
            errors.push("public-exam configuration is required");
        else {
            const t = p.target;
            const identity = [t.organization, t.role, t.board].filter(Boolean).length;
            if (identity !== 0 && identity !== 3)
                errors.push("public-exam organization, role and board must be supplied together or all blank");
            if (t.estimatedCutoff != null && (t.estimatedCutoff < 0 || t.estimatedCutoff > 100))
                errors.push("estimated cutoff must be between 0 and 100");
            if (!p.reviewIntervalsDays.length || p.reviewIntervalsDays.length > 8 || p.reviewIntervalsDays.some(v => !Number.isInteger(v) || v <= 0 || v > 365))
                errors.push("review intervals must be 1 to 8 whole-day values from 1 to 365");
            if (p.reviewIntervalsDays.some((v, i) => i > 0 && v < p.reviewIntervalsDays[i - 1]))
                errors.push("review intervals must be non-decreasing");
            errors.push(...(0, exam_foundation_1.validateStudyPhaseDefinitions)(p.phaseDefinitions));
            if (!Number.isInteger(p.cockpitStaleReferenceDays) || p.cockpitStaleReferenceDays <= 0)
                errors.push("pre-edital reference staleness window must be a positive number of days");
        }
    }
    if (mode === "PROGRAMMING_COURSE" && !config.programming)
        errors.push("programming configuration is required");
    if (mode === "LANGUAGE_DECK" && !config.language)
        errors.push("language configuration is required");
    if (mode === "READING_STUDY" && !config.reading)
        errors.push("reading configuration is required");
    if (mode === "GENERAL_SKILL" && !config.general)
        errors.push("general-skill configuration is required");
    return errors;
}
exports.PUBLIC_EXAM_STARTER_TEMPLATES = Object.freeze({
    balanced: { id: "balanced", label: "Balanced", weeklyQuestionQuota: 100, reviewIntervalsDays: [1, 7, 30], mockQuestions: 40 },
    questions: { id: "questions", label: "Question intensive", weeklyQuestionQuota: 200, reviewIntervalsDays: [1, 5, 20], mockQuestions: 60 },
    revision: { id: "revision", label: "Revision intensive", weeklyQuestionQuota: 75, reviewIntervalsDays: [1, 3, 10, 30], mockQuestions: 30 },
});
function normalizeMockBlueprint(input) {
    var _a, _b, _c;
    const x = (input && typeof input === "object" ? input : {});
    const scoring = EXAM_SCORING.includes(x.scoring) ? x.scoring : "no-penalty";
    const minimum = n(x.minimumOverallScorePercent);
    return { scoring, negativePoints: Math.max(0, (_a = n(x.negativePoints)) !== null && _a !== void 0 ? _a : 0), totalQuestions: Math.max(1, Math.min(500, Math.trunc((_b = n(x.totalQuestions)) !== null && _b !== void 0 ? _b : 20))), durationMinutes: Math.max(5, Math.min(1440, Math.trunc((_c = n(x.durationMinutes)) !== null && _c !== void 0 ? _c : 60))), sections: arr(x.sections).map((s, i) => { var _a, _b; const weight = n(s === null || s === void 0 ? void 0 : s.weight); return { id: (_a = text(s === null || s === void 0 ? void 0 : s.id)) !== null && _a !== void 0 ? _a : `section-${i + 1}`, subjectId: text(s === null || s === void 0 ? void 0 : s.subjectId), count: Math.max(1, Math.trunc((_b = n(s === null || s === void 0 ? void 0 : s.count)) !== null && _b !== void 0 ? _b : 1)), weight: weight != null && weight > 0 ? weight : 1 }; }), examBoard: text(x.examBoard), sourceFilter: text(x.sourceFilter), requiredTags: uniqueStrings(x.requiredTags), includeDisputed: Boolean(x.includeDisputed), minimumOverallScorePercent: minimum == null ? null : Math.max(0, Math.min(100, minimum)) };
}
function normalizeProgrammingExercise(input, index = 0) { var _a, _b, _c, _d, _e; const x = (input && typeof input === "object" ? input : {}); const language = (_a = text(x.language)) !== null && _a !== void 0 ? _a : "TypeScript"; const files = arr(x.files).map((f, j) => { var _a, _b, _c; return ({ path: (_a = text(f === null || f === void 0 ? void 0 : f.path)) !== null && _a !== void 0 ? _a : `file-${j + 1}`, language: (_b = text(f === null || f === void 0 ? void 0 : f.language)) !== null && _b !== void 0 ? _b : language, content: String((_c = f === null || f === void 0 ? void 0 : f.content) !== null && _c !== void 0 ? _c : "") }); }); return { id: (_b = text(x.id)) !== null && _b !== void 0 ? _b : `exercise-${index + 1}`, title: (_c = text(x.title)) !== null && _c !== void 0 ? _c : `Exercise ${index + 1}`, language, prompt: (_d = text(x.prompt)) !== null && _d !== void 0 ? _d : "", starterCode: String((_e = x.starterCode) !== null && _e !== void 0 ? _e : ""), expectedOutput: text(x.expectedOutput), hints: uniqueStrings(x.hints), solutionNotes: text(x.solutionNotes), difficulty: ["easy", "medium", "hard"].includes(String(x.difficulty)) ? x.difficulty : "medium", status: ["todo", "attempted", "completed"].includes(String(x.status)) ? x.status : "todo", files }; }
function normalizeLanguageExpression(input, index = 0) { var _a, _b, _c; const x = (input && typeof input === "object" ? input : {}); return { id: (_a = text(x.id)) !== null && _a !== void 0 ? _a : `expression-${index + 1}`, expression: (_b = text(x.expression)) !== null && _b !== void 0 ? _b : `Expression ${index + 1}`, meaning: (_c = text(x.meaning)) !== null && _c !== void 0 ? _c : "", literalTranslation: text(x.literalTranslation), region: text(x.region), register: text(x.register), pronunciationNote: text(x.pronunciationNote), commonMistakes: uniqueStrings(x.commonMistakes), exampleSentence: text(x.exampleSentence) }; }
function normalizeReadingItem(input, index = 0) { var _a, _b, _c, _d, _e; const x = (input && typeof input === "object" ? input : {}); const total = Math.max(1, Math.trunc((_a = n(x.totalUnits)) !== null && _a !== void 0 ? _a : 1)); return { id: (_b = text(x.id)) !== null && _b !== void 0 ? _b : `reading-${index + 1}`, title: (_c = text(x.title)) !== null && _c !== void 0 ? _c : `Reading ${index + 1}`, totalUnits: total, currentUnit: Math.max(0, Math.min(total, Math.trunc((_d = n(x.currentUnit)) !== null && _d !== void 0 ? _d : 0))), unit: (_e = text(x.unit)) !== null && _e !== void 0 ? _e : "pages", status: ["planned", "reading", "completed"].includes(String(x.status)) ? x.status : "planned", notes: text(x.notes) }; }
function normalizeGeneralDrill(input, index = 0) { var _a, _b, _c, _d, _e; const x = (input && typeof input === "object" ? input : {}); return { id: (_a = text(x.id)) !== null && _a !== void 0 ? _a : `drill-${index + 1}`, title: (_b = text(x.title)) !== null && _b !== void 0 ? _b : `Drill ${index + 1}`, metric: (_c = text(x.metric)) !== null && _c !== void 0 ? _c : "repetitions", target: Math.max(0, (_d = n(x.target)) !== null && _d !== void 0 ? _d : 1), unit: (_e = text(x.unit)) !== null && _e !== void 0 ? _e : "count", status: ["active", "paused", "completed"].includes(String(x.status)) ? x.status : "active" }; }
function normalizeQuestionMetadata(input) { const points = n(input.points); const diff = text(input.difficulty); return { difficulty: ["easy", "medium", "hard"].includes(String(diff)) ? diff : null, favorite: bool(input.favorite), organization: text(input.organization), role: text(input.role), examStage: text(input.examStage), examBoard: text(input.examBoard), year: n(input.year) == null ? null : Math.trunc(n(input.year)), authorship: text(input.authorship), editalRevision: text(input.editalRevision), externalReference: text(input.externalReference), points: points != null && points > 0 ? points : null }; }
function hashText(source) { let h = 2166136261; for (let i = 0; i < source.length; i++) {
    h ^= source.charCodeAt(i);
    h = Math.imul(h, 16777619);
} return (h >>> 0).toString(16).padStart(8, "0"); }
function syllabusFingerprint(state, planId) { const subjects = state.subjects.filter(x => x.planId === planId).map(x => `${x.name}|${x.archived}`).sort(); const topics = state.topics.filter(x => x.planId === planId).map(x => { var _a; return `${x.subjectId}|${(_a = x.parentTopicId) !== null && _a !== void 0 ? _a : ""}|${x.name}|${x.status}`; }).sort(); return hashText(JSON.stringify([subjects, topics])); }
function parseExamNoticeText(source) {
    var _a;
    const subjects = [];
    const unrecognized = [];
    let current = null;
    const numbered = new Map();
    for (const raw of String(source !== null && source !== void 0 ? source : "").split(/\r?\n/)) {
        const line = raw.trim();
        if (!line)
            continue;
        const heading = line.match(/^(?:disciplina|mat[eé]ria|subject)\s*:\s*(.+)$/i);
        if (heading) {
            current = { name: heading[1].trim(), topics: [], topicPaths: [] };
            subjects.push(current);
            numbered.clear();
            continue;
        }
        if (!current) {
            current = { name: line.replace(/:$/, "").trim(), topics: [], topicPaths: [] };
            subjects.push(current);
            numbered.clear();
            continue;
        }
        const m = line.match(/^(?:(\d+(?:\.\d+)*)[.)]?\s+|[-*•]\s*)(.+)$/);
        const label = ((_a = m === null || m === void 0 ? void 0 : m[2]) !== null && _a !== void 0 ? _a : line).trim();
        if (!label) {
            unrecognized.push(line);
            continue;
        }
        let path = [label];
        if (m === null || m === void 0 ? void 0 : m[1]) {
            const num = m[1];
            const parent = num.includes(".") ? num.slice(0, num.lastIndexOf(".")) : "";
            path = parent && numbered.has(parent) ? [...numbered.get(parent), label] : [label];
            numbered.set(num, path);
        }
        current.topics.push(label);
        current.topicPaths.push(path);
    }
    const seen = new Set(), duplicates = [];
    for (const s of subjects) {
        const key = s.name.toLowerCase();
        if (seen.has(key))
            duplicates.push(s.name);
        seen.add(key);
        const paths = new Set();
        const keep = [];
        s.topicPaths.forEach((p, i) => { const k = p.map(x => x.toLowerCase()).join(">"); if (paths.has(k))
            duplicates.push(`${s.name} / ${p.join(" > ")}`);
        else {
            paths.add(k);
            keep.push(i);
        } });
        s.topicPaths = keep.map(i => s.topicPaths[i]);
        s.topics = keep.map(i => s.topics[i]);
    }
    return { subjects: subjects.filter(s => s.name), unrecognized, duplicates };
}
function buildExamNoticePreview(state, source) {
    var _a, _b, _c;
    const parsed = parseExamNoticeText(source);
    const planId = state.activePlanId;
    if (!planId)
        throw new TypeError("No active plan");
    const plan = state.plans.find(p => p.id === planId);
    if ((plan === null || plan === void 0 ? void 0 : plan.mode) !== "EXAM_PLAN")
        throw new TypeError("Edital import requires an EXAM_PLAN");
    const desiredSubjects = new Set(parsed.subjects.map(s => s.name.toLowerCase()));
    const desiredTopics = new Set(parsed.subjects.flatMap(s => s.topicPaths.map(p => `${s.name.toLowerCase()}|${p.map(x => x.toLowerCase()).join(">")}`)));
    const add = [], archive = [], reactivate = [], unchanged = [];
    for (const subj of parsed.subjects) {
        const existing = state.subjects.find(x => x.planId === planId && x.name.toLowerCase() === subj.name.toLowerCase());
        if (!existing)
            add.push(`subject:${subj.name}`);
        else if (existing.archived)
            reactivate.push(`subject:${subj.name}`);
        else
            unchanged.push(`subject:${subj.name}`);
        for (const path of subj.topicPaths) {
            const leaf = path[path.length - 1];
            const topic = existing ? state.topics.find(x => x.subjectId === existing.id && x.name.toLowerCase() === leaf.toLowerCase()) : null;
            if (!topic)
                add.push(`topic:${subj.name}>${path.join(">")}`);
            else if (topic.status === "archived")
                reactivate.push(`topic:${subj.name}>${path.join(">")}`);
            else
                unchanged.push(`topic:${subj.name}>${path.join(">")}`);
        }
    }
    for (const subj of state.subjects.filter(x => x.planId === planId && !x.archived)) {
        if (!desiredSubjects.has(subj.name.toLowerCase()))
            archive.push(`subject:${subj.name}`);
        for (const topic of state.topics.filter(x => x.subjectId === subj.id && x.status !== "archived")) {
            const key = `${subj.name.toLowerCase()}|${topic.name.toLowerCase()}`;
            if (![...desiredTopics].some(x => x.endsWith(`>${topic.name.toLowerCase()}`) || x === `${subj.name.toLowerCase()}|${topic.name.toLowerCase()}`))
                archive.push(`topic:${subj.name}>${topic.name}`);
        }
    }
    const revisions = (_b = (_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.noticeRevisions) !== null && _b !== void 0 ? _b : [];
    const latest = revisions.length ? revisions[revisions.length - 1] : null;
    return { baseRevision: state.revision, generatedAt: new Date().toISOString(), sourceHash: hashText(source), syllabusFingerprint: syllabusFingerprint(state, planId), previousRevisionId: (_c = latest === null || latest === void 0 ? void 0 : latest.id) !== null && _c !== void 0 ? _c : null, subjects: parsed.subjects, duplicates: parsed.duplicates, unrecognized: parsed.unrecognized, destructiveChanges: archive.length, diff: { add, archive, reactivate, unchanged } };
}
function deriveFinalStretchGuidance(plan, at = new Date()) {
    const d = (0, promise_cleanup_1.daysUntilOfficialExam)(plan, at);
    if (d == null)
        return { band: "unknown", daysRemaining: null, allocation: { newContent: 0, reviews: 0, errors: 0, questions: 0, mocks: 0, legislation: 0 } };
    let band = "over-30";
    if (d <= 1)
        band = "eve";
    else if (d <= 2)
        band = "2-day";
    else if (d <= 7)
        band = "7-day";
    else if (d <= 15)
        band = "15-day";
    else if (d <= 30)
        band = "30-day";
    const table = { "over-30": [35, 20, 10, 20, 5, 10], "30-day": [25, 25, 10, 20, 10, 10], "15-day": [15, 30, 15, 20, 10, 10], "7-day": [5, 30, 20, 20, 15, 10], "2-day": [0, 30, 20, 20, 20, 10], eve: [0, 35, 15, 15, 25, 10] };
    const a = table[band];
    return { band, daysRemaining: d, allocation: { newContent: a[0], reviews: a[1], errors: a[2], questions: a[3], mocks: a[4], legislation: a[5] } };
}
function questionEvidenceStatus(state, questionId) { const attempts = state.evidence.filter(e => e.questionId === questionId && e.kind === "question-attempt" && typeof e.value === "object" && e.value).sort((a, b) => b.observedAt.localeCompare(a.observedAt)); if (!attempts.length)
    return "unseen"; return attempts[0].value.correct === true ? "correct" : "wrong"; }
function filterExamQuestions(state, filter = {}) { return state.questions.filter(q => q.planId === state.activePlanId).filter(q => { var _a; return filter.board ? String((_a = q.examBoard) !== null && _a !== void 0 ? _a : "").toLowerCase() === filter.board.toLowerCase() : true; }).filter(q => { var _a; return filter.organization ? String((_a = q.organization) !== null && _a !== void 0 ? _a : "").toLowerCase() === filter.organization.toLowerCase() : true; }).filter(q => { var _a; return filter.role ? String((_a = q.role) !== null && _a !== void 0 ? _a : "").toLowerCase() === filter.role.toLowerCase() : true; }).filter(q => { var _a; return filter.stage ? String((_a = q.examStage) !== null && _a !== void 0 ? _a : "").toLowerCase() === filter.stage.toLowerCase() : true; }).filter(q => filter.subjectId ? q.subjectId === filter.subjectId : true).filter(q => filter.topicId ? q.topicId === filter.topicId : true).filter(q => { var _a; return filter.yearFrom != null ? ((_a = q.year) !== null && _a !== void 0 ? _a : -Infinity) >= filter.yearFrom : true; }).filter(q => { var _a; return filter.yearTo != null ? ((_a = q.year) !== null && _a !== void 0 ? _a : Infinity) <= filter.yearTo : true; }).filter(q => { var _a; return filter.authorship ? String((_a = q.authorship) !== null && _a !== void 0 ? _a : "").toLowerCase() === filter.authorship.toLowerCase() : true; }).filter(q => filter.difficulty ? q.difficulty === filter.difficulty : true).filter(q => filter.favorite != null ? q.favorite === filter.favorite : true).filter(q => filter.disputed != null ? q.disputed === filter.disputed : true).filter(q => filter.evidence ? questionEvidenceStatus(state, q.id) === filter.evidence : true); }
exports.filterPracticeQuestions = filterExamQuestions;
function buildWeaknessDrill(state, { limit = 10, minQuestions = 2 } = {}) { const planId = state.activePlanId; if (!planId)
    return { baseRevision: state.revision, generatedAt: new Date().toISOString(), ready: false, questionIds: [], reasons: ["No active plan"], signals: [] }; const scores = new Map(); const add = (subjectId, topicId, score, reason) => { var _a; const k = `${subjectId !== null && subjectId !== void 0 ? subjectId : ""}|${topicId !== null && topicId !== void 0 ? topicId : ""}`; const x = (_a = scores.get(k)) !== null && _a !== void 0 ? _a : { subjectId, topicId, score: 0, reasons: new Set() }; x.score += score; x.reasons.add(reason); scores.set(k, x); }; for (const err of state.errorEntries.filter(e => e.planId === planId && e.status === "open")) {
    add(err.subjectId, err.topicId, 2 + Math.min(5, err.recurrenceCount), `open ${err.category} error ×${err.recurrenceCount}`);
} for (const r of state.reviews.filter(r => r.planId === planId && r.status === "due" && Date.parse(r.dueAt) <= Date.now()))
    add(r.subjectId, r.topicId, 2, "overdue review"); for (const ev of state.evidence.filter(e => { var _a; return e.planId === planId && e.kind === "question-attempt" && ((_a = e.value) === null || _a === void 0 ? void 0 : _a.correct) === false; }))
    add(ev.subjectId, ev.topicId, 1, "wrong question attempt"); const signals = [...scores.values()].sort((a, b) => { var _a, _b; return b.score - a.score || String((_a = a.topicId) !== null && _a !== void 0 ? _a : a.subjectId).localeCompare(String((_b = b.topicId) !== null && _b !== void 0 ? _b : b.subjectId)); }).map(x => ({ ...x, reasons: [...x.reasons] })); if (!signals.length)
    return { baseRevision: state.revision, generatedAt: new Date().toISOString(), ready: false, questionIds: [], reasons: ["No persisted weakness signal exists yet."], signals: [] }; const candidates = state.questions.filter(q => q.planId === planId && !q.disputed).map(q => { let score = 0; for (const s of signals) {
    if (s.topicId && q.topicId === s.topicId)
        score = Math.max(score, s.score + 4);
    else if (s.subjectId && q.subjectId === s.subjectId)
        score = Math.max(score, s.score);
} return { q, score }; }).filter(x => x.score > 0).sort((a, b) => b.score - a.score || a.q.id.localeCompare(b.q.id)); const ids = candidates.slice(0, Math.max(1, limit)).map(x => x.q.id); return { baseRevision: state.revision, generatedAt: new Date().toISOString(), ready: ids.length >= minQuestions, questionIds: ids, reasons: ids.length >= minQuestions ? [`${ids.length} questions matched persisted weakness signals.`] : [`Only ${ids.length} matching question(s); at least ${minQuestions} required.`], signals }; }
function derivePublicExamNextActions(state, limit = 5) { const planId = state.activePlanId; if (!planId)
    return []; const topics = state.topics.filter(t => t.planId === planId && t.status !== "archived"); const attempts = state.evidence.filter(e => e.planId === planId && e.kind === "question-attempt"); return topics.map(topic => { var _a; const subject = state.subjects.find(s => s.id === topic.subjectId); const ta = attempts.filter(e => { var _a; return e.topicId === topic.id && ((_a = e.value) === null || _a === void 0 ? void 0 : _a.gradable) === true; }); const accuracy = ta.length ? ta.filter(e => e.value.correct === true).length / ta.length : null; const errors = state.errorEntries.filter(e => e.topicId === topic.id && e.status === "open").reduce((n, e) => n + e.recurrenceCount, 0); const reviews = state.reviews.filter(r => r.topicId === topic.id && r.status === "due").length; let score = ((_a = subject === null || subject === void 0 ? void 0 : subject.weight) !== null && _a !== void 0 ? _a : 1) * 10 + (topic.priority === "high" ? 20 : topic.priority === "low" ? 0 : 10) + errors * 5 + reviews * 4 + (accuracy == null ? 5 : (1 - accuracy) * 25); const reasonCodes = []; if (topic.priority === "high")
    reasonCodes.push("high-topic-priority"); if (errors)
    reasonCodes.push("open-error-backlog"); if (reviews)
    reasonCodes.push("due-review-backlog"); if (accuracy != null && accuracy < 0.7)
    reasonCodes.push("low-observed-accuracy"); if (accuracy == null)
    reasonCodes.push("limited-evidence"); return { subjectId: topic.subjectId, topicId: topic.id, score: roundScore(score), reasonCodes, explanation: reasonCodes.length ? reasonCodes.join(", ") : "baseline cycle priority" }; }).sort((a, b) => b.score - a.score || String(a.topicId).localeCompare(String(b.topicId))).slice(0, limit); }
function deriveEditalMatrix(state) { const planId = state.activePlanId; if (!planId)
    return []; const activeSubjects = state.subjects.filter(s => s.planId === planId && !s.archived); const weights = activeSubjects.map(s => { var _a; return (_a = s.weight) !== null && _a !== void 0 ? _a : 1; }); const maxWeight = Math.max(1, ...weights); return activeSubjects.map(subject => { var _a; const topics = state.topics.filter(t => t.subjectId === subject.id && t.status !== "archived"); const masteryValues = topics.map(t => t.status === "mastered" ? 1 : t.status === "reviewing" ? .75 : t.status === "learning" ? .4 : 0); const mastery = topics.length ? masteryValues.reduce((a, b) => a + b, 0) / topics.length : null; const incidence = subject.examIncidence == null ? null : subject.examIncidence / 100; const backlog = state.errorEntries.filter(e => e.subjectId === subject.id && e.status === "open").reduce((n, e) => n + e.recurrenceCount, 0) + state.reviews.filter(r => r.subjectId === subject.id && r.status === "due").length; const relativeWeight = ((_a = subject.weight) !== null && _a !== void 0 ? _a : 1) / maxWeight; const priority = roundScore((.35 * relativeWeight + .20 * (incidence !== null && incidence !== void 0 ? incidence : .5) + .30 * (1 - (mastery !== null && mastery !== void 0 ? mastery : 0)) + .15 * Math.min(1, backlog / 10)) * 100); return { subjectId: subject.id, subjectName: subject.name, relativeWeight, incidence, mastery, backlog, priority }; }).sort((a, b) => { var _a, _b; return ((_a = b.priority) !== null && _a !== void 0 ? _a : 0) - ((_b = a.priority) !== null && _b !== void 0 ? _b : 0) || a.subjectName.localeCompare(b.subjectName); }); }
function normalizedMatch(value) { return String(value !== null && value !== void 0 ? value : "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim().toLowerCase(); }
function mockQuestionMatches(q, blueprint) {
    if (!q.answer)
        return false;
    if (!blueprint.includeDisputed && q.disputed)
        return false;
    if (blueprint.examBoard && normalizedMatch(q.examBoard) !== normalizedMatch(blueprint.examBoard))
        return false;
    if (blueprint.sourceFilter && !normalizedMatch(q.source).includes(normalizedMatch(blueprint.sourceFilter)))
        return false;
    if (blueprint.requiredTags.length) {
        const tags = new Set(q.tags.map(normalizedMatch));
        if (!blueprint.requiredTags.every(tag => tags.has(normalizedMatch(tag))))
            return false;
    }
    return true;
}
function previewMockExam(state, blueprintInput = {}) { var _a, _b; const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan || plan.mode !== "EXAM_PLAN")
    throw new TypeError("Mock preview requires an EXAM_PLAN"); const blueprint = normalizeMockBlueprint({ ...(_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.mockBlueprint, ...blueprintInput }); const bank = state.questions.filter(q => q.planId === plan.id && mockQuestionMatches(q, blueprint)); const selected = []; const deficits = []; if (blueprint.sections.length) {
    for (const s of blueprint.sections) {
        const candidates = bank.filter(q => !selected.includes(q.id) && (!s.subjectId || q.subjectId === s.subjectId)).sort((a, b) => { var _a, _b; return ((_a = b.points) !== null && _a !== void 0 ? _a : 1) - ((_b = a.points) !== null && _b !== void 0 ? _b : 1) || a.id.localeCompare(b.id); });
        if (candidates.length < s.count)
            deficits.push(`${(_b = s.subjectId) !== null && _b !== void 0 ? _b : "all"}: need ${s.count}, have ${candidates.length}`);
        selected.push(...candidates.slice(0, s.count).map(q => q.id));
    }
}
else {
    selected.push(...bank.sort((a, b) => { var _a, _b; return ((_a = b.points) !== null && _a !== void 0 ? _a : 1) - ((_b = a.points) !== null && _b !== void 0 ? _b : 1) || a.id.localeCompare(b.id); }).slice(0, blueprint.totalQuestions).map(q => q.id));
    if (selected.length < blueprint.totalQuestions)
        deficits.push(`need ${blueprint.totalQuestions}, have ${selected.length}`);
} return { baseRevision: state.revision, generatedAt: new Date().toISOString(), blueprint, questionIds: selected, deficits, ready: deficits.length === 0 }; }
function allocateBbCounts(weights) { const raw = weights.map(weight => weight / weights.reduce((a, b) => a + b, 0) * task_workflows_1.BB_OBJECTIVE_QUESTION_COUNT), counts = raw.map(Math.floor); let remaining = task_workflows_1.BB_OBJECTIVE_QUESTION_COUNT - counts.reduce((a, b) => a + b, 0); raw.map((value, index) => ({ index, fraction: value - Math.floor(value) })).sort((a, b) => b.fraction - a.fraction || a.index - b.index).forEach(item => { if (remaining > 0) {
    counts[item.index]++;
    remaining--;
} }); return counts; }
function buildBbReferenceMockBlueprint(state) { var _a, _b; const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan || plan.mode !== "EXAM_PLAN" || ((_b = (_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.bbReferencePreset) === null || _b === void 0 ? void 0 : _b.id) !== task_workflows_1.BB_AGENTE_COMERCIAL_PRE_EDITAL)
    return null; const aliases = { "Português": ["portugues", "lingua portuguesa", "portuguese"], "Inglês": ["ingles", "lingua inglesa", "english"], "Matemática": ["matematica", "mathematics"], "Atualidades do Mercado Financeiro": ["atualidades do mercado financeiro", "financial market current affairs"], "Matemática Financeira": ["matematica financeira", "financial mathematics"], "Conhecimentos Bancários": ["conhecimentos bancarios", "banking knowledge"], "Tecnologia da Informação": ["tecnologia da informacao", "conhecimentos de informatica", "information technology"], "Vendas e Negociação": ["vendas e negociacao", "sales and negotiation"] }; const subjects = task_workflows_1.BB_SUBJECT_WEIGHTS.map(([name, weight]) => state.subjects.find(subject => { var _a; return subject.planId === plan.id && !subject.archived && aliases[name].includes(normalizedMatch(subject.name)) && Math.abs(Number((_a = subject.weight) !== null && _a !== void 0 ? _a : 0) - weight) < 0.0001; })); if (subjects.some(subject => !subject))
    return null; const counts = allocateBbCounts(task_workflows_1.BB_SUBJECT_WEIGHTS.map(([, weight]) => weight)); return normalizeMockBlueprint({ scoring: "no-penalty", negativePoints: 0, totalQuestions: task_workflows_1.BB_OBJECTIVE_QUESTION_COUNT, durationMinutes: 300, sections: subjects.map((subject, index) => ({ id: `bb-${index + 1}`, subjectId: subject.id, count: counts[index], weight: task_workflows_1.BB_SUBJECT_WEIGHTS[index][1] })), examBoard: "Cesgranrio", sourceFilter: null, requiredTags: [], includeDisputed: false, minimumOverallScorePercent: null }); }
function previewBbReferenceMockExam(state) { const blueprint = buildBbReferenceMockBlueprint(state); if (!blueprint)
    return { preview: null, essayMinimumScorePercent: task_workflows_1.BB_ESSAY_MINIMUM_SCORE_PERCENT, allocationPolicy: "derived-from-reference-point-weights", missingQuestionCount: task_workflows_1.BB_OBJECTIVE_QUESTION_COUNT }; const allowed = new Set(["cesgranrio", "fundacao cesgranrio"]); const candidateState = { ...state, questions: state.questions.map(q => allowed.has(normalizedMatch(q.examBoard)) ? { ...q, examBoard: "Cesgranrio" } : q) }; const preview = previewMockExam(candidateState, blueprint); return { preview, essayMinimumScorePercent: task_workflows_1.BB_ESSAY_MINIMUM_SCORE_PERCENT, allocationPolicy: "derived-from-reference-point-weights", missingQuestionCount: preview.deficits.reduce((sum, item) => { const m = item.match(/need (\d+), have (\d+)/); return sum + (m ? Math.max(0, Number(m[1]) - Number(m[2])) : 0); }, 0) }; }
const roundScore = (value) => Math.round((value + Number.EPSILON) * 100) / 100;
const clampPercent = (value) => Math.max(0, Math.min(100, roundScore(value)));
function scoreMockExam(preview, answers, state) {
    const byId = new Map(state.questions.map(q => [q.id, q]));
    const groups = [];
    if (preview.blueprint.sections.length) {
        let offset = 0;
        for (const section of preview.blueprint.sections) {
            groups.push({ id: section.id, subjectId: section.subjectId, weight: section.weight, questionIds: preview.questionIds.slice(offset, offset + section.count) });
            offset += section.count;
        }
    }
    else
        groups.push({ id: "general", subjectId: null, weight: null, questionIds: [...preview.questionIds] });
    let correct = 0, wrong = 0, unanswered = 0, rawScore = 0, maximumScore = 0;
    const sections = [];
    const questions = [];
    for (const group of groups) {
        const selected = group.questionIds.map(id => byId.get(id)).filter(Boolean);
        const bases = selected.map(q => q.points != null && q.points > 0 ? q.points : 1);
        const baseTotal = bases.reduce((sum, value) => sum + value, 0) || 1;
        const sectionMaximum = group.weight == null ? baseTotal : group.weight;
        let sectionEarned = 0, sectionCorrect = 0, sectionWrong = 0, sectionUnanswered = 0;
        selected.forEach((q, index) => {
            var _a;
            const maximum = group.weight == null ? bases[index] : sectionMaximum * (bases[index] / baseTotal);
            const submitted = String((_a = answers[q.id]) !== null && _a !== void 0 ? _a : "").trim();
            let outcome = "unanswered";
            let earned = 0;
            if (!q.answer || !submitted) {
                unanswered++;
                sectionUnanswered++;
            }
            else if (submitted.toLowerCase() === q.answer.trim().toLowerCase()) {
                outcome = "correct";
                correct++;
                sectionCorrect++;
                earned = maximum;
            }
            else {
                outcome = "wrong";
                wrong++;
                sectionWrong++;
                if (preview.blueprint.scoring === "right-minus-wrong")
                    earned = -maximum;
                else if (preview.blueprint.scoring === "fixed-negative")
                    earned = -(preview.blueprint.negativePoints * maximum);
            }
            sectionEarned += earned;
            rawScore += earned;
            maximumScore += maximum;
            questions.push({ questionId: q.id, sectionId: group.id, subjectId: q.subjectId, outcome, basePoints: roundScore(bases[index]), maximumPoints: roundScore(maximum), earnedPoints: roundScore(earned) });
        });
        sections.push({ sectionId: group.id, subjectId: group.subjectId, correct: sectionCorrect, wrong: sectionWrong, unanswered: sectionUnanswered, earnedPoints: roundScore(sectionEarned), maximumPoints: roundScore(sectionMaximum), percent: sectionMaximum > 0 ? clampPercent((Math.max(0, sectionEarned) / sectionMaximum) * 100) : 0 });
    }
    const percent = maximumScore > 0 ? clampPercent((Math.max(0, rawScore) / maximumScore) * 100) : null;
    const minimumOverallScorePercent = preview.blueprint.minimumOverallScorePercent;
    const passed = minimumOverallScorePercent == null || percent == null ? null : percent >= minimumOverallScorePercent;
    return { scoringPolicy: preview.blueprint.scoring, correct, wrong, unanswered, rawScore: roundScore(rawScore), maximumScore: roundScore(maximumScore), percent, passed, minimumOverallScorePercent, rounding: "nearest-0.01", sections, questions };
}
function derivePublicExamDashboard(state) { var _a, _b, _c, _d, _e, _f, _g; const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan || plan.mode !== "EXAM_PLAN")
    return null; const activeTopics = state.topics.filter(t => t.planId === plan.id && t.status !== "archived"); const progressed = activeTopics.filter(t => t.status !== "not-started"); const attempts = state.evidence.filter(e => e.planId === plan.id && e.kind === "question-attempt" && typeof e.value === "object" && e.value && "gradable" in e.value && e.value.gradable === true); const correct = attempts.filter(e => e.value.correct === true).length; const due = state.reviews.filter(r => r.planId === plan.id && r.status === "due").length; const mocks = state.evidence.filter(e => e.planId === plan.id && e.kind === "mock-exam-result").sort((a, b) => b.observedAt.localeCompare(a.observedAt)); const latest = (_a = mocks[0]) === null || _a === void 0 ? void 0 : _a.value; const cutoff = (_c = (_b = plan.configuration.publicExam) === null || _b === void 0 ? void 0 : _b.target.estimatedCutoff) !== null && _c !== void 0 ? _c : null; const window = (days) => { const since = Date.now() - days * 86400000; const xs = attempts.filter(e => Date.parse(e.observedAt) >= since); return xs.length ? xs.filter(e => e.value.correct === true).length / xs.length : null; }; const matrix = deriveEditalMatrix(state); const known = matrix.filter(r => r.mastery != null); const weightedMastery = known.length ? known.reduce((n, r) => { var _a, _b; return n + ((_a = r.mastery) !== null && _a !== void 0 ? _a : 0) * ((_b = r.relativeWeight) !== null && _b !== void 0 ? _b : 0); }, 0) / known.reduce((n, r) => { var _a; return n + ((_a = r.relativeWeight) !== null && _a !== void 0 ? _a : 0); }, 0) : null; return { coverage: activeTopics.length ? progressed.length / activeTopics.length : null, accuracy: attempts.length ? correct / attempts.length : null, accuracy7: window(7), accuracy30: window(30), accuracy90: window(90), weightedMastery, reviewBacklog: due, cutoffGap: cutoff != null && typeof (latest === null || latest === void 0 ? void 0 : latest.percent) === "number" ? roundScore(latest.percent - cutoff) : null, noticeRevisionCount: (_e = (_d = plan.configuration.publicExam) === null || _d === void 0 ? void 0 : _d.noticeRevisions.length) !== null && _e !== void 0 ? _e : 0, legislationCount: (_g = (_f = plan.configuration.publicExam) === null || _f === void 0 ? void 0 : _f.legislation.length) !== null && _g !== void 0 ? _g : 0, matrix }; }
function createProgrammingExercise(input) { var _a; return normalizeProgrammingExercise({ ...input, id: (_a = input.id) !== null && _a !== void 0 ? _a : id("exercise") }); }
function createLanguageExpression(input) { var _a; return normalizeLanguageExpression({ ...input, id: (_a = input.id) !== null && _a !== void 0 ? _a : id("expression") }); }
function createReadingItem(input) { var _a; return normalizeReadingItem({ ...input, id: (_a = input.id) !== null && _a !== void 0 ? _a : id("reading") }); }
function createGeneralSkillDrill(input) { var _a; return normalizeGeneralDrill({ ...input, id: (_a = input.id) !== null && _a !== void 0 ? _a : id("drill") }); }
function deriveModeAnalytics(state) { var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p; const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan)
    return { mode: null, metrics: [] }; const evidence = state.evidence.filter(e => e.planId === plan.id); if (plan.mode === "EXAM_PLAN") {
    const d = derivePublicExamDashboard(state);
    return { mode: plan.mode, metrics: [{ label: "Edital coverage", value: (_a = d === null || d === void 0 ? void 0 : d.coverage) !== null && _a !== void 0 ? _a : null }, { label: "Question accuracy", value: (_b = d === null || d === void 0 ? void 0 : d.accuracy) !== null && _b !== void 0 ? _b : null }, { label: "Review backlog", value: (_c = d === null || d === void 0 ? void 0 : d.reviewBacklog) !== null && _c !== void 0 ? _c : 0 }, { label: "Cutoff gap", value: (_d = d === null || d === void 0 ? void 0 : d.cutoffGap) !== null && _d !== void 0 ? _d : null }] };
} if (plan.mode === "PROGRAMMING_COURSE") {
    const ex = (_f = (_e = plan.configuration.programming) === null || _e === void 0 ? void 0 : _e.exercises) !== null && _f !== void 0 ? _f : [];
    const done = ex.filter(x => x.status === "completed").length;
    const attempts = evidence.filter(e => e.kind === "programming-exercise");
    return { mode: plan.mode, metrics: [{ label: "Exercises completed", value: ex.length ? done / ex.length : null }, { label: "Exercise attempts", value: attempts.length }, { label: "Projects", value: (_h = (_g = plan.configuration.programming) === null || _g === void 0 ? void 0 : _g.projects.length) !== null && _h !== void 0 ? _h : 0 }] };
} if (plan.mode === "LANGUAGE_DECK") {
    const expressions = (_k = (_j = plan.configuration.language) === null || _j === void 0 ? void 0 : _j.expressions) !== null && _k !== void 0 ? _k : [];
    const reviews = evidence.filter(e => e.kind === "language-review");
    const good = reviews.filter(e => { var _a; return Number((_a = e.value) === null || _a === void 0 ? void 0 : _a.rating) >= 3; }).length;
    return { mode: plan.mode, metrics: [{ label: "Expressions", value: expressions.length }, { label: "Usage accuracy", value: reviews.length ? good / reviews.length : null }, { label: "Review attempts", value: reviews.length }] };
} if (plan.mode === "READING_STUDY") {
    const items = (_m = (_l = plan.configuration.reading) === null || _l === void 0 ? void 0 : _l.items) !== null && _m !== void 0 ? _m : [];
    const total = items.reduce((s, x) => s + x.totalUnits, 0), done = items.reduce((s, x) => s + x.currentUnit, 0);
    return { mode: plan.mode, metrics: [{ label: "Reading progress", value: total ? done / total : null }, { label: "Reading items", value: items.length }] };
} const drills = (_p = (_o = plan.configuration.general) === null || _o === void 0 ? void 0 : _o.drills) !== null && _p !== void 0 ? _p : []; return { mode: plan.mode, metrics: [{ label: "Active drills", value: drills.filter(x => x.status === "active").length }, { label: "Drill evidence", value: evidence.filter(e => e.kind === "skill-drill").length }] }; }
const KEYWORDS = { javascript: new Set(["const", "let", "var", "function", "return", "if", "else", "for", "while", "class", "new", "async", "await", "import", "export", "true", "false", "null", "undefined"]), typescript: new Set(["const", "let", "var", "function", "return", "if", "else", "for", "while", "class", "new", "async", "await", "import", "export", "interface", "type", "extends", "implements", "string", "number", "boolean", "unknown", "never", "true", "false", "null", "undefined"]), python: new Set(["def", "return", "if", "elif", "else", "for", "while", "class", "import", "from", "as", "async", "await", "True", "False", "None", "with", "try", "except"]), java: new Set(["class", "public", "private", "protected", "static", "final", "void", "int", "long", "double", "boolean", "new", "return", "if", "else", "for", "while", "try", "catch"]), kotlin: new Set(["fun", "val", "var", "class", "object", "data", "when", "if", "else", "for", "while", "return", "suspend", "null", "true", "false"]), go: new Set(["package", "import", "func", "var", "const", "type", "struct", "interface", "return", "if", "else", "for", "range", "go", "defer"]), rust: new Set(["fn", "let", "mut", "struct", "enum", "impl", "trait", "pub", "use", "mod", "match", "if", "else", "for", "while", "return", "async", "await"]) };
function tokenizeCode(source, language) { var _a, _b; const words = (_a = KEYWORDS[String(language !== null && language !== void 0 ? language : "").toLowerCase()]) !== null && _a !== void 0 ? _a : new Set(); const tokens = []; const re = /(\/\/[^\n]*|#[^\n]*|\/\*[\s\S]*?\*\/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_$][\w$]*\b)/g; let last = 0; for (const m of source.matchAll(re)) {
    const i = (_b = m.index) !== null && _b !== void 0 ? _b : 0;
    if (i > last)
        tokens.push({ kind: "plain", text: source.slice(last, i) });
    const v = m[0];
    let kind = "plain";
    if (v.startsWith("//") || v.startsWith("#") || v.startsWith("/*"))
        kind = "comment";
    else if (/^['"`]/.test(v))
        kind = "string";
    else if (/^\d/.test(v))
        kind = "number";
    else if (words.has(v))
        kind = "keyword";
    tokens.push({ kind, text: v });
    last = i + v.length;
} if (last < source.length)
    tokens.push({ kind: "plain", text: source.slice(last) }); return tokens; }
function assessProgrammingAttempt(exercise, observedOutput, completed = false) { const observed = text(observedOutput); const expected = text(exercise.expectedOutput); const norm = (v) => v.replace(/\r\n/g, "\n").trim(); return { exerciseId: exercise.id, expectedOutput: expected, observedOutput: observed, matchesExpected: expected == null || observed == null ? null : norm(expected) === norm(observed), executionMode: "manual-output", completed: Boolean(completed) }; }
function buildLanguageReviewPrompt(expression, direction) { let prompt = "", expected = null, manualReview = false, ready = true, reason = null; switch (direction) {
    case "recognition":
        prompt = `What does “${expression.expression}” mean?`;
        expected = expression.meaning;
        break;
    case "production":
        prompt = `Produce the expression for: ${expression.meaning}`;
        expected = expression.expression;
        break;
    case "fill-blank":
        if (expression.exampleSentence && expression.exampleSentence.toLowerCase().includes(expression.expression.toLowerCase())) {
            prompt = expression.exampleSentence.replace(new RegExp(expression.expression.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"), "____");
            expected = expression.expression;
        }
        else {
            ready = false;
            reason = "Add an example sentence containing the expression before fill-in review.";
            prompt = "Fill-in review needs an example sentence.";
        }
        break;
    case "natural-usage":
        prompt = expression.exampleSentence ? `Judge and explain whether this usage is natural: ${expression.exampleSentence}` : `Write a natural context for “${expression.expression}”.`;
        expected = expression.exampleSentence;
        manualReview = true;
        break;
    case "mistake-spotting":
        if (expression.commonMistakes.length) {
            prompt = `Spot and correct the mistake: ${expression.commonMistakes[0]}`;
            expected = expression.expression;
        }
        else {
            ready = false;
            reason = "Add at least one common mistake before mistake-spotting review.";
            prompt = "Mistake spotting needs a stored common mistake.";
        }
        break;
    case "sentence-creation":
        prompt = `Create your own sentence using “${expression.expression}”.`;
        expected = null;
        manualReview = true;
        break;
} return { expressionId: expression.id, direction, prompt, expected, manualReview, ready, reason }; }
