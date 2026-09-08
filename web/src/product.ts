"use strict";

import type { AppLocale, AppPreferences, GlobalSearchResult, SamplePlanTemplateId, StudyMode, StudyState } from "./types";

export const APP_LOCALES = Object.freeze(["en", "pt-BR"] as const);
export const DEFAULT_APP_PREFERENCES:AppPreferences = Object.freeze({ locale:"en", onboardingCompleted:false, showArchivedInSearch:false });

export function normalizeAppPreferences(input:Partial<AppPreferences>|null|undefined):AppPreferences {
  const locale:AppLocale = input?.locale === "pt-BR" ? "pt-BR" : "en";
  return { locale, onboardingCompleted:Boolean(input?.onboardingCompleted), showArchivedInSearch:Boolean(input?.showArchivedInSearch) };
}

function norm(value:unknown){return String(value??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().trim();}
function activePlanIds(state:StudyState,includeArchived:boolean){return new Set(state.plans.filter(p=>includeArchived||p.status!=="archived").map(p=>p.id));}
function result(type:GlobalSearchResult["type"],id:string,planId:string,title:string,detail:string,searchText:string,score:number):GlobalSearchResult{return{type,id,planId,title,detail,searchText,score};}
export function searchStudyState(state:StudyState,query:string,{limit=40,includeArchived=state.preferences.showArchivedInSearch}:{limit?:number;includeArchived?:boolean}={}):GlobalSearchResult[]{
  const q=norm(query); if(q.length<2)return[]; const plans=activePlanIds(state,includeArchived); const out:GlobalSearchResult[]=[];
  const planName=(id:string)=>state.plans.find(p=>p.id===id)?.name??"Unknown plan";
  const subjectName=(id:string|null)=>id?state.subjects.find(s=>s.id===id)?.name??"":null;
  const topicPath=(id:string|null)=>{if(!id)return"";const parts:string[]=[];let cur=state.topics.find(t=>t.id===id)??null;let guard=0;while(cur&&guard++<32){parts.unshift(cur.name);cur=cur.parentTopicId?state.topics.find(t=>t.id===cur!.parentTopicId)??null:null;}return parts.join(" › ")};
  const push=(x:GlobalSearchResult)=>{const hay=norm(x.searchText);const at=hay.indexOf(q);if(at<0)return;const exact=hay===q?100:hay.startsWith(q)?60:30;out.push({...x,score:x.score+exact-Math.min(20,at/8)});};
  for(const p of state.plans)if(includeArchived||p.status!=="archived")push(result("plan",p.id,p.id,p.name,`${p.mode} · ${p.status}`,`${p.name} ${p.mode}`,10));
  for(const s of state.subjects)if(plans.has(s.planId)&&(!s.archived||includeArchived))push(result("subject",s.id,s.planId,s.name,planName(s.planId),`${s.name} ${s.notes??""}`,20));
  for(const t of state.topics)if(plans.has(t.planId)&&(t.status!=="archived"||includeArchived))push(result("topic",t.id,t.planId,t.name,`${subjectName(t.subjectId)??""} · ${topicPath(t.id)}`,`${t.name} ${t.notes??""} ${topicPath(t.id)}`,25));
  for(const qn of state.questions)if(plans.has(qn.planId))push(result("question",qn.id,qn.planId,qn.prompt.slice(0,96),`${subjectName(qn.subjectId)??planName(qn.planId)}${qn.source?` · ${qn.source}`:""}`,`${qn.prompt} ${qn.explanation??""} ${qn.source??""} ${qn.organization??""} ${qn.examBoard??""} ${(qn.tags??[]).join(" ")}`,35));
  for(const f of state.flashcards)if(plans.has(f.planId))push(result("flashcard",f.id,f.planId,f.front.slice(0,96),`${subjectName(f.subjectId)??planName(f.planId)} · ${f.type}${f.suspended?" · suspended":""}`,`${f.front} ${f.back} ${f.clozeText??""} ${f.tags.join(" ")}`,32));
  for(const m of state.materials)if(plans.has(m.planId))push(result("material",m.id,m.planId,m.title,`${subjectName(m.subjectId)??planName(m.planId)}${m.mimeType?` · ${m.mimeType}`:""}`,`${m.title} ${m.locator??""} ${m.mimeType??""}`,25));
  for(const e of state.errorEntries)if(plans.has(e.planId))push(result("error",e.id,e.planId,e.note??`Error · ${e.category}`,`${e.category} · ${e.status} · ${subjectName(e.subjectId)??planName(e.planId)}`,`${e.note??""} ${e.category} ${topicPath(e.topicId)}`,30));
  return out.sort((a,b)=>b.score-a.score||a.title.localeCompare(b.title)).slice(0,Math.max(1,Math.min(100,limit)));
}

export interface StarterWorkspaceInput { locale:AppLocale; mode:StudyMode; planName:string; useSample:boolean; template?:"sample"|"blank"|"bb-agente-comercial-pre-edital"|SamplePlanTemplateId; }
export function validateStarterWorkspace(input:StarterWorkspaceInput){const name=String(input.planName??"").trim();if(!name)throw new TypeError("Plan name is required");if(name.length>120)throw new TypeError("Plan name must be 120 characters or fewer");const template=input.template??(input.useSample?"sample":"blank");if(!["sample","blank","bb-agente-comercial-pre-edital","public-exam-12-week","university-semester","certification-bootcamp","free-study-habit"].includes(template))throw new TypeError("Unsupported onboarding template");return{...input,template,planName:name,locale:input.locale==="pt-BR"?"pt-BR":"en" as AppLocale,useSample:template==="sample"};}

export const UI_TEXT = Object.freeze({
  en:{search:"Search",settings:"Settings",close:"Close",language:"Language",privacy:"Privacy & storage",onboarding:"Welcome to StudyFlow Local",start:"Start workspace",skip:"Keep sample workspace",noResults:"No matching local records.",localOnly:"Your study data stays in this browser unless you explicitly export it.",encryption:"Password-encrypted portable backups are supported locally with the StudyFlow encrypted envelope. Keep the password separately; StudyFlow cannot recover it."},
  "pt-BR":{search:"Pesquisar",settings:"Configurações",close:"Fechar",language:"Idioma",privacy:"Privacidade e armazenamento",onboarding:"Bem-vindo ao StudyFlow Local",start:"Criar espaço de estudo",skip:"Manter espaço de exemplo",noResults:"Nenhum registro local encontrado.",localOnly:"Seus dados de estudo permanecem neste navegador, a menos que você os exporte explicitamente.",encryption:"Backups portáteis protegidos por senha são suportados localmente pelo envelope criptografado do StudyFlow. Guarde a senha separadamente; o StudyFlow não consegue recuperá-la."}
});
export function uiText(locale:AppLocale,key:keyof typeof UI_TEXT.en){return UI_TEXT[locale]?.[key]??UI_TEXT.en[key];}

const PT_BR_EXACT:Record<string,string>=Object.freeze({
  "Today":"Hoje","Plan":"Plano","Practice":"Prática","Progress":"Progresso","Overview":"Visão geral","Schedule":"Agenda","Content":"Conteúdo","Library":"Biblioteca","Reviews":"Revisões","Questions":"Questões","Tests":"Simulados","Errors":"Erros","Summary":"Resumo","Performance":"Desempenho","Goals":"Metas","Tools":"Ferramentas",
  "Next up":"Próximo passo","No queued activity":"Nenhuma atividade na fila","Focus":"Foco","Start":"Iniciar","Resume":"Retomar","Pause":"Pausar","Complete":"Concluir","Cancel":"Cancelar","Quick Add":"Adicionar rápido","Today queue":"Fila de hoje","No planned activities remain today.":"Não há atividades planejadas restantes para hoje.",
  "Plan workspace":"Espaço do plano","Create plan":"Criar plano","Archived plans":"Planos arquivados","Subjects":"Disciplinas","Topics":"Tópicos","Import Plan Content":"Importar conteúdo do plano","Preview import":"Pré-visualizar importação","Apply import preview":"Aplicar prévia de importação","Import as new plan":"Importar como novo plano","Parent topic":"Tópico pai",
  "Capacity & availability":"Capacidade e disponibilidade","Fixed commitments":"Compromissos fixos","Preview 7 days":"Pré-visualizar 7 dias","Unscheduled work":"Trabalho não agendado","Schedule placement locked":"Posição na agenda bloqueada",
  "Question import":"Importar questões","Weakness drill":"Treino de fraquezas","Save current filter":"Salvar filtro atual","Weekly question quota":"Meta semanal de questões","Study phase":"Fase de estudo","Edital Matrix":"Matriz do edital","Explainable next actions":"Próximas ações explicáveis","Local starter templates":"Modelos locais iniciais","Exam incidence":"Incidência em prova",
  "Weekly coach":"Coach semanal","Preview smart cleanup":"Pré-visualizar limpeza inteligente","Export HTML exam report":"Exportar relatório HTML da prova","Preview 7-day sprint":"Pré-visualizar sprint de 7 dias","Mode-aware analytics":"Análises por modo","Evidence Timeline":"Linha do tempo de evidências","Content Integrity":"Integridade do conteúdo","Local state":"Estado local","Storage health":"Saúde do armazenamento","Install & offline":"Instalação e offline",
  "Programming course":"Curso de programação","Language deck":"Baralho de idiomas","Reading study":"Estudo de leitura","General skill":"Habilidade geral","Concurso público":"Concurso público","Six-direction review studio":"Estúdio de revisão em seis direções","Import template":"Importar modelo",
  "No data":"Sem dados","No active plan":"Nenhum plano ativo","No package selected.":"Nenhum pacote selecionado.","Reset sample data":"Restaurar dados de exemplo","Check storage":"Verificar armazenamento","Clean orphan files":"Limpar arquivos órfãos","Install StudyFlow":"Instalar StudyFlow",
  "Starter template":"Modelo inicial","BB Agente Comercial — pre-edital":"BB Agente Comercial — pré-edital","Writing Workspace":"Espaço de Redação","Theme / task":"Tema / proposta","Draft":"Rascunho","Rubric":"Rubrica","Completion checklist":"Checklist de conclusão","Save draft":"Salvar rascunho","Finish Writing":"Concluir redação","Theme and task":"Tema e proposta","Text type":"Tipo textual","Cohesion":"Coesão","Argumentation":"Argumentação","Standard language":"Norma-padrão","Task reviewed":"Proposta revisada","Structure reviewed":"Estrutura revisada","Cohesion reviewed":"Coesão revisada","Standard language reviewed":"Norma-padrão revisada"
});

export function installDocumentLocalization(locale:AppLocale){
  if(typeof document==="undefined"||typeof MutationObserver==="undefined")return{dispose(){}};
  document.documentElement.lang=locale;
  type LocalizedValue={source:string;rendered:string};
  const textState=new WeakMap<Text,LocalizedValue>(); const attrState=new WeakMap<Element,Record<string,LocalizedValue>>();
  const translated=new Map(Object.entries(PT_BR_EXACT).map(([en,pt])=>[pt,en]));
  function canonical(value:string){return translated.get(value)??value;}
  function translate(value:string){const key=canonical(value);return locale==="pt-BR"?(PT_BR_EXACT[key]??key):key;}
  function localizedText(value:string){const lead=value.match(/^\s*/)?.[0]??"",trail=value.match(/\s*$/)?.[0]??"",core=value.trim();return core&&PT_BR_EXACT[canonical(core)]?lead+translate(core)+trail:value;}
  function visit(node:Node){
    if(node.nodeType===Node.TEXT_NODE){
      const t=node as Text; const current=t.nodeValue??""; let state=textState.get(t);
      if(!state||current!==state.rendered){const source=canonical(current.trim());const lead=current.match(/^\s*/)?.[0]??"",trail=current.match(/\s*$/)?.[0]??"";state={source:lead+source+trail,rendered:current};textState.set(t,state);}
      const next=localizedText(state.source); state.rendered=next; if(next!==current)t.nodeValue=next; return;
    }
    if(!(node instanceof Element))return;
    const attrs=["placeholder","aria-label","title"] as const; let states=attrState.get(node); if(!states){states={};attrState.set(node,states);}
    for(const name of attrs){const current=node.getAttribute(name);if(current==null)continue;let state=states[name];if(!state||current!==state.rendered){state={source:canonical(current),rendered:current};states[name]=state;}const next=translate(state.source);state.rendered=next;if(next!==current)node.setAttribute(name,next);}
    for(const child of Array.from(node.childNodes))visit(child);
  }
  visit(document.body);
  const observer=new MutationObserver(records=>{for(const record of records){if(record.type==="characterData")visit(record.target);for(const n of Array.from(record.addedNodes))visit(n);if(record.type==="attributes")visit(record.target);}});
  observer.observe(document.body,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:["placeholder","aria-label","title"]});
  return{dispose(){observer.disconnect();}};
}
