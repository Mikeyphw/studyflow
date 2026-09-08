"use strict";

import type { Plan, PublicExamResolvedDate, StudyState, StudyStrategyType } from "./types";


function localDateKey(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export const STUDY_STRATEGY_TYPES: readonly StudyStrategyType[] = [
  "FIXED_WEEKLY",
  "STUDY_CYCLE",
  "GOAL_BASED",
  "ADAPTIVE",
  "FREE_GUIDED",
  "INTENSIVE_SPRINT",
  "COUNTDOWN",
] as const;

export function normalizeStudyStrategyType(value: unknown): StudyStrategyType {
  const candidate = String(value ?? "").trim().toUpperCase() as StudyStrategyType;
  return STUDY_STRATEGY_TYPES.includes(candidate) ? candidate : "FIXED_WEEKLY";
}

export function resolvePublicExamDate(plan: Plan): PublicExamResolvedDate {
  if (plan.mode !== "EXAM_PLAN") return { date: null, source: "none", official: false };
  const target = plan.configuration.publicExam?.target;
  if (target?.dateStatus === "confirmed" && target.examDate) return { date: target.examDate, source: "confirmed-target", official: true };
  if (target?.dateStatus === "expected" && target.examDate) return { date: target.examDate, source: "expected-target", official: false };
  // Legacy plan-only dates remain official only when no target date is carrying a newer truth state.
  if (!target?.examDate && plan.examDate) return { date: plan.examDate, source: "legacy-plan", official: true };
  return { date: null, source: "none", official: false };
}

export function officialCountdownDate(plan: Plan): string | null {
  const resolved = resolvePublicExamDate(plan);
  return resolved.official ? resolved.date : null;
}

function dateOnlyNumber(value: string): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) throw new TypeError(`Invalid date-only value: ${value}`);
  return Math.floor(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])) / 86_400_000);
}

export function daysUntilOfficialExam(plan: Plan, at: Date = new Date()): number | null {
  const examDate = officialCountdownDate(plan);
  if (!examDate) return null;
  return Math.max(0, dateOnlyNumber(examDate) - dateOnlyNumber(localDateKey(at)));
}

export function publicExamCoverageTopicCount(state: StudyState, planId: string): number {
  return state.topics.filter(topic => topic.planId === planId && topic.status !== "archived").length;
}

export function shouldOfferStudyCycle(plan: Plan | null | undefined): boolean {
  return Boolean(plan && plan.configuration.schedule.strategyType === "STUDY_CYCLE");
}
