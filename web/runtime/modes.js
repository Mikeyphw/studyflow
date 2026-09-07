"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.filterPracticeQuestions = exports.PUBLIC_EXAM_STARTER_TEMPLATES = void 0;
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
exports.scoreMockExam = scoreMockExam;
exports.derivePublicExamDashboard = derivePublicExamDashboard;
exports.createProgrammingExercise = createProgrammingExercise;
exports.createLanguageExpression = createLanguageExpression;
exports.createReadingItem = createReadingItem;
exports.createGeneralSkillDrill = createGeneralSkillDrill;
exports.deriveModeAnalytics = deriveModeAnalytics;
const schedule_1 = require("./schedule");
const STUDY_MODES = ["EXAM_PLAN", "PROGRAMMING_COURSE", "LANGUAGE_DECK", "READING_STUDY", "GENERAL_SKILL"];
const LANGUAGE_DIRECTIONS = ["recognition", "production", "fill-blank", "natural-usage", "mistake-spotting", "sentence-creation"];
const EXAM_SCORING = ["no-penalty", "fixed-negative", "right-minus-wrong", "weighted"];
const clone = (value) => JSON.parse(JSON.stringify(value));
const text = (value) => { const v = String(value ?? "").trim(); return v || null; };
const n = (value) => { if (value == null || value === "")
    return null; const v = Number(value); return Number.isFinite(v) ? v : null; };
const bool = (value) => Boolean(value);
const arr = (value) => Array.isArray(value) ? value : [];
const id = (prefix) => `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`}`;
const iso = (value) => { const v = text(value); if (!v)
    return null; const ms = Date.parse(v); return Number.isFinite(ms) ? new Date(ms).toISOString() : null; };
const dateOnly = (value) => { const v = text(value); if (!v)
    return null; const m = v.match(/^\d{4}-\d{2}-\d{2}$/); return m ? v : null; };
const uniqueStrings = (value) => [...new Set(arr(value).map(x => String(x ?? "").trim()).filter(Boolean))];
function defaultPlanConfiguration(mode = "GENERAL_SKILL") {
    const base = { version: 1, schedule: (0, schedule_1.defaultScheduleConfiguration)(), practice: { savedSets: [], weeklyQuestionQuota: null } };
    if (mode === "EXAM_PLAN")
        base.publicExam = {
            target: { organization: null, role: null, board: null, editalRef: null, dateStatus: "unknown", examDate: null, openings: null, estimatedCutoff: null },
            stages: [], noticeRevisions: [], reviewIntervalsDays: [1, 7, 30], legislation: [], studyPhase: "pre-edital", mockBlueprint: { scoring: "no-penalty", negativePoints: 0, totalQuestions: 20, durationMinutes: 60, sections: [] }
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
    const raw = (input && typeof input === "object" && !Array.isArray(input) ? clone(input) : {});
    const out = { version: 1, schedule: (0, schedule_1.normalizeScheduleConfiguration)(raw.schedule), practice: { savedSets: arr(raw.practice?.savedSets).map((x, i) => ({ id: text(x?.id) ?? `practice-set-${i + 1}`, name: text(x?.name) ?? `Set ${i + 1}`, questionIds: uniqueStrings(x?.questionIds), filter: (x?.filter && typeof x.filter === "object" ? x.filter : {}), createdAt: iso(x?.createdAt) ?? new Date(0).toISOString() })), weeklyQuestionQuota: n(raw.practice?.weeklyQuestionQuota) == null ? null : Math.max(1, Math.trunc(n(raw.practice?.weeklyQuestionQuota))) } };
    if (mode === "EXAM_PLAN") {
        const d = defaultPlanConfiguration(mode).publicExam;
        const p = raw.publicExam ?? {};
        out.publicExam = {
            target: normalizeExamTarget(p.target),
            stages: arr(p.stages).map((s, i) => ({ id: text(s?.id) ?? `stage-${i + 1}`, name: text(s?.name) ?? `Stage ${i + 1}`, date: dateOnly(s?.date), status: ["planned", "completed", "cancelled"].includes(String(s?.status)) ? s.status : "planned" })),
            noticeRevisions: arr(p.noticeRevisions).map((r, i) => ({ id: text(r?.id) ?? `notice-${i + 1}`, acceptedAt: iso(r?.acceptedAt) ?? new Date(0).toISOString(), sourceHash: text(r?.sourceHash) ?? `legacy-${i + 1}`, added: Math.max(0, Math.trunc(n(r?.added) ?? 0)), archived: Math.max(0, Math.trunc(n(r?.archived) ?? 0)), reactivated: Math.max(0, Math.trunc(n(r?.reactivated) ?? 0)), unchanged: Math.max(0, Math.trunc(n(r?.unchanged) ?? 0)), previousRevisionId: text(r?.previousRevisionId), syllabusFingerprint: text(r?.syllabusFingerprint) })),
            reviewIntervalsDays: arr(p.reviewIntervalsDays).map(Number).filter(v => Number.isInteger(v) && v > 0 && v <= 365).slice(0, 8),
            legislation: arr(p.legislation).map((d, i) => ({ id: text(d?.id) ?? `law-${i + 1}`, title: text(d?.title) ?? `Legislation ${i + 1}`, reference: text(d?.reference), notes: text(d?.notes), sourceUpdatedAt: iso(d?.sourceUpdatedAt), verifiedAt: iso(d?.verifiedAt), provisions: arr(d?.provisions).map((q, j) => ({ id: text(q?.id) ?? `provision-${i + 1}-${j + 1}`, label: text(q?.label) ?? `Provision ${j + 1}`, text: text(q?.text) ?? "", status: ["unseen", "studying", "reviewing", "known"].includes(String(q?.status)) ? q.status : "unseen" })), evidenceLinks: arr(d?.evidenceLinks).map((l, j) => ({ id: text(l?.id) ?? `law-link-${i + 1}-${j + 1}`, provisionId: text(l?.provisionId) ?? "", questionId: text(l?.questionId), errorId: text(l?.errorId), reviewId: text(l?.reviewId), note: text(l?.note), createdAt: iso(l?.createdAt) ?? new Date(0).toISOString() })) })),
            studyPhase: ["pre-edital", "post-edital", "final-stretch"].includes(String(p.studyPhase)) ? p.studyPhase : (arr(p.noticeRevisions).length ? "post-edital" : "pre-edital"),
            mockBlueprint: normalizeMockBlueprint(p.mockBlueprint ?? d.mockBlueprint),
        };
        if (!out.publicExam.reviewIntervalsDays.length)
            out.publicExam.reviewIntervalsDays = [1, 7, 30];
    }
    if (mode === "PROGRAMMING_COURSE") {
        const p = raw.programming ?? {};
        out.programming = { language: text(p.language) ?? "TypeScript", level: ["beginner", "intermediate", "advanced"].includes(String(p.level)) ? p.level : "beginner", runtime: "browser-only",
            exercises: arr(p.exercises).map((e, i) => normalizeProgrammingExercise(e, i)), projects: arr(p.projects).map((q, i) => ({ id: text(q?.id) ?? `project-${i + 1}`, title: text(q?.title) ?? `Project ${i + 1}`, status: ["planned", "active", "completed"].includes(String(q?.status)) ? q.status : "planned", checkpoints: uniqueStrings(q?.checkpoints), deliverables: uniqueStrings(q?.deliverables) })) };
    }
    if (mode === "LANGUAGE_DECK") {
        const p = raw.language ?? {};
        out.language = { language: text(p.language) ?? "English", nativeLanguage: text(p.nativeLanguage), expressions: arr(p.expressions).map((e, i) => normalizeLanguageExpression(e, i)), reviewDirections: arr(p.reviewDirections).map(String).filter(x => LANGUAGE_DIRECTIONS.includes(x)) };
        if (!out.language.reviewDirections.length)
            out.language.reviewDirections = [...LANGUAGE_DIRECTIONS];
    }
    if (mode === "READING_STUDY") {
        const p = raw.reading ?? {};
        out.reading = { items: arr(p.items).map((r, i) => normalizeReadingItem(r, i)) };
    }
    if (mode === "GENERAL_SKILL") {
        const p = raw.general ?? {};
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
    const x = (input && typeof input === "object" ? input : {});
    const scoring = EXAM_SCORING.includes(x.scoring) ? x.scoring : "no-penalty";
    return { scoring, negativePoints: Math.max(0, n(x.negativePoints) ?? 0), totalQuestions: Math.max(1, Math.min(500, Math.trunc(n(x.totalQuestions) ?? 20))), durationMinutes: Math.max(5, Math.min(1440, Math.trunc(n(x.durationMinutes) ?? 60))), sections: arr(x.sections).map((s, i) => { const weight = n(s?.weight); return { id: text(s?.id) ?? `section-${i + 1}`, subjectId: text(s?.subjectId), count: Math.max(1, Math.trunc(n(s?.count) ?? 1)), weight: weight != null && weight > 0 ? weight : 1 }; }) };
}
function normalizeProgrammingExercise(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); return { id: text(x.id) ?? `exercise-${index + 1}`, title: text(x.title) ?? `Exercise ${index + 1}`, language: text(x.language) ?? "TypeScript", prompt: text(x.prompt) ?? "", starterCode: String(x.starterCode ?? ""), expectedOutput: text(x.expectedOutput), hints: uniqueStrings(x.hints), solutionNotes: text(x.solutionNotes), difficulty: ["easy", "medium", "hard"].includes(String(x.difficulty)) ? x.difficulty : "medium", status: ["todo", "attempted", "completed"].includes(String(x.status)) ? x.status : "todo" }; }
function normalizeLanguageExpression(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); return { id: text(x.id) ?? `expression-${index + 1}`, expression: text(x.expression) ?? `Expression ${index + 1}`, meaning: text(x.meaning) ?? "", literalTranslation: text(x.literalTranslation), region: text(x.region), register: text(x.register), pronunciationNote: text(x.pronunciationNote), commonMistakes: uniqueStrings(x.commonMistakes) }; }
function normalizeReadingItem(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); const total = Math.max(1, Math.trunc(n(x.totalUnits) ?? 1)); return { id: text(x.id) ?? `reading-${index + 1}`, title: text(x.title) ?? `Reading ${index + 1}`, totalUnits: total, currentUnit: Math.max(0, Math.min(total, Math.trunc(n(x.currentUnit) ?? 0))), unit: text(x.unit) ?? "pages", status: ["planned", "reading", "completed"].includes(String(x.status)) ? x.status : "planned", notes: text(x.notes) }; }
function normalizeGeneralDrill(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); return { id: text(x.id) ?? `drill-${index + 1}`, title: text(x.title) ?? `Drill ${index + 1}`, metric: text(x.metric) ?? "repetitions", target: Math.max(0, n(x.target) ?? 1), unit: text(x.unit) ?? "count", status: ["active", "paused", "completed"].includes(String(x.status)) ? x.status : "active" }; }
function normalizeQuestionMetadata(input) { const points = n(input.points); const diff = text(input.difficulty); return { difficulty: ["easy", "medium", "hard"].includes(String(diff)) ? diff : null, favorite: bool(input.favorite), organization: text(input.organization), role: text(input.role), examStage: text(input.examStage), examBoard: text(input.examBoard), year: n(input.year) == null ? null : Math.trunc(n(input.year)), authorship: text(input.authorship), editalRevision: text(input.editalRevision), externalReference: text(input.externalReference), points: points != null && points > 0 ? points : null }; }
function hashText(source) { let h = 2166136261; for (let i = 0; i < source.length; i++) {
    h ^= source.charCodeAt(i);
    h = Math.imul(h, 16777619);
} return (h >>> 0).toString(16).padStart(8, "0"); }
function syllabusFingerprint(state, planId) { const subjects = state.subjects.filter(x => x.planId === planId).map(x => `${x.name}|${x.archived}`).sort(); const topics = state.topics.filter(x => x.planId === planId).map(x => `${x.subjectId}|${x.parentTopicId ?? ""}|${x.name}|${x.status}`).sort(); return hashText(JSON.stringify([subjects, topics])); }
function parseExamNoticeText(source) {
    const subjects = [];
    const unrecognized = [];
    let current = null;
    const numbered = new Map();
    for (const raw of String(source ?? "").split(/\r?\n/)) {
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
        const label = (m?.[2] ?? line).trim();
        if (!label) {
            unrecognized.push(line);
            continue;
        }
        let path = [label];
        if (m?.[1]) {
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
    const parsed = parseExamNoticeText(source);
    const planId = state.activePlanId;
    if (!planId)
        throw new TypeError("No active plan");
    const plan = state.plans.find(p => p.id === planId);
    if (plan?.mode !== "EXAM_PLAN")
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
    const revisions = plan.configuration.publicExam?.noticeRevisions ?? [];
    const latest = revisions.length ? revisions[revisions.length - 1] : null;
    return { baseRevision: state.revision, generatedAt: new Date().toISOString(), sourceHash: hashText(source), syllabusFingerprint: syllabusFingerprint(state, planId), previousRevisionId: latest?.id ?? null, subjects: parsed.subjects, duplicates: parsed.duplicates, unrecognized: parsed.unrecognized, destructiveChanges: archive.length, diff: { add, archive, reactivate, unchanged } };
}
function deriveFinalStretchGuidance(plan, at = new Date()) {
    const target = plan.configuration.publicExam?.target;
    const exam = target?.examDate ?? plan.examDate;
    if (!exam || target?.dateStatus !== "confirmed")
        return { band: "unknown", daysRemaining: null, allocation: { newContent: 0, reviews: 0, errors: 0, questions: 0, mocks: 0, legislation: 0 } };
    const days = Math.ceil((Date.parse(`${exam}T23:59:59Z`) - at.getTime()) / 86400000);
    const d = Math.max(0, days);
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
function filterExamQuestions(state, filter = {}) { return state.questions.filter(q => q.planId === state.activePlanId).filter(q => filter.board ? String(q.examBoard ?? "").toLowerCase() === filter.board.toLowerCase() : true).filter(q => filter.organization ? String(q.organization ?? "").toLowerCase() === filter.organization.toLowerCase() : true).filter(q => filter.role ? String(q.role ?? "").toLowerCase() === filter.role.toLowerCase() : true).filter(q => filter.stage ? String(q.examStage ?? "").toLowerCase() === filter.stage.toLowerCase() : true).filter(q => filter.subjectId ? q.subjectId === filter.subjectId : true).filter(q => filter.topicId ? q.topicId === filter.topicId : true).filter(q => filter.yearFrom != null ? (q.year ?? -Infinity) >= filter.yearFrom : true).filter(q => filter.yearTo != null ? (q.year ?? Infinity) <= filter.yearTo : true).filter(q => filter.authorship ? String(q.authorship ?? "").toLowerCase() === filter.authorship.toLowerCase() : true).filter(q => filter.difficulty ? q.difficulty === filter.difficulty : true).filter(q => filter.favorite != null ? q.favorite === filter.favorite : true).filter(q => filter.disputed != null ? q.disputed === filter.disputed : true).filter(q => filter.evidence ? questionEvidenceStatus(state, q.id) === filter.evidence : true); }
exports.filterPracticeQuestions = filterExamQuestions;
function buildWeaknessDrill(state, { limit = 10, minQuestions = 2 } = {}) { const planId = state.activePlanId; if (!planId)
    return { baseRevision: state.revision, generatedAt: new Date().toISOString(), ready: false, questionIds: [], reasons: ["No active plan"], signals: [] }; const scores = new Map(); const add = (subjectId, topicId, score, reason) => { const k = `${subjectId ?? ""}|${topicId ?? ""}`; const x = scores.get(k) ?? { subjectId, topicId, score: 0, reasons: new Set() }; x.score += score; x.reasons.add(reason); scores.set(k, x); }; for (const err of state.errorEntries.filter(e => e.planId === planId && e.status === "open")) {
    add(err.subjectId, err.topicId, 2 + Math.min(5, err.recurrenceCount), `open ${err.category} error ×${err.recurrenceCount}`);
} for (const r of state.reviews.filter(r => r.planId === planId && r.status === "due" && Date.parse(r.dueAt) <= Date.now()))
    add(r.subjectId, r.topicId, 2, "overdue review"); for (const ev of state.evidence.filter(e => e.planId === planId && e.kind === "question-attempt" && e.value?.correct === false))
    add(ev.subjectId, ev.topicId, 1, "wrong question attempt"); const signals = [...scores.values()].sort((a, b) => b.score - a.score || String(a.topicId ?? a.subjectId).localeCompare(String(b.topicId ?? b.subjectId))).map(x => ({ ...x, reasons: [...x.reasons] })); if (!signals.length)
    return { baseRevision: state.revision, generatedAt: new Date().toISOString(), ready: false, questionIds: [], reasons: ["No persisted weakness signal exists yet."], signals: [] }; const candidates = state.questions.filter(q => q.planId === planId && !q.disputed).map(q => { let score = 0; for (const s of signals) {
    if (s.topicId && q.topicId === s.topicId)
        score = Math.max(score, s.score + 4);
    else if (s.subjectId && q.subjectId === s.subjectId)
        score = Math.max(score, s.score);
} return { q, score }; }).filter(x => x.score > 0).sort((a, b) => b.score - a.score || a.q.id.localeCompare(b.q.id)); const ids = candidates.slice(0, Math.max(1, limit)).map(x => x.q.id); return { baseRevision: state.revision, generatedAt: new Date().toISOString(), ready: ids.length >= minQuestions, questionIds: ids, reasons: ids.length >= minQuestions ? [`${ids.length} questions matched persisted weakness signals.`] : [`Only ${ids.length} matching question(s); at least ${minQuestions} required.`], signals }; }
function derivePublicExamNextActions(state, limit = 5) { const planId = state.activePlanId; if (!planId)
    return []; const topics = state.topics.filter(t => t.planId === planId && t.status !== "archived"); const attempts = state.evidence.filter(e => e.planId === planId && e.kind === "question-attempt"); return topics.map(topic => { const subject = state.subjects.find(s => s.id === topic.subjectId); const ta = attempts.filter(e => e.topicId === topic.id && e.value?.gradable === true); const accuracy = ta.length ? ta.filter(e => e.value.correct === true).length / ta.length : null; const errors = state.errorEntries.filter(e => e.topicId === topic.id && e.status === "open").reduce((n, e) => n + e.recurrenceCount, 0); const reviews = state.reviews.filter(r => r.topicId === topic.id && r.status === "due").length; let score = (subject?.weight ?? 1) * 10 + (topic.priority === "high" ? 20 : topic.priority === "low" ? 0 : 10) + errors * 5 + reviews * 4 + (accuracy == null ? 5 : (1 - accuracy) * 25); const reasonCodes = []; if (topic.priority === "high")
    reasonCodes.push("high-topic-priority"); if (errors)
    reasonCodes.push("open-error-backlog"); if (reviews)
    reasonCodes.push("due-review-backlog"); if (accuracy != null && accuracy < 0.7)
    reasonCodes.push("low-observed-accuracy"); if (accuracy == null)
    reasonCodes.push("limited-evidence"); return { subjectId: topic.subjectId, topicId: topic.id, score: roundScore(score), reasonCodes, explanation: reasonCodes.length ? reasonCodes.join(", ") : "baseline cycle priority" }; }).sort((a, b) => b.score - a.score || String(a.topicId).localeCompare(String(b.topicId))).slice(0, limit); }
function deriveEditalMatrix(state) { const planId = state.activePlanId; if (!planId)
    return []; const activeSubjects = state.subjects.filter(s => s.planId === planId && !s.archived); const weights = activeSubjects.map(s => s.weight ?? 1); const maxWeight = Math.max(1, ...weights); return activeSubjects.map(subject => { const topics = state.topics.filter(t => t.subjectId === subject.id && t.status !== "archived"); const masteryValues = topics.map(t => t.status === "mastered" ? 1 : t.status === "reviewing" ? .75 : t.status === "learning" ? .4 : 0); const mastery = topics.length ? masteryValues.reduce((a, b) => a + b, 0) / topics.length : null; const incidence = subject.examIncidence == null ? null : subject.examIncidence / 100; const backlog = state.errorEntries.filter(e => e.subjectId === subject.id && e.status === "open").reduce((n, e) => n + e.recurrenceCount, 0) + state.reviews.filter(r => r.subjectId === subject.id && r.status === "due").length; const relativeWeight = (subject.weight ?? 1) / maxWeight; const priority = roundScore((.35 * relativeWeight + .20 * (incidence ?? .5) + .30 * (1 - (mastery ?? 0)) + .15 * Math.min(1, backlog / 10)) * 100); return { subjectId: subject.id, subjectName: subject.name, relativeWeight, incidence, mastery, backlog, priority }; }).sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0) || a.subjectName.localeCompare(b.subjectName)); }
function previewMockExam(state, blueprintInput = {}) { const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan || plan.mode !== "EXAM_PLAN")
    throw new TypeError("Mock preview requires an EXAM_PLAN"); const blueprint = normalizeMockBlueprint({ ...plan.configuration.publicExam?.mockBlueprint, ...blueprintInput }); const bank = state.questions.filter(q => q.planId === plan.id && !q.disputed && Boolean(q.answer)); const selected = []; const deficits = []; if (blueprint.sections.length) {
    for (const s of blueprint.sections) {
        const candidates = bank.filter(q => !selected.includes(q.id) && (!s.subjectId || q.subjectId === s.subjectId)).sort((a, b) => (b.points ?? 1) - (a.points ?? 1) || a.id.localeCompare(b.id));
        if (candidates.length < s.count)
            deficits.push(`${s.subjectId ?? "all"}: need ${s.count}, have ${candidates.length}`);
        selected.push(...candidates.slice(0, s.count).map(q => q.id));
    }
}
else {
    selected.push(...bank.sort((a, b) => (b.points ?? 1) - (a.points ?? 1) || a.id.localeCompare(b.id)).slice(0, blueprint.totalQuestions).map(q => q.id));
    if (selected.length < blueprint.totalQuestions)
        deficits.push(`need ${blueprint.totalQuestions}, have ${selected.length}`);
} return { baseRevision: state.revision, generatedAt: new Date().toISOString(), blueprint, questionIds: selected, deficits, ready: deficits.length === 0 }; }
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
            const maximum = group.weight == null ? bases[index] : sectionMaximum * (bases[index] / baseTotal);
            const submitted = String(answers[q.id] ?? "").trim();
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
    return { scoringPolicy: preview.blueprint.scoring, correct, wrong, unanswered, rawScore: roundScore(rawScore), maximumScore: roundScore(maximumScore), percent: maximumScore > 0 ? clampPercent((Math.max(0, rawScore) / maximumScore) * 100) : null, rounding: "nearest-0.01", sections, questions };
}
function derivePublicExamDashboard(state) { const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan || plan.mode !== "EXAM_PLAN")
    return null; const activeTopics = state.topics.filter(t => t.planId === plan.id && t.status !== "archived"); const progressed = activeTopics.filter(t => t.status !== "not-started"); const attempts = state.evidence.filter(e => e.planId === plan.id && e.kind === "question-attempt" && typeof e.value === "object" && e.value && "gradable" in e.value && e.value.gradable === true); const correct = attempts.filter(e => e.value.correct === true).length; const due = state.reviews.filter(r => r.planId === plan.id && r.status === "due").length; const mocks = state.evidence.filter(e => e.planId === plan.id && e.kind === "mock-exam-result").sort((a, b) => b.observedAt.localeCompare(a.observedAt)); const latest = mocks[0]?.value; const cutoff = plan.configuration.publicExam?.target.estimatedCutoff ?? null; const window = (days) => { const since = Date.now() - days * 86400000; const xs = attempts.filter(e => Date.parse(e.observedAt) >= since); return xs.length ? xs.filter(e => e.value.correct === true).length / xs.length : null; }; const matrix = deriveEditalMatrix(state); const known = matrix.filter(r => r.mastery != null); const weightedMastery = known.length ? known.reduce((n, r) => n + (r.mastery ?? 0) * (r.relativeWeight ?? 0), 0) / known.reduce((n, r) => n + (r.relativeWeight ?? 0), 0) : null; return { coverage: activeTopics.length ? progressed.length / activeTopics.length : null, accuracy: attempts.length ? correct / attempts.length : null, accuracy7: window(7), accuracy30: window(30), accuracy90: window(90), weightedMastery, reviewBacklog: due, cutoffGap: cutoff != null && typeof latest?.percent === "number" ? roundScore(latest.percent - cutoff) : null, noticeRevisionCount: plan.configuration.publicExam?.noticeRevisions.length ?? 0, legislationCount: plan.configuration.publicExam?.legislation.length ?? 0, matrix }; }
function createProgrammingExercise(input) { return normalizeProgrammingExercise({ ...input, id: input.id ?? id("exercise") }); }
function createLanguageExpression(input) { return normalizeLanguageExpression({ ...input, id: input.id ?? id("expression") }); }
function createReadingItem(input) { return normalizeReadingItem({ ...input, id: input.id ?? id("reading") }); }
function createGeneralSkillDrill(input) { return normalizeGeneralDrill({ ...input, id: input.id ?? id("drill") }); }
function deriveModeAnalytics(state) { const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan)
    return { mode: null, metrics: [] }; const evidence = state.evidence.filter(e => e.planId === plan.id); if (plan.mode === "EXAM_PLAN") {
    const d = derivePublicExamDashboard(state);
    return { mode: plan.mode, metrics: [{ label: "Edital coverage", value: d?.coverage ?? null }, { label: "Question accuracy", value: d?.accuracy ?? null }, { label: "Review backlog", value: d?.reviewBacklog ?? 0 }, { label: "Cutoff gap", value: d?.cutoffGap ?? null }] };
} if (plan.mode === "PROGRAMMING_COURSE") {
    const ex = plan.configuration.programming?.exercises ?? [];
    const done = ex.filter(x => x.status === "completed").length;
    const attempts = evidence.filter(e => e.kind === "programming-exercise");
    return { mode: plan.mode, metrics: [{ label: "Exercises completed", value: ex.length ? done / ex.length : null }, { label: "Exercise attempts", value: attempts.length }, { label: "Projects", value: plan.configuration.programming?.projects.length ?? 0 }] };
} if (plan.mode === "LANGUAGE_DECK") {
    const expressions = plan.configuration.language?.expressions ?? [];
    const reviews = evidence.filter(e => e.kind === "language-review");
    const good = reviews.filter(e => Number(e.value?.rating) >= 3).length;
    return { mode: plan.mode, metrics: [{ label: "Expressions", value: expressions.length }, { label: "Usage accuracy", value: reviews.length ? good / reviews.length : null }, { label: "Review attempts", value: reviews.length }] };
} if (plan.mode === "READING_STUDY") {
    const items = plan.configuration.reading?.items ?? [];
    const total = items.reduce((s, x) => s + x.totalUnits, 0), done = items.reduce((s, x) => s + x.currentUnit, 0);
    return { mode: plan.mode, metrics: [{ label: "Reading progress", value: total ? done / total : null }, { label: "Reading items", value: items.length }] };
} const drills = plan.configuration.general?.drills ?? []; return { mode: plan.mode, metrics: [{ label: "Active drills", value: drills.filter(x => x.status === "active").length }, { label: "Drill evidence", value: evidence.filter(e => e.kind === "skill-drill").length }] }; }
