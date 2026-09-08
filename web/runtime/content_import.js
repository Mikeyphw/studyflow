"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.detectPlanContentImportFormat = detectPlanContentImportFormat;
exports.parsePlanContentImport = parsePlanContentImport;
exports.buildPlanContentImportPreview = buildPlanContentImportPreview;
const PRIORITIES = new Set(["low", "normal", "high"]);
const STATUSES = new Set(["not-started", "learning", "reviewing", "mastered", "archived"]);
function text(value) { return String(value !== null && value !== void 0 ? value : "").trim(); }
function optionalText(value) { const valueText = text(value); return valueText || null; }
function normalized(value) { return text(value).replace(/\s+/g, " ").toLocaleLowerCase(); }
function pathKey(subject, path) { return [normalized(subject), ...path.map(normalized)].join("\u001f"); }
function cleanPath(parts) { return parts.map(text).filter(Boolean); }
function splitPath(value) { return cleanPath(text(value).split(/\s*(?:>|\/|::)\s*/g)); }
function finiteWeight(value) {
    if (value == null || text(value) === "")
        return undefined;
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : null;
}
function priority(value) { const v = normalized(value); return PRIORITIES.has(v) ? v : undefined; }
function status(value) {
    const raw = normalized(value).replace(/[_ ]+/g, "-");
    return STATUSES.has(raw) ? raw : undefined;
}
function fingerprint(source) {
    let hash = 2166136261;
    for (let index = 0; index < source.length; index += 1) {
        hash ^= source.charCodeAt(index);
        hash = Math.imul(hash, 16777619);
    }
    return `fnv1a32-${(hash >>> 0).toString(16).padStart(8, "0")}`;
}
function parseDelimitedLine(line, delimiter) {
    const out = [];
    let current = "";
    let quoted = false;
    for (let index = 0; index < line.length; index += 1) {
        const char = line[index];
        if (char === '"') {
            if (quoted && line[index + 1] === '"') {
                current += '"';
                index += 1;
            }
            else
                quoted = !quoted;
        }
        else if (char === delimiter && !quoted) {
            out.push(current.trim());
            current = "";
        }
        else
            current += char;
    }
    out.push(current.trim());
    return out;
}
function rowFromObject(value, row, issues) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m;
    const subject = text((_c = (_b = (_a = value.subject) !== null && _a !== void 0 ? _a : value.subjectName) !== null && _b !== void 0 ? _b : value.discipline) !== null && _c !== void 0 ? _c : value.disciplina);
    const explicitPath = (_e = (_d = value.path) !== null && _d !== void 0 ? _d : value.topicPath) !== null && _e !== void 0 ? _e : value.topics;
    let topicPath = [];
    if (Array.isArray(explicitPath))
        topicPath = cleanPath(explicitPath);
    else if (text(explicitPath))
        topicPath = splitPath(explicitPath);
    else {
        const parent = splitPath((_g = (_f = value.parent) !== null && _f !== void 0 ? _f : value.parentTopic) !== null && _g !== void 0 ? _g : value.parentPath);
        const topic = text((_j = (_h = value.topic) !== null && _h !== void 0 ? _h : value.topicName) !== null && _j !== void 0 ? _j : value.name);
        topicPath = topic ? [...parent, topic] : parent;
    }
    if (!subject) {
        issues.push({ severity: "error", code: "missing-subject", row, message: "A subject name is required." });
        return null;
    }
    const weightValue = finiteWeight(value.weight);
    if (weightValue === null) {
        issues.push({ severity: "error", code: "invalid-weight", row, message: `Subject weight must be a non-negative number (${subject}).` });
        return null;
    }
    const priorityValue = text(value.priority) ? priority(value.priority) : undefined;
    if (text(value.priority) && !priorityValue) {
        issues.push({ severity: "error", code: "invalid-priority", row, message: `Unknown topic priority: ${text(value.priority)}.` });
        return null;
    }
    const statusValue = text(value.status) ? status(value.status) : undefined;
    if (text(value.status) && !statusValue) {
        issues.push({ severity: "error", code: "invalid-status", row, message: `Unknown topic status: ${text(value.status)}.` });
        return null;
    }
    return { row, subject, topicPath, weight: weightValue, subjectNotes: optionalText((_k = value.subjectNotes) !== null && _k !== void 0 ? _k : value.subject_notes), priority: priorityValue, status: statusValue, topicNotes: optionalText((_m = (_l = value.topicNotes) !== null && _l !== void 0 ? _l : value.topic_notes) !== null && _m !== void 0 ? _m : value.notes) };
}
function flattenJsonSubject(subjectValue, rowStart, issues) {
    var _a;
    const subject = text((_a = subjectValue.name) !== null && _a !== void 0 ? _a : subjectValue.subject);
    if (!subject) {
        issues.push({ severity: "error", code: "missing-subject", row: rowStart, message: "JSON subject is missing name." });
        return [];
    }
    const rows = [];
    const weightValue = finiteWeight(subjectValue.weight);
    if (weightValue === null) {
        issues.push({ severity: "error", code: "invalid-weight", row: rowStart, message: `Subject weight must be a non-negative number (${subject}).` });
        return [];
    }
    rows.push({ row: rowStart, subject, topicPath: [], weight: weightValue, subjectNotes: optionalText(subjectValue.notes) });
    let row = rowStart + 1;
    function visit(nodes, path) {
        var _a, _b;
        if (!Array.isArray(nodes))
            return;
        for (const raw of nodes) {
            if (typeof raw === "string") {
                const name = text(raw);
                if (name)
                    rows.push({ row: row++, subject, topicPath: [...path, name] });
                continue;
            }
            if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
                issues.push({ severity: "error", code: "invalid-topic", row: row++, message: `Invalid JSON topic under ${subject}.` });
                continue;
            }
            const obj = raw;
            const name = text((_a = obj.name) !== null && _a !== void 0 ? _a : obj.topic);
            if (!name) {
                issues.push({ severity: "error", code: "missing-topic", row: row++, message: `JSON topic under ${subject} is missing name.` });
                continue;
            }
            const priorityValue = text(obj.priority) ? priority(obj.priority) : undefined;
            const statusValue = text(obj.status) ? status(obj.status) : undefined;
            if (text(obj.priority) && !priorityValue) {
                issues.push({ severity: "error", code: "invalid-priority", row, message: `Unknown topic priority: ${text(obj.priority)}.` });
            }
            if (text(obj.status) && !statusValue) {
                issues.push({ severity: "error", code: "invalid-status", row, message: `Unknown topic status: ${text(obj.status)}.` });
            }
            const current = [...path, name];
            rows.push({ row: row++, subject, topicPath: current, priority: priorityValue, status: statusValue, topicNotes: optionalText(obj.notes) });
            visit((_b = obj.children) !== null && _b !== void 0 ? _b : obj.topics, current);
        }
    }
    visit(subjectValue.topics, []);
    return rows;
}
function parseJson(source) {
    const issues = [];
    let parsed;
    try {
        parsed = JSON.parse(source);
    }
    catch (error) {
        return { format: "json", records: [], issues: [{ severity: "error", code: "invalid-json", row: null, message: `JSON parse failed: ${error instanceof Error ? error.message : String(error)}` }] };
    }
    const records = [];
    if (Array.isArray(parsed)) {
        parsed.forEach((item, index) => { if (!item || typeof item !== "object" || Array.isArray(item))
            issues.push({ severity: "error", code: "invalid-row", row: index + 1, message: "JSON import rows must be objects." });
        else {
            const record = rowFromObject(item, index + 1, issues);
            if (record)
                records.push(record);
        } });
    }
    else if (parsed && typeof parsed === "object") {
        const object = parsed;
        if (Array.isArray(object.subjects)) {
            let row = 1;
            for (const raw of object.subjects) {
                if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
                    issues.push({ severity: "error", code: "invalid-subject", row, message: "JSON subjects must be objects." });
                    row += 1;
                    continue;
                }
                const flattened = flattenJsonSubject(raw, row, issues);
                records.push(...flattened);
                row += Math.max(1, flattened.length);
            }
        }
        else if (Array.isArray(object.rows)) {
            object.rows.forEach((item, index) => { if (!item || typeof item !== "object" || Array.isArray(item))
                issues.push({ severity: "error", code: "invalid-row", row: index + 1, message: "JSON rows must be objects." });
            else {
                const record = rowFromObject(item, index + 1, issues);
                if (record)
                    records.push(record);
            } });
        }
        else {
            const record = rowFromObject(object, 1, issues);
            if (record)
                records.push(record);
        }
    }
    else
        issues.push({ severity: "error", code: "invalid-json-shape", row: null, message: "JSON import must be an object or array." });
    return { format: "json", records, issues };
}
function parseDelimited(source, format) {
    const issues = [];
    const lines = source.replace(/^\uFEFF/, "").split(/\r?\n/).filter((line) => line.trim());
    if (!lines.length)
        return { format, records: [], issues: [{ severity: "error", code: "empty-source", row: null, message: "Import source is empty." }] };
    const delimiter = format === "csv" ? "," : "\t";
    const headers = parseDelimitedLine(lines[0], delimiter).map(normalized);
    const aliases = new Map([["discipline", "subject"], ["disciplina", "subject"], ["subjectname", "subject"], ["topicname", "topic"], ["parenttopic", "parent"], ["topicpath", "path"], ["subject_notes", "subjectnotes"], ["topic_notes", "topicnotes"]]);
    const canonical = headers.map((header) => { var _a; return (_a = aliases.get(header.replace(/\s+/g, ""))) !== null && _a !== void 0 ? _a : header.replace(/\s+/g, ""); });
    if (!canonical.includes("subject"))
        issues.push({ severity: "error", code: "missing-header", row: 1, message: "Delimited import requires a subject column." });
    const records = [];
    lines.slice(1).forEach((line, index) => {
        const values = parseDelimitedLine(line, delimiter);
        const object = {};
        canonical.forEach((header, column) => { var _a; object[header] = (_a = values[column]) !== null && _a !== void 0 ? _a : ""; });
        const record = rowFromObject(object, index + 2, issues);
        if (record)
            records.push(record);
    });
    return { format, records, issues };
}
function parseMarkdown(source) {
    const issues = [];
    const records = [];
    let subject = "";
    let path = [];
    source.replace(/^\uFEFF/, "").split(/\r?\n/).forEach((raw, index) => {
        const line = raw.trim();
        if (!line)
            return;
        const heading = line.match(/^(#{1,6})\s+(.+)$/);
        if (heading) {
            const level = heading[1].length;
            const name = text(heading[2].replace(/\s+#+\s*$/, "").trim());
            if (level === 1) {
                subject = name;
                path = [];
                if (subject)
                    records.push({ row: index + 1, subject, topicPath: [] });
            }
            else {
                if (!subject) {
                    issues.push({ severity: "error", code: "topic-before-subject", row: index + 1, message: "Markdown topic appears before any level-1 subject heading." });
                    return;
                }
                const depth = level - 1;
                path = [...path.slice(0, Math.max(0, depth - 1)), name];
                records.push({ row: index + 1, subject, topicPath: [...path] });
            }
            return;
        }
        const bullet = line.match(/^[-*+]\s+(.+)$/);
        if (bullet) {
            if (!subject) {
                issues.push({ severity: "error", code: "topic-before-subject", row: index + 1, message: "Markdown topic appears before any level-1 subject heading." });
                return;
            }
            const parts = splitPath(bullet[1]);
            if (parts.length)
                records.push({ row: index + 1, subject, topicPath: parts });
        }
    });
    if (!records.length && !issues.length)
        issues.push({ severity: "error", code: "empty-source", row: null, message: "No Markdown subjects/topics were recognized." });
    return { format: "markdown", records, issues };
}
function parseText(source) {
    const issues = [];
    const records = [];
    let currentSubject = "";
    source.replace(/^\uFEFF/, "").split(/\r?\n/).forEach((raw, index) => {
        var _a;
        const line = raw.trim();
        if (!line || line.startsWith("#"))
            return;
        const subjectMatch = line.match(/^subject\s*:\s*(.+)$/i);
        if (subjectMatch) {
            currentSubject = text(subjectMatch[1]);
            if (currentSubject)
                records.push({ row: index + 1, subject: currentSubject, topicPath: [] });
            return;
        }
        const topicMatch = line.match(/^topic\s*:\s*(.+)$/i);
        if (topicMatch) {
            if (!currentSubject) {
                issues.push({ severity: "error", code: "topic-before-subject", row: index + 1, message: "Topic line appears before a Subject: line." });
                return;
            }
            const parts = splitPath(topicMatch[1]);
            if (parts.length)
                records.push({ row: index + 1, subject: currentSubject, topicPath: parts });
            return;
        }
        const parts = splitPath(line);
        if (parts.length > 1) {
            currentSubject = parts[0];
            records.push({ row: index + 1, subject: parts[0], topicPath: parts.slice(1) });
        }
        else {
            currentSubject = (_a = parts[0]) !== null && _a !== void 0 ? _a : "";
            if (currentSubject)
                records.push({ row: index + 1, subject: currentSubject, topicPath: [] });
        }
    });
    if (!records.length && !issues.length)
        issues.push({ severity: "error", code: "empty-source", row: null, message: "No text subjects/topics were recognized." });
    return { format: "txt", records, issues };
}
function detectPlanContentImportFormat(nameOrType, source = "") {
    const value = normalized(nameOrType);
    if (value.endsWith(".json") || value.includes("json"))
        return "json";
    if (value.endsWith(".csv") || value.includes("csv"))
        return "csv";
    if (value.endsWith(".tsv") || value.includes("tab-separated"))
        return "tsv";
    if (value.endsWith(".md") || value.endsWith(".markdown") || value.includes("markdown"))
        return "markdown";
    const trimmed = source.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("["))
        return "json";
    if (/^#\s+/m.test(source))
        return "markdown";
    return "txt";
}
function parsePlanContentImport(source, { format }) {
    if (typeof source !== "string")
        throw new TypeError("Plan Content import source must be text");
    if (source.length > 5 * 1024 * 1024)
        return { format, records: [], issues: [{ severity: "error", code: "source-too-large", row: null, message: "Plan Content import is limited to 5 MiB of text." }] };
    if (format === "json")
        return parseJson(source);
    if (format === "csv" || format === "tsv")
        return parseDelimited(source, format);
    if (format === "markdown")
        return parseMarkdown(source);
    return parseText(source);
}
function uniqueInput(records, issues) {
    const seen = new Set();
    const out = [];
    for (const record of records) {
        const key = pathKey(record.subject, record.topicPath);
        if (seen.has(key)) {
            issues.push({ severity: "warning", code: "duplicate-input", row: record.row, message: `Duplicate input path skipped: ${[record.subject, ...record.topicPath].join(" > ")}.` });
            continue;
        }
        seen.add(key);
        out.push(record);
    }
    return out;
}
function matchingSubjects(state, planId, name) { const key = normalized(name); return state.subjects.filter((item) => item.planId === planId && normalized(item.name) === key); }
function matchingTopics(state, planId, subjectId, parentTopicId, name) { const key = normalized(name); return state.topics.filter((item) => { var _a; return item.planId === planId && item.subjectId === subjectId && ((_a = item.parentTopicId) !== null && _a !== void 0 ? _a : null) === parentTopicId && normalized(item.name) === key; }); }
function buildPlanContentImportPreview(state, source, options) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q;
    const mode = (_a = options.mode) !== null && _a !== void 0 ? _a : "merge";
    const target = (_b = options.target) !== null && _b !== void 0 ? _b : "active";
    const parsed = parsePlanContentImport(source, { format: options.format });
    const issues = [...parsed.issues];
    const records = uniqueInput(parsed.records, issues);
    const active = (_c = state.plans.find((plan) => plan.id === state.activePlanId)) !== null && _c !== void 0 ? _c : null;
    const targetPlanId = target === "active" ? (_d = active === null || active === void 0 ? void 0 : active.id) !== null && _d !== void 0 ? _d : null : null;
    const targetPlanName = target === "new-plan" ? text(options.planName) : null;
    if (target === "active" && !targetPlanId)
        issues.push({ severity: "error", code: "missing-active-plan", row: null, message: "An active plan is required for this import." });
    if (target === "new-plan" && !targetPlanName)
        issues.push({ severity: "error", code: "missing-plan-name", row: null, message: "A name is required when importing as a new plan." });
    const operations = [];
    const subjectTokens = new Map();
    const topicTokens = new Map();
    const includedSubjects = new Set();
    const includedTopics = new Set();
    let matched = 0, reactivate = 0, updated = 0;
    for (const record of records) {
        const subjectKey = normalized(record.subject);
        includedSubjects.add(subjectKey);
        let subjectRef = (_e = subjectTokens.get(subjectKey)) !== null && _e !== void 0 ? _e : null;
        if (!subjectRef) {
            if (targetPlanId) {
                const matches = matchingSubjects(state, targetPlanId, record.subject);
                if (matches.length > 1) {
                    issues.push({ severity: "error", code: "ambiguous-subject", row: record.row, message: `Multiple existing subjects match ${record.subject}.` });
                    continue;
                }
                if (matches.length === 1) {
                    const current = matches[0];
                    subjectRef = current.id;
                    subjectTokens.set(subjectKey, subjectRef);
                    const changes = {};
                    if (current.archived) {
                        changes.archived = false;
                        reactivate += 1;
                    }
                    if (record.weight !== undefined && record.weight !== current.weight) {
                        changes.weight = record.weight;
                        updated += 1;
                    }
                    if (record.subjectNotes !== undefined && record.subjectNotes !== current.notes) {
                        changes.notes = record.subjectNotes;
                        updated += 1;
                    }
                    if (Object.keys(changes).length)
                        operations.push({ action: "update-subject", subjectRef, subjectName: record.subject, changes });
                    else
                        matched += 1;
                }
            }
            if (!subjectRef) {
                subjectRef = `import-subject:${subjectKey}`;
                subjectTokens.set(subjectKey, subjectRef);
                operations.push({ action: "add-subject", subjectRef, subjectName: record.subject, changes: { weight: (_f = record.weight) !== null && _f !== void 0 ? _f : null, notes: (_g = record.subjectNotes) !== null && _g !== void 0 ? _g : null } });
            }
        }
        let parentRef = null;
        const currentPath = [];
        for (let depth = 0; depth < record.topicPath.length; depth += 1) {
            const name = record.topicPath[depth];
            currentPath.push(name);
            const topicKey = pathKey(record.subject, currentPath);
            includedTopics.add(topicKey);
            let topicRef = (_h = topicTokens.get(topicKey)) !== null && _h !== void 0 ? _h : null;
            if (!topicRef && targetPlanId && !subjectRef.startsWith("import-subject:")) {
                const matches = matchingTopics(state, targetPlanId, subjectRef, parentRef && parentRef.startsWith("import-topic:") ? null : parentRef, name);
                // A not-yet-created parent cannot have an existing child; if parent is temporary we intentionally add the child too.
                if (parentRef === null || parentRef === void 0 ? void 0 : parentRef.startsWith("import-topic:")) { }
                else if (matches.length > 1) {
                    issues.push({ severity: "error", code: "ambiguous-topic", row: record.row, message: `Multiple existing topics match ${[record.subject, ...currentPath].join(" > ")}.` });
                    break;
                }
                else if (matches.length === 1) {
                    const current = matches[0];
                    topicRef = current.id;
                    topicTokens.set(topicKey, topicRef);
                    const changes = {};
                    const isLeaf = depth === record.topicPath.length - 1;
                    if (current.status === "archived") {
                        changes.status = isLeaf && record.status && record.status !== "archived" ? record.status : "not-started";
                        reactivate += 1;
                    }
                    else if (isLeaf && record.status !== undefined && record.status !== current.status) {
                        changes.status = record.status;
                        updated += 1;
                    }
                    if (isLeaf && record.priority !== undefined && record.priority !== current.priority) {
                        changes.priority = record.priority;
                        updated += 1;
                    }
                    if (isLeaf && record.topicNotes !== undefined && record.topicNotes !== current.notes) {
                        changes.notes = record.topicNotes;
                        updated += 1;
                    }
                    if (Object.keys(changes).length)
                        operations.push({ action: "update-topic", subjectRef, topicRef, subjectName: record.subject, topicPath: [...currentPath], parentRef, changes });
                    else
                        matched += 1;
                }
            }
            if (!topicRef) {
                topicRef = `import-topic:${topicKey}`;
                topicTokens.set(topicKey, topicRef);
                const isLeaf = depth === record.topicPath.length - 1;
                operations.push({ action: "add-topic", subjectRef, topicRef, subjectName: record.subject, topicPath: [...currentPath], parentRef, changes: { priority: isLeaf ? (_j = record.priority) !== null && _j !== void 0 ? _j : "normal" : "normal", status: isLeaf ? (_k = record.status) !== null && _k !== void 0 ? _k : "not-started" : "not-started", notes: isLeaf ? (_l = record.topicNotes) !== null && _l !== void 0 ? _l : null : null } });
            }
            parentRef = topicRef;
        }
    }
    if (mode === "reconcile" && targetPlanId) {
        for (const topic of state.topics.filter((item) => item.planId === targetPlanId && item.status !== "archived")) {
            const subject = state.subjects.find((item) => item.id === topic.subjectId);
            if (!subject)
                continue;
            const parts = [];
            let current = topic;
            const visited = new Set();
            while (current) {
                if (visited.has(current.id))
                    break;
                visited.add(current.id);
                parts.unshift(current.name);
                current = current.parentTopicId ? state.topics.find((item) => item.id === current.parentTopicId) : undefined;
            }
            const key = pathKey(subject.name, parts);
            if (!includedTopics.has(key))
                operations.push({ action: "archive-topic", subjectRef: topic.subjectId, topicRef: topic.id, subjectName: subject.name, topicPath: parts, parentRef: (_m = topic.parentTopicId) !== null && _m !== void 0 ? _m : null, changes: { status: "archived" } });
        }
        for (const subject of state.subjects.filter((item) => item.planId === targetPlanId && !item.archived)) {
            if (!includedSubjects.has(normalized(subject.name)))
                operations.push({ action: "archive-subject", subjectRef: subject.id, subjectName: subject.name, changes: { archived: true } });
        }
    }
    const errors = issues.filter((issue) => issue.severity === "error").length;
    const duplicate = issues.filter((issue) => issue.code === "duplicate-input").length;
    const ambiguous = issues.filter((issue) => issue.code.startsWith("ambiguous-")).length;
    const summary = {
        subjectsAdd: operations.filter((op) => op.action === "add-subject").length,
        topicsAdd: operations.filter((op) => op.action === "add-topic").length,
        update: operations.filter((op) => op.action === "update-subject" || op.action === "update-topic").length,
        reactivate,
        archive: operations.filter((op) => op.action === "archive-subject" || op.action === "archive-topic").length,
        matched,
        duplicate,
        invalid: errors - ambiguous,
        ambiguous,
    };
    return { baseRevision: state.revision, generatedAt: ((_o = options.now) !== null && _o !== void 0 ? _o : (() => new Date().toISOString()))(), sourceFingerprint: fingerprint(source), format: options.format, mode, target, targetPlanId, targetPlanName, targetPlanMode: (_q = (_p = options.planMode) !== null && _p !== void 0 ? _p : active === null || active === void 0 ? void 0 : active.mode) !== null && _q !== void 0 ? _q : "GENERAL_SKILL", ready: errors === 0 && records.length > 0, summary, issues, operations };
}
