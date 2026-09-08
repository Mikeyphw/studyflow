"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.BB_REFERENCE_RHYTHM = exports.BB_SUBJECT_WEIGHTS = exports.BB_REFERENCE_SCHEDULED_MINUTES = exports.BB_WEEKLY_ACCURACY_GOAL_PERCENT = exports.BB_WEEKLY_WRITING_SESSION_GOAL = exports.BB_WEEKLY_GOAL_HOURS = exports.BB_ESSAY_MINIMUM_SCORE_PERCENT = exports.BB_OBJECTIVE_MAXIMUM_POINTS = exports.BB_OBJECTIVE_QUESTION_COUNT = exports.BB_REFERENCE_NOTICE_URL = exports.BB_REFERENCE_NOTICE = exports.BB_AGENTE_COMERCIAL_PRE_EDITAL = void 0;
exports.createBbAgenteComercialReferencePreset = createBbAgenteComercialReferencePreset;
exports.bbReferenceExamStages = bbReferenceExamStages;
exports.bbReferenceGoals = bbReferenceGoals;
exports.normalizeStudyActivityType = normalizeStudyActivityType;
exports.activityKindForType = activityKindForType;
exports.routeActivityDeepLink = routeActivityDeepLink;
exports.normalizeWritingChecklist = normalizeWritingChecklist;
exports.writingChecklistComplete = writingChecklistComplete;
exports.normalizeWritingRubric = normalizeWritingRubric;
exports.writingRubricTotal = writingRubricTotal;
exports.createWritingSessionRecord = createWritingSessionRecord;
exports.encodeWritingSessionRecord = encodeWritingSessionRecord;
exports.decodeWritingSessionRecord = decodeWritingSessionRecord;
exports.deriveWritingWeeklyProgress = deriveWritingWeeklyProgress;
exports.activeWritingSession = activeWritingSession;
exports.writingActivity = writingActivity;
exports.BB_AGENTE_COMERCIAL_PRE_EDITAL = "BB_AGENTE_COMERCIAL_PRE_EDITAL";
exports.BB_REFERENCE_NOTICE = "Seleção Externa 2022/001";
exports.BB_REFERENCE_NOTICE_URL = "https://www.bb.com.br/docs/portal/dipes/Edital-de-Abertura-de-Selecao-Externa-2022-01.pdf";
exports.BB_OBJECTIVE_QUESTION_COUNT = 70;
exports.BB_OBJECTIVE_MAXIMUM_POINTS = 100;
exports.BB_ESSAY_MINIMUM_SCORE_PERCENT = 70;
exports.BB_WEEKLY_GOAL_HOURS = 19;
exports.BB_WEEKLY_WRITING_SESSION_GOAL = 1;
exports.BB_WEEKLY_ACCURACY_GOAL_PERCENT = 80;
exports.BB_REFERENCE_SCHEDULED_MINUTES = 1140;
exports.BB_SUBJECT_WEIGHTS = Object.freeze([
    ["Português", 15],
    ["Inglês", 5],
    ["Matemática", 7.5],
    ["Atualidades do Mercado Financeiro", 5],
    ["Matemática Financeira", 7.5],
    ["Conhecimentos Bancários", 15],
    ["Tecnologia da Informação", 22.5],
    ["Vendas e Negociação", 22.5],
]);
exports.BB_REFERENCE_RHYTHM = Object.freeze([
    { weekday: 1, startMinute: 9 * 60, durationMinutes: 90, subjectName: "Português" },
    { weekday: 1, startMinute: 10 * 60 + 40, durationMinutes: 90, subjectName: "Tecnologia da Informação" },
    { weekday: 2, startMinute: 9 * 60, durationMinutes: 90, subjectName: "Vendas e Negociação" },
    { weekday: 2, startMinute: 10 * 60 + 40, durationMinutes: 90, subjectName: "Conhecimentos Bancários" },
    { weekday: 3, startMinute: 9 * 60, durationMinutes: 90, subjectName: "Matemática" },
    { weekday: 3, startMinute: 10 * 60 + 40, durationMinutes: 90, subjectName: "Tecnologia da Informação" },
    { weekday: 4, startMinute: 9 * 60, durationMinutes: 90, subjectName: "Vendas e Negociação" },
    { weekday: 4, startMinute: 10 * 60 + 40, durationMinutes: 90, subjectName: "Conhecimentos Bancários" },
    { weekday: 5, startMinute: 9 * 60, durationMinutes: 60, subjectName: "Matemática Financeira" },
    { weekday: 5, startMinute: 10 * 60 + 10, durationMinutes: 60, subjectName: "Atualidades do Mercado Financeiro" },
    { weekday: 5, startMinute: 11 * 60 + 20, durationMinutes: 60, subjectName: "Inglês" },
    { weekday: 6, startMinute: 9 * 60, durationMinutes: 120, subjectName: null },
    { weekday: 6, startMinute: 11 * 60 + 10, durationMinutes: 60, subjectName: null },
    { weekday: 6, startMinute: 12 * 60 + 20, durationMinutes: 40, subjectName: null },
    { weekday: 6, startMinute: 13 * 60 + 10, durationMinutes: 20, subjectName: null },
]);
function createBbAgenteComercialReferencePreset() {
    const objectivePoints = exports.BB_SUBJECT_WEIGHTS.reduce((sum, [, weight]) => sum + weight, 0);
    if (objectivePoints !== exports.BB_OBJECTIVE_MAXIMUM_POINTS)
        throw new TypeError("BB reference subject weights must total 100 points");
    const scheduledMinutes = exports.BB_REFERENCE_RHYTHM.reduce((sum, block) => sum + block.durationMinutes, 0);
    if (scheduledMinutes !== exports.BB_REFERENCE_SCHEDULED_MINUTES)
        throw new TypeError("BB reference rhythm must total 1140 scheduled minutes");
    return {
        id: exports.BB_AGENTE_COMERCIAL_PRE_EDITAL,
        referenceNotice: exports.BB_REFERENCE_NOTICE,
        referenceNoticeUrl: exports.BB_REFERENCE_NOTICE_URL,
        objectiveQuestionCount: exports.BB_OBJECTIVE_QUESTION_COUNT,
        objectiveMaximumPoints: exports.BB_OBJECTIVE_MAXIMUM_POINTS,
        essayMinimumScorePercent: exports.BB_ESSAY_MINIMUM_SCORE_PERCENT,
        weeklyGoalHours: exports.BB_WEEKLY_GOAL_HOURS,
        weeklyScheduledMinutes: exports.BB_REFERENCE_SCHEDULED_MINUTES,
        weeklyWritingSessionGoal: exports.BB_WEEKLY_WRITING_SESSION_GOAL,
        weeklyAccuracyGoalPercent: exports.BB_WEEKLY_ACCURACY_GOAL_PERCENT,
        reviewIntervalsDays: [1, 7, 30],
        scheduleRhythm: exports.BB_REFERENCE_RHYTHM.map(block => ({ ...block })),
    };
}
function bbReferenceExamStages(planId = "bb") {
    return [
        { id: `${planId}-stage-objective`, name: "Prova objetiva", date: null, status: "planned", type: "objective", weight: 100, minimumScorePercent: null, referenceQuestionCount: 70 },
        { id: `${planId}-stage-essay`, name: "Redação", date: null, status: "planned", type: "essay", weight: 0, minimumScorePercent: 70, referenceQuestionCount: null },
    ];
}
function bbReferenceGoals(planId, at) {
    const base = { planId, createdAt: at, updatedAt: at, active: true };
    return [
        { ...base, id: `${planId}-goal-weekly-study`, name: "BB weekly study goal", metric: "study-hours", target: 19, period: "weekly" },
        { ...base, id: `${planId}-goal-weekly-accuracy`, name: "BB weekly accuracy goal", metric: "accuracy-percent", target: 80, period: "weekly" },
        { ...base, id: `${planId}-goal-weekly-writing`, name: "BB weekly writing goal", metric: "writing-sessions", target: 1, period: "weekly" },
    ];
}
function normalizeStudyActivityType(value, kind = "study") {
    const raw = String(value !== null && value !== void 0 ? value : "").trim().toUpperCase();
    const supported = ["THEORY", "VIDEO", "READING", "CUSTOM", "QUESTIONS", "FLASHCARDS", "REVIEW", "MOCK_EXAM", "WRITING"];
    if (supported.includes(raw))
        return raw;
    return kind === "questions" ? "QUESTIONS" : kind === "review" ? "REVIEW" : "THEORY";
}
function activityKindForType(type) {
    if (type === "QUESTIONS" || type === "MOCK_EXAM")
        return "questions";
    if (type === "FLASHCARDS" || type === "REVIEW")
        return "review";
    return "study";
}
function routeActivityDeepLink(state, activityId) {
    var _a, _b;
    const activity = state.activities.find(item => item.id === activityId && item.planId === state.activePlanId);
    if (!activity)
        throw new TypeError("Activity not found in the active plan");
    const activityType = normalizeStudyActivityType(activity.activityType, activity.kind);
    const base = { activityId: activity.id, activityType, subjectId: activity.subjectId, topicId: activity.topicId };
    if (activityType === "MOCK_EXAM")
        return { ...base, screen: "practice", practiceTab: "tests", focusMode: null, reviewId: null };
    if (activityType === "QUESTIONS")
        return { ...base, screen: "practice", practiceTab: "questions", focusMode: null, reviewId: null };
    if (activityType === "FLASHCARDS")
        return { ...base, screen: "practice", practiceTab: "reviews", focusMode: null, reviewId: null };
    if (activityType === "REVIEW") {
        const due = (_a = state.reviews.filter(review => review.planId === activity.planId && review.status === "due")
            .filter(review => activity.topicId ? review.topicId === activity.topicId : activity.subjectId ? review.subjectId === activity.subjectId : true)
            .sort((a, b) => a.dueAt.localeCompare(b.dueAt))[0]) !== null && _a !== void 0 ? _a : null;
        return { ...base, screen: "practice", practiceTab: "reviews", focusMode: null, reviewId: (_b = due === null || due === void 0 ? void 0 : due.id) !== null && _b !== void 0 ? _b : null };
    }
    if (activityType === "WRITING")
        return { ...base, screen: "today", practiceTab: null, focusMode: "writing", reviewId: null };
    return { ...base, screen: "today", practiceTab: null, focusMode: "focus", reviewId: null };
}
const WRITING_PREFIX = "studyflowWritingV1=";
const defaultChecklist = () => ({ taskReviewed: false, structureReviewed: false, cohesionReviewed: false, languageReviewed: false });
function normalizeWritingChecklist(input) { return { taskReviewed: Boolean(input === null || input === void 0 ? void 0 : input.taskReviewed), structureReviewed: Boolean(input === null || input === void 0 ? void 0 : input.structureReviewed), cohesionReviewed: Boolean(input === null || input === void 0 ? void 0 : input.cohesionReviewed), languageReviewed: Boolean(input === null || input === void 0 ? void 0 : input.languageReviewed) }; }
;
function writingChecklistComplete(checklist) { return checklist.taskReviewed && checklist.structureReviewed && checklist.cohesionReviewed && checklist.languageReviewed; }
function normalizeWritingRubric(input) {
    const out = { themeAndTask: Number(input.themeAndTask), textType: Number(input.textType), cohesion: Number(input.cohesion), argumentation: Number(input.argumentation), standardLanguage: Number(input.standardLanguage) };
    for (const value of Object.values(out))
        if (!Number.isInteger(value) || value < 0 || value > 20)
            throw new TypeError("Writing rubric dimensions must be integers from 0 to 20");
    return out;
}
function writingRubricTotal(rubric) { return rubric.themeAndTask + rubric.textType + rubric.cohesion + rubric.argumentation + rubric.standardLanguage; }
function createWritingSessionRecord(input) {
    var _a, _b, _c;
    const prompt = String((_a = input.prompt) !== null && _a !== void 0 ? _a : "").trim();
    if (!prompt)
        throw new TypeError("Writing prompt cannot be blank");
    return { version: 1, prompt, draftText: String((_b = input.draftText) !== null && _b !== void 0 ? _b : ""), rubric: input.rubric ? normalizeWritingRubric(input.rubric) : null, checklist: normalizeWritingChecklist((_c = input.checklist) !== null && _c !== void 0 ? _c : defaultChecklist()) };
}
function encodeWritingSessionRecord(record) { return WRITING_PREFIX + JSON.stringify(createWritingSessionRecord(record)); }
function decodeWritingSessionRecord(note) {
    if (!String(note !== null && note !== void 0 ? note : "").startsWith(WRITING_PREFIX))
        return null;
    try {
        const raw = JSON.parse(String(note).slice(WRITING_PREFIX.length));
        if ((raw === null || raw === void 0 ? void 0 : raw.version) !== 1)
            return null;
        return createWritingSessionRecord(raw);
    }
    catch {
        return null;
    }
}
function localWeekBounds(at) { const d = at instanceof Date ? new Date(at) : new Date(at); if (!Number.isFinite(d.getTime()))
    throw new TypeError("Invalid writing progress timestamp"); const day = (d.getDay() + 6) % 7; const start = new Date(d.getFullYear(), d.getMonth(), d.getDate() - day, 0, 0, 0, 0).getTime(); return [start, start + 7 * 86400000]; }
function deriveWritingWeeklyProgress(state, at = new Date()) {
    var _a, _b, _c, _d;
    if (!state.activePlanId)
        return { completed: 0, target: null, reached: false };
    const [start, end] = localWeekBounds(at);
    const completed = state.sessions.filter(session => session.planId === state.activePlanId && session.status === "completed" && decodeWritingSessionRecord(session.note) != null && session.endedAt != null).filter(session => { const ms = Date.parse(session.endedAt); return ms >= start && ms < end; }).length;
    const goal = state.goals.find(item => item.planId === state.activePlanId && item.active && item.metric === "writing-sessions" && item.period === "weekly");
    const target = goal ? Math.max(1, Math.round(goal.target)) : (_d = (_c = (_b = (_a = state.plans.find(plan => plan.id === state.activePlanId)) === null || _a === void 0 ? void 0 : _a.configuration.publicExam) === null || _b === void 0 ? void 0 : _b.bbReferencePreset) === null || _c === void 0 ? void 0 : _c.weeklyWritingSessionGoal) !== null && _d !== void 0 ? _d : null;
    return { completed, target, reached: target != null && completed >= target };
}
function activeWritingSession(state) {
    var _a, _b, _c;
    const activityId = (_b = (_a = state.focus) === null || _a === void 0 ? void 0 : _a.activityId) !== null && _b !== void 0 ? _b : null;
    if (!activityId)
        return null;
    const activity = state.activities.find(item => item.id === activityId);
    if (!activity || normalizeStudyActivityType(activity.activityType, activity.kind) !== "WRITING")
        return null;
    return (_c = state.sessions.filter(session => session.planId === state.activePlanId && session.activityId === activityId && (session.status === "running" || session.status === "paused")).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0]) !== null && _c !== void 0 ? _c : null;
}
function writingActivity(activity) { return Boolean(activity && normalizeStudyActivityType(activity.activityType, activity.kind) === "WRITING"); }
