"use strict";
import { deriveGenericQuestionQuotaPlan, derivePublicExamQuestionQuotaPlan, deriveExamReadinessParity } from "./parity_intelligence";
import {
  buildContentCleanupRefined, buildExamReportRefined, buildExamRunbookRefined, buildStudySprintRefined,
  deriveWeeklyCoachRefined, renderExamReportHtmlRefined, renderExamReportPdf, validateStudySprintApply,
} from "./refinement";
import type { ContentCleanupPreview, ExamReportSnapshot, ExamRunbook, QuestionQuotaPlan, ReadinessExplanation, StudySprintPreview, StudyState, WeeklyCoachReview } from "./types";
export function deriveQuestionQuotaPlan(state:StudyState,at=new Date()):QuestionQuotaPlan{return derivePublicExamQuestionQuotaPlan(state,at)??deriveGenericQuestionQuotaPlan(state,at);}
export function deriveReadinessExplanation(state:StudyState,at=new Date()):ReadinessExplanation{return deriveExamReadinessParity(state,at);}
export function deriveWeeklyCoach(state:StudyState,at=new Date()):WeeklyCoachReview{return deriveWeeklyCoachRefined(state,at);}
export function buildContentCleanupPreview(state:StudyState,at=new Date()):ContentCleanupPreview{return buildContentCleanupRefined(state,at);}
export function buildExamRunbook(state:StudyState,at=new Date()):ExamRunbook{return buildExamRunbookRefined(state,at);}
export function buildStudySprintPreview(state:StudyState,at=new Date()):StudySprintPreview{return buildStudySprintRefined(state,at);}
export function buildExamReportSnapshot(state:StudyState,at=new Date()):ExamReportSnapshot{return buildExamReportRefined(state,at);}
export function renderExamReportHtml(report:ExamReportSnapshot):string{return renderExamReportHtmlRefined(report);}
export { renderExamReportPdf, validateStudySprintApply };
