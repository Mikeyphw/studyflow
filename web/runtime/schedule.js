"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.defaultScheduleConfiguration = defaultScheduleConfiguration;
exports.normalizeScheduleConfiguration = normalizeScheduleConfiguration;
exports.validateScheduleConfiguration = validateScheduleConfiguration;
exports.addAvailabilityWindow = addAvailabilityWindow;
exports.addFixedScheduleBlock = addFixedScheduleBlock;
exports.buildAdaptiveSchedulePreview = buildAdaptiveSchedulePreview;
const clone = (value) => JSON.parse(JSON.stringify(value));
const clamp = (n, min, max) => Math.max(min, Math.min(max, n));
const dayKey = (value) => { const d = value instanceof Date ? value : new Date(value); if (Number.isNaN(d.getTime()))
    throw new TypeError(`Invalid date: ${value}`); return d.toISOString().slice(0, 10); };
const plusDays = (value, days) => { const d = new Date(value); d.setUTCDate(d.getUTCDate() + days); return d; };
const dateWeekday = (date) => new Date(`${date}T12:00:00Z`).getUTCDay();
const cleanText = (v) => String(v ?? "").trim();
const makeId = (prefix) => `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`}`;
function defaultScheduleConfiguration() { return { dailyCapacityMinutes: 120, availability: [], fixedBlocks: [] }; }
function normalizeScheduleConfiguration(input) {
    const raw = input && typeof input === "object" && !Array.isArray(input) ? input : {};
    const daily = Number(raw.dailyCapacityMinutes);
    const availability = (Array.isArray(raw.availability) ? raw.availability : []).map((w, i) => ({ id: cleanText(w?.id) || `window-${i + 1}`, weekday: clamp(Math.trunc(Number(w?.weekday) || 0), 0, 6), startMinute: clamp(Math.trunc(Number(w?.startMinute) || 0), 0, 1439), endMinute: clamp(Math.trunc(Number(w?.endMinute) || 0), 1, 1440) })).filter((w) => w.endMinute > w.startMinute);
    const fixedBlocks = (Array.isArray(raw.fixedBlocks) ? raw.fixedBlocks : []).map((b, i) => ({ id: cleanText(b?.id) || `fixed-${i + 1}`, title: cleanText(b?.title) || `Fixed block ${i + 1}`, date: /^\d{4}-\d{2}-\d{2}$/.test(cleanText(b?.date)) ? cleanText(b.date) : null, weekday: b?.weekday == null ? null : clamp(Math.trunc(Number(b.weekday) || 0), 0, 6), startMinute: clamp(Math.trunc(Number(b?.startMinute) || 0), 0, 1439), durationMinutes: clamp(Math.trunc(Number(b?.durationMinutes) || 30), 5, 1440) }));
    return { dailyCapacityMinutes: Number.isInteger(daily) && daily >= 30 && daily <= 1440 ? daily : 120, availability, fixedBlocks };
}
function validateScheduleConfiguration(input) { const c = normalizeScheduleConfiguration(input); const e = []; if (c.dailyCapacityMinutes < 30 || c.dailyCapacityMinutes > 1440)
    e.push("daily schedule capacity must be 30 to 1440 minutes"); for (const w of c.availability)
    if (w.endMinute <= w.startMinute)
        e.push(`availability window ${w.id} must end after it starts`); for (const b of c.fixedBlocks)
    if (b.date == null && b.weekday == null)
        e.push(`fixed block ${b.id} needs a date or weekday`); return e; }
function addAvailabilityWindow(config, input) { return normalizeScheduleConfiguration({ ...config, availability: [...config.availability, { id: input.id ?? makeId("window"), weekday: input.weekday ?? 1, startMinute: input.startMinute ?? 1080, endMinute: input.endMinute ?? 1200 }] }); }
function addFixedScheduleBlock(config, input) { return normalizeScheduleConfiguration({ ...config, fixedBlocks: [...config.fixedBlocks, { id: input.id ?? makeId("fixed"), title: input.title ?? "Fixed block", date: input.date ?? null, weekday: input.weekday ?? null, startMinute: input.startMinute ?? 1080, durationMinutes: input.durationMinutes ?? 30 }] }); }
function planConfig(state) { const plan = state.plans.find(p => p.id === state.activePlanId); return normalizeScheduleConfiguration(plan?.configuration.schedule); }
function windowsFor(config, date) { const weekday = dateWeekday(date); return config.availability.filter(w => w.weekday === weekday).sort((a, b) => a.startMinute - b.startMinute); }
function fixedFor(config, date) { const weekday = dateWeekday(date); return config.fixedBlocks.filter(b => b.date === date || (b.date == null && b.weekday === weekday)); }
function overlap(a0, a1, b0, b1) { return Math.max(a0, b0) < Math.min(a1, b1); }
function intervalForActivity(a) { return a.scheduledStartMinute == null ? null : [a.scheduledStartMinute, a.scheduledStartMinute + a.durationMinutes]; }
function capacityFor(config, date) { const windows = windowsFor(config, date); const windowCapacity = windows.length ? windows.reduce((s, w) => s + (w.endMinute - w.startMinute), 0) : config.dailyCapacityMinutes; return Math.min(config.dailyCapacityMinutes, windowCapacity); }
function fixedMinutes(config, date) { const windows = windowsFor(config, date); if (!windows.length)
    return fixedFor(config, date).reduce((s, b) => s + b.durationMinutes, 0); return fixedFor(config, date).reduce((sum, b) => { const b0 = b.startMinute, b1 = b.startMinute + b.durationMinutes; return sum + windows.reduce((inside, w) => inside + Math.max(0, Math.min(b1, w.endMinute) - Math.max(b0, w.startMinute)), 0); }, 0); }
function findStart(config, date, duration, activities) {
    const windows = windowsFor(config, date);
    if (!windows.length)
        return null;
    const occupied = [];
    for (const b of fixedFor(config, date))
        occupied.push([b.startMinute, b.startMinute + b.durationMinutes]);
    for (const a of activities) {
        const r = intervalForActivity(a);
        if (r)
            occupied.push(r);
    }
    occupied.sort((a, b) => a[0] - b[0]);
    for (const w of windows) {
        let cursor = w.startMinute;
        for (const [s, e] of occupied) {
            if (e <= cursor || s >= w.endMinute)
                continue;
            if (cursor + duration <= s)
                return cursor;
            cursor = Math.max(cursor, e);
            if (cursor + duration > w.endMinute)
                break;
        }
        if (cursor + duration <= w.endMinute)
            return cursor;
    }
    return null;
}
function cycleSubjects(state, planId) { const subjects = state.subjects.filter(s => s.planId === planId && !s.archived).sort((a, b) => a.order - b.order); const cycle = state.studyCycles?.[planId] ?? state.studyCycle; const ids = (cycle?.subjectIds ?? []).filter(id => subjects.some(s => s.id === id)); if (!ids.length)
    return subjects; const cursor = Math.max(0, Math.min(ids.length - 1, cycle?.cursor ?? 0)); const rotated = [...ids.slice(cursor), ...ids.slice(0, cursor)]; return rotated.map(id => subjects.find(s => s.id === id)).filter(Boolean); }
function topicFor(state, subjectId) { return state.topics.filter(t => t.subjectId === subjectId && t.status !== "archived").sort((a, b) => { const p = (x) => x.priority === "high" ? 0 : x.priority === "normal" ? 1 : 2; return p(a) - p(b) || a.order - b.order; })[0] ?? null; }
function buildAdaptiveSchedulePreview(state, { at = new Date().toISOString(), days = 7, maxBlocks = 14 } = {}) {
    const generatedAt = new Date(at).toISOString();
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const empty = { baseRevision: state.revision, generatedAt, operations: [], summary: { add: 0, move: 0, conflicts: 0, overloadedDays: 0, unscheduled: 0 }, reasons: ["No active plan"], conflicts: [], days: [], unscheduled: [] };
    if (!plan)
        return empty;
    const config = planConfig(state), today = dayKey(generatedAt), horizon = Math.max(1, Math.min(31, Math.trunc(days))), limit = Math.max(1, Math.min(100, Math.trunc(maxBlocks)));
    const dates = Array.from({ length: horizon }, (_, i) => dayKey(plusDays(generatedAt, i)));
    const planned = state.activities.filter(a => a.planId === plan.id && a.status === "planned");
    const conflicts = [];
    const daySummaries = [];
    const byDate = new Map();
    for (const date of dates)
        byDate.set(date, planned.filter(a => a.scheduledFor === date));
    for (const a of planned.filter(a => a.locked && a.scheduledFor && a.scheduledFor < today))
        conflicts.push({ date: a.scheduledFor, kind: "locked-overdue", activityId: a.id, fixedBlockId: null, message: `Locked overdue activity remains on ${a.scheduledFor}` });
    for (const date of dates) {
        const items = byDate.get(date);
        const cap = Math.max(0, capacityFor(config, date) - fixedMinutes(config, date));
        const used = items.reduce((s, a) => s + a.durationMinutes, 0);
        if (used > cap)
            conflicts.push({ date, kind: "capacity", activityId: null, fixedBlockId: null, message: `Planned ${used} min exceeds ${cap} min available capacity` });
        const explicit = items.filter(a => a.scheduledStartMinute != null);
        for (let i = 0; i < explicit.length; i++)
            for (let j = i + 1; j < explicit.length; j++) {
                const a = explicit[i], b = explicit[j], ar = intervalForActivity(a), br = intervalForActivity(b);
                if (overlap(ar[0], ar[1], br[0], br[1]))
                    conflicts.push({ date, kind: "overlap", activityId: a.id, fixedBlockId: null, message: `${a.title} overlaps ${b.title}` });
            }
        for (const a of explicit) {
            const ar = intervalForActivity(a);
            for (const b of fixedFor(config, date)) {
                if (overlap(ar[0], ar[1], b.startMinute, b.startMinute + b.durationMinutes))
                    conflicts.push({ date, kind: "overlap", activityId: a.id, fixedBlockId: b.id, message: `${a.title} overlaps fixed block ${b.title}` });
            }
        }
        daySummaries.push({ date, capacityMinutes: capacityFor(config, date), fixedMinutes: fixedMinutes(config, date), plannedMinutes: used, remainingMinutes: Math.max(0, cap - used), overloaded: used > cap });
    }
    const operations = [];
    const unscheduled = [];
    const mutableByDate = new Map([...byDate].map(([d, a]) => [d, a.map(x => clone(x))]));
    const occupiedMinutes = (date) => mutableByDate.get(date).reduce((s, a) => s + a.durationMinutes, 0);
    const available = (date) => Math.max(0, capacityFor(config, date) - fixedMinutes(config, date) - occupiedMinutes(date));
    const candidates = [];
    for (const a of planned.filter(a => !a.locked && a.scheduledFor && a.scheduledFor < today).sort((a, b) => a.scheduledFor.localeCompare(b.scheduledFor)))
        candidates.push({ activity: a, title: a.title, kind: a.kind, durationMinutes: a.durationMinutes, subjectId: a.subjectId, topicId: a.topicId, origin: a.origin, reason: "Missed planned activity; moved forward without changing completed history" });
    for (const a of planned.filter(a => !a.locked && !a.scheduledFor).sort((a, b) => a.createdAt.localeCompare(b.createdAt)))
        candidates.push({ activity: a, title: a.title, kind: a.kind, durationMinutes: a.durationMinutes, subjectId: a.subjectId, topicId: a.topicId, origin: a.origin, reason: "Unscheduled planned work fitted into available capacity" });
    const due = state.reviews.filter(r => r.planId === plan.id && r.status === "due" && r.dueAt <= generatedAt).sort((a, b) => a.dueAt.localeCompare(b.dueAt));
    for (const r of due) {
        if (planned.some(a => a.kind === "review" && a.topicId === r.topicId))
            continue;
        candidates.push({ activity: null, title: state.topics.find(t => t.id === r.topicId)?.name ?? "Due review", kind: "review", durationMinutes: 20, subjectId: r.subjectId, topicId: r.topicId, origin: "review", reason: "Due review backed by persisted review evidence" });
    }
    for (const subject of cycleSubjects(state, plan.id)) {
        const topic = topicFor(state, subject.id);
        if (planned.some(a => a.kind === "study" && a.subjectId === subject.id))
            continue;
        candidates.push({ activity: null, title: topic ? `${subject.name}: ${topic.name}` : subject.name, kind: "study", durationMinutes: 40, subjectId: subject.id, topicId: topic?.id ?? null, origin: "cycle", reason: topic?.priority === "high" ? "Study-cycle subject with high-priority topic" : "Study-cycle subject in persisted cursor order" });
    }
    let usedOps = 0;
    for (const c of candidates) {
        if (usedOps >= limit)
            break;
        let placed = false;
        for (const date of dates) {
            if (available(date) < c.durationMinutes)
                continue;
            const start = findStart(config, date, c.durationMinutes, mutableByDate.get(date));
            if (windowsFor(config, date).length && start == null)
                continue;
            if (c.activity) {
                if (c.activity.scheduledFor === date && c.activity.scheduledStartMinute === start) {
                    placed = true;
                    break;
                }
                operations.push({ action: "move", activityId: c.activity.id, from: c.activity.scheduledFor, to: date, fromStartMinute: c.activity.scheduledStartMinute, toStartMinute: start, reason: c.reason });
                const source = c.activity.scheduledFor;
                if (source && mutableByDate.has(source))
                    mutableByDate.set(source, mutableByDate.get(source).filter(a => a.id !== c.activity.id));
                mutableByDate.get(date).push({ ...c.activity, scheduledFor: date, scheduledStartMinute: start });
            }
            else {
                operations.push({ action: "add", scheduledFor: date, scheduledStartMinute: start, activity: { title: c.title, kind: c.kind, durationMinutes: c.durationMinutes, subjectId: c.subjectId, topicId: c.topicId, origin: c.origin, queue: date === today ? "today" : "future", locked: false }, reason: c.reason });
                mutableByDate.get(date).push({ id: `preview-${usedOps}`, planId: plan.id, title: c.title, kind: c.kind, durationMinutes: c.durationMinutes, subjectId: c.subjectId, topicId: c.topicId, origin: c.origin, queue: date === today ? "today" : "future", status: "planned", scheduledFor: date, scheduledStartMinute: start, locked: false, scheduleProvenance: null, createdAt: generatedAt, updatedAt: generatedAt });
            }
            usedOps++;
            placed = true;
            break;
        }
        if (!placed)
            unscheduled.push({ activityId: c.activity?.id ?? null, title: c.title, durationMinutes: c.durationMinutes, reason: "No remaining capacity inside the preview horizon" });
    }
    const finalDays = dates.map(date => { const fixed = fixedMinutes(config, date), capacity = capacityFor(config, date), used = mutableByDate.get(date).reduce((s, a) => s + a.durationMinutes, 0); return { date, capacityMinutes: capacity, fixedMinutes: fixed, plannedMinutes: used, remainingMinutes: Math.max(0, capacity - fixed - used), overloaded: used > Math.max(0, capacity - fixed) }; });
    const reasons = [];
    if (conflicts.length)
        reasons.push("Existing schedule conflicts are reported, never silently rewritten.");
    if (unscheduled.length)
        reasons.push("Work that cannot fit remains explicitly unscheduled.");
    if (!operations.length && !conflicts.length)
        reasons.push("No schedule changes are justified by current local plan data.");
    return { baseRevision: state.revision, generatedAt, operations, summary: { add: operations.filter(o => o.action === "add").length, move: operations.filter(o => o.action === "move").length, conflicts: conflicts.length, overloadedDays: finalDays.filter(d => d.overloaded).length, unscheduled: unscheduled.length }, reasons, conflicts, days: finalDays, unscheduled };
}
