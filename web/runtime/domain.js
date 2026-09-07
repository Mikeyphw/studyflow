// TypeScript migration source. Public contracts are declared in types.ts.
// @ts-nocheck -- legacy internals preserved byte-for-byte for W14 behavioral parity; new/changed code is typed.
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.GOAL_METRICS = exports.ERROR_CATEGORIES = exports.FOCUS_STATUSES = exports.ACTIVITY_STATUSES = exports.ACTIVITY_KINDS = exports.STUDY_MODES = exports.ENTITY_COLLECTIONS = exports.LEGACY_STATE_SCHEMA_VERSIONS = exports.STATE_SCHEMA_VERSION = void 0;
exports.createEntityId = createEntityId;
exports.createPlan = createPlan;
exports.createSubject = createSubject;
exports.createTopic = createTopic;
exports.createActivity = createActivity;
exports.createStudySession = createStudySession;
exports.createGoal = createGoal;
exports.createMaterial = createMaterial;
exports.createReview = createReview;
exports.createQuestion = createQuestion;
exports.createEvidence = createEvidence;
exports.createErrorEntry = createErrorEntry;
exports.gradeQuestion = gradeQuestion;
exports.buildSchedulePreview = buildSchedulePreview;
exports.createStudyCycleState = createStudyCycleState;
exports.selectCurrentCycleSubject = selectCurrentCycleSubject;
exports.createFocusState = createFocusState;
exports.focusRemainingMs = focusRemainingMs;
exports.migrateStudyState = migrateStudyState;
exports.createEmptyStudyState = createEmptyStudyState;
exports.createInitialStudyState = createInitialStudyState;
exports.validateStudyState = validateStudyState;
exports.selectActivePlan = selectActivePlan;
exports.selectAllSubjectsForPlan = selectAllSubjectsForPlan;
exports.selectSubjectsForPlan = selectSubjectsForPlan;
exports.selectAllTopicsForSubject = selectAllTopicsForSubject;
exports.selectTopicChildren = selectTopicChildren;
exports.topicDepth = topicDepth;
exports.buildTopicPath = buildTopicPath;
exports.selectTopicsForSubject = selectTopicsForSubject;
exports.selectActivitiesForQueue = selectActivitiesForQueue;
exports.selectOverdueReviews = selectOverdueReviews;
exports.recommendNextActivity = recommendNextActivity;
exports.derivePlanInventory = derivePlanInventory;
exports.deriveProgressMetrics = deriveProgressMetrics;
exports.goalMetricValue = goalMetricValue;
exports.deriveGoalProgress = deriveGoalProgress;
exports.buildEvidenceTimeline = buildEvidenceTimeline;
exports.buildContentIntegrityReport = buildContentIntegrityReport;
exports.selectDueReviews = selectDueReviews;
exports.selectOpenErrors = selectOpenErrors;
exports.deriveQuestionStats = deriveQuestionStats;
const modes_1 = require("./modes");
exports.STATE_SCHEMA_VERSION = 6;
exports.LEGACY_STATE_SCHEMA_VERSIONS = Object.freeze([1, 2, 3, 4, 5]);
exports.ENTITY_COLLECTIONS = Object.freeze([
    "plans",
    "subjects",
    "topics",
    "activities",
    "sessions",
    "goals",
    "materials",
    "reviews",
    "questions",
    "evidence",
    "errorEntries",
]);
exports.STUDY_MODES = Object.freeze([
    "EXAM_PLAN",
    "PROGRAMMING_COURSE",
    "LANGUAGE_DECK",
    "READING_STUDY",
    "GENERAL_SKILL",
]);
exports.ACTIVITY_KINDS = Object.freeze(["study", "questions", "review"]);
exports.ACTIVITY_STATUSES = Object.freeze(["planned", "completed", "cancelled"]);
exports.FOCUS_STATUSES = Object.freeze(["idle", "running", "paused"]);
exports.ERROR_CATEGORIES = Object.freeze([
    "content-gap", "forgotten", "concept-confusion", "interpretation",
    "distraction", "calculation", "time-pressure", "disputed", "other",
]);
const ID_PREFIXES = Object.freeze({
    plan: "plan",
    subject: "subject",
    topic: "topic",
    activity: "activity",
    session: "session",
    goal: "goal",
    material: "material",
    review: "review",
    question: "question",
    evidence: "evidence",
    errorEntry: "error",
});
function requireText(value, field) {
    const text = String(value ?? "").trim();
    if (!text)
        throw new TypeError(`${field} is required`);
    return text;
}
function optionalText(value) {
    const text = String(value ?? "").trim();
    return text || null;
}
function optionalId(value) {
    return optionalText(value);
}
function requirePositiveInteger(value, field, { min = 1, max = Number.MAX_SAFE_INTEGER } = {}) {
    const number = Number(value);
    if (!Number.isInteger(number) || number < min || number > max) {
        throw new TypeError(`${field} must be an integer from ${min} to ${max}`);
    }
    return number;
}
function requireOneOf(value, allowed, field) {
    const text = requireText(value, field);
    if (!allowed.includes(text)) {
        throw new TypeError(`${field} must be one of: ${allowed.join(", ")}`);
    }
    return text;
}
function isoNow() {
    return new Date().toISOString();
}
function fallbackUuid() {
    const random = Math.random().toString(36).slice(2, 12);
    return `${Date.now().toString(36)}-${random}`;
}
function createEntityId(kind, uuidFactory = null) {
    const prefix = ID_PREFIXES[kind];
    if (!prefix)
        throw new TypeError(`Unknown entity kind: ${kind}`);
    const factory = uuidFactory ?? (() => globalThis.crypto?.randomUUID?.() ?? fallbackUuid());
    return `${prefix}-${String(factory())}`;
}
function entityBase(kind, input = {}) {
    return {
        id: optionalId(input.id) ?? createEntityId(kind, input.uuidFactory),
        createdAt: optionalText(input.createdAt) ?? isoNow(),
        updatedAt: optionalText(input.updatedAt) ?? optionalText(input.createdAt) ?? isoNow(),
    };
}
function createPlan(input = {}) {
    const mode = requireOneOf(input.mode ?? "GENERAL_SKILL", exports.STUDY_MODES, "plan.mode");
    const configuration = (0, modes_1.normalizePlanConfiguration)(input.configuration ?? {}, mode);
    const configErrors = (0, modes_1.validatePlanConfiguration)(configuration, mode);
    if (configErrors.length)
        throw new TypeError(`plan.configuration is invalid: ${configErrors[0]}`);
    const configuredExamDate = mode === "EXAM_PLAN" ? configuration.publicExam?.target.examDate ?? null : null;
    return {
        ...entityBase("plan", input),
        name: requireText(input.name, "plan.name"),
        mode,
        status: requireOneOf(input.status ?? "active", ["active", "archived"], "plan.status"),
        examDate: optionalText(input.examDate) ?? configuredExamDate,
        configuration,
    };
}
function createSubject(input = {}) {
    const weight = input.weight == null ? null : Number(input.weight);
    if (weight != null && (!Number.isFinite(weight) || weight < 0)) {
        throw new TypeError("subject.weight must be null or a non-negative number");
    }
    return {
        ...entityBase("subject", input),
        planId: requireText(input.planId, "subject.planId"),
        name: requireText(input.name, "subject.name"),
        weight,
        order: Number.isInteger(Number(input.order)) ? Math.max(0, Number(input.order)) : 0,
        notes: optionalText(input.notes),
        archived: Boolean(input.archived),
    };
}
function createTopic(input = {}) {
    return {
        ...entityBase("topic", input),
        planId: requireText(input.planId, "topic.planId"),
        subjectId: requireText(input.subjectId, "topic.subjectId"),
        parentTopicId: optionalId(input.parentTopicId),
        name: requireText(input.name, "topic.name"),
        order: Number.isInteger(Number(input.order)) ? Math.max(0, Number(input.order)) : 0,
        priority: requireOneOf(input.priority ?? "normal", ["low", "normal", "high"], "topic.priority"),
        status: requireOneOf(input.status ?? "not-started", ["not-started", "learning", "reviewing", "mastered", "archived"], "topic.status"),
        notes: optionalText(input.notes),
    };
}
function createActivity(input = {}) {
    return {
        ...entityBase("activity", input),
        planId: requireText(input.planId, "activity.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        title: requireText(input.title, "activity.title"),
        kind: requireOneOf(input.kind ?? "study", exports.ACTIVITY_KINDS, "activity.kind"),
        durationMinutes: requirePositiveInteger(input.durationMinutes ?? 25, "activity.durationMinutes", { min: 5, max: 240 }),
        status: requireOneOf(input.status ?? "planned", exports.ACTIVITY_STATUSES, "activity.status"),
        origin: requireOneOf(input.origin ?? "manual", ["manual", "seed", "cycle", "review", "import", "schedule"], "activity.origin"),
        queue: requireOneOf(input.queue ?? "today", ["today", "future", "backlog"], "activity.queue"),
        scheduledFor: optionalText(input.scheduledFor),
    };
}
function createStudySession(input = {}) {
    const durationMinutes = input.durationMinutes == null
        ? null
        : requirePositiveInteger(input.durationMinutes, "session.durationMinutes", { min: 1, max: 24 * 60 });
    return {
        ...entityBase("session", input),
        planId: requireText(input.planId, "session.planId"),
        activityId: optionalId(input.activityId),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        startedAt: requireText(input.startedAt, "session.startedAt"),
        endedAt: optionalText(input.endedAt),
        durationMinutes,
        status: requireOneOf(input.status ?? "completed", ["running", "paused", "completed", "cancelled"], "session.status"),
        note: optionalText(input.note),
    };
}
function createGoal(input = {}) {
    const target = Number(input.target);
    if (!Number.isFinite(target))
        throw new TypeError("goal.target must be a finite number");
    return {
        ...entityBase("goal", input),
        planId: requireText(input.planId, "goal.planId"),
        name: requireText(input.name, "goal.name"),
        metric: requireText(input.metric, "goal.metric"),
        target,
        period: requireOneOf(input.period ?? "weekly", ["daily", "weekly", "monthly", "plan"], "goal.period"),
        active: input.active !== false,
    };
}
function createMaterial(input = {}) {
    const sizeBytes = input.sizeBytes == null ? null : Number(input.sizeBytes);
    if (sizeBytes != null && (!Number.isInteger(sizeBytes) || sizeBytes < 0))
        throw new TypeError("material.sizeBytes must be null or a non-negative integer");
    return {
        ...entityBase("material", input),
        planId: requireText(input.planId, "material.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        title: requireText(input.title, "material.title"),
        kind: requireOneOf(input.kind ?? "note", ["note", "file", "link", "book", "legislation"], "material.kind"),
        locator: optionalText(input.locator),
        mimeType: optionalText(input.mimeType),
        attachmentId: optionalId(input.attachmentId),
        sizeBytes,
    };
}
function createReview(input = {}) {
    const intervalDays = input.intervalDays == null ? null : requirePositiveInteger(input.intervalDays, "review.intervalDays", { min: 1, max: 365 });
    const intervalIndex = Number(input.intervalIndex ?? 0);
    const repetitionCount = Number(input.repetitionCount ?? 0);
    if (!Number.isInteger(intervalIndex) || intervalIndex < 0 || intervalIndex > 7)
        throw new TypeError("review.intervalIndex must be an integer from 0 to 7");
    if (!Number.isInteger(repetitionCount) || repetitionCount < 0 || repetitionCount > 9999)
        throw new TypeError("review.repetitionCount must be an integer from 0 to 9999");
    return {
        ...entityBase("review", input),
        planId: requireText(input.planId, "review.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        dueAt: requireText(input.dueAt, "review.dueAt"),
        status: requireOneOf(input.status ?? "due", ["due", "completed", "dismissed"], "review.status"),
        sourceType: optionalText(input.sourceType),
        sourceId: optionalId(input.sourceId),
        intervalIndex,
        intervalDays,
        repetitionCount,
        previousReviewId: optionalId(input.previousReviewId),
        completedAt: optionalText(input.completedAt),
    };
}
function createQuestion(input = {}) {
    const choices = Array.isArray(input.choices) ? input.choices.map((choice) => String(choice)) : [];
    return {
        ...entityBase("question", input),
        planId: requireText(input.planId, "question.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        prompt: requireText(input.prompt, "question.prompt"),
        choices,
        answer: optionalText(input.answer),
        source: optionalText(input.source),
        explanation: optionalText(input.explanation),
        disputed: Boolean(input.disputed),
        ...(0, modes_1.normalizeQuestionMetadata)(input),
    };
}
function createEvidence(input = {}) {
    return {
        ...entityBase("evidence", input),
        planId: requireText(input.planId, "evidence.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        activityId: optionalId(input.activityId),
        sessionId: optionalId(input.sessionId),
        questionId: optionalId(input.questionId),
        kind: requireText(input.kind, "evidence.kind"),
        observedAt: requireText(input.observedAt, "evidence.observedAt"),
        value: input.value ?? null,
        provenance: requireOneOf(input.provenance ?? "user", ["user", "session", "question", "import", "derived"], "evidence.provenance"),
    };
}
function createErrorEntry(input = {}) {
    return {
        ...entityBase("errorEntry", input),
        planId: requireText(input.planId, "errorEntry.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        questionId: optionalId(input.questionId),
        reviewId: optionalId(input.reviewId),
        category: requireOneOf(input.category ?? "other", exports.ERROR_CATEGORIES, "errorEntry.category"),
        note: optionalText(input.note),
        recurrenceCount: requirePositiveInteger(input.recurrenceCount ?? 1, "errorEntry.recurrenceCount", { min: 1, max: 9999 }),
        status: requireOneOf(input.status ?? "open", ["open", "resolved"], "errorEntry.status"),
        observedAt: requireText(input.observedAt, "errorEntry.observedAt"),
        resolvedAt: optionalText(input.resolvedAt),
    };
}
function dayKey(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime()))
        throw new TypeError(`Invalid date: ${value}`);
    return date.toISOString().slice(0, 10);
}
function plusDays(value, days) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime()))
        throw new TypeError(`Invalid date: ${value}`);
    date.setUTCDate(date.getUTCDate() + Number(days));
    return date.toISOString();
}
function gradeQuestion(question, answer) {
    const submitted = String(answer ?? "").trim();
    if (!submitted)
        return { gradable: Boolean(question.answer), correct: null, submitted: null, expected: question.answer ?? null };
    if (!question.answer)
        return { gradable: false, correct: null, submitted, expected: null };
    const expected = String(question.answer).trim();
    return { gradable: true, correct: submitted === expected, submitted, expected };
}
function buildSchedulePreview(state, { at = isoNow(), days = 7, maxBlocks = 7 } = {}) {
    const generatedAt = new Date(at).toISOString();
    const activePlan = selectActivePlan(state);
    if (!activePlan)
        return { baseRevision: state.revision, generatedAt, operations: [], summary: { add: 0, move: 0 }, reasons: ["No active plan"] };
    const today = dayKey(generatedAt);
    const operations = [];
    const reasons = [];
    const planned = state.activities.filter((activity) => activity.planId === activePlan.id && activity.status === "planned");
    const overdueScheduled = planned
        .filter((activity) => activity.scheduledFor && activity.scheduledFor < today)
        .sort((a, b) => a.scheduledFor.localeCompare(b.scheduledFor));
    for (let i = 0; i < Math.min(overdueScheduled.length, maxBlocks); i += 1) {
        const activity = overdueScheduled[i];
        operations.push({
            action: "move",
            activityId: activity.id,
            from: activity.scheduledFor,
            to: dayKey(plusDays(generatedAt, i)),
            reason: "Overdue planned activity; history remains unchanged",
        });
    }
    const remainingSlots = Math.max(0, maxBlocks - operations.length);
    const existingKeys = new Set(planned.map((activity) => `${activity.kind}|${activity.subjectId ?? ""}|${activity.topicId ?? ""}|${activity.scheduledFor ?? ""}`));
    const dueReviews = selectOverdueReviews(state, generatedAt, activePlan.id);
    const cycleSubjects = selectSubjectsForPlan(state, activePlan.id);
    let slot = 0;
    const candidates = [];
    for (const review of dueReviews) {
        candidates.push({
            title: state.topics.find((topic) => topic.id === review.topicId)?.name ?? "Due review",
            kind: "review", durationMinutes: 20, subjectId: review.subjectId, topicId: review.topicId, origin: "review",
            reason: "Overdue review backed by a persisted review record",
        });
    }
    for (const subject of cycleSubjects) {
        const topic = selectTopicsForSubject(state, subject.id).sort((a, b) => (a.priority === "high" ? -1 : 0) - (b.priority === "high" ? -1 : 0))[0] ?? null;
        candidates.push({
            title: topic ? `${subject.name}: ${topic.name}` : subject.name,
            kind: "study", durationMinutes: 40, subjectId: subject.id, topicId: topic?.id ?? null, origin: "cycle",
            reason: topic?.priority === "high" ? "Study-cycle subject with a high-priority topic" : "Study-cycle subject",
        });
    }
    for (const candidate of candidates) {
        if (slot >= remainingSlots)
            break;
        const scheduledFor = dayKey(plusDays(generatedAt, Math.min(days - 1, slot)));
        const key = `${candidate.kind}|${candidate.subjectId ?? ""}|${candidate.topicId ?? ""}|${scheduledFor}`;
        if (existingKeys.has(key))
            continue;
        operations.push({ action: "add", scheduledFor, activity: { ...candidate, queue: scheduledFor === today ? "today" : "future" } });
        existingKeys.add(key);
        slot += 1;
    }
    if (!operations.length)
        reasons.push("No schedule changes are justified by current local plan data.");
    return {
        baseRevision: state.revision, generatedAt, operations,
        summary: { add: operations.filter((item) => item.action === "add").length, move: operations.filter((item) => item.action === "move").length },
        reasons,
    };
}
function createStudyCycleState(input = {}) {
    const subjectIds = Array.isArray(input.subjectIds) ? input.subjectIds.map((value) => requireText(value, "studyCycle.subjectId")) : [];
    const cursor = subjectIds.length ? Math.max(0, Math.min(subjectIds.length - 1, Number.isInteger(Number(input.cursor)) ? Number(input.cursor) : 0)) : 0;
    return { subjectIds, cursor };
}
function selectCurrentCycleSubject(state) {
    const cycle = createStudyCycleState(state.studyCycle ?? {});
    if (!cycle.subjectIds.length)
        return null;
    return state.subjects.find((subject) => subject.id === cycle.subjectIds[cycle.cursor] && !subject.archived) ?? null;
}
function createFocusState(input = {}) {
    const durationMinutes = requirePositiveInteger(input.durationMinutes ?? 25, "focus.durationMinutes", { min: 5, max: 240 });
    const remainingMs = input.remainingMs == null ? durationMinutes * 60000 : Number(input.remainingMs);
    if (!Number.isFinite(remainingMs) || remainingMs < 0 || remainingMs > durationMinutes * 60000) {
        throw new TypeError("focus.remainingMs must be within the configured duration");
    }
    const elapsedMs = input.elapsedMs == null ? 0 : Number(input.elapsedMs);
    if (!Number.isFinite(elapsedMs) || elapsedMs < 0)
        throw new TypeError("focus.elapsedMs must be non-negative");
    return {
        status: requireOneOf(input.status ?? "idle", exports.FOCUS_STATUSES, "focus.status"),
        activityId: optionalId(input.activityId),
        durationMinutes,
        remainingMs: Math.round(remainingMs),
        elapsedMs: Math.round(elapsedMs),
        startedAt: optionalText(input.startedAt),
        lastResumedAt: optionalText(input.lastResumedAt),
        endsAt: optionalText(input.endsAt),
        note: optionalText(input.note),
    };
}
function focusRemainingMs(focus, at = Date.now()) {
    const current = createFocusState(focus ?? {});
    if (current.status !== "running" || !current.endsAt)
        return current.remainingMs;
    const atMs = typeof at === "number" ? at : Date.parse(String(at));
    const endsAtMs = Date.parse(current.endsAt);
    if (!Number.isFinite(atMs) || !Number.isFinite(endsAtMs))
        return current.remainingMs;
    return Math.max(0, endsAtMs - atMs);
}
function migrateStudyState(input, { now = isoNow() } = {}) {
    if (!input || typeof input !== "object" || Array.isArray(input))
        return input;
    if (![1, 2, 3, 4, 5, exports.STATE_SCHEMA_VERSION].includes(input.schemaVersion))
        return input;
    const state = JSON.parse(JSON.stringify(input));
    const originalVersion = state.schemaVersion;
    state.schemaVersion = exports.STATE_SCHEMA_VERSION;
    state.plans = Array.isArray(state.plans) ? state.plans.map((plan) => createPlan(plan)) : [];
    state.focus = createFocusState(state.focus ?? {});
    state.subjects = Array.isArray(state.subjects) ? state.subjects.map((subject, index) => createSubject({ ...subject, order: Number.isInteger(subject.order) ? subject.order : index, notes: subject.notes ?? null })) : [];
    state.topics = Array.isArray(state.topics) ? state.topics.map((topic, index) => createTopic({ ...topic, parentTopicId: topic.parentTopicId ?? null, order: Number.isInteger(topic.order) ? topic.order : index, notes: topic.notes ?? null })) : [];
    state.errorEntries = Array.isArray(state.errorEntries) ? state.errorEntries : [];
    state.materials = Array.isArray(state.materials) ? state.materials.map((material) => ({ ...material, attachmentId: material.attachmentId ?? null, sizeBytes: material.sizeBytes ?? null })) : [];
    state.reviews = Array.isArray(state.reviews) ? state.reviews.map((review) => createReview(review)) : [];
    state.questions = Array.isArray(state.questions) ? state.questions.map((question) => createQuestion(question)) : [];
    const cycles = {};
    const savedCycles = state.studyCycles && typeof state.studyCycles === "object" && !Array.isArray(state.studyCycles) ? state.studyCycles : {};
    for (const plan of state.plans) {
        const allowed = state.subjects.filter((subject) => subject.planId === plan.id && !subject.archived).map((subject) => subject.id);
        const source = savedCycles[plan.id] ?? (plan.id === state.activePlanId ? state.studyCycle : null) ?? { subjectIds: allowed, cursor: 0 };
        const sourceCycle = createStudyCycleState(source);
        const filtered = sourceCycle.subjectIds.filter((subjectId) => allowed.includes(subjectId));
        const subjectIds = filtered.length ? filtered : allowed;
        cycles[plan.id] = createStudyCycleState({ subjectIds, cursor: sourceCycle.cursor });
    }
    state.studyCycles = cycles;
    state.studyCycle = state.activePlanId && cycles[state.activePlanId] ? createStudyCycleState(cycles[state.activePlanId]) : createStudyCycleState();
    if (originalVersion !== exports.STATE_SCHEMA_VERSION)
        state.migratedAt = now;
    return state;
}
function createEmptyStudyState() {
    return {
        schemaVersion: exports.STATE_SCHEMA_VERSION,
        revision: 0,
        activePlanId: null,
        focus: createFocusState(),
        studyCycle: createStudyCycleState(),
        studyCycles: {},
        plans: [],
        subjects: [],
        topics: [],
        activities: [],
        sessions: [],
        goals: [],
        materials: [],
        reviews: [],
        questions: [],
        evidence: [],
        errorEntries: [],
    };
}
function createInitialStudyState({ now = isoNow() } = {}) {
    const planId = "plan-local-default";
    const constitutionalId = "subject-constitutional-law";
    const portugueseId = "subject-portuguese";
    const bankingId = "subject-banking-knowledge";
    const state = createEmptyStudyState();
    state.activePlanId = planId;
    state.plans = [createPlan({
            id: planId,
            name: "General Study Plan",
            mode: "EXAM_PLAN",
            status: "active",
            createdAt: now,
            updatedAt: now,
        })];
    state.subjects = [
        createSubject({ id: constitutionalId, planId, name: "Constitutional Law", weight: null, order: 0, createdAt: now, updatedAt: now }),
        createSubject({ id: portugueseId, planId, name: "Portuguese", weight: null, order: 1, createdAt: now, updatedAt: now }),
        createSubject({ id: bankingId, planId, name: "Banking Knowledge", weight: null, order: 2, createdAt: now, updatedAt: now }),
    ];
    state.studyCycle = createStudyCycleState({ subjectIds: state.subjects.map((subject) => subject.id), cursor: 0 });
    state.studyCycles = { [planId]: createStudyCycleState(state.studyCycle) };
    state.topics = [
        createTopic({ id: "topic-fundamental-rights", planId, order: 0, subjectId: constitutionalId, name: "Fundamental rights", priority: "high", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-public-administration", planId, order: 1, subjectId: constitutionalId, name: "Public administration", priority: "normal", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-constitutional-remedies", planId, order: 2, subjectId: constitutionalId, name: "Constitutional remedies", priority: "high", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-text-interpretation", planId, order: 3, subjectId: portugueseId, name: "Text interpretation", priority: "normal", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-grammar", planId, order: 4, subjectId: portugueseId, name: "Grammar", priority: "normal", createdAt: now, updatedAt: now }),
    ];
    state.activities = [
        createActivity({ id: "activity-seed-admin-review", planId, title: "Administrative Law review", kind: "review", durationMinutes: 20, origin: "seed", createdAt: now, updatedAt: now }),
        createActivity({ id: "activity-seed-portuguese-questions", planId, subjectId: portugueseId, title: "Portuguese questions", kind: "questions", durationMinutes: 30, origin: "seed", createdAt: now, updatedAt: now }),
        createActivity({ id: "activity-seed-banking-study", planId, subjectId: bankingId, title: "Banking Knowledge study-cycle block", kind: "study", durationMinutes: 40, origin: "seed", createdAt: now, updatedAt: now }),
    ];
    state.questions = [createQuestion({
            id: "question-seed-html-main", planId, subjectId: portugueseId, topicId: "topic-text-interpretation",
            prompt: "Which HTML element represents the primary content of a page?",
            choices: ["header", "main", "aside"], answer: "main",
            explanation: "The main element represents the document's primary content.", source: "StudyFlow sample",
            createdAt: now, updatedAt: now,
        })];
    state.reviews = [createReview({
            id: "review-seed-fundamental-rights", planId, subjectId: constitutionalId, topicId: "topic-fundamental-rights",
            dueAt: now, status: "due", sourceType: "topic", sourceId: "topic-fundamental-rights", createdAt: now, updatedAt: now,
        })];
    return state;
}
function idsFor(collection) {
    return new Set(collection.map((entity) => entity.id));
}
function validateCollectionShape(state, errors) {
    for (const key of exports.ENTITY_COLLECTIONS) {
        if (!Array.isArray(state[key]))
            errors.push(`${key} must be an array`);
    }
}
function requireReference(errors, entity, field, ids, label) {
    const value = entity[field];
    if (value != null && !ids.has(value)) {
        errors.push(`${label} ${entity.id} references missing ${field}: ${value}`);
    }
}
function validateEntityContracts(state, errors) {
    const constructors = {
        plans: createPlan,
        subjects: createSubject,
        topics: createTopic,
        activities: createActivity,
        sessions: createStudySession,
        goals: createGoal,
        materials: createMaterial,
        reviews: createReview,
        questions: createQuestion,
        evidence: createEvidence,
        errorEntries: createErrorEntry,
    };
    for (const [collectionName, create] of Object.entries(constructors)) {
        for (const entity of state[collectionName]) {
            if (!entity || typeof entity !== "object" || Array.isArray(entity) || !String(entity.id ?? "").trim())
                continue;
            try {
                create(entity);
            }
            catch (error) {
                const message = error instanceof Error ? error.message : String(error);
                errors.push(`${collectionName} ${entity.id} is invalid: ${message}`);
            }
        }
    }
}
function validateStudyState(state) {
    const errors = [];
    if (!state || typeof state !== "object" || Array.isArray(state))
        return ["state must be an object"];
    if (state.schemaVersion !== exports.STATE_SCHEMA_VERSION) {
        errors.push(`unsupported state schemaVersion: ${String(state.schemaVersion)}`);
    }
    if (!Number.isInteger(state.revision) || state.revision < 0)
        errors.push("revision must be a non-negative integer");
    try {
        const focus = createFocusState(state.focus ?? {});
        if (focus.status === "running" && (!focus.startedAt || !focus.lastResumedAt || !focus.endsAt))
            errors.push("running focus requires startedAt, lastResumedAt and endsAt");
        if (focus.status === "paused" && (!focus.startedAt || focus.endsAt != null))
            errors.push("paused focus requires startedAt and no endsAt");
    }
    catch (error) {
        errors.push(`focus is invalid: ${error instanceof Error ? error.message : String(error)}`);
    }
    try {
        createStudyCycleState(state.studyCycle ?? {});
    }
    catch (error) {
        errors.push(`studyCycle is invalid: ${error instanceof Error ? error.message : String(error)}`);
    }
    if (!state.studyCycles || typeof state.studyCycles !== "object" || Array.isArray(state.studyCycles))
        errors.push("studyCycles must be an object");
    validateCollectionShape(state, errors);
    if (errors.some((error) => error.endsWith("must be an array")))
        return errors;
    const seen = new Set();
    for (const key of exports.ENTITY_COLLECTIONS) {
        for (const entity of state[key]) {
            if (!entity || typeof entity !== "object" || Array.isArray(entity)) {
                errors.push(`${key} contains a non-object entity`);
                continue;
            }
            if (!String(entity.id ?? "").trim()) {
                errors.push(`${key} contains an entity without an id`);
                continue;
            }
            if (seen.has(entity.id))
                errors.push(`duplicate entity id: ${entity.id}`);
            seen.add(entity.id);
        }
    }
    validateEntityContracts(state, errors);
    const planIds = idsFor(state.plans);
    const subjectIds = idsFor(state.subjects);
    const topicIds = idsFor(state.topics);
    const activityIds = idsFor(state.activities);
    const sessionIds = idsFor(state.sessions);
    const questionIds = idsFor(state.questions);
    const reviewIds = idsFor(state.reviews);
    if (state.activePlanId != null && !planIds.has(state.activePlanId)) {
        errors.push(`activePlanId references missing plan: ${state.activePlanId}`);
    }
    const activePlan = state.activePlanId == null ? null : state.plans.find((plan) => plan.id === state.activePlanId);
    if (activePlan?.status === "archived")
        errors.push(`activePlanId references archived plan: ${state.activePlanId}`);
    if (state.studyCycles && typeof state.studyCycles === "object" && !Array.isArray(state.studyCycles)) {
        for (const [planId, rawCycle] of Object.entries(state.studyCycles)) {
            if (!planIds.has(planId)) {
                errors.push(`studyCycles contains missing plan: ${planId}`);
                continue;
            }
            try {
                const cycle = createStudyCycleState(rawCycle);
                for (const subjectId of cycle.subjectIds) {
                    const subject = state.subjects.find((candidate) => candidate.id === subjectId);
                    if (!subject)
                        errors.push(`studyCycles ${planId} references missing subjectId: ${subjectId}`);
                    else if (subject.planId !== planId)
                        errors.push(`studyCycles ${planId} references subject from another plan: ${subjectId}`);
                }
            }
            catch (error) {
                errors.push(`studyCycles ${planId} is invalid: ${error instanceof Error ? error.message : String(error)}`);
            }
        }
        for (const plan of state.plans)
            if (!(plan.id in state.studyCycles))
                errors.push(`studyCycles is missing plan: ${plan.id}`);
        if (state.activePlanId && state.studyCycles[state.activePlanId]) {
            const canonical = createStudyCycleState(state.studyCycles[state.activePlanId]);
            const activeCycle = createStudyCycleState(state.studyCycle ?? {});
            if (JSON.stringify(canonical) !== JSON.stringify(activeCycle))
                errors.push("studyCycle must match the active plan's studyCycles entry");
        }
    }
    if (state.focus?.activityId != null && !activityIds.has(state.focus.activityId)) {
        errors.push(`focus references missing activityId: ${state.focus.activityId}`);
    }
    for (const subjectId of state.studyCycle?.subjectIds ?? []) {
        const subject = state.subjects.find((candidate) => candidate.id === subjectId);
        if (!subject)
            errors.push(`studyCycle references missing subjectId: ${subjectId}`);
        else if (state.activePlanId && subject.planId !== state.activePlanId)
            errors.push(`studyCycle subject ${subjectId} does not belong to active plan`);
    }
    for (const subject of state.subjects)
        requireReference(errors, subject, "planId", planIds, "subject");
    for (const topic of state.topics) {
        requireReference(errors, topic, "planId", planIds, "topic");
        requireReference(errors, topic, "subjectId", subjectIds, "topic");
        const subject = state.subjects.find((candidate) => candidate.id === topic.subjectId);
        if (subject && subject.planId !== topic.planId)
            errors.push(`topic ${topic.id} planId does not match its subject`);
        if (topic.parentTopicId != null) {
            requireReference(errors, topic, "parentTopicId", topicIds, "topic");
            const parent = state.topics.find((candidate) => candidate.id === topic.parentTopicId);
            if (parent && parent.planId !== topic.planId)
                errors.push(`topic ${topic.id} parentTopicId belongs to another plan`);
            if (parent && parent.subjectId !== topic.subjectId)
                errors.push(`topic ${topic.id} parentTopicId belongs to another subject`);
            if (topic.parentTopicId === topic.id)
                errors.push(`topic ${topic.id} cannot parent itself`);
        }
    }
    for (const topic of state.topics) {
        const visited = new Set([topic.id]);
        let parentId = topic.parentTopicId;
        while (parentId != null) {
            if (visited.has(parentId)) {
                errors.push(`topic hierarchy cycle detected at ${topic.id}`);
                break;
            }
            visited.add(parentId);
            const parent = state.topics.find((candidate) => candidate.id === parentId);
            if (!parent)
                break;
            parentId = parent.parentTopicId;
        }
    }
    for (const [label, collection] of [
        ["activity", state.activities], ["session", state.sessions], ["goal", state.goals],
        ["material", state.materials], ["review", state.reviews], ["question", state.questions], ["evidence", state.evidence], ["errorEntry", state.errorEntries],
    ]) {
        for (const entity of collection) {
            requireReference(errors, entity, "planId", planIds, label);
            if ("subjectId" in entity)
                requireReference(errors, entity, "subjectId", subjectIds, label);
            if ("topicId" in entity)
                requireReference(errors, entity, "topicId", topicIds, label);
            const subject = entity.subjectId == null ? null : state.subjects.find((candidate) => candidate.id === entity.subjectId);
            const topic = entity.topicId == null ? null : state.topics.find((candidate) => candidate.id === entity.topicId);
            if (subject && subject.planId !== entity.planId)
                errors.push(`${label} ${entity.id} planId does not match its subject`);
            if (topic && topic.planId !== entity.planId)
                errors.push(`${label} ${entity.id} planId does not match its topic`);
            if (subject && topic && topic.subjectId !== subject.id)
                errors.push(`${label} ${entity.id} topicId does not belong to its subjectId`);
        }
    }
    for (const session of state.sessions) {
        requireReference(errors, session, "activityId", activityIds, "session");
        const activity = session.activityId == null ? null : state.activities.find((candidate) => candidate.id === session.activityId);
        if (activity && activity.planId !== session.planId)
            errors.push(`session ${session.id} planId does not match its activity`);
    }
    for (const review of state.reviews) {
        if (review.sourceType === "topic" && review.sourceId != null && !topicIds.has(review.sourceId)) {
            errors.push(`review ${review.id} references missing topic sourceId: ${review.sourceId}`);
        }
        if (review.sourceType === "activity" && review.sourceId != null && !activityIds.has(review.sourceId)) {
            errors.push(`review ${review.id} references missing activity sourceId: ${review.sourceId}`);
        }
        if (review.previousReviewId != null && !reviewIds.has(review.previousReviewId)) {
            errors.push(`review ${review.id} references missing previousReviewId: ${review.previousReviewId}`);
        }
    }
    for (const errorEntry of state.errorEntries) {
        requireReference(errors, errorEntry, "questionId", questionIds, "errorEntry");
        requireReference(errors, errorEntry, "reviewId", reviewIds, "errorEntry");
        const question = errorEntry.questionId == null ? null : state.questions.find((candidate) => candidate.id === errorEntry.questionId);
        if (question && question.planId !== errorEntry.planId)
            errors.push(`errorEntry ${errorEntry.id} planId does not match its questionId`);
    }
    for (const evidence of state.evidence) {
        requireReference(errors, evidence, "activityId", activityIds, "evidence");
        requireReference(errors, evidence, "sessionId", sessionIds, "evidence");
        requireReference(errors, evidence, "questionId", questionIds, "evidence");
        for (const [field, collection] of [["activityId", state.activities], ["sessionId", state.sessions], ["questionId", state.questions]]) {
            const reference = evidence[field] == null ? null : collection.find((candidate) => candidate.id === evidence[field]);
            if (reference && reference.planId !== evidence.planId)
                errors.push(`evidence ${evidence.id} planId does not match its ${field}`);
        }
    }
    return errors;
}
function selectActivePlan(state) {
    return state.plans.find((plan) => plan.id === state.activePlanId) ?? null;
}
function selectAllSubjectsForPlan(state, planId = state.activePlanId) {
    return state.subjects
        .filter((subject) => subject.planId === planId)
        .sort((a, b) => (a.order ?? 0) - (b.order ?? 0) || a.name.localeCompare(b.name));
}
function selectSubjectsForPlan(state, planId = state.activePlanId) {
    return selectAllSubjectsForPlan(state, planId).filter((subject) => !subject.archived);
}
function selectAllTopicsForSubject(state, subjectId) {
    return state.topics
        .filter((topic) => topic.subjectId === subjectId)
        .sort((a, b) => (a.order ?? 0) - (b.order ?? 0) || a.name.localeCompare(b.name));
}
function selectTopicChildren(state, parentTopicId = null, subjectId = null) {
    return state.topics.filter((topic) => (topic.parentTopicId ?? null) === parentTopicId && (subjectId == null || topic.subjectId === subjectId)).sort((a, b) => (a.order ?? 0) - (b.order ?? 0) || a.name.localeCompare(b.name));
}
function topicDepth(state, topicId) {
    let depth = 0;
    let current = state.topics.find((topic) => topic.id === topicId) ?? null;
    const visited = new Set();
    while (current?.parentTopicId) {
        if (visited.has(current.id))
            return depth;
        visited.add(current.id);
        depth += 1;
        current = state.topics.find((topic) => topic.id === current.parentTopicId) ?? null;
    }
    return depth;
}
function buildTopicPath(state, topicId) {
    const names = [];
    let current = state.topics.find((topic) => topic.id === topicId) ?? null;
    const visited = new Set();
    while (current) {
        if (visited.has(current.id))
            break;
        visited.add(current.id);
        names.unshift(current.name);
        current = current.parentTopicId ? state.topics.find((topic) => topic.id === current.parentTopicId) ?? null : null;
    }
    return names;
}
function selectTopicsForSubject(state, subjectId) {
    return selectAllTopicsForSubject(state, subjectId).filter((topic) => topic.status !== "archived");
}
function selectActivitiesForQueue(state, queue = "today", planId = state.activePlanId) {
    return state.activities.filter((activity) => activity.planId === planId && activity.queue === queue && activity.status === "planned");
}
function selectOverdueReviews(state, at = Date.now(), planId = state.activePlanId) {
    const atMs = typeof at === "number" ? at : Date.parse(String(at));
    return state.reviews.filter((review) => review.planId === planId && review.status === "due" && Date.parse(review.dueAt) <= atMs);
}
function recommendNextActivity(state) {
    const activities = selectActivitiesForQueue(state, "today");
    if (!activities.length)
        return null;
    const kindScore = { review: 30, questions: 20, study: 10 };
    const priorityScore = { high: 8, normal: 4, low: 0 };
    const scored = activities.map((activity, index) => {
        const topic = activity.topicId ? state.topics.find((candidate) => candidate.id === activity.topicId) : null;
        const subject = activity.subjectId ? state.subjects.find((candidate) => candidate.id === activity.subjectId) : null;
        const weight = Number.isFinite(Number(subject?.weight)) ? Number(subject.weight) : 0;
        const score = (kindScore[activity.kind] ?? 0) + (priorityScore[topic?.priority] ?? 0) + Math.min(10, weight) - index / 100;
        const signals = ["Queued for today"];
        if (activity.kind === "review")
            signals.push("Review work");
        if (activity.kind === "questions")
            signals.push("Question practice");
        if (topic?.priority === "high")
            signals.push("High-priority topic");
        if (weight > 0)
            signals.push(`Subject weight ${weight}`);
        if (activity.origin === "cycle")
            signals.push("Study-cycle item");
        return { activity, score, signals };
    });
    scored.sort((a, b) => b.score - a.score);
    const winner = scored[0];
    return {
        activity: winner.activity,
        signals: winner.signals,
        reason: `Recommended from current local plan data: ${winner.signals.join(", ").toLowerCase()}.`,
    };
}
function derivePlanInventory(state, planId = state.activePlanId) {
    const subjects = selectSubjectsForPlan(state, planId);
    const subjectIds = new Set(subjects.map((subject) => subject.id));
    const topics = state.topics.filter((topic) => subjectIds.has(topic.subjectId) && topic.status !== "archived");
    const activities = state.activities.filter((activity) => activity.planId === planId && activity.status !== "cancelled");
    return activities.reduce((summary, activity) => ({ ...summary, plannedMinutes: summary.plannedMinutes + activity.durationMinutes }), { subjects: subjects.length, topics: topics.length, activities: activities.length, plannedMinutes: 0 });
}
exports.GOAL_METRICS = Object.freeze(["study-minutes", "graded-questions", "topics-covered", "reviews-completed"]);
function periodStartMs(period, atMs) {
    const date = new Date(atMs);
    if (period === "daily")
        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
    if (period === "weekly")
        return atMs - 7 * 86400000;
    if (period === "monthly")
        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 1);
    return Number.NEGATIVE_INFINITY;
}
function observedWithin(value, period, atMs) {
    const ms = Date.parse(String(value ?? ""));
    return Number.isFinite(ms) && ms <= atMs && ms >= periodStartMs(period, atMs);
}
function completedSessionsForPeriod(state, planId, period, atMs) {
    return state.sessions.filter((session) => session.planId === planId && session.status === "completed" && session.durationMinutes != null && observedWithin(session.endedAt ?? session.updatedAt ?? session.startedAt, period, atMs));
}
function gradedAttemptsForPeriod(state, planId, period, atMs) {
    return state.evidence.filter((item) => item.planId === planId && item.kind === "question-attempt" && item.value?.gradable === true && observedWithin(item.observedAt, period, atMs));
}
function topicEvidenceForPeriod(state, planId, period, atMs) {
    return state.evidence.filter((item) => item.planId === planId && item.topicId && observedWithin(item.observedAt, period, atMs));
}
function deriveProgressMetrics(state, { at = isoNow(), planId = state.activePlanId } = {}) {
    const atMs = Date.parse(at);
    if (!Number.isFinite(atMs))
        throw new TypeError("Progress timestamp must be valid ISO time");
    if (!planId)
        return { weeklyStudyMinutes: 0, gradedAttempts: 0, accuracy: null, topicCoverage: null, coveredTopics: 0, activeTopics: 0, reviewCompletionRate: null, readiness: null, readinessReason: "No active plan", confidence: "none" };
    const weekSessions = completedSessionsForPeriod(state, planId, "weekly", atMs);
    const weeklyStudyMinutes = weekSessions.reduce((total, session) => total + Number(session.durationMinutes || 0), 0);
    const allGraded = state.evidence.filter((item) => item.planId === planId && item.kind === "question-attempt" && item.value?.gradable === true);
    const correct = allGraded.filter((item) => item.value?.correct === true).length;
    const accuracy = allGraded.length ? correct / allGraded.length : null;
    const topics = state.topics.filter((topic) => topic.planId === planId && topic.status !== "archived");
    const observedTopicIds = new Set(state.evidence.filter((item) => item.planId === planId && item.topicId).map((item) => item.topicId));
    const coveredTopics = topics.filter((topic) => observedTopicIds.has(topic.id)).length;
    const topicCoverage = topics.length ? coveredTopics / topics.length : null;
    const reviewPool = state.reviews.filter((review) => review.planId === planId && ["due", "completed"].includes(review.status));
    const reviewCompletionRate = reviewPool.length ? reviewPool.filter((review) => review.status === "completed").length / reviewPool.length : null;
    let readiness = null;
    let readinessReason = "Insufficient evidence";
    let confidence = "none";
    if (accuracy != null && topicCoverage != null && allGraded.length >= 5 && coveredTopics >= 2) {
        readiness = Math.round((accuracy * 0.7 + topicCoverage * 0.3) * 100);
        confidence = allGraded.length >= 20 && coveredTopics >= 5 ? "moderate" : "limited";
        readinessReason = `${allGraded.length} graded attempts · ${coveredTopics} evidenced topics · ${confidence} confidence`;
    }
    else {
        const needs = [];
        if (allGraded.length < 5)
            needs.push(`${Math.max(0, 5 - allGraded.length)} more graded attempt${Math.max(0, 5 - allGraded.length) === 1 ? "" : "s"}`);
        if (coveredTopics < 2)
            needs.push(`${Math.max(0, 2 - coveredTopics)} more evidenced topic${Math.max(0, 2 - coveredTopics) === 1 ? "" : "s"}`);
        readinessReason = needs.length ? `Needs ${needs.join(" and ")}` : "Insufficient evidence";
    }
    return { weeklyStudyMinutes, gradedAttempts: allGraded.length, accuracy, topicCoverage, coveredTopics, activeTopics: topics.length, reviewCompletionRate, readiness, readinessReason, confidence };
}
function goalMetricValue(state, goal, { at = isoNow() } = {}) {
    const atMs = Date.parse(at);
    if (!Number.isFinite(atMs))
        throw new TypeError("Goal timestamp must be valid ISO time");
    const planId = goal.planId ?? state.activePlanId;
    if (goal.metric === "study-minutes")
        return completedSessionsForPeriod(state, planId, goal.period, atMs).reduce((sum, session) => sum + Number(session.durationMinutes || 0), 0);
    if (goal.metric === "graded-questions")
        return gradedAttemptsForPeriod(state, planId, goal.period, atMs).length;
    if (goal.metric === "topics-covered")
        return new Set(topicEvidenceForPeriod(state, planId, goal.period, atMs).map((item) => item.topicId)).size;
    if (goal.metric === "reviews-completed")
        return state.evidence.filter((item) => item.planId === planId && item.kind === "review-completed" && observedWithin(item.observedAt, goal.period, atMs)).length;
    return null;
}
function deriveGoalProgress(state, goal, options = {}) {
    const actual = goalMetricValue(state, goal, options);
    const progress = actual == null ? null : goal.target > 0 ? Math.max(0, actual / goal.target) : null;
    return { goal, actual, target: goal.target, progress, reached: actual != null && goal.target > 0 && actual >= goal.target };
}
function buildEvidenceTimeline(state, { planId = state.activePlanId, limit = 50 } = {}) {
    return state.evidence.filter((item) => !planId || item.planId === planId).slice().sort((a, b) => Date.parse(b.observedAt) - Date.parse(a.observedAt)).slice(0, Math.max(1, Math.min(500, Number(limit) || 50)));
}
function buildContentIntegrityReport(state) {
    const errors = validateStudyState(state);
    return { valid: errors.length === 0, errors, counts: Object.fromEntries(exports.ENTITY_COLLECTIONS.map((name) => [name, Array.isArray(state[name]) ? state[name].length : 0])), revision: state.revision };
}
function selectDueReviews(state, at = Date.now(), planId = state.activePlanId) {
    return selectOverdueReviews(state, at, planId).sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt));
}
function selectOpenErrors(state, planId = state.activePlanId) {
    return state.errorEntries
        .filter((entry) => entry.planId === planId && entry.status === "open")
        .sort((a, b) => Date.parse(b.observedAt) - Date.parse(a.observedAt));
}
function deriveQuestionStats(state, planId = state.activePlanId) {
    const attempts = state.evidence.filter((item) => item.planId === planId && item.kind === "question-attempt");
    const gradable = attempts.filter((item) => item.value && item.value.gradable === true);
    const correct = gradable.filter((item) => item.value.correct === true);
    return { attempts: attempts.length, gradedAttempts: gradable.length, correct: correct.length, accuracy: gradable.length ? correct.length / gradable.length : null };
}
