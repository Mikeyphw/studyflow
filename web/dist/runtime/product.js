"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.UI_TEXT = exports.DEFAULT_APP_PREFERENCES = exports.APP_LOCALES = void 0;
exports.normalizeAppPreferences = normalizeAppPreferences;
exports.searchStudyState = searchStudyState;
exports.validateStarterWorkspace = validateStarterWorkspace;
exports.uiText = uiText;
exports.installDocumentLocalization = installDocumentLocalization;
exports.APP_LOCALES = Object.freeze(["en", "pt-BR"]);
exports.DEFAULT_APP_PREFERENCES = Object.freeze({ locale: "en", onboardingCompleted: false, showArchivedInSearch: false });
function normalizeAppPreferences(input) {
    const locale = (input === null || input === void 0 ? void 0 : input.locale) === "pt-BR" ? "pt-BR" : "en";
    return { locale, onboardingCompleted: Boolean(input === null || input === void 0 ? void 0 : input.onboardingCompleted), showArchivedInSearch: Boolean(input === null || input === void 0 ? void 0 : input.showArchivedInSearch) };
}
function norm(value) { return String(value !== null && value !== void 0 ? value : "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim(); }
function activePlanIds(state, includeArchived) { return new Set(state.plans.filter(p => includeArchived || p.status !== "archived").map(p => p.id)); }
function result(type, id, planId, title, detail, searchText, score) { return { type, id, planId, title, detail, searchText, score }; }
function searchStudyState(state, query, { limit = 40, includeArchived = state.preferences.showArchivedInSearch } = {}) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r, _s;
    const q = norm(query);
    if (q.length < 2)
        return [];
    const plans = activePlanIds(state, includeArchived);
    const out = [];
    const planName = (id) => { var _a, _b; return (_b = (_a = state.plans.find(p => p.id === id)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : "Unknown plan"; };
    const subjectName = (id) => { var _a, _b; return id ? (_b = (_a = state.subjects.find(s => s.id === id)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : "" : null; };
    const topicPath = (id) => { var _a, _b; if (!id)
        return ""; const parts = []; let cur = (_a = state.topics.find(t => t.id === id)) !== null && _a !== void 0 ? _a : null; let guard = 0; while (cur && guard++ < 32) {
        parts.unshift(cur.name);
        cur = cur.parentTopicId ? (_b = state.topics.find(t => t.id === cur.parentTopicId)) !== null && _b !== void 0 ? _b : null : null;
    } return parts.join(" › "); };
    const push = (x) => { const hay = norm(x.searchText); const at = hay.indexOf(q); if (at < 0)
        return; const exact = hay === q ? 100 : hay.startsWith(q) ? 60 : 30; out.push({ ...x, score: x.score + exact - Math.min(20, at / 8) }); };
    for (const p of state.plans)
        if (includeArchived || p.status !== "archived")
            push(result("plan", p.id, p.id, p.name, `${p.mode} · ${p.status}`, `${p.name} ${p.mode}`, 10));
    for (const s of state.subjects)
        if (plans.has(s.planId) && (!s.archived || includeArchived))
            push(result("subject", s.id, s.planId, s.name, planName(s.planId), `${s.name} ${(_a = s.notes) !== null && _a !== void 0 ? _a : ""}`, 20));
    for (const t of state.topics)
        if (plans.has(t.planId) && (t.status !== "archived" || includeArchived))
            push(result("topic", t.id, t.planId, t.name, `${(_b = subjectName(t.subjectId)) !== null && _b !== void 0 ? _b : ""} · ${topicPath(t.id)}`, `${t.name} ${(_c = t.notes) !== null && _c !== void 0 ? _c : ""} ${topicPath(t.id)}`, 25));
    for (const qn of state.questions)
        if (plans.has(qn.planId))
            push(result("question", qn.id, qn.planId, qn.prompt.slice(0, 96), `${(_d = subjectName(qn.subjectId)) !== null && _d !== void 0 ? _d : planName(qn.planId)}${qn.source ? ` · ${qn.source}` : ""}`, `${qn.prompt} ${(_e = qn.explanation) !== null && _e !== void 0 ? _e : ""} ${(_f = qn.source) !== null && _f !== void 0 ? _f : ""} ${(_g = qn.organization) !== null && _g !== void 0 ? _g : ""} ${(_h = qn.examBoard) !== null && _h !== void 0 ? _h : ""} ${((_j = qn.tags) !== null && _j !== void 0 ? _j : []).join(" ")}`, 35));
    for (const f of state.flashcards)
        if (plans.has(f.planId))
            push(result("flashcard", f.id, f.planId, f.front.slice(0, 96), `${(_k = subjectName(f.subjectId)) !== null && _k !== void 0 ? _k : planName(f.planId)} · ${f.type}${f.suspended ? " · suspended" : ""}`, `${f.front} ${f.back} ${(_l = f.clozeText) !== null && _l !== void 0 ? _l : ""} ${f.tags.join(" ")}`, 32));
    for (const m of state.materials)
        if (plans.has(m.planId))
            push(result("material", m.id, m.planId, m.title, `${(_m = subjectName(m.subjectId)) !== null && _m !== void 0 ? _m : planName(m.planId)}${m.mimeType ? ` · ${m.mimeType}` : ""}`, `${m.title} ${(_o = m.locator) !== null && _o !== void 0 ? _o : ""} ${(_p = m.mimeType) !== null && _p !== void 0 ? _p : ""}`, 25));
    for (const e of state.errorEntries)
        if (plans.has(e.planId))
            push(result("error", e.id, e.planId, (_q = e.note) !== null && _q !== void 0 ? _q : `Error · ${e.category}`, `${e.category} · ${e.status} · ${(_r = subjectName(e.subjectId)) !== null && _r !== void 0 ? _r : planName(e.planId)}`, `${(_s = e.note) !== null && _s !== void 0 ? _s : ""} ${e.category} ${topicPath(e.topicId)}`, 30));
    return out.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title)).slice(0, Math.max(1, Math.min(100, limit)));
}
function validateStarterWorkspace(input) { var _a, _b; const name = String((_a = input.planName) !== null && _a !== void 0 ? _a : "").trim(); if (!name)
    throw new TypeError("Plan name is required"); if (name.length > 120)
    throw new TypeError("Plan name must be 120 characters or fewer"); const template = (_b = input.template) !== null && _b !== void 0 ? _b : (input.useSample ? "sample" : "blank"); if (!["sample", "blank", "bb-agente-comercial-pre-edital", "public-exam-12-week", "university-semester", "certification-bootcamp", "free-study-habit"].includes(template))
    throw new TypeError("Unsupported onboarding template"); return { ...input, template, planName: name, locale: input.locale === "pt-BR" ? "pt-BR" : "en", useSample: template === "sample" }; }
exports.UI_TEXT = Object.freeze({
    en: { search: "Search", settings: "Settings", close: "Close", language: "Language", privacy: "Privacy & storage", onboarding: "Welcome to StudyFlow Local", start: "Start workspace", skip: "Keep sample workspace", noResults: "No matching local records.", localOnly: "Your study data stays in this browser unless you explicitly export it.", encryption: "Password-encrypted portable backups are supported locally with the StudyFlow encrypted envelope. Keep the password separately; StudyFlow cannot recover it." },
    "pt-BR": { search: "Pesquisar", settings: "Configurações", close: "Fechar", language: "Idioma", privacy: "Privacidade e armazenamento", onboarding: "Bem-vindo ao StudyFlow Local", start: "Criar espaço de estudo", skip: "Manter espaço de exemplo", noResults: "Nenhum registro local encontrado.", localOnly: "Seus dados de estudo permanecem neste navegador, a menos que você os exporte explicitamente.", encryption: "Backups portáteis protegidos por senha são suportados localmente pelo envelope criptografado do StudyFlow. Guarde a senha separadamente; o StudyFlow não consegue recuperá-la." }
});
function uiText(locale, key) { var _a, _b; return (_b = (_a = exports.UI_TEXT[locale]) === null || _a === void 0 ? void 0 : _a[key]) !== null && _b !== void 0 ? _b : exports.UI_TEXT.en[key]; }
const PT_BR_EXACT = Object.freeze({
    "Today": "Hoje", "Plan": "Plano", "Practice": "Prática", "Progress": "Progresso", "Overview": "Visão geral", "Schedule": "Agenda", "Content": "Conteúdo", "Library": "Biblioteca", "Reviews": "Revisões", "Questions": "Questões", "Tests": "Simulados", "Errors": "Erros", "Summary": "Resumo", "Performance": "Desempenho", "Goals": "Metas", "Tools": "Ferramentas",
    "Next up": "Próximo passo", "No queued activity": "Nenhuma atividade na fila", "Focus": "Foco", "Start": "Iniciar", "Resume": "Retomar", "Pause": "Pausar", "Complete": "Concluir", "Cancel": "Cancelar", "Quick Add": "Adicionar rápido", "Today queue": "Fila de hoje", "No planned activities remain today.": "Não há atividades planejadas restantes para hoje.",
    "Plan workspace": "Espaço do plano", "Create plan": "Criar plano", "Archived plans": "Planos arquivados", "Subjects": "Disciplinas", "Topics": "Tópicos", "Import Plan Content": "Importar conteúdo do plano", "Preview import": "Pré-visualizar importação", "Apply import preview": "Aplicar prévia de importação", "Import as new plan": "Importar como novo plano", "Parent topic": "Tópico pai",
    "Capacity & availability": "Capacidade e disponibilidade", "Fixed commitments": "Compromissos fixos", "Preview 7 days": "Pré-visualizar 7 dias", "Unscheduled work": "Trabalho não agendado", "Schedule placement locked": "Posição na agenda bloqueada",
    "Question import": "Importar questões", "Weakness drill": "Treino de fraquezas", "Save current filter": "Salvar filtro atual", "Weekly question quota": "Meta semanal de questões", "Study phase": "Fase de estudo", "Edital Matrix": "Matriz do edital", "Explainable next actions": "Próximas ações explicáveis", "Local starter templates": "Modelos locais iniciais", "Exam incidence": "Incidência em prova",
    "Weekly coach": "Coach semanal", "Preview smart cleanup": "Pré-visualizar limpeza inteligente", "Export HTML exam report": "Exportar relatório HTML da prova", "Preview 7-day sprint": "Pré-visualizar sprint de 7 dias", "Mode-aware analytics": "Análises por modo", "Evidence Timeline": "Linha do tempo de evidências", "Content Integrity": "Integridade do conteúdo", "Local state": "Estado local", "Storage health": "Saúde do armazenamento", "Install & offline": "Instalação e offline",
    "Programming course": "Curso de programação", "Language deck": "Baralho de idiomas", "Reading study": "Estudo de leitura", "General skill": "Habilidade geral", "Concurso público": "Concurso público", "Six-direction review studio": "Estúdio de revisão em seis direções", "Import template": "Importar modelo",
    "No data": "Sem dados", "No active plan": "Nenhum plano ativo", "No package selected.": "Nenhum pacote selecionado.", "Reset sample data": "Restaurar dados de exemplo", "Check storage": "Verificar armazenamento", "Clean orphan files": "Limpar arquivos órfãos", "Install StudyFlow": "Instalar StudyFlow",
    "Starter template": "Modelo inicial", "BB Agente Comercial — pre-edital": "BB Agente Comercial — pré-edital", "Writing Workspace": "Espaço de Redação", "Theme / task": "Tema / proposta", "Draft": "Rascunho", "Rubric": "Rubrica", "Completion checklist": "Checklist de conclusão", "Save draft": "Salvar rascunho", "Finish Writing": "Concluir redação", "Theme and task": "Tema e proposta", "Text type": "Tipo textual", "Cohesion": "Coesão", "Argumentation": "Argumentação", "Standard language": "Norma-padrão", "Task reviewed": "Proposta revisada", "Structure reviewed": "Estrutura revisada", "Cohesion reviewed": "Coesão revisada", "Standard language reviewed": "Norma-padrão revisada"
});
function installDocumentLocalization(locale) {
    if (typeof document === "undefined" || typeof MutationObserver === "undefined")
        return { dispose() { } };
    document.documentElement.lang = locale;
    const textState = new WeakMap();
    const attrState = new WeakMap();
    const translated = new Map(Object.entries(PT_BR_EXACT).map(([en, pt]) => [pt, en]));
    function canonical(value) { var _a; return (_a = translated.get(value)) !== null && _a !== void 0 ? _a : value; }
    function translate(value) { var _a; const key = canonical(value); return locale === "pt-BR" ? ((_a = PT_BR_EXACT[key]) !== null && _a !== void 0 ? _a : key) : key; }
    function localizedText(value) { var _a, _b, _c, _d; const lead = (_b = (_a = value.match(/^\s*/)) === null || _a === void 0 ? void 0 : _a[0]) !== null && _b !== void 0 ? _b : "", trail = (_d = (_c = value.match(/\s*$/)) === null || _c === void 0 ? void 0 : _c[0]) !== null && _d !== void 0 ? _d : "", core = value.trim(); return core && PT_BR_EXACT[canonical(core)] ? lead + translate(core) + trail : value; }
    function visit(node) {
        var _a, _b, _c, _d, _e;
        if (node.nodeType === Node.TEXT_NODE) {
            const t = node;
            const current = (_a = t.nodeValue) !== null && _a !== void 0 ? _a : "";
            let state = textState.get(t);
            if (!state || current !== state.rendered) {
                const source = canonical(current.trim());
                const lead = (_c = (_b = current.match(/^\s*/)) === null || _b === void 0 ? void 0 : _b[0]) !== null && _c !== void 0 ? _c : "", trail = (_e = (_d = current.match(/\s*$/)) === null || _d === void 0 ? void 0 : _d[0]) !== null && _e !== void 0 ? _e : "";
                state = { source: lead + source + trail, rendered: current };
                textState.set(t, state);
            }
            const next = localizedText(state.source);
            state.rendered = next;
            if (next !== current)
                t.nodeValue = next;
            return;
        }
        if (!(node instanceof Element))
            return;
        const attrs = ["placeholder", "aria-label", "title"];
        let states = attrState.get(node);
        if (!states) {
            states = {};
            attrState.set(node, states);
        }
        for (const name of attrs) {
            const current = node.getAttribute(name);
            if (current == null)
                continue;
            let state = states[name];
            if (!state || current !== state.rendered) {
                state = { source: canonical(current), rendered: current };
                states[name] = state;
            }
            const next = translate(state.source);
            state.rendered = next;
            if (next !== current)
                node.setAttribute(name, next);
        }
        for (const child of Array.from(node.childNodes))
            visit(child);
    }
    visit(document.body);
    const observer = new MutationObserver(records => { for (const record of records) {
        if (record.type === "characterData")
            visit(record.target);
        for (const n of Array.from(record.addedNodes))
            visit(n);
        if (record.type === "attributes")
            visit(record.target);
    } });
    observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ["placeholder", "aria-label", "title"] });
    return { dispose() { observer.disconnect(); } };
}
