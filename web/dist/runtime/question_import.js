"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.detectQuestionImportFormat = detectQuestionImportFormat;
exports.parseQuestionImport = parseQuestionImport;
exports.buildQuestionImportPreview = buildQuestionImportPreview;
const clean = (v) => String(v !== null && v !== void 0 ? v : "").trim();
const opt = (v) => { const x = clean(v); return x || null; };
const numeric = (v) => { if (v == null || v === "")
    return null; const n = Number(v); return Number.isFinite(n) ? n : null; };
const splitChoices = (v) => clean(v).split(/\s*\|\s*/).map(clean).filter(Boolean);
const splitTags = (v) => Array.from(new Set(clean(v).split(/\s*[,;|]\s*/).map(clean).filter(Boolean)));
const difficulty = (v) => { const x = clean(v).toLowerCase(); return ["easy", "medium", "hard"].includes(x) ? x : null; };
const bool = (v) => ["1", "true", "yes", "y", "sim"].includes(clean(v).toLowerCase());
function csvLine(line, delimiter) { const out = []; let cur = "", quoted = false; for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
        if (quoted && line[i + 1] === '"') {
            cur += '"';
            i++;
        }
        else
            quoted = !quoted;
    }
    else if (c === delimiter && !quoted) {
        out.push(cur);
        cur = "";
    }
    else
        cur += c;
} out.push(cur); return out.map(clean); }
function fingerprint(prompt, answer) { return `${prompt.toLowerCase().replace(/\s+/g, " ")}|${String(answer !== null && answer !== void 0 ? answer : "").toLowerCase().trim()}`; }
function draft(row, x) { var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o; const prompt = clean((_b = (_a = x.prompt) !== null && _a !== void 0 ? _a : x.question) !== null && _b !== void 0 ? _b : x.front); if (!prompt)
    return null; const topic = clean((_d = (_c = x.topic) !== null && _c !== void 0 ? _c : x.topicPath) !== null && _d !== void 0 ? _d : x.topic_path); const year = numeric(x.year), points = numeric(x.points); return { row, prompt, choices: splitChoices((_e = x.choices) !== null && _e !== void 0 ? _e : x.options), answer: opt((_f = x.answer) !== null && _f !== void 0 ? _f : x.back), explanation: opt(x.explanation), source: opt(x.source), subjectName: opt((_g = x.subject) !== null && _g !== void 0 ? _g : x.subjectName), topicPath: topic ? topic.split(/\s*>\s*/).filter(Boolean) : [], examBoard: opt((_j = (_h = x.examBoard) !== null && _h !== void 0 ? _h : x.board) !== null && _j !== void 0 ? _j : x.banca), organization: opt(x.organization), role: opt((_k = x.role) !== null && _k !== void 0 ? _k : x.cargo), examStage: opt((_l = x.examStage) !== null && _l !== void 0 ? _l : x.stage), year: year == null ? null : Math.trunc(year), authorship: opt(x.authorship), externalReference: opt((_m = x.externalReference) !== null && _m !== void 0 ? _m : x.reference), points: points != null && points > 0 ? points : null, difficulty: difficulty(x.difficulty), favorite: bool(x.favorite), disputed: bool(x.disputed), tags: splitTags((_o = x.tags) !== null && _o !== void 0 ? _o : x.tag) }; }
function detectQuestionImportFormat(source) { var _a; const first = (_a = String(source !== null && source !== void 0 ? source : "").split(/\r?\n/).find(x => x.trim())) !== null && _a !== void 0 ? _a : ""; if (/^#{1,6}\s+/.test(first) || /^Q(?:uestion)?\s*:/i.test(first))
    return "markdown"; return first.includes("\t") ? "tsv" : "csv"; }
function parseQuestionImport(source, format = detectQuestionImportFormat(source)) {
    var _a, _b;
    const issues = [];
    const drafts = [];
    const text = String(source !== null && source !== void 0 ? source : "").trim();
    if (!text)
        return { drafts, issues: [{ severity: "error", row: null, code: "empty", message: "Question import source is empty." }] };
    if (format === "markdown") {
        const blocks = text.split(/\n\s*\n(?=(?:#{1,6}\s+|Q(?:uestion)?\s*:))/i);
        let row = 1;
        for (const block of blocks) {
            const lines = block.split(/\r?\n/).map(clean).filter(Boolean);
            const header = (_a = lines.shift()) !== null && _a !== void 0 ? _a : "";
            const prompt = header.replace(/^#{1,6}\s*/, "").replace(/^Q(?:uestion)?\s*:\s*/i, "");
            const x = { prompt };
            for (const line of lines) {
                const m = line.match(/^([A-Za-z][A-Za-z _-]*):\s*(.*)$/);
                if (m)
                    x[m[1].replace(/[ -]+/g, "").toLowerCase()] = m[2];
                else if (/^[-*]\s+/.test(line))
                    x.choices = [...(Array.isArray(x.choices) ? x.choices : []), line.replace(/^[-*]\s+/, "")].join("|");
            }
            const d = draft(row++, x);
            if (d)
                drafts.push(d);
            else
                issues.push({ severity: "error", row: row - 1, code: "missing-prompt", message: "Question block has no prompt." });
        }
    }
    else {
        const delimiter = format === "tsv" ? "\t" : ",";
        const lines = text.split(/\r?\n/).filter(x => x.trim());
        const headers = csvLine((_b = lines.shift()) !== null && _b !== void 0 ? _b : "", delimiter).map(x => x.replace(/[ -]+/g, "").toLowerCase());
        if (!headers.some(x => ["prompt", "question", "front"].includes(x.toLowerCase())))
            return { drafts, issues: [{ severity: "error", row: 1, code: "header", message: "Delimited import requires a prompt/question/front column." }] };
        lines.forEach((line, i) => { const cells = csvLine(line, delimiter); const x = {}; headers.forEach((h, j) => { var _a; return x[h] = (_a = cells[j]) !== null && _a !== void 0 ? _a : ""; }); const d = draft(i + 2, x); if (d)
            drafts.push(d);
        else
            issues.push({ severity: "error", row: i + 2, code: "missing-prompt", message: "Question row has no prompt." }); });
    }
    return { drafts, issues };
}
function buildQuestionImportPreview(state, source, format = detectQuestionImportFormat(source)) { const { drafts, issues } = parseQuestionImport(source, format); const existing = new Set(state.questions.filter(q => q.planId === state.activePlanId).map(q => fingerprint(q.prompt, q.answer))); const seen = new Set(), accepted = []; let duplicates = 0; for (const d of drafts) {
    const key = fingerprint(d.prompt, d.answer);
    if (existing.has(key) || seen.has(key)) {
        duplicates++;
        issues.push({ severity: "warning", row: d.row, code: "duplicate", message: "Duplicate question was excluded from Apply." });
        continue;
    }
    seen.add(key);
    if (d.year != null && (d.year < 1900 || d.year > 2200)) {
        issues.push({ severity: "error", row: d.row, code: "year", message: "Question year must be between 1900 and 2200." });
        continue;
    }
    if (d.subjectName) {
        const subject = state.subjects.find(s => s.planId === state.activePlanId && s.name.toLowerCase() === d.subjectName.toLowerCase());
        if (!subject) {
            issues.push({ severity: "error", row: d.row, code: "subject", message: `Subject not found in active plan: ${d.subjectName}.` });
            continue;
        }
        if (d.topicPath.length) {
            let parent = null;
            let ok = true;
            for (const name of d.topicPath) {
                const topic = state.topics.find(t => { var _a; return t.subjectId === subject.id && ((_a = t.parentTopicId) !== null && _a !== void 0 ? _a : null) === parent && t.name.toLowerCase() === name.toLowerCase(); });
                if (!topic) {
                    ok = false;
                    break;
                }
                parent = topic.id;
            }
            if (!ok) {
                issues.push({ severity: "error", row: d.row, code: "topic", message: `Topic path not found in active plan: ${d.topicPath.join(" > ")}.` });
                continue;
            }
        }
    }
    accepted.push(d);
} const invalid = issues.filter(x => x.severity === "error").length; return { baseRevision: state.revision, generatedAt: new Date().toISOString(), format, ready: invalid === 0 && accepted.length > 0, drafts, accepted, issues, summary: { parsed: drafts.length, accepted: accepted.length, duplicates, invalid } }; }
