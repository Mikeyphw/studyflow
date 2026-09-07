"use strict";

import { STATE_SCHEMA_VERSION, migrateStudyState, validateStudyState } from "./domain";
import { createIndexedDbPersistence } from "./indexeddb";
import type { AttachmentRepository, BrowserPersistence, StudyRepository, StudyState } from "./types";

export const STORAGE_KEY = `studyflow.local.state.v${STATE_SCHEMA_VERSION}`;
export const LEGACY_STORAGE_KEYS = Object.freeze(["studyflow.local.state.v3", "studyflow.local.state.v2", "studyflow.local.state.v1"]);

type RepositoryResult = { status: string; state?: StudyState | null; reason?: string | null; legacyKey?: string };
type BrowserRepository = StudyRepository & { backend: "localstorage"; key: string };

function resolveBrowserStorage(): Storage | null { try { return globalThis.localStorage ?? null; } catch { return null; } }
function result(status: string, extra: Omit<RepositoryResult,"status"> = {}): RepositoryResult { return { status, ...extra }; }
function parseState(raw: string, now: string): RepositoryResult {
  let state: unknown;
  try { state = JSON.parse(raw); } catch { return result("invalid", { state: null, reason: "Saved local state is not valid JSON." }); }
  const migrated = migrateStudyState(state, { now }) as StudyState;
  const errors = validateStudyState(migrated) as string[];
  if (errors.length) return result("invalid", { state: null, reason: `Saved local state failed validation: ${errors[0]}` });
  const originalVersion = typeof state === "object" && state !== null && "schemaVersion" in state ? Number((state as {schemaVersion?:unknown}).schemaVersion) : null;
  return result(originalVersion === STATE_SCHEMA_VERSION ? "loaded" : "migrated", { state: migrated, reason: null });
}

export function createBrowserRepository({
  storage = resolveBrowserStorage(), key = STORAGE_KEY, legacyKeys = LEGACY_STORAGE_KEYS, now = () => new Date().toISOString(),
}: {storage?: Storage|null; key?: string; legacyKeys?: readonly string[]; now?: ()=>string} = {}): BrowserRepository {
  return {
    backend: "localstorage", key,
    load(): RepositoryResult {
      if (!storage) return result("unavailable", { state: null, reason: "Browser local storage is unavailable." });
      try {
        const raw=storage.getItem(key); if(raw!=null)return parseState(raw,now());
        for(const legacyKey of legacyKeys){if(legacyKey===key)continue;const legacyRaw=storage.getItem(legacyKey);if(legacyRaw==null)continue;const parsed=parseState(legacyRaw,now());if(parsed.state)return {...parsed,status:"migrated",legacyKey};return parsed;}
        return result("empty",{state:null,reason:null});
      } catch(error:unknown) { return result("unavailable",{state:null,reason:`Local storage read failed: ${error instanceof Error?error.message:String(error)}`}); }
    },
    save(state: StudyState): RepositoryResult {
      if(!storage)return result("unavailable",{reason:"Browser local storage is unavailable."}); const errors=validateStudyState(state) as string[]; if(errors.length)return result("invalid",{reason:`Refused to save invalid state: ${errors[0]}`});
      try{storage.setItem(key,JSON.stringify(state));}catch(error:unknown){return result("unavailable",{reason:`Local storage write failed: ${error instanceof Error?error.message:String(error)}`});} return result("saved",{reason:null});
    },
    clear(): RepositoryResult { if(!storage)return result("unavailable",{reason:"Browser local storage is unavailable."});try{storage.removeItem(key);for(const legacyKey of legacyKeys)storage.removeItem(legacyKey);}catch(error:unknown){return result("unavailable",{reason:`Local storage clear failed: ${error instanceof Error?error.message:String(error)}`});}return result("cleared",{reason:null}); },
  };
}

function unavailableAttachments(reason:string): AttachmentRepository {
  const fail=async():Promise<never>=>{throw new TypeError(reason);};
  return { put:fail, get:fail, list:async()=>[], delete:async()=>{await fail();}, clear:async()=>undefined };
}

export async function createBrowserPersistence({
  storage=resolveBrowserStorage(), indexedDBFactory=globalThis.indexedDB, now=()=>new Date().toISOString(), dbName,
}: {storage?:Storage|null;indexedDBFactory?:IDBFactory;now?:()=>string;dbName?:string} = {}): Promise<BrowserPersistence & {fallbackReason?:string}> {
  const legacyRepository=createBrowserRepository({storage,now});
  if(!indexedDBFactory?.open)return {backend:"localstorage",repository:legacyRepository,attachments:unavailableAttachments("Attachment storage requires IndexedDB."),fallbackReason:"IndexedDB is unavailable; using localStorage compatibility mode."};
  try{return await createIndexedDbPersistence({indexedDBFactory,legacyRepository,now,dbName});}
  catch(error:unknown){return {backend:"localstorage",repository:legacyRepository,attachments:unavailableAttachments("Attachment storage is unavailable because IndexedDB failed to initialize."),fallbackReason:error instanceof Error?error.message:String(error)};}
}
