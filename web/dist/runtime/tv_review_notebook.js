"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TV_ERROR_CATEGORIES = exports.TV_REVIEW_RATINGS = void 0;
exports.tvReviewRatingOptionId = tvReviewRatingOptionId;
exports.tvErrorCategoryOptionId = tvErrorCategoryOptionId;
exports.tvReviewProgressPercent = tvReviewProgressPercent;
exports.tvReviewIndexAfterCompletion = tvReviewIndexAfterCompletion;
exports.TV_REVIEW_RATINGS = ["again", "hard", "good", "easy"];
exports.TV_ERROR_CATEGORIES = ["content-gap", "forgotten", "concept-confusion", "interpretation", "distraction", "calculation", "time-pressure", "disputed", "other"];
function tvReviewRatingOptionId(rating) { return `rating-${rating}`; }
function tvErrorCategoryOptionId(category) { return `category-${category}`; }
function tvReviewProgressPercent(completed, total) { return total <= 0 ? 100 : Math.max(0, Math.min(100, Math.round((Math.max(0, completed) / total) * 100))); }
function tvReviewIndexAfterCompletion(index, nextTotal) { return nextTotal <= 0 ? 0 : Math.max(0, Math.min(index, nextTotal - 1)); }
