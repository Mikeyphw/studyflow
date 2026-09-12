"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultCalendarAttentionPreferences = defaultCalendarAttentionPreferences;
exports.buildStudyFlowCalendarScheduleItems = buildStudyFlowCalendarScheduleItems;
exports.buildStudyFlowCalendarExport = buildStudyFlowCalendarExport;
exports.serializeStudyFlowCalendarIcs = serializeStudyFlowCalendarIcs;
exports.calendarAttentionSummary = calendarAttentionSummary;
exports.downloadStudyFlowCalendarIcs = downloadStudyFlowCalendarIcs;
const attention_1 = require("./attention");
const DAY_MS = 86400000;
const MINUTE_MS = 60000;
const DEFAULT_CALENDAR_PREFS = {
    provider: "ics_file",
    privacyMode: "full_details",
    includeCompleted: false,
    includeReviews: true,
    includeExamDates: true,
    includeAttentionLead: true,
    lookaheadDays: 90,
    historyDays: 14,
    reminderLeadMinutes: 10,
    calendarName: "StudyFlow schedule",
};
function defaultCalendarAttentionPreferences() {
    return { ...DEFAULT_CALENDAR_PREFS };
}
function clampNumber(value, min, max, fallback) {
    return Number.isFinite(value) ? Math.max(min, Math.min(max, Math.round(value))) : fallback;
}
function localDateAtMinute(dateKey, minute) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey))
        return null;
    const [year, month, day] = dateKey.split("-").map(Number);
    const value = new Date(year, month - 1, day, Math.floor(minute / 60), minute % 60, 0, 0).getTime();
    return Number.isFinite(value) ? value : null;
}
function dateKeyToAllDayStart(dateKey) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey))
        return null;
    const [year, month, day] = dateKey.split("-").map(Number);
    const value = new Date(year, month - 1, day, 0, 0, 0, 0).getTime();
    return Number.isFinite(value) ? value : null;
}
function subjectName(state, subjectId) {
    var _a, _b;
    return subjectId ? (_b = (_a = state.subjects.find(subject => subject.id === subjectId)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : null : null;
}
function activityToCalendarItem(state, activity, fallbackWorkspaceId) {
    var _a;
    if (!activity.scheduledFor)
        return null;
    const startMinute = (_a = activity.scheduledStartMinute) !== null && _a !== void 0 ? _a : 9 * 60;
    const start = localDateAtMinute(activity.scheduledFor, startMinute);
    if (start == null)
        return null;
    const subject = subjectName(state, activity.subjectId);
    const isReview = activity.kind === "review" || activity.activityType === "REVIEW";
    const isMock = activity.activityType === "MOCK_EXAM";
    return {
        itemId: activity.id,
        workspaceId: activity.planId || fallbackWorkspaceId,
        title: activity.title,
        body: subject ? `${subject} · ${activity.durationMinutes} min` : `${activity.durationMinutes} min planned block`,
        kind: isMock ? "mock_exam" : isReview ? "review" : "study_block",
        startEpochMillis: start,
        durationMinutes: Math.max(1, activity.durationMinutes),
        timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
        allDay: false,
        completed: activity.status === "completed" || activity.status === "cancelled",
        routeHash: isReview ? "#practice" : isMock ? "#practice" : "#today",
        attentionCategory: isReview ? "due_review" : "planned_study",
        reminderLeadMinutes: DEFAULT_CALENDAR_PREFS.reminderLeadMinutes,
    };
}
function reviewToCalendarItem(state, review, fallbackWorkspaceId) {
    if (!review.dueAt || review.status !== "due")
        return null;
    const dueKey = review.dueAt.slice(0, 10);
    const start = localDateAtMinute(dueKey, 8 * 60);
    if (start == null)
        return null;
    const subject = subjectName(state, review.subjectId);
    return {
        itemId: review.id,
        workspaceId: review.planId || fallbackWorkspaceId,
        title: subject ? `Review ${subject}` : "StudyFlow review",
        body: "Spaced review due in StudyFlow.",
        kind: "review",
        startEpochMillis: start,
        durationMinutes: 20,
        timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
        allDay: false,
        completed: false,
        routeHash: "#practice",
        attentionCategory: "due_review",
        reminderLeadMinutes: DEFAULT_CALENDAR_PREFS.reminderLeadMinutes,
    };
}
function examDateCalendarItem(state, fallbackWorkspaceId) {
    var _a, _b, _c, _d;
    const activePlan = (_a = state.plans.find(plan => plan.id === state.activePlanId)) !== null && _a !== void 0 ? _a : state.plans[0];
    const examDate = (_d = (_c = (_b = activePlan === null || activePlan === void 0 ? void 0 : activePlan.configuration.publicExam) === null || _b === void 0 ? void 0 : _b.target.examDate) !== null && _c !== void 0 ? _c : activePlan === null || activePlan === void 0 ? void 0 : activePlan.examDate) !== null && _d !== void 0 ? _d : null;
    if (!activePlan || !examDate)
        return null;
    const start = dateKeyToAllDayStart(examDate);
    if (start == null)
        return null;
    return {
        itemId: `${activePlan.id}-exam-date`,
        workspaceId: activePlan.id || fallbackWorkspaceId,
        title: `${activePlan.name} exam day`,
        body: "Exam date from StudyFlow target profile. Confirm against the official notice before relying on it.",
        kind: "exam_date",
        startEpochMillis: start,
        durationMinutes: 0,
        timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
        allDay: true,
        completed: false,
        routeHash: "#progress",
        attentionCategory: "exam_countdown",
        reminderLeadMinutes: 24 * 60,
    };
}
function buildStudyFlowCalendarScheduleItems(state) {
    var _a;
    const fallbackWorkspaceId = (_a = state.activePlanId) !== null && _a !== void 0 ? _a : "studyflow-local";
    const activities = state.activities
        .map(activity => activityToCalendarItem(state, activity, fallbackWorkspaceId))
        .filter((item) => item != null);
    const reviews = state.reviews
        .map(review => reviewToCalendarItem(state, review, fallbackWorkspaceId))
        .filter((item) => item != null);
    const exam = examDateCalendarItem(state, fallbackWorkspaceId);
    const seen = new Set();
    return [...activities, ...reviews, ...(exam ? [exam] : [])]
        .filter(item => {
        const key = `${item.kind}:${item.itemId}:${item.startEpochMillis}`;
        if (seen.has(key))
            return false;
        seen.add(key);
        return true;
    })
        .sort((a, b) => a.startEpochMillis - b.startEpochMillis || a.itemId.localeCompare(b.itemId));
}
function sanitizePreferences(input = {}) {
    var _a, _b, _c;
    const privacyMode = input.privacyMode === "private_title" || input.privacyMode === "busy_only" ? input.privacyMode : "full_details";
    return {
        ...DEFAULT_CALENDAR_PREFS,
        ...input,
        privacyMode,
        lookaheadDays: clampNumber((_a = input.lookaheadDays) !== null && _a !== void 0 ? _a : DEFAULT_CALENDAR_PREFS.lookaheadDays, 1, 366, DEFAULT_CALENDAR_PREFS.lookaheadDays),
        historyDays: clampNumber((_b = input.historyDays) !== null && _b !== void 0 ? _b : DEFAULT_CALENDAR_PREFS.historyDays, 0, 366, DEFAULT_CALENDAR_PREFS.historyDays),
        reminderLeadMinutes: clampNumber((_c = input.reminderLeadMinutes) !== null && _c !== void 0 ? _c : DEFAULT_CALENDAR_PREFS.reminderLeadMinutes, 0, 1440, DEFAULT_CALENDAR_PREFS.reminderLeadMinutes),
        calendarName: String(input.calendarName || DEFAULT_CALENDAR_PREFS.calendarName),
    };
}
function privateCopy(item, privacyMode) {
    if (privacyMode === "busy_only")
        return { summary: "Busy", description: "Busy time exported by StudyFlow. Details hidden." };
    if (privacyMode === "private_title")
        return {
            summary: "StudyFlow block",
            description: "Study details are hidden by your ATT09 calendar privacy setting. Open StudyFlow for the full plan.",
        };
    return { summary: item.title, description: `${item.body}\nOpen in StudyFlow: ${item.routeHash}` };
}
function makeAttentionOccurrence(item, summary, description, preferences) {
    var _a;
    if (!preferences.includeAttentionLead)
        return null;
    const lead = item.kind === "exam_date" ? item.reminderLeadMinutes : preferences.reminderLeadMinutes;
    const scheduledAtEpochMillis = Math.max(0, item.startEpochMillis - lead * MINUTE_MS);
    return {
        occurrenceId: (0, attention_1.stableWebAttentionOccurrenceId)(["calendar", item.workspaceId, item.itemId, String(item.startEpochMillis)]),
        workspaceId: item.workspaceId,
        scheduleEntityId: item.itemId,
        category: item.attentionCategory,
        priority: item.kind === "exam_date" ? "time_sensitive" : "normal",
        scheduledAtEpochMillis,
        originalScheduledAtEpochMillis: scheduledAtEpochMillis,
        expiresAtEpochMillis: item.startEpochMillis + Math.max(0, item.durationMinutes) * MINUTE_MS,
        timezoneId: (_a = item.timezoneId) !== null && _a !== void 0 ? _a : undefined,
        deliveryPolicy: "active_then_primary",
        actions: [{ kind: "open", payload: {} }, { kind: "snooze", payload: {} }, { kind: "dismiss", payload: {} }],
        sourceDeviceId: "web-calendar-export",
        targetDeviceIds: [],
        payload: { title: summary, body: description, routeHash: item.routeHash, calendarItemId: item.itemId, calendarKind: item.kind },
    };
}
function buildStudyFlowCalendarExport(state, options = {}) {
    const preferences = sanitizePreferences(options);
    const now = Number.isFinite(options.nowEpochMillis) ? Number(options.nowEpochMillis) : Date.now();
    const windowStart = now - preferences.historyDays * DAY_MS;
    const windowEnd = now + preferences.lookaheadDays * DAY_MS;
    let completedSuppressedCount = 0;
    let outsideWindowSuppressedCount = 0;
    let reviewSuppressedCount = 0;
    const sourceItems = buildStudyFlowCalendarScheduleItems(state).map(item => ({ ...item, reminderLeadMinutes: preferences.reminderLeadMinutes }));
    const events = sourceItems.flatMap(item => {
        if (item.completed && !preferences.includeCompleted) {
            completedSuppressedCount += 1;
            return [];
        }
        if (item.kind === "review" && !preferences.includeReviews) {
            reviewSuppressedCount += 1;
            return [];
        }
        if (item.kind === "exam_date" && !preferences.includeExamDates) {
            reviewSuppressedCount += 1;
            return [];
        }
        if (item.startEpochMillis < windowStart || item.startEpochMillis > windowEnd) {
            outsideWindowSuppressedCount += 1;
            return [];
        }
        const copy = privateCopy(item, preferences.privacyMode);
        const end = item.allDay ? item.startEpochMillis : item.startEpochMillis + Math.max(0, item.durationMinutes) * MINUTE_MS;
        return [{
                exportId: (0, attention_1.stableWebAttentionOccurrenceId)(["ics", item.workspaceId, item.itemId, String(item.startEpochMillis)]),
                item,
                summary: copy.summary,
                description: copy.description,
                startsAtEpochMillis: item.startEpochMillis,
                endsAtEpochMillis: end,
                attentionOccurrence: makeAttentionOccurrence(item, copy.summary, copy.description, preferences),
            }];
    });
    const diagnostics = {
        inputItemCount: sourceItems.length,
        exportedEventCount: events.length,
        completedSuppressedCount,
        outsideWindowSuppressedCount,
        reviewSuppressedCount,
        attentionOccurrenceCount: events.filter(event => event.attentionOccurrence != null).length,
        privacyMode: preferences.privacyMode,
        explanation: "ATT09 exports portable calendar events and ATT01-compatible attention occurrences without silently mutating an external calendar.",
    };
    const feed = { preferences, generatedAtEpochMillis: now, events, diagnostics };
    return { ...feed, icsText: serializeStudyFlowCalendarIcs(feed) };
}
function icsEscape(value) {
    return value.replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\n/g, "\\n");
}
function utcStamp(epochMillis) {
    return new Date(epochMillis).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}
function localDateStamp(epochMillis) {
    const date = new Date(epochMillis);
    return `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
}
function serializeStudyFlowCalendarIcs(feed) {
    const lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//StudyFlow//Calendar Attention Export//EN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        `X-WR-CALNAME:${icsEscape(feed.preferences.calendarName)}`,
        "X-STUDYFLOW-ATTENTION-INTEGRATION:ATT09",
    ];
    for (const event of feed.events) {
        lines.push("BEGIN:VEVENT");
        lines.push(`UID:${event.exportId}@studyflow.local`);
        lines.push(`DTSTAMP:${utcStamp(feed.generatedAtEpochMillis)}`);
        if (event.item.allDay) {
            lines.push(`DTSTART;VALUE=DATE:${localDateStamp(event.startsAtEpochMillis)}`);
            lines.push(`DTEND;VALUE=DATE:${localDateStamp(event.startsAtEpochMillis + DAY_MS)}`);
        }
        else {
            lines.push(`DTSTART:${utcStamp(event.startsAtEpochMillis)}`);
            lines.push(`DTEND:${utcStamp(event.endsAtEpochMillis)}`);
        }
        lines.push(`SUMMARY:${icsEscape(event.summary)}`);
        lines.push(`DESCRIPTION:${icsEscape(event.description)}`);
        const lead = event.item.kind === "exam_date" ? event.item.reminderLeadMinutes : feed.preferences.reminderLeadMinutes;
        if (lead > 0) {
            lines.push("BEGIN:VALARM");
            lines.push("ACTION:DISPLAY");
            lines.push(`DESCRIPTION:${icsEscape(event.summary)}`);
            lines.push(`TRIGGER:-PT${lead}M`);
            lines.push("END:VALARM");
        }
        lines.push("END:VEVENT");
    }
    lines.push("END:VCALENDAR");
    return `${lines.join("\r\n")}\r\n`;
}
function calendarAttentionSummary(feed) {
    return `${feed.events.length} events · ${feed.diagnostics.attentionOccurrenceCount} attention reminders · ${feed.diagnostics.privacyMode.replace("_", " ")}`;
}
function downloadStudyFlowCalendarIcs(feed) {
    const safeName = feed.preferences.calendarName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "") || "studyflow-schedule";
    const blob = new Blob([feed.icsText], { type: "text/calendar;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    try {
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `${safeName}.ics`;
        anchor.click();
    }
    finally {
        URL.revokeObjectURL(url);
    }
}
