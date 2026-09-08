"use strict";

import type {
  ExamNoticeRevision, Material, Plan, PublicExamPreEditalSnapshot, StudyPhaseDefinition,
  StudyReferenceFreshness, StudyReferenceOrigin, StudyReferenceProvenance,
} from "./types";
import { resolvePublicExamDate } from "./promise_cleanup";

const DAY_MS=86_400_000;
const PHASE_KINDS=new Set(["foundation","expansion","consolidation","questions-reviews","custom"]);
const ORIGIN_PREFIX="studyflow:reference-origin=";
const VERIFIED_PREFIX="studyflow:reference-verified-on=";
const SUPERSEDES_PREFIX="studyflow:reference-supersedes=";
export const REFERENCE_POSSIBLY_STALE_AFTER_DAYS=90;
export const PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS=365;

const cleanText=(value:unknown):string|null=>{const text=String(value??"").trim();return text||null;};
const dateOnly=(value:unknown):string|null=>{const text=cleanText(value);return text&&/^\d{4}-\d{2}-\d{2}$/.test(text)&&Number.isFinite(Date.parse(`${text}T00:00:00Z`))?text:null;};
const dayFromDateLike=(value:string|Date):string|null=>{if(value instanceof Date)return Number.isNaN(value.getTime())?null:value.toISOString().slice(0,10);const canonical=dateOnly(value);if(canonical)return canonical;const parsed=new Date(value);return Number.isNaN(parsed.getTime())?null:parsed.toISOString().slice(0,10);};
const nonNegativeInt=(value:unknown):number|null=>{if(value==null||value==="")return null;const n=Number(value);return Number.isInteger(n)&&n>=0?n:null;};
const pct=(value:unknown):number=>{const n=Number(value);return Number.isInteger(n)&&n>=0&&n<=100?n:0;};
const clone=<T>(value:T):T=>JSON.parse(JSON.stringify(value)) as T;

export const BB_STUDY_PHASE_SUGGESTIONS:readonly StudyPhaseDefinition[]=Object.freeze([
  {id:"bb-foundation-2026",kind:"foundation",label:"Foundation",startsOn:"2026-09-07",endsOn:"2026-09-30",theoryRatioPercent:60,questionRatioPercent:25,reviewRatioPercent:15,questionGoal:300,mockExamGoal:3,allocationIsStudyFlowSuggested:true},
  {id:"bb-expansion-2026",kind:"expansion",label:"Expansion",startsOn:"2026-10-01",endsOn:"2026-10-31",theoryRatioPercent:45,questionRatioPercent:40,reviewRatioPercent:15,questionGoal:500,mockExamGoal:5,allocationIsStudyFlowSuggested:true},
  {id:"bb-consolidation-2026",kind:"consolidation",label:"Consolidation",startsOn:"2026-11-01",endsOn:"2026-11-30",theoryRatioPercent:25,questionRatioPercent:55,reviewRatioPercent:20,questionGoal:700,mockExamGoal:4,allocationIsStudyFlowSuggested:true},
  {id:"bb-questions-reviews-2026",kind:"questions-reviews",label:"Questions & reviews",startsOn:"2026-12-01",endsOn:"2026-12-31",theoryRatioPercent:10,questionRatioPercent:65,reviewRatioPercent:25,questionGoal:1000,mockExamGoal:4,allocationIsStudyFlowSuggested:true},
] as StudyPhaseDefinition[]);

export function normalizeStudyPhaseDefinitions(input:unknown):StudyPhaseDefinition[]{
  if(!Array.isArray(input))return [];
  return input.map((raw:any,index)=>{
    const kind=PHASE_KINDS.has(String(raw?.kind))?String(raw.kind) as StudyPhaseDefinition["kind"]:"custom";
    const startsOn=dateOnly(raw?.startsOn)??"1970-01-01";
    const endsOn=dateOnly(raw?.endsOn)??startsOn;
    return {
      id:cleanText(raw?.id)??`phase-${index+1}`,
      kind,
      label:cleanText(raw?.label)??kind.replace(/-/g," "),
      startsOn,endsOn,
      theoryRatioPercent:pct(raw?.theoryRatioPercent),
      questionRatioPercent:pct(raw?.questionRatioPercent),
      reviewRatioPercent:pct(raw?.reviewRatioPercent),
      questionGoal:nonNegativeInt(raw?.questionGoal),
      mockExamGoal:nonNegativeInt(raw?.mockExamGoal),
      allocationIsStudyFlowSuggested:Boolean(raw?.allocationIsStudyFlowSuggested),
    };
  });
}

export function validateStudyPhaseDefinitions(input:unknown):string[]{
  const phases=normalizeStudyPhaseDefinitions(input);const errors:string[]=[];
  const raw=Array.isArray(input)?input:[];
  if(raw.length!==phases.length)return ["study phases must be an array"];
  const ids=new Set<string>(),kinds=new Set<string>();
  for(const phase of phases){
    if(ids.has(phase.id))errors.push(`study phase id must be unique: ${phase.id}`);ids.add(phase.id);
    if(kinds.has(phase.kind))errors.push(`study phase kind may appear only once: ${phase.kind}`);kinds.add(phase.kind);
    if(phase.endsOn<phase.startsOn)errors.push(`study phase ${phase.label} ends before it starts`);
    if(phase.theoryRatioPercent+phase.questionRatioPercent+phase.reviewRatioPercent!==100)errors.push(`study phase ${phase.label} allocation must total 100%`);
  }
  const ordered=[...phases].sort((a,b)=>a.startsOn.localeCompare(b.startsOn)||a.id.localeCompare(b.id));
  for(let i=1;i<ordered.length;i++)if(ordered[i].startsOn<=ordered[i-1].endsOn)errors.push(`study phases cannot overlap: ${ordered[i-1].label} / ${ordered[i].label}`);
  return errors;
}

export function deriveActiveStudyPhase(plan:Plan,date:string|Date=new Date()):StudyPhaseDefinition|null{
  if(plan.mode!=="EXAM_PLAN")return null;const day=dayFromDateLike(date);if(!day)return null;
  return plan.configuration.publicExam?.phaseDefinitions.find(phase=>phase.startsOn<=day&&day<=phase.endsOn)??null;
}

export function cloneBbStudyPhaseSuggestions():StudyPhaseDefinition[]{return clone(BB_STUDY_PHASE_SUGGESTIONS as StudyPhaseDefinition[]);}

export function derivePreEditalCockpit(plan:Plan,at:string|Date=new Date()):PublicExamPreEditalSnapshot|null{
  if(plan.mode!=="EXAM_PLAN"||!plan.configuration.publicExam)return null;
  const cfg=plan.configuration.publicExam,target=cfg.target;const now=at instanceof Date?at:new Date(at);if(Number.isNaN(now.getTime()))throw new TypeError("Invalid cockpit timestamp");
  const ordered=[...cfg.noticeRevisions].sort((a,b)=>a.acceptedAt.localeCompare(b.acceptedAt)||a.id.localeCompare(b.id));const latest=ordered.length?ordered[ordered.length-1]:null;
  const resolved=resolvePublicExamDate(plan);
  const resolvedDate={date:resolved.date,source:resolved.source,isConfirmed:resolved.official} as PublicExamPreEditalSnapshot["resolvedDate"];
  const referenceLabel=latest?.reference??latest?.id??target.editalRef??null;
  const referenceAgeDays=latest?.publishedAt?Math.max(0,Math.floor((Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate())-Date.parse(`${latest.publishedAt}T00:00:00Z`))/DAY_MS)):null;
  const staleReferenceDays=Math.max(1,Math.trunc(cfg.cockpitStaleReferenceDays||PRE_EDITAL_DEFAULT_STALE_REFERENCE_DAYS));
  if(resolvedDate.isConfirmed)return {preEdital:false,officialCountdownDays:null,resolvedDate,latestRevision:latest,referenceLabel,referenceAgeDays,issues:[],staleReferenceDays};
  const issues:PublicExamPreEditalSnapshot["issues"]=["no-confirmed-exam-date"];
  if(!latest&&!target.editalRef)issues.push("no-reference-notice");
  if((latest&&!latest.publishedAt)||(!latest&&Boolean(target.editalRef)))issues.push("reference-publication-date-unknown");
  if(referenceAgeDays!=null&&referenceAgeDays>staleReferenceDays)issues.push("stale-reference-notice");
  return {preEdital:true,officialCountdownDays:null,resolvedDate,latestRevision:latest,referenceLabel,referenceAgeDays,issues,staleReferenceDays};
}

function managedReferenceTag(tag:string):boolean{return tag.startsWith(ORIGIN_PREFIX)||tag.startsWith(VERIFIED_PREFIX)||tag.startsWith(SUPERSEDES_PREFIX);}
function valueFor(tags:string[],prefix:string):string|null{return tags.find(tag=>tag.startsWith(prefix))?.slice(prefix.length)??null;}
export function decodeReferenceProvenance(materialOrTags:Material|string[]):StudyReferenceProvenance{
  const tags=Array.isArray(materialOrTags)?materialOrTags:materialOrTags.tags;
  const originRaw=valueFor(tags,ORIGIN_PREFIX);const verifiedOn=dateOnly(valueFor(tags,VERIFIED_PREFIX));const supersedesMaterialId=cleanText(valueFor(tags,SUPERSEDES_PREFIX));
  return {origin:originRaw==="EDITAL_2022"?originRaw as StudyReferenceOrigin:null,verifiedOn,supersedesMaterialId};
}
export function encodeReferenceProvenance(tags:string[],provenance:StudyReferenceProvenance):string[]{
  const next=tags.filter(tag=>!managedReferenceTag(tag));if(provenance.origin)next.push(`${ORIGIN_PREFIX}${provenance.origin}`);if(provenance.verifiedOn)next.push(`${VERIFIED_PREFIX}${provenance.verifiedOn}`);if(provenance.supersedesMaterialId)next.push(`${SUPERSEDES_PREFIX}${provenance.supersedesMaterialId}`);return [...new Set(next)];
}
export function deriveMaterialReferenceFreshness(material:Material,today:string|Date=new Date()):StudyReferenceFreshness{
  const provenance=decodeReferenceProvenance(material);if(!provenance.origin)return {status:"untracked",ageDays:null,provenance};if(!provenance.verifiedOn)return {status:"needs-verification",ageDays:null,provenance};
  const now=today instanceof Date?today:new Date(today);if(Number.isNaN(now.getTime()))throw new TypeError("Invalid reference freshness date");const day=Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate());const ageDays=Math.max(0,Math.floor((day-Date.parse(`${provenance.verifiedOn}T00:00:00Z`))/DAY_MS));return {status:ageDays>REFERENCE_POSSIBLY_STALE_AFTER_DAYS?"possibly-stale":"current",ageDays,provenance};
}
