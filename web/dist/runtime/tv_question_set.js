"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TV_QUESTION_SET_LIMIT = void 0;
exports.tvQuestionChoiceOptionId = tvQuestionChoiceOptionId;
exports.tvQuestionChoiceIndex = tvQuestionChoiceIndex;
exports.tvQuestionProgressPercent = tvQuestionProgressPercent;
exports.tvQuestionChoiceLabel = tvQuestionChoiceLabel;
exports.TV_QUESTION_SET_LIMIT = 40;
function tvQuestionChoiceOptionId(index) { return `choice-${Math.max(0, Math.trunc(index))}`; }
function tvQuestionChoiceIndex(optionId) {
    const match = /^choice-(\d+)$/.exec(String(optionId !== null && optionId !== void 0 ? optionId : ""));
    if (!match)
        return null;
    const index = Number(match[1]);
    return Number.isSafeInteger(index) && index >= 0 ? index : null;
}
function tvQuestionProgressPercent(index, total) {
    if (total <= 0)
        return 0;
    return Math.max(0, Math.min(100, Math.round(((Math.max(0, index) + 1) / total) * 100)));
}
function tvQuestionChoiceLabel(index) {
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    return index >= 0 && index < alphabet.length ? alphabet[index] : String(index + 1);
}
