"use strict";

import type { Activity, Review, StudyState } from "./types";
import {
  type StudyFlowAttentionCategory,
  type StudyFlowAttentionOccurrence,
} from "./sflp_link";
import { stableWebAttentionOccurrenceId } from "./attention";

export type StudyFlowCalendarPrivacyMode = "full_details" | "private_title" | "busy_only";
export type StudyFlowCalendarExportProvider = "ics_file" | "webcal_feed" | "google_calendar" | "outlook_calendar" | "apple_calendar";
export type StudyFlowCalendarEventKind = "study_block" | "review" | "mock_exam" | "exam_date" | "daily_summary" | "schedule_change";

export interface StudyFlowCalendarExportPreferences {
  provider: StudyFlowCalendarExportProvider;
  privacyMode: StudyFlowCalendarPrivacyMode;
  includeCompleted: boolean;
  includeReviews: boolean;
  includeExamDates: boolean;
  includeAttentionLead: boolean;
  lookaheadDays: number;
  historyDays: number;
  reminderLeadMinutes: number;
  calendarName: string;
}

export interface StudyFlowCalendarScheduleItem {
  itemId: string;
  workspaceId: string;
  title: string;
  body: string;
  kind: StudyFlowCalendarEventKind;
  startEpochMillis: number;
  durationMinutes: number;
  timezoneId: string | null;
  allDay: boolean;
  completed: boolean;
  routeHash: string;
  attentionCategory: StudyFlowAttentionCategory;
  reminderLeadMinutes: number;
}

export interface StudyFlowCalendarAttentionEvent {
  exportId: string;
  item: StudyFlowCalendarScheduleItem;
  summary: string;
  description: string;
  startsAtEpochMillis: number;
  endsAtEpochMillis: number;
  attentionOccurrence: StudyFlowAttentionOccurrence | null;
}

export interface StudyFlowCalendarExportDiagnostics {
  inputItemCount: number;
  exportedEventCount: number;
  completedSuppressedCount: number;
  outsideWindowSuppressedCount: number;
  reviewSuppressedCount: number;
  attentionOccurrenceCount: number;
  privacyMode: StudyFlowCalendarPrivacyMode;
  explanation: string;
}

export interface StudyFlowCalendarExportFeed {
  preferences: StudyFlowCalendarExportPreferences;
  generatedAtEpochMillis: number;
  events: StudyFlowCalendarAttentionEvent[];
  diagnostics: StudyFlowCalendarExportDiagnostics;
  icsText: string;
}

const DAY_MS = 86_400_000;
const MINUTE_MS = 60_000;
const DEFAULT_CALENDAR_PREFS: StudyFlowCalendarExportPreferences = {
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

export function defaultCalendarAttentionPreferences(): StudyFlowCalendarExportPreferences {
  return { ...DEFAULT_CALENDAR_PREFS };
}

function clampNumber(value: number, min: number, max: number, fallback: number): number {
  return Number.isFinite(value) ? Math.max(min, Math.min(max, Math.round(value))) : fallback;
}

function localDateAtMinute(dateKey: string, minute: number): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) return null;
  const [year, month, day] = dateKey.split("-").map(Number);
  const value = new Date(year, month - 1, day, Math.floor(minute / 60), minute % 60, 0, 0).getTime();
  return Number.isFinite(value) ? value : null;
}

function dateKeyToAllDayStart(dateKey: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) return null;
  const [year, month, day] = dateKey.split("-").map(Number);
  const value = new Date(year, month - 1, day, 0, 0, 0, 0).getTime();
  return Number.isFinite(value) ? value : null;
}

function subjectName(state: StudyState, subjectId: string | null | undefined): string | null {
  return subjectId ? state.subjects.find(subject => subject.id === subjectId)?.name ?? null : null;
}

function activityToCalendarItem(state: StudyState, activity: Activity, fallbackWorkspaceId: string): StudyFlowCalendarScheduleItem | null {
  if (!activity.scheduledFor) return null;
  const startMinute = activity.scheduledStartMinute ?? 9 * 60;
  const start = localDateAtMinute(activity.scheduledFor, startMinute);
  if (start == null) return null;
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

function reviewToCalendarItem(state: StudyState, review: Review, fallbackWorkspaceId: string): StudyFlowCalendarScheduleItem | null {
  if (!review.dueAt || review.status !== "due") return null;
  const dueKey = review.dueAt.slice(0, 10);
  const start = localDateAtMinute(dueKey, 8 * 60);
  if (start == null) return null;
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

function examDateCalendarItem(state: StudyState, fallbackWorkspaceId: string): StudyFlowCalendarScheduleItem | null {
  const activePlan = state.plans.find(plan => plan.id === state.activePlanId) ?? state.plans[0];
  const examDate = activePlan?.configuration.publicExam?.target.examDate ?? activePlan?.examDate ?? null;
  if (!activePlan || !examDate) return null;
  const start = dateKeyToAllDayStart(examDate);
  if (start == null) return null;
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

export function buildStudyFlowCalendarScheduleItems(state: StudyState): StudyFlowCalendarScheduleItem[] {
  const fallbackWorkspaceId = state.activePlanId ?? "studyflow-local";
  const activities = state.activities
    .map(activity => activityToCalendarItem(state, activity, fallbackWorkspaceId))
    .filter((item): item is StudyFlowCalendarScheduleItem => item != null);
  const reviews = state.reviews
    .map(review => reviewToCalendarItem(state, review, fallbackWorkspaceId))
    .filter((item): item is StudyFlowCalendarScheduleItem => item != null);
  const exam = examDateCalendarItem(state, fallbackWorkspaceId);
  const seen = new Set<string>();
  return [...activities, ...reviews, ...(exam ? [exam] : [])]
    .filter(item => {
      const key = `${item.kind}:${item.itemId}:${item.startEpochMillis}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => a.startEpochMillis - b.startEpochMillis || a.itemId.localeCompare(b.itemId));
}

function sanitizePreferences(input: Partial<StudyFlowCalendarExportPreferences> = {}): StudyFlowCalendarExportPreferences {
  const privacyMode = input.privacyMode === "private_title" || input.privacyMode === "busy_only" ? input.privacyMode : "full_details";
  return {
    ...DEFAULT_CALENDAR_PREFS,
    ...input,
    privacyMode,
    lookaheadDays: clampNumber(input.lookaheadDays ?? DEFAULT_CALENDAR_PREFS.lookaheadDays, 1, 366, DEFAULT_CALENDAR_PREFS.lookaheadDays),
    historyDays: clampNumber(input.historyDays ?? DEFAULT_CALENDAR_PREFS.historyDays, 0, 366, DEFAULT_CALENDAR_PREFS.historyDays),
    reminderLeadMinutes: clampNumber(input.reminderLeadMinutes ?? DEFAULT_CALENDAR_PREFS.reminderLeadMinutes, 0, 1440, DEFAULT_CALENDAR_PREFS.reminderLeadMinutes),
    calendarName: String(input.calendarName || DEFAULT_CALENDAR_PREFS.calendarName),
  };
}

function privateCopy(item: StudyFlowCalendarScheduleItem, privacyMode: StudyFlowCalendarPrivacyMode): { summary: string; description: string } {
  if (privacyMode === "busy_only") return { summary: "Busy", description: "Busy time exported by StudyFlow. Details hidden." };
  if (privacyMode === "private_title") return {
    summary: "StudyFlow block",
    description: "Study details are hidden by your ATT09 calendar privacy setting. Open StudyFlow for the full plan.",
  };
  return { summary: item.title, description: `${item.body}\nOpen in StudyFlow: ${item.routeHash}` };
}

function makeAttentionOccurrence(item: StudyFlowCalendarScheduleItem, summary: string, description: string, preferences: StudyFlowCalendarExportPreferences): StudyFlowAttentionOccurrence | null {
  if (!preferences.includeAttentionLead) return null;
  const lead = item.kind === "exam_date" ? item.reminderLeadMinutes : preferences.reminderLeadMinutes;
  const scheduledAtEpochMillis = Math.max(0, item.startEpochMillis - lead * MINUTE_MS);
  return {
    occurrenceId: stableWebAttentionOccurrenceId(["calendar", item.workspaceId, item.itemId, String(item.startEpochMillis)]),
    workspaceId: item.workspaceId,
    scheduleEntityId: item.itemId,
    category: item.attentionCategory,
    priority: item.kind === "exam_date" ? "time_sensitive" : "normal",
    scheduledAtEpochMillis,
    originalScheduledAtEpochMillis: scheduledAtEpochMillis,
    expiresAtEpochMillis: item.startEpochMillis + Math.max(0, item.durationMinutes) * MINUTE_MS,
    timezoneId: item.timezoneId ?? undefined,
    deliveryPolicy: "active_then_primary",
    actions: [{ kind: "open", payload: {} }, { kind: "snooze", payload: {} }, { kind: "dismiss", payload: {} }],
    sourceDeviceId: "web-calendar-export",
    targetDeviceIds: [],
    payload: { title: summary, body: description, routeHash: item.routeHash, calendarItemId: item.itemId, calendarKind: item.kind },
  };
}

export function buildStudyFlowCalendarExport(
  state: StudyState,
  options: Partial<StudyFlowCalendarExportPreferences> & { nowEpochMillis?: number } = {},
): StudyFlowCalendarExportFeed {
  const preferences = sanitizePreferences(options);
  const now = Number.isFinite(options.nowEpochMillis) ? Number(options.nowEpochMillis) : Date.now();
  const windowStart = now - preferences.historyDays * DAY_MS;
  const windowEnd = now + preferences.lookaheadDays * DAY_MS;
  let completedSuppressedCount = 0;
  let outsideWindowSuppressedCount = 0;
  let reviewSuppressedCount = 0;
  const sourceItems = buildStudyFlowCalendarScheduleItems(state).map(item => ({ ...item, reminderLeadMinutes: preferences.reminderLeadMinutes }));
  const events = sourceItems.flatMap(item => {
    if (item.completed && !preferences.includeCompleted) { completedSuppressedCount += 1; return []; }
    if (item.kind === "review" && !preferences.includeReviews) { reviewSuppressedCount += 1; return []; }
    if (item.kind === "exam_date" && !preferences.includeExamDates) { reviewSuppressedCount += 1; return []; }
    if (item.startEpochMillis < windowStart || item.startEpochMillis > windowEnd) { outsideWindowSuppressedCount += 1; return []; }
    const copy = privateCopy(item, preferences.privacyMode);
    const end = item.allDay ? item.startEpochMillis : item.startEpochMillis + Math.max(0, item.durationMinutes) * MINUTE_MS;
    return [{
      exportId: stableWebAttentionOccurrenceId(["ics", item.workspaceId, item.itemId, String(item.startEpochMillis)]),
      item,
      summary: copy.summary,
      description: copy.description,
      startsAtEpochMillis: item.startEpochMillis,
      endsAtEpochMillis: end,
      attentionOccurrence: makeAttentionOccurrence(item, copy.summary, copy.description, preferences),
    }];
  });
  const diagnostics: StudyFlowCalendarExportDiagnostics = {
    inputItemCount: sourceItems.length,
    exportedEventCount: events.length,
    completedSuppressedCount,
    outsideWindowSuppressedCount,
    reviewSuppressedCount,
    attentionOccurrenceCount: events.filter(event => event.attentionOccurrence != null).length,
    privacyMode: preferences.privacyMode,
    explanation: "ATT09 exports portable calendar events and ATT01-compatible attention occurrences without silently mutating an external calendar.",
  };
  const feed: Omit<StudyFlowCalendarExportFeed, "icsText"> = { preferences, generatedAtEpochMillis: now, events, diagnostics };
  return { ...feed, icsText: serializeStudyFlowCalendarIcs(feed) };
}

function icsEscape(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\n/g, "\\n");
}

function utcStamp(epochMillis: number): string {
  return new Date(epochMillis).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

function localDateStamp(epochMillis: number): string {
  const date = new Date(epochMillis);
  return `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
}

export function serializeStudyFlowCalendarIcs(feed: Omit<StudyFlowCalendarExportFeed, "icsText">): string {
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
    } else {
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

export function calendarAttentionSummary(feed: StudyFlowCalendarExportFeed): string {
  return `${feed.events.length} events · ${feed.diagnostics.attentionOccurrenceCount} attention reminders · ${feed.diagnostics.privacyMode.replace("_", " ")}`;
}

export function downloadStudyFlowCalendarIcs(feed: StudyFlowCalendarExportFeed): void {
  const safeName = feed.preferences.calendarName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "") || "studyflow-schedule";
  const blob = new Blob([feed.icsText], { type: "text/calendar;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${safeName}.ics`;
    anchor.click();
  } finally {
    URL.revokeObjectURL(url);
  }
}
