"use strict";

import { migrateStudyState, validateStudyState } from "./domain";
import type {
  AttachmentMetadata, AttachmentRecord, AttachmentRepository, BrowserPersistence,
  StudyRepository, StudyState,
} from "./types";

export const STUDYFLOW_DB_NAME="studyflow-local";
export const STUDYFLOW_DB_VERSION=1;
export const STATE_STORE="state";
export const ATTACHMENT_STORE="attachments";
export const CURRENT_STATE_KEY="current";
export const RECOVERY_STATE_KEY="recovery";

function clone<T>(value:T):T{return value==null?value:structuredClone(value);}
function requestResult<T>(request:IDBRequest<T>):Promise<T>{return new Promise<T>((resolve,reject)=>{request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error??new Error("IndexedDB request failed"));});}
function transactionDone(transaction:IDBTransaction):Promise<void>{return new Promise<void>((resolve,reject)=>{transaction.oncomplete=()=>resolve();transaction.onerror=()=>reject(transaction.error??new Error("IndexedDB transaction failed"));transaction.onabort=()=>reject(transaction.error??new Error("IndexedDB transaction aborted"));});}
function idFactory():string{return globalThis.crypto?.randomUUID?.()??`${Date.now().toString(36)}-${Math.random().toString(36).slice(2,12)}`;}

export async function openStudyFlowDatabase({indexedDBFactory=globalThis.indexedDB,name=STUDYFLOW_DB_NAME}:{indexedDBFactory?:IDBFactory;name?:string}={}):Promise<IDBDatabase>{
  if(!indexedDBFactory?.open)throw new TypeError("IndexedDB is unavailable");
  const request=indexedDBFactory.open(name,STUDYFLOW_DB_VERSION);
  request.onupgradeneeded=()=>{
    const db=request.result;
    if(!db.objectStoreNames.contains(STATE_STORE))db.createObjectStore(STATE_STORE);
    if(!db.objectStoreNames.contains(ATTACHMENT_STORE))db.createObjectStore(ATTACHMENT_STORE,{keyPath:"id"});
  };
  return requestResult(request);
}

async function readStoredState(db:IDBDatabase,key:string):Promise<StudyState|null>{
  const tx=db.transaction(STATE_STORE,"readonly");
  const value=await requestResult<StudyState|undefined>(tx.objectStore(STATE_STORE).get(key));
  await transactionDone(tx);
  return value??null;
}

async function writeCurrentOnly(db:IDBDatabase,state:StudyState):Promise<void>{
  const tx=db.transaction(STATE_STORE,"readwrite");
  tx.objectStore(STATE_STORE).put(clone(state),CURRENT_STATE_KEY);
  await transactionDone(tx);
}

async function writeStateWithRecovery(db:IDBDatabase,next:StudyState,previous:StudyState|null):Promise<void>{
  const tx=db.transaction(STATE_STORE,"readwrite");
  const store=tx.objectStore(STATE_STORE);
  if(previous)store.put(clone(previous),RECOVERY_STATE_KEY);
  store.put(clone(next),CURRENT_STATE_KEY);
  await transactionDone(tx);
}

async function clearAll(db:IDBDatabase):Promise<void>{
  const tx=db.transaction([STATE_STORE,ATTACHMENT_STORE],"readwrite");
  tx.objectStore(STATE_STORE).clear();
  tx.objectStore(ATTACHMENT_STORE).clear();
  await transactionDone(tx);
}

function checkedState(raw:StudyState,now:string):StudyState{
  const candidate=migrateStudyState(clone(raw),{now}) as StudyState;
  const errors=validateStudyState(candidate) as string[];
  if(errors.length)throw new TypeError(`IndexedDB state failed validation: ${errors[0]}`);
  return candidate;
}

export function createIndexedDbAttachmentRepository(db:IDBDatabase,{now=()=>new Date().toISOString()}:{now?:()=>string}={}):AttachmentRepository{
  if(!db)throw new TypeError("Attachment repository requires an IndexedDB database");
  return {
    async put(file:File|Blob,{id=null,name=null}:{id?:string|null;name?:string|null}={}):Promise<AttachmentMetadata>{
      if(!(file instanceof Blob))throw new TypeError("Attachment must be a Blob or File");
      const fileName=file instanceof File&&file.name?file.name:"attachment";
      const lastModified=file instanceof File&&Number.isFinite(file.lastModified)?file.lastModified:null;
      const record:AttachmentRecord={id:id??`attachment-${idFactory()}`,name:name??fileName,mimeType:file.type||"application/octet-stream",sizeBytes:file.size,lastModified,createdAt:now(),blob:file};
      const tx=db.transaction(ATTACHMENT_STORE,"readwrite");
      tx.objectStore(ATTACHMENT_STORE).put(record);
      await transactionDone(tx);
      const {blob:_,...metadata}=record;
      return metadata;
    },
    async get(id:string):Promise<AttachmentRecord|null>{
      const tx=db.transaction(ATTACHMENT_STORE,"readonly");
      const record=await requestResult<AttachmentRecord|undefined>(tx.objectStore(ATTACHMENT_STORE).get(id));
      await transactionDone(tx);
      return record??null;
    },
    async list():Promise<AttachmentMetadata[]>{
      const tx=db.transaction(ATTACHMENT_STORE,"readonly");
      const records=await requestResult<AttachmentRecord[]>(tx.objectStore(ATTACHMENT_STORE).getAll());
      await transactionDone(tx);
      return (records??[]).map(({blob:_,...metadata})=>metadata).sort((a,b)=>a.name.localeCompare(b.name));
    },
    async delete(id:string):Promise<void>{const tx=db.transaction(ATTACHMENT_STORE,"readwrite");tx.objectStore(ATTACHMENT_STORE).delete(id);await transactionDone(tx);},
    async clear():Promise<void>{const tx=db.transaction(ATTACHMENT_STORE,"readwrite");tx.objectStore(ATTACHMENT_STORE).clear();await transactionDone(tx);},
  };
}

export async function createIndexedDbPersistence({indexedDBFactory=globalThis.indexedDB,legacyRepository=null,dbName=STUDYFLOW_DB_NAME,now=()=>new Date().toISOString()}:{indexedDBFactory?:IDBFactory;legacyRepository?:StudyRepository|null;dbName?:string;now?:()=>string}={}):Promise<BrowserPersistence & {db:IDBDatabase}>{
  const db=await openStudyFlowDatabase({indexedDBFactory,name:dbName});
  let raw=await readStoredState(db,CURRENT_STATE_KEY);
  let status="empty";
  let reason:string|null=null;
  let cache:StudyState|null=null;

  if(raw==null&&legacyRepository?.load){
    const legacy=legacyRepository.load();
    if(legacy?.state){raw=legacy.state;status="migrated";reason="Migrated browser localStorage state into IndexedDB.";}
  }

  if(raw!=null){
    try{
      const candidate=checkedState(raw,now());
      cache=candidate;
      if(status!=="migrated")status=raw.schemaVersion===candidate.schemaVersion?"loaded":"migrated";
      await writeStateWithRecovery(db,candidate,raw.schemaVersion===candidate.schemaVersion?null:raw);
      if(status==="migrated"&&legacyRepository?.clear)legacyRepository.clear();
    }catch(currentError:unknown){
      const recoveryRaw=await readStoredState(db,RECOVERY_STATE_KEY);
      if(!recoveryRaw)throw currentError;
      try{
        cache=checkedState(recoveryRaw,now());
        status="recovered";
        reason="Recovered the last verified local checkpoint after the current IndexedDB state failed validation.";
        await writeCurrentOnly(db,cache);
      }catch{
        throw currentError;
      }
    }
  }

  let queue:Promise<void>=Promise.resolve();
  let lastAsyncError:unknown=null;
  const repository:StudyRepository={
    backend:"indexeddb",
    load(){return{status,state:clone(cache),reason};},
    save(state:StudyState){
      const errors=validateStudyState(state) as string[];
      if(errors.length)return{status:"invalid",reason:`Refused to save invalid state: ${errors[0]}`};
      const previous=clone(cache);
      const next=clone(state);
      cache=next;
      status="loaded";
      reason=null;
      lastAsyncError=null;
      queue=queue.then(()=>writeStateWithRecovery(db,next,previous)).catch((error:unknown)=>{lastAsyncError=error;});
      return{status:"saved",reason:null};
    },
    clear(){
      cache=null;status="empty";reason=null;lastAsyncError=null;
      queue=queue.then(()=>clearAll(db)).catch((error:unknown)=>{lastAsyncError=error;});
      legacyRepository?.clear?.();
      return{status:"cleared",reason:null};
    },
    async flush(){await queue;if(lastAsyncError)throw lastAsyncError;},
    close(){db.close();},
  };
  return {backend:"indexeddb",repository,attachments:createIndexedDbAttachmentRepository(db,{now}),db};
}
