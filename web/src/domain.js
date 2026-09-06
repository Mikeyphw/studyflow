"use strict";

export const STATE_SCHEMA_VERSION = 1;

export const ENTITY_COLLECTIONS = Object.freeze([
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
]);

export const STUDY_MODES = Object.freeze([
    "EXAM_PLAN",
    "PROGRAMMING_COURSE",
    "LANGUAGE_DECK",
    "READING_STUDY",
    "GENERAL_SKILL",
]);

export const ACTIVITY_KINDS = Object.freeze(["study", "questions", "review"]);
export const ACTIVITY_STATUSES = Object.freeze(["planned", "completed", "cancelled"]);

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
});

function requireText(value, field) {
    const text = String(value ?? "").trim();
    if (!text) throw new TypeError(`${field} is required`);
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

export function createEntityId(kind, uuidFactory = null) {
    const prefix = ID_PREFIXES[kind];
    if (!prefix) throw new TypeError(`Unknown entity kind: ${kind}`);
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

export function createPlan(input = {}) {
    return {
        ...entityBase("plan", input),
        name: requireText(input.name, "plan.name"),
        mode: requireOneOf(input.mode ?? "GENERAL_SKILL", STUDY_MODES, "plan.mode"),
        status: requireOneOf(input.status ?? "active", ["active", "archived"], "plan.status"),
        examDate: optionalText(input.examDate),
    };
}

export function createSubject(input = {}) {
    const weight = input.weight == null ? null : Number(input.weight);
    if (weight != null && (!Number.isFinite(weight) || weight < 0)) {
        throw new TypeError("subject.weight must be null or a non-negative number");
    }
    return {
        ...entityBase("subject", input),
        planId: requireText(input.planId, "subject.planId"),
        name: requireText(input.name, "subject.name"),
        weight,
        archived: Boolean(input.archived),
    };
}

export function createTopic(input = {}) {
    return {
        ...entityBase("topic", input),
        planId: requireText(input.planId, "topic.planId"),
        subjectId: requireText(input.subjectId, "topic.subjectId"),
        name: requireText(input.name, "topic.name"),
        priority: requireOneOf(input.priority ?? "normal", ["low", "normal", "high"], "topic.priority"),
        status: requireOneOf(input.status ?? "not-started", ["not-started", "learning", "reviewing", "mastered", "archived"], "topic.status"),
    };
}

export function createActivity(input = {}) {
    return {
        ...entityBase("activity", input),
        planId: requireText(input.planId, "activity.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        title: requireText(input.title, "activity.title"),
        kind: requireOneOf(input.kind ?? "study", ACTIVITY_KINDS, "activity.kind"),
        durationMinutes: requirePositiveInteger(input.durationMinutes ?? 25, "activity.durationMinutes", { min: 5, max: 240 }),
        status: requireOneOf(input.status ?? "planned", ACTIVITY_STATUSES, "activity.status"),
        origin: requireOneOf(input.origin ?? "manual", ["manual", "seed", "cycle", "review", "import"], "activity.origin"),
        queue: requireOneOf(input.queue ?? "today", ["today", "future", "backlog"], "activity.queue"),
    };
}

export function createStudySession(input = {}) {
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

export function createGoal(input = {}) {
    const target = Number(input.target);
    if (!Number.isFinite(target)) throw new TypeError("goal.target must be a finite number");
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

export function createMaterial(input = {}) {
    return {
        ...entityBase("material", input),
        planId: requireText(input.planId, "material.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        title: requireText(input.title, "material.title"),
        kind: requireOneOf(input.kind ?? "note", ["note", "file", "link", "book", "legislation"], "material.kind"),
        locator: optionalText(input.locator),
        mimeType: optionalText(input.mimeType),
    };
}

export function createReview(input = {}) {
    return {
        ...entityBase("review", input),
        planId: requireText(input.planId, "review.planId"),
        subjectId: optionalId(input.subjectId),
        topicId: optionalId(input.topicId),
        dueAt: requireText(input.dueAt, "review.dueAt"),
        status: requireOneOf(input.status ?? "due", ["due", "completed", "dismissed"], "review.status"),
        sourceType: optionalText(input.sourceType),
        sourceId: optionalId(input.sourceId),
    };
}

export function createQuestion(input = {}) {
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
        disputed: Boolean(input.disputed),
    };
}

export function createEvidence(input = {}) {
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

export function createEmptyStudyState() {
    return {
        schemaVersion: STATE_SCHEMA_VERSION,
        revision: 0,
        activePlanId: null,
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
    };
}

export function createInitialStudyState({ now = isoNow() } = {}) {
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
        createSubject({ id: constitutionalId, planId, name: "Constitutional Law", weight: null, createdAt: now, updatedAt: now }),
        createSubject({ id: portugueseId, planId, name: "Portuguese", weight: null, createdAt: now, updatedAt: now }),
        createSubject({ id: bankingId, planId, name: "Banking Knowledge", weight: null, createdAt: now, updatedAt: now }),
    ];
    state.topics = [
        createTopic({ id: "topic-fundamental-rights", planId, subjectId: constitutionalId, name: "Fundamental rights", priority: "high", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-public-administration", planId, subjectId: constitutionalId, name: "Public administration", priority: "normal", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-constitutional-remedies", planId, subjectId: constitutionalId, name: "Constitutional remedies", priority: "high", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-text-interpretation", planId, subjectId: portugueseId, name: "Text interpretation", priority: "normal", createdAt: now, updatedAt: now }),
        createTopic({ id: "topic-grammar", planId, subjectId: portugueseId, name: "Grammar", priority: "normal", createdAt: now, updatedAt: now }),
    ];
    state.activities = [
        createActivity({ id: "activity-seed-admin-review", planId, title: "Administrative Law review", kind: "review", durationMinutes: 20, origin: "seed", createdAt: now, updatedAt: now }),
        createActivity({ id: "activity-seed-portuguese-questions", planId, subjectId: portugueseId, title: "Portuguese questions", kind: "questions", durationMinutes: 30, origin: "seed", createdAt: now, updatedAt: now }),
        createActivity({ id: "activity-seed-banking-study", planId, subjectId: bankingId, title: "Banking Knowledge study-cycle block", kind: "study", durationMinutes: 40, origin: "seed", createdAt: now, updatedAt: now }),
    ];
    return state;
}

function idsFor(collection) {
    return new Set(collection.map((entity) => entity.id));
}

function validateCollectionShape(state, errors) {
    for (const key of ENTITY_COLLECTIONS) {
        if (!Array.isArray(state[key])) errors.push(`${key} must be an array`);
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
    };
    for (const [collectionName, create] of Object.entries(constructors)) {
        for (const entity of state[collectionName]) {
            if (!entity || typeof entity !== "object" || Array.isArray(entity) || !String(entity.id ?? "").trim()) continue;
            try {
                create(entity);
            } catch (error) {
                const message = error instanceof Error ? error.message : String(error);
                errors.push(`${collectionName} ${entity.id} is invalid: ${message}`);
            }
        }
    }
}

export function validateStudyState(state) {
    const errors = [];
    if (!state || typeof state !== "object" || Array.isArray(state)) return ["state must be an object"];
    if (state.schemaVersion !== STATE_SCHEMA_VERSION) {
        errors.push(`unsupported state schemaVersion: ${String(state.schemaVersion)}`);
    }
    if (!Number.isInteger(state.revision) || state.revision < 0) errors.push("revision must be a non-negative integer");
    validateCollectionShape(state, errors);
    if (errors.some((error) => error.endsWith("must be an array"))) return errors;

    const seen = new Set();
    for (const key of ENTITY_COLLECTIONS) {
        for (const entity of state[key]) {
            if (!entity || typeof entity !== "object" || Array.isArray(entity)) {
                errors.push(`${key} contains a non-object entity`);
                continue;
            }
            if (!String(entity.id ?? "").trim()) {
                errors.push(`${key} contains an entity without an id`);
                continue;
            }
            if (seen.has(entity.id)) errors.push(`duplicate entity id: ${entity.id}`);
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

    if (state.activePlanId != null && !planIds.has(state.activePlanId)) {
        errors.push(`activePlanId references missing plan: ${state.activePlanId}`);
    }

    for (const subject of state.subjects) requireReference(errors, subject, "planId", planIds, "subject");
    for (const topic of state.topics) {
        requireReference(errors, topic, "planId", planIds, "topic");
        requireReference(errors, topic, "subjectId", subjectIds, "topic");
        const subject = state.subjects.find((candidate) => candidate.id === topic.subjectId);
        if (subject && subject.planId !== topic.planId) errors.push(`topic ${topic.id} planId does not match its subject`);
    }

    for (const [label, collection] of [
        ["activity", state.activities], ["session", state.sessions], ["goal", state.goals],
        ["material", state.materials], ["review", state.reviews], ["question", state.questions], ["evidence", state.evidence],
    ]) {
        for (const entity of collection) {
            requireReference(errors, entity, "planId", planIds, label);
            if ("subjectId" in entity) requireReference(errors, entity, "subjectId", subjectIds, label);
            if ("topicId" in entity) requireReference(errors, entity, "topicId", topicIds, label);

            const subject = entity.subjectId == null ? null : state.subjects.find((candidate) => candidate.id === entity.subjectId);
            const topic = entity.topicId == null ? null : state.topics.find((candidate) => candidate.id === entity.topicId);
            if (subject && subject.planId !== entity.planId) errors.push(`${label} ${entity.id} planId does not match its subject`);
            if (topic && topic.planId !== entity.planId) errors.push(`${label} ${entity.id} planId does not match its topic`);
            if (subject && topic && topic.subjectId !== subject.id) errors.push(`${label} ${entity.id} topicId does not belong to its subjectId`);
        }
    }

    for (const session of state.sessions) {
        requireReference(errors, session, "activityId", activityIds, "session");
        const activity = session.activityId == null ? null : state.activities.find((candidate) => candidate.id === session.activityId);
        if (activity && activity.planId !== session.planId) errors.push(`session ${session.id} planId does not match its activity`);
    }
    for (const review of state.reviews) {
        if (review.sourceType === "topic" && review.sourceId != null && !topicIds.has(review.sourceId)) {
            errors.push(`review ${review.id} references missing topic sourceId: ${review.sourceId}`);
        }
        if (review.sourceType === "activity" && review.sourceId != null && !activityIds.has(review.sourceId)) {
            errors.push(`review ${review.id} references missing activity sourceId: ${review.sourceId}`);
        }
    }
    for (const evidence of state.evidence) {
        requireReference(errors, evidence, "activityId", activityIds, "evidence");
        requireReference(errors, evidence, "sessionId", sessionIds, "evidence");
        requireReference(errors, evidence, "questionId", questionIds, "evidence");
        for (const [field, collection] of [["activityId", state.activities], ["sessionId", state.sessions], ["questionId", state.questions]]) {
            const reference = evidence[field] == null ? null : collection.find((candidate) => candidate.id === evidence[field]);
            if (reference && reference.planId !== evidence.planId) errors.push(`evidence ${evidence.id} planId does not match its ${field}`);
        }
    }

    return errors;
}

export function selectActivePlan(state) {
    return state.plans.find((plan) => plan.id === state.activePlanId) ?? null;
}

export function selectSubjectsForPlan(state, planId = state.activePlanId) {
    return state.subjects.filter((subject) => subject.planId === planId && !subject.archived);
}

export function selectTopicsForSubject(state, subjectId) {
    return state.topics.filter((topic) => topic.subjectId === subjectId && topic.status !== "archived");
}

export function selectActivitiesForQueue(state, queue = "today", planId = state.activePlanId) {
    return state.activities.filter((activity) => activity.planId === planId && activity.queue === queue && activity.status === "planned");
}

export function derivePlanInventory(state, planId = state.activePlanId) {
    const subjects = selectSubjectsForPlan(state, planId);
    const subjectIds = new Set(subjects.map((subject) => subject.id));
    const topics = state.topics.filter((topic) => subjectIds.has(topic.subjectId) && topic.status !== "archived");
    const activities = state.activities.filter((activity) => activity.planId === planId && activity.status !== "cancelled");
    return activities.reduce(
        (summary, activity) => ({ ...summary, plannedMinutes: summary.plannedMinutes + activity.durationMinutes }),
        { subjects: subjects.length, topics: topics.length, activities: activities.length, plannedMinutes: 0 },
    );
}
