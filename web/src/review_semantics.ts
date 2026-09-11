"use strict";

export const REVIEW_DAY_MS = 86_400_000;
export const DEFAULT_REVIEW_EASE_FACTOR = 2.5;
export const MIN_REVIEW_EASE_FACTOR = 1.3;
export const MAX_PUBLIC_EXAM_EASE_FACTOR = 3.0;

export interface ReviewProgressionInput {
  intervalDays: number;
  repetitionCount: number;
  easeFactor: number;
  grade: number;
  reviewedAtEpochMillis: number;
}

export interface ReviewProgressionResult {
  intervalDays: number;
  repetitionCount: number;
  easeFactor: number;
  dueAtEpochMillis: number;
}

function normalizedGrade(grade:number):number {
  if(!Number.isFinite(grade)) throw new TypeError("Review grade must be finite");
  const value=Math.round(grade);
  if(value<0||value>5) throw new RangeError("Review grade must be between 0 and 5");
  return value;
}

/** Mirrors core/domain SpacedRepetitionScheduler for non-public-exam review progression. */
export function scheduleSpacedRepetition(input:ReviewProgressionInput):ReviewProgressionResult {
  const grade=normalizedGrade(input.grade);
  const qualityGap=5-grade;
  const easeFactor=Math.max(MIN_REVIEW_EASE_FACTOR,input.easeFactor+(0.1-qualityGap*(0.08+qualityGap*0.02)));
  const repetitionCount=grade<3?0:input.repetitionCount+1;
  const intervalDays=grade<3?1:repetitionCount<=1?1:repetitionCount===2?3:Math.max(4,Math.round(Math.max(1,input.intervalDays)*easeFactor));
  return {intervalDays,repetitionCount,easeFactor,dueAtEpochMillis:input.reviewedAtEpochMillis+intervalDays*REVIEW_DAY_MS};
}

/** Mirrors PublicExamReviewPolicy while preserving the user's configured interval ladder. */
export function schedulePublicExamReview(input:ReviewProgressionInput,intervalsDays:number[]):ReviewProgressionResult {
  const grade=normalizedGrade(input.grade);
  const intervals=intervalsDays.map(Number).filter(v=>Number.isInteger(v)&&v>=1&&v<=365);
  if(intervals.length<1||intervals.length>8) throw new TypeError("Public-exam review policy needs between 1 and 8 intervals");
  for(let i=1;i<intervals.length;i++) if(intervals[i]!<intervals[i-1]!) throw new TypeError("Public-exam review intervals must be non-decreasing");
  const repetitionCount=input.repetitionCount+1;
  const index=grade<3?0:Math.min(repetitionCount,intervals.length-1);
  const intervalDays=intervals[index]!;
  const easeFactor=grade<3?Math.max(MIN_REVIEW_EASE_FACTOR,input.easeFactor-0.2):grade===5?Math.min(MAX_PUBLIC_EXAM_EASE_FACTOR,input.easeFactor+0.1):input.easeFactor;
  return {intervalDays,repetitionCount,easeFactor,dueAtEpochMillis:input.reviewedAtEpochMillis+intervalDays*REVIEW_DAY_MS};
}
