"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultPlanConfiguration = defaultPlanConfiguration;
exports.normalizePlanConfiguration = normalizePlanConfiguration;
exports.validatePlanConfiguration = validatePlanConfiguration;
exports.normalizeMockBlueprint = normalizeMockBlueprint;
exports.normalizeQuestionMetadata = normalizeQuestionMetadata;
exports.parseExamNoticeText = parseExamNoticeText;
exports.buildExamNoticePreview = buildExamNoticePreview;
exports.deriveFinalStretchGuidance = deriveFinalStretchGuidance;
exports.filterExamQuestions = filterExamQuestions;
exports.previewMockExam = previewMockExam;
exports.scoreMockExam = scoreMockExam;
exports.derivePublicExamDashboard = derivePublicExamDashboard;
exports.createProgrammingExercise = createProgrammingExercise;
exports.createLanguageExpression = createLanguageExpression;
exports.createReadingItem = createReadingItem;
exports.createGeneralSkillDrill = createGeneralSkillDrill;
exports.deriveModeAnalytics = deriveModeAnalytics;
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
    const base = { version: 1 };
    if (mode === "EXAM_PLAN")
        base.publicExam = {
            target: { organization: null, role: null, board: null, editalRef: null, dateStatus: "unknown", examDate: null, openings: null, estimatedCutoff: null },
            stages: [], noticeRevisions: [], reviewIntervalsDays: [1, 7, 30], legislation: [], mockBlueprint: { scoring: "no-penalty", negativePoints: 0, totalQuestions: 20, durationMinutes: 60, sections: [] }
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
    const out = { version: 1 };
    if (mode === "EXAM_PLAN") {
        const d = defaultPlanConfiguration(mode).publicExam;
        const p = raw.publicExam ?? {};
        out.publicExam = {
            target: normalizeExamTarget(p.target),
            stages: arr(p.stages).map((s, i) => ({ id: text(s?.id) ?? `stage-${i + 1}`, name: text(s?.name) ?? `Stage ${i + 1}`, date: dateOnly(s?.date), status: ["planned", "completed", "cancelled"].includes(String(s?.status)) ? s.status : "planned" })),
            noticeRevisions: arr(p.noticeRevisions).map((r, i) => ({ id: text(r?.id) ?? `notice-${i + 1}`, acceptedAt: iso(r?.acceptedAt) ?? new Date(0).toISOString(), sourceHash: text(r?.sourceHash) ?? `legacy-${i + 1}`, added: Math.max(0, Math.trunc(n(r?.added) ?? 0)), archived: Math.max(0, Math.trunc(n(r?.archived) ?? 0)) })),
            reviewIntervalsDays: arr(p.reviewIntervalsDays).map(Number).filter(v => Number.isInteger(v) && v > 0 && v <= 365).slice(0, 8),
            legislation: arr(p.legislation).map((d, i) => ({ id: text(d?.id) ?? `law-${i + 1}`, title: text(d?.title) ?? `Legislation ${i + 1}`, reference: text(d?.reference), notes: text(d?.notes), provisions: arr(d?.provisions).map((q, j) => ({ id: text(q?.id) ?? `provision-${i + 1}-${j + 1}`, label: text(q?.label) ?? `Provision ${j + 1}`, text: text(q?.text) ?? "", status: ["unseen", "studying", "reviewing", "known"].includes(String(q?.status)) ? q.status : "unseen" })) })),
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
function normalizeMockBlueprint(input) {
    const x = (input && typeof input === "object" ? input : {});
    const scoring = EXAM_SCORING.includes(x.scoring) ? x.scoring : "no-penalty";
    return { scoring, negativePoints: Math.max(0, n(x.negativePoints) ?? 0), totalQuestions: Math.max(1, Math.min(500, Math.trunc(n(x.totalQuestions) ?? 20))), durationMinutes: Math.max(5, Math.min(1440, Math.trunc(n(x.durationMinutes) ?? 60))), sections: arr(x.sections).map((s, i) => { const weight = n(s?.weight); return { id: text(s?.id) ?? `section-${i + 1}`, subjectId: text(s?.subjectId), count: Math.max(1, Math.trunc(n(s?.count) ?? 1)), weight: weight != null && weight > 0 ? weight : 1 }; }) };
}
function normalizeProgrammingExercise(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); return { id: text(x.id) ?? `exercise-${index + 1}`, title: text(x.title) ?? `Exercise ${index + 1}`, language: text(x.language) ?? "TypeScript", prompt: text(x.prompt) ?? "", starterCode: String(x.starterCode ?? ""), expectedOutput: text(x.expectedOutput), hints: uniqueStrings(x.hints), solutionNotes: text(x.solutionNotes), difficulty: ["easy", "medium", "hard"].includes(String(x.difficulty)) ? x.difficulty : "medium", status: ["todo", "attempted", "completed"].includes(String(x.status)) ? x.status : "todo" }; }
function normalizeLanguageExpression(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); return { id: text(x.id) ?? `expression-${index + 1}`, expression: text(x.expression) ?? `Expression ${index + 1}`, meaning: text(x.meaning) ?? "", literalTranslation: text(x.literalTranslation), region: text(x.region), register: text(x.register), pronunciationNote: text(x.pronunciationNote), commonMistakes: uniqueStrings(x.commonMistakes) }; }
function normalizeReadingItem(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); const total = Math.max(1, Math.trunc(n(x.totalUnits) ?? 1)); return { id: text(x.id) ?? `reading-${index + 1}`, title: text(x.title) ?? `Reading ${index + 1}`, totalUnits: total, currentUnit: Math.max(0, Math.min(total, Math.trunc(n(x.currentUnit) ?? 0))), unit: text(x.unit) ?? "pages", status: ["planned", "reading", "completed"].includes(String(x.status)) ? x.status : "planned", notes: text(x.notes) }; }
function normalizeGeneralDrill(input, index = 0) { const x = (input && typeof input === "object" ? input : {}); return { id: text(x.id) ?? `drill-${index + 1}`, title: text(x.title) ?? `Drill ${index + 1}`, metric: text(x.metric) ?? "repetitions", target: Math.max(0, n(x.target) ?? 1), unit: text(x.unit) ?? "count", status: ["active", "paused", "completed"].includes(String(x.status)) ? x.status : "active" }; }
function normalizeQuestionMetadata(input) { const points = n(input.points); return { organization: text(input.organization), role: text(input.role), examStage: text(input.examStage), examBoard: text(input.examBoard), year: n(input.year) == null ? null : Math.trunc(n(input.year)), authorship: text(input.authorship), editalRevision: text(input.editalRevision), externalReference: text(input.externalReference), points: points != null && points > 0 ? points : null }; }
function hashText(source) { let h = 2166136261; for (let i = 0; i < source.length; i++) {
    h ^= source.charCodeAt(i);
    h = Math.imul(h, 16777619);
} return (h >>> 0).toString(16).padStart(8, "0"); }
function parseExamNoticeText(source) {
    const subjects = [];
    const unrecognized = [];
    let current = null;
    for (const raw of String(source ?? "").split(/\r?\n/)) {
        const line = raw.trim();
        if (!line)
            continue;
        const heading = line.match(/^(?:disciplina|mat[eé]ria|subject)\s*:\s*(.+)$/i);
        if (heading) {
            current = { name: heading[1].trim(), topics: [] };
            subjects.push(current);
            continue;
        }
        const bullet = line.match(/^(?:[-*•]|\d+[.)])\s*(.+)$/);
        if (bullet && current) {
            current.topics.push(bullet[1].trim());
            continue;
        }
        if (!current) {
            current = { name: line.replace(/:$/, "").trim(), topics: [] };
            subjects.push(current);
            continue;
        }
        current.topics.push(line);
    }
    const seen = new Set(), duplicates = [];
    for (const s of subjects) {
        const key = s.name.toLowerCase();
        if (seen.has(key))
            duplicates.push(s.name);
        seen.add(key);
        const ts = new Set();
        s.topics = s.topics.filter(t => { const k = t.toLowerCase(); if (ts.has(k)) {
            duplicates.push(`${s.name} / ${t}`);
            return false;
        } ts.add(k); return true; });
        if (!s.name)
            unrecognized.push("blank subject");
    }
    return { subjects: subjects.filter(s => s.name), unrecognized, duplicates };
}
function buildExamNoticePreview(state, source) { const parsed = parseExamNoticeText(source); const planId = state.activePlanId; if (!planId)
    throw new TypeError("No active plan"); const plan = state.plans.find(p => p.id === planId); if (plan?.mode !== "EXAM_PLAN")
    throw new TypeError("Edital import requires an EXAM_PLAN"); return { baseRevision: state.revision, generatedAt: new Date().toISOString(), sourceHash: hashText(source), subjects: parsed.subjects, duplicates: parsed.duplicates, unrecognized: parsed.unrecognized }; }
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
function filterExamQuestions(state, filter = {}) { return state.questions.filter(q => q.planId === state.activePlanId).filter(q => filter.board ? String(q.examBoard ?? "").toLowerCase() === filter.board.toLowerCase() : true).filter(q => filter.organization ? String(q.organization ?? "").toLowerCase() === filter.organization.toLowerCase() : true).filter(q => filter.role ? String(q.role ?? "").toLowerCase() === filter.role.toLowerCase() : true).filter(q => filter.subjectId ? q.subjectId === filter.subjectId : true).filter(q => filter.topicId ? q.topicId === filter.topicId : true).filter(q => filter.yearFrom != null ? (q.year ?? -Infinity) >= filter.yearFrom : true).filter(q => filter.yearTo != null ? (q.year ?? Infinity) <= filter.yearTo : true).filter(q => filter.disputed != null ? q.disputed === filter.disputed : true); }
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
    return null; const activeTopics = state.topics.filter(t => t.planId === plan.id && t.status !== "archived"); const evidenced = new Set(state.evidence.filter(e => e.planId === plan.id && e.topicId).map(e => e.topicId)); const attempts = state.evidence.filter(e => e.planId === plan.id && e.kind === "question-attempt" && typeof e.value === "object" && e.value && "gradable" in e.value && e.value.gradable === true); const correct = attempts.filter(e => e.value.correct === true).length; const due = state.reviews.filter(r => r.planId === plan.id && r.status === "due").length; const mocks = state.evidence.filter(e => e.planId === plan.id && e.kind === "mock-exam-result").sort((a, b) => b.observedAt.localeCompare(a.observedAt)); const latest = mocks[0]?.value; const cutoff = plan.configuration.publicExam?.target.estimatedCutoff ?? null; return { coverage: activeTopics.length ? evidenced.size / activeTopics.length : null, accuracy: attempts.length ? correct / attempts.length : null, reviewBacklog: due, cutoffGap: cutoff != null && typeof latest?.percent === "number" ? roundScore(latest.percent - cutoff) : null, noticeRevisionCount: plan.configuration.publicExam?.noticeRevisions.length ?? 0, legislationCount: plan.configuration.publicExam?.legislation.length ?? 0 }; }
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
