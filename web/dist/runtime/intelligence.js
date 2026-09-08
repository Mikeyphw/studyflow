"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateStudySprintApply = exports.renderExamReportPdf = void 0;
exports.deriveQuestionQuotaPlan = deriveQuestionQuotaPlan;
exports.deriveReadinessExplanation = deriveReadinessExplanation;
exports.deriveWeeklyCoach = deriveWeeklyCoach;
exports.buildContentCleanupPreview = buildContentCleanupPreview;
exports.buildExamRunbook = buildExamRunbook;
exports.buildStudySprintPreview = buildStudySprintPreview;
exports.buildExamReportSnapshot = buildExamReportSnapshot;
exports.renderExamReportHtml = renderExamReportHtml;
const parity_intelligence_1 = require("./parity_intelligence");
const refinement_1 = require("./refinement");
Object.defineProperty(exports, "renderExamReportPdf", { enumerable: true, get: function () { return refinement_1.renderExamReportPdf; } });
Object.defineProperty(exports, "validateStudySprintApply", { enumerable: true, get: function () { return refinement_1.validateStudySprintApply; } });
function deriveQuestionQuotaPlan(state, at = new Date()) { var _a; return (_a = (0, parity_intelligence_1.derivePublicExamQuestionQuotaPlan)(state, at)) !== null && _a !== void 0 ? _a : (0, parity_intelligence_1.deriveGenericQuestionQuotaPlan)(state, at); }
function deriveReadinessExplanation(state, at = new Date()) { return (0, parity_intelligence_1.deriveExamReadinessParity)(state, at); }
function deriveWeeklyCoach(state, at = new Date()) { return (0, refinement_1.deriveWeeklyCoachRefined)(state, at); }
function buildContentCleanupPreview(state, at = new Date()) { return (0, refinement_1.buildContentCleanupRefined)(state, at); }
function buildExamRunbook(state, at = new Date()) { return (0, refinement_1.buildExamRunbookRefined)(state, at); }
function buildStudySprintPreview(state, at = new Date()) { return (0, refinement_1.buildStudySprintRefined)(state, at); }
function buildExamReportSnapshot(state, at = new Date()) { return (0, refinement_1.buildExamReportRefined)(state, at); }
function renderExamReportHtml(report) { return (0, refinement_1.renderExamReportHtmlRefined)(report); }
