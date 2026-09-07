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
    const locale = input?.locale === "pt-BR" ? "pt-BR" : "en";
    return { locale, onboardingCompleted: Boolean(input?.onboardingCompleted), showArchivedInSearch: Boolean(input?.showArchivedInSearch) };
}
function norm(value) { return String(value ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim(); }
function activePlanIds(state, includeArchived) { return new Set(state.plans.filter(p => includeArchived || p.status !== "archived").map(p => p.id)); }
function result(type, id, planId, title, detail, searchText, score) { return { type, id, planId, title, detail, searchText, score }; }
function searchStudyState(state, query, { limit = 40, includeArchived = state.preferences.showArchivedInSearch } = {}) {
    const q = norm(query);
    if (q.length < 2)
        return [];
    const plans = activePlanIds(state, includeArchived);
    const out = [];
    const planName = (id) => state.plans.find(p => p.id === id)?.name ?? "Unknown plan";
    const subjectName = (id) => id ? state.subjects.find(s => s.id === id)?.name ?? "" : null;
    const topicPath = (id) => { if (!id)
        return ""; const parts = []; let cur = state.topics.find(t => t.id === id) ?? null; let guard = 0; while (cur && guard++ < 32) {
        parts.unshift(cur.name);
        cur = cur.parentTopicId ? state.topics.find(t => t.id === cur.parentTopicId) ?? null : null;
    } return parts.join(" › "); };
    const push = (x) => { const hay = norm(x.searchText); const at = hay.indexOf(q); if (at < 0)
        return; const exact = hay === q ? 100 : hay.startsWith(q) ? 60 : 30; out.push({ ...x, score: x.score + exact - Math.min(20, at / 8) }); };
    for (const p of state.plans)
        if (includeArchived || p.status !== "archived")
            push(result("plan", p.id, p.id, p.name, `${p.mode} · ${p.status}`, `${p.name} ${p.mode}`, 10));
    for (const s of state.subjects)
        if (plans.has(s.planId) && (!s.archived || includeArchived))
            push(result("subject", s.id, s.planId, s.name, planName(s.planId), `${s.name} ${s.notes ?? ""}`, 20));
    for (const t of state.topics)
        if (plans.has(t.planId) && (t.status !== "archived" || includeArchived))
            push(result("topic", t.id, t.planId, t.name, `${subjectName(t.subjectId) ?? ""} · ${topicPath(t.id)}`, `${t.name} ${t.notes ?? ""} ${topicPath(t.id)}`, 25));
    for (const qn of state.questions)
        if (plans.has(qn.planId))
            push(result("question", qn.id, qn.planId, qn.prompt.slice(0, 96), `${subjectName(qn.subjectId) ?? planName(qn.planId)}${qn.source ? ` · ${qn.source}` : ""}`, `${qn.prompt} ${qn.explanation ?? ""} ${qn.source ?? ""} ${qn.organization ?? ""} ${qn.examBoard ?? ""}`, 35));
    for (const m of state.materials)
        if (plans.has(m.planId))
            push(result("material", m.id, m.planId, m.title, `${subjectName(m.subjectId) ?? planName(m.planId)}${m.mimeType ? ` · ${m.mimeType}` : ""}`, `${m.title} ${m.locator ?? ""} ${m.mimeType ?? ""}`, 25));
    for (const e of state.errorEntries)
        if (plans.has(e.planId))
            push(result("error", e.id, e.planId, e.note ?? `Error · ${e.category}`, `${e.category} · ${e.status} · ${subjectName(e.subjectId) ?? planName(e.planId)}`, `${e.note ?? ""} ${e.category} ${topicPath(e.topicId)}`, 30));
    return out.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title)).slice(0, Math.max(1, Math.min(100, limit)));
}
function validateStarterWorkspace(input) { const name = String(input.planName ?? "").trim(); if (!name)
    throw new TypeError("Plan name is required"); if (name.length > 120)
    throw new TypeError("Plan name must be 120 characters or fewer"); const template = input.template ?? (input.useSample ? "sample" : "blank"); if (!["sample", "blank", "bb-agente-comercial-pre-edital"].includes(template))
    throw new TypeError("Unsupported onboarding template"); return { ...input, template, planName: name, locale: input.locale === "pt-BR" ? "pt-BR" : "en", useSample: template === "sample" }; }
exports.UI_TEXT = Object.freeze({
    en: { search: "Search", settings: "Settings", close: "Close", language: "Language", privacy: "Privacy & storage", onboarding: "Welcome to StudyFlow Local", start: "Start workspace", skip: "Keep sample workspace", noResults: "No matching local records.", localOnly: "Your study data stays in this browser unless you explicitly export it.", encryption: "Encrypted portable backups are not included in StudyFlow Local 1.0. Use device/OS encryption for at-rest protection." },
    "pt-BR": { search: "Pesquisar", settings: "Configurações", close: "Fechar", language: "Idioma", privacy: "Privacidade e armazenamento", onboarding: "Bem-vindo ao StudyFlow Local", start: "Criar espaço de estudo", skip: "Manter espaço de exemplo", noResults: "Nenhum registro local encontrado.", localOnly: "Seus dados de estudo permanecem neste navegador, a menos que você os exporte explicitamente.", encryption: "Backups portáteis criptografados não fazem parte do StudyFlow Local 1.0. Use a criptografia do dispositivo/sistema operacional para proteção em repouso." }
});
function uiText(locale, key) { return exports.UI_TEXT[locale]?.[key] ?? exports.UI_TEXT.en[key]; }
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
    const textOriginal = new WeakMap();
    const attrOriginal = new WeakMap();
    const translated = new Map(Object.entries(PT_BR_EXACT).map(([en, pt]) => [pt, en]));
    function canonical(value) { return translated.get(value) ?? value; }
    function translate(value) { const key = canonical(value); return locale === "pt-BR" ? (PT_BR_EXACT[key] ?? key) : key; }
    function visit(node) {
        if (node.nodeType === Node.TEXT_NODE) {
            const t = node;
            const raw = textOriginal.get(t) ?? canonical(t.nodeValue ?? "");
            textOriginal.set(t, raw);
            const lead = raw.match(/^\s*/)?.[0] ?? "", trail = raw.match(/\s*$/)?.[0] ?? "", core = raw.trim();
            if (core && PT_BR_EXACT[core])
                t.nodeValue = lead + translate(core) + trail;
            return;
        }
        if (!(node instanceof Element))
            return;
        const attrs = ["placeholder", "aria-label", "title"];
        let originals = attrOriginal.get(node);
        if (!originals) {
            originals = {};
            attrOriginal.set(node, originals);
        }
        for (const name of attrs) {
            const value = node.getAttribute(name);
            if (value == null)
                continue;
            const raw = originals[name] ?? canonical(value);
            originals[name] = raw;
            if (PT_BR_EXACT[raw])
                node.setAttribute(name, translate(raw));
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
