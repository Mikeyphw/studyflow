"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.tvScheduleActivityDestination = tvScheduleActivityDestination;
exports.tvScheduleDateLabel = tvScheduleDateLabel;
exports.tvScheduleTimeLabel = tvScheduleTimeLabel;
exports.buildTvScheduleSnapshot = buildTvScheduleSnapshot;
const parity_intelligence_1 = require("./parity_intelligence");
const addLocalDays = (key, days) => { const [y, m, d] = key.split("-").map(Number); const date = new Date(y, m - 1, d + days); return (0, parity_intelligence_1.localDateKey)(date); };
function tvScheduleActivityDestination(activityType) {
    if (activityType === "QUESTIONS")
        return "QUESTION_SET";
    if (activityType === "REVIEW" || activityType === "FLASHCARDS")
        return "REVIEW";
    if (activityType === "MOCK_EXAM")
        return "MOCK_EXAM";
    if (activityType === "WRITING")
        return null;
    return "STUDY_SESSION";
}
function tvScheduleDateLabel(date, today) {
    if (date === today)
        return "Today";
    if (date === addLocalDays(today, 1))
        return "Tomorrow";
    const [y, m, d] = date.split("-").map(Number);
    const weekday = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][new Date(y, m - 1, d).getDay()];
    return `${weekday} · ${date}`;
}
function tvScheduleTimeLabel(minute) { return minute == null ? "Any time" : `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`; }
function buildTvScheduleSnapshot(state, view = "today", at = new Date()) {
    var _a, _b, _c, _d;
    const calendar = (0, parity_intelligence_1.deriveScheduleCalendarSnapshot)(state, { view: view === "today" ? "today" : "this-week", at });
    const today = (0, parity_intelligence_1.localDateKey)(at);
    const items = calendar.activities.map(activity => { var _a, _b, _c, _d, _e; const subject = (_b = (_a = state.subjects.find(item => item.id === activity.subjectId)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : null; const topic = (_d = (_c = state.topics.find(item => item.id === activity.topicId)) === null || _c === void 0 ? void 0 : _c.name) !== null && _d !== void 0 ? _d : null; const destination = tvScheduleActivityDestination(activity.activityType); return { activity, subjectName: subject, topicName: topic, dateLabel: tvScheduleDateLabel((_e = activity.scheduledFor) !== null && _e !== void 0 ? _e : today, today), timeLabel: tvScheduleTimeLabel(activity.scheduledStartMinute), destination, launchable: activity.status === "planned" && destination != null }; });
    const groupMap = new Map();
    for (const item of items) {
        const key = (_a = item.activity.scheduledFor) !== null && _a !== void 0 ? _a : today;
        const list = (_b = groupMap.get(key)) !== null && _b !== void 0 ? _b : [];
        list.push(item);
        groupMap.set(key, list);
    }
    const days = [...groupMap.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, dayItems]) => ({ date, label: tvScheduleDateLabel(date, today), items: dayItems }));
    const nowMinute = at.getHours() * 60 + at.getMinutes();
    let nextIndex = items.findIndex(item => { var _a; return item.activity.status === "planned" && item.activity.scheduledFor != null && (item.activity.scheduledFor > today || (item.activity.scheduledFor === today && ((_a = item.activity.scheduledStartMinute) !== null && _a !== void 0 ? _a : 0) >= nowMinute)); });
    if (nextIndex < 0)
        nextIndex = items.findIndex(item => item.activity.status === "planned");
    if (nextIndex < 0)
        nextIndex = 0;
    const start = (_c = calendar.window.startsOn) !== null && _c !== void 0 ? _c : today, end = (_d = calendar.window.endsOnInclusive) !== null && _d !== void 0 ? _d : today;
    return { view, windowLabel: view === "today" ? today : `${start} → ${end}`, visibleCount: calendar.visibleCount, plannedMinutes: calendar.plannedMinutes, plannedCount: calendar.activities.filter(item => item.status === "planned").length, completedCount: calendar.activities.filter(item => item.status === "completed").length, cancelledCount: calendar.activities.filter(item => item.status === "cancelled").length, days, flatItems: items, nextIndex };
}
