"use strict";

import {
    derivePlanInventory,
    selectActivitiesForQueue,
    selectActivePlan,
    selectSubjectsForPlan,
    selectTopicsForSubject,
} from "./domain.js";

function byId(id) {
    return document.getElementById(id);
}

function titleCase(value) {
    return `${value.slice(0, 1).toUpperCase()}${value.slice(1)}`;
}

function emptyListMessage(list, title, detail) {
    const item = document.createElement("li");
    item.className = "empty-state";
    const strong = document.createElement("strong");
    strong.textContent = title;
    const span = document.createElement("span");
    span.textContent = detail;
    item.append(strong, span);
    list.append(item);
}

export function renderTodayActivities(state) {
    const list = byId("today-activity-list");
    if (!list) return;
    list.replaceChildren();
    const activities = selectActivitiesForQueue(state, "today");
    if (!activities.length) {
        emptyListMessage(list, "Nothing queued for today", "Use Quick Add to create a local activity.");
        return;
    }
    for (const activity of activities) {
        const item = document.createElement("li");
        item.dataset.kind = activity.kind;
        item.dataset.entityId = activity.id;
        const strong = document.createElement("strong");
        strong.textContent = activity.title;
        const meta = document.createElement("span");
        meta.textContent = `${titleCase(activity.kind)} · ${activity.durationMinutes} min`;
        item.append(strong, meta);
        list.append(item);
    }
}

export function renderPlanContent(state) {
    const container = byId("plan-subject-list");
    if (!container) return;
    container.replaceChildren();
    const subjects = selectSubjectsForPlan(state);
    if (!subjects.length) {
        const empty = document.createElement("div");
        empty.className = "empty-state";
        const strong = document.createElement("strong");
        strong.textContent = "No subjects yet";
        const span = document.createElement("span");
        span.textContent = "W07 will add full subject/topic editing and import preview.";
        empty.append(strong, span);
        container.append(empty);
        return;
    }

    for (const subject of subjects) {
        const details = document.createElement("details");
        if (container.children.length === 0) details.open = true;
        details.dataset.entityId = subject.id;
        const summary = document.createElement("summary");
        const name = document.createElement("span");
        name.textContent = subject.name;
        const topics = selectTopicsForSubject(state, subject.id);
        const count = document.createElement("span");
        count.className = "muted";
        count.textContent = `${topics.length} ${topics.length === 1 ? "topic" : "topics"}`;
        summary.append(name, count);
        details.append(summary);

        const list = document.createElement("ul");
        list.className = "topic-list";
        if (!topics.length) {
            emptyListMessage(list, "No topics yet", "Topic editing arrives in W07.");
        } else {
            for (const topic of topics) {
                const item = document.createElement("li");
                item.dataset.entityId = topic.id;
                const label = document.createElement("span");
                label.textContent = topic.name;
                const priority = document.createElement("span");
                priority.className = "pill";
                priority.textContent = titleCase(topic.priority);
                item.append(label, priority);
                list.append(item);
            }
        }
        details.append(list);
        container.append(details);
    }
}

export function renderPlanInventory(state) {
    const plan = selectActivePlan(state);
    const inventory = derivePlanInventory(state);
    const planName = byId("active-plan-name");
    const subjectCount = byId("plan-subject-count");
    const topicCount = byId("plan-topic-count");
    if (planName) planName.textContent = plan?.name ?? "No active plan";
    if (subjectCount) subjectCount.textContent = String(inventory.subjects);
    if (topicCount) topicCount.textContent = String(inventory.topics);
}

export function renderPersistenceStatus(persistence) {
    const output = byId("persistence-status");
    if (!output) return;
    output.textContent = persistence.message;
    output.dataset.persistence = persistence.durable ? "durable" : "memory";
}

export function renderStudyState(state, persistence) {
    renderTodayActivities(state);
    renderPlanContent(state);
    renderPlanInventory(state);
    renderPersistenceStatus(persistence);
}
