"use strict";

import { STATE_SCHEMA_VERSION, validateStudyState } from "./domain";
import { STUDYFLOW_DB_VERSION } from "./indexeddb";
import { PORTABLE_FORMAT_VERSION } from "./portable";
import type { StudyState } from "./types";

export const STUDYFLOW_LOCAL_RELEASE=Object.freeze({
  product:"StudyFlow Local",
  version:"1.0.0",
  stateSchema:STATE_SCHEMA_VERSION,
  indexedDbSchema:STUDYFLOW_DB_VERSION,
  portableFormat:PORTABLE_FORMAT_VERSION,
  channel:"local-first",
  releaseSeal:"parity-closure",
  runtimeArtifact:"self-contained-dist",
});

export interface ReleaseReadiness {
  status:"ready"|"degraded"|"blocked";
  version:string;
  reasons:string[];
}

export function evaluateReleaseReadiness(state:StudyState,persistenceBackend:string):ReleaseReadiness{
  const errors=validateStudyState(state) as string[];
  if(errors.length)return{status:"blocked",version:STUDYFLOW_LOCAL_RELEASE.version,reasons:[`State validation failed: ${errors[0]}`]};
  const reasons:string[]=[];
  if(!state.activePlanId)reasons.push("No active plan is selected.");
  if(persistenceBackend!=="indexeddb")reasons.push("IndexedDB is unavailable; structured state is running in compatibility storage and local attachment bytes are disabled.");
  return{status:reasons.length?"degraded":"ready",version:STUDYFLOW_LOCAL_RELEASE.version,reasons:reasons.length?reasons:["Local state, schema and release contracts are valid."]};
}
