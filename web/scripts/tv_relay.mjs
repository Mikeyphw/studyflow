import {randomBytes, randomInt} from "node:crypto";

export const TV_CONTROL_PROTOCOL_VERSION=1;
export const TV_PAIRING_CODE_LENGTH=6;
export const TV_PAIRING_TTL_SECONDS=120;
const SURFACES=new Set(["HOME","TODAY","STUDY_SESSION","QUESTION_SET","REVIEW","MOCK_EXAM","PROGRESS","SCHEDULE","LIBRARY","SETTINGS"]);
const ACTIONS=new Set(["OPEN","START","PAUSE","RESUME","NEXT","PREVIOUS","SELECT_ANSWER","SHOW_EXPLANATION","MARK_FOR_REVIEW","SUBMIT","REVIEW_RESULTS","COMPLETE","BACK","STOP_CAST"]);
const CASTABLE_SURFACES=new Set(["STUDY_SESSION","QUESTION_SET","REVIEW","MOCK_EXAM","PROGRESS","SCHEDULE","LIBRARY"]);
const MAX_BODY=64*1024;
const EVENT_LIMIT=64;
const token=bytes=>randomBytes(bytes).toString("hex");
const code=()=>String(randomInt(0,10**TV_PAIRING_CODE_LENGTH)).padStart(TV_PAIRING_CODE_LENGTH,"0");

function json(res,status,value){const body=JSON.stringify(value);res.writeHead(status,{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store","Content-Length":Buffer.byteLength(body)});res.end(body);}
function text(res,status,value){res.writeHead(status,{"Content-Type":"text/plain; charset=utf-8","Cache-Control":"no-store"});res.end(value);}
async function body(req){let size=0;const chunks=[];for await(const chunk of req){size+=chunk.length;if(size>MAX_BODY)throw new Error("request body too large");chunks.push(chunk);}if(!chunks.length)return{};return JSON.parse(Buffer.concat(chunks).toString("utf8"));}
function isCommand(v){return v&&typeof v==="object"&&typeof v.commandId==="string"&&v.commandId&&v.protocolVersion===1&&SURFACES.has(v.surface)&&ACTIONS.has(v.action)&&Number.isFinite(v.issuedAtEpochMillis)&&v.requiresUserPresence===true;}
function isCast(v){return v&&typeof v==="object"&&typeof v.castId==="string"&&v.castId&&v.protocolVersion===1&&CASTABLE_SURFACES.has(v.surface)&&typeof v.title==="string"&&v.title.trim()&&Number.isFinite(v.updatedAtEpochMillis)&&(v.progressPercent==null||(Number.isFinite(v.progressPercent)&&v.progressPercent>=0&&v.progressPercent<=100));}

export function createTvRelay({now=()=>Date.now()}={}){
 const receivers=new Map();const codes=new Map();const controllers=new Map();
 function uniqueCode(){for(let i=0;i<20;i++){const value=code();if(!codes.has(value))return value;}throw new Error("Unable to allocate pairing code");}
 function cleanup(){const t=now();for(const [id,r] of receivers){if(!r.paired&&r.expiresAtEpochMillis<=t){receivers.delete(id);codes.delete(r.pairingCode);for(const waiter of r.pollWaiters)waiter();for(const res of r.sse)res.end();}}}
 function emit(r,type,payload){const envelope={sequence:++r.sequence,type,payload};r.events.push(envelope);if(r.events.length>EVENT_LIMIT)r.events.splice(0,r.events.length-EVENT_LIMIT);for(const res of r.sse)res.write(`id: ${envelope.sequence}\ndata: ${JSON.stringify(envelope)}\n\n`);for(const waiter of [...r.pollWaiters])waiter();return envelope;}
 function receiverFromQuery(url){const id=url.searchParams.get("receiverId")||"",nonce=url.searchParams.get("nonce")||"";const r=receivers.get(id);return r&&r.sessionNonce===nonce?r:null;}
 function bearer(req){const value=req.headers.authorization||"";return value.startsWith("Bearer ")?value.slice(7):"";}
 function controller(req){const entry=controllers.get(bearer(req));if(!entry)return null;const r=receivers.get(entry.receiverId);if(!r||r.revoked)return null;return{entry,r};}
 async function handle(req,res,url){cleanup();
  if(req.method==="POST"&&url.pathname==="/api/tv/receiver"){try{const input=await body(req);const deviceName=typeof input.deviceName==="string"&&input.deviceName.trim()?input.deviceName.trim().slice(0,80):"StudyFlow Browser TV";const pairingCode=uniqueCode();const issuedAtEpochMillis=now();const r={receiverId:token(12),deviceName,pairingCode,sessionNonce:token(16),issuedAtEpochMillis,expiresAtEpochMillis:issuedAtEpochMillis+TV_PAIRING_TTL_SECONDS*1000,paired:false,revoked:false,controllerToken:null,sequence:0,events:[],sse:new Set(),pollWaiters:new Set()};receivers.set(r.receiverId,r);codes.set(pairingCode,r.receiverId);json(res,201,{receiverId:r.receiverId,deviceName:r.deviceName,pairingCode:r.pairingCode,sessionNonce:r.sessionNonce,issuedAtEpochMillis:r.issuedAtEpochMillis,expiresAtEpochMillis:r.expiresAtEpochMillis,paired:false,protocolVersion:1});}catch(e){json(res,400,{error:e instanceof Error?e.message:String(e)});}return true;}
  if(req.method==="POST"&&url.pathname==="/api/tv/pair"){try{const input=await body(req);const pairingCode=String(input.pairingCode??"").trim();const id=codes.get(pairingCode);const r=id?receivers.get(id):null;if(!r||r.expiresAtEpochMillis<=now()||r.revoked){json(res,404,{error:"Pairing code is invalid or expired."});return true;}if(r.controllerToken)controllers.delete(r.controllerToken);r.controllerToken=token(32);r.paired=true;codes.delete(r.pairingCode);controllers.set(r.controllerToken,{receiverId:r.receiverId});emit(r,"pairing",{state:"PAIRED",deviceName:r.deviceName});json(res,200,{controllerToken:r.controllerToken,receiverId:r.receiverId,deviceName:r.deviceName,protocolVersion:1});}catch(e){json(res,400,{error:e instanceof Error?e.message:String(e)});}return true;}
  if(req.method==="GET"&&url.pathname==="/api/tv/events"){const r=receiverFromQuery(url);if(!r){text(res,403,"Invalid receiver session");return true;}res.writeHead(200,{"Content-Type":"text/event-stream; charset=utf-8","Cache-Control":"no-store","Connection":"keep-alive","X-Accel-Buffering":"no"});res.write(": StudyFlow TV relay\n\n");r.sse.add(res);const keep=setInterval(()=>{try{res.write(": keepalive\n\n");}catch{}},15000);req.on("close",()=>{clearInterval(keep);r.sse.delete(res);});return true;}
  if(req.method==="GET"&&url.pathname==="/api/tv/poll"){const r=receiverFromQuery(url);if(!r){json(res,403,{error:"Invalid receiver session"});return true;}const after=Math.max(0,Number(url.searchParams.get("after"))||0);const available=()=>r.events.filter(event=>event.sequence>after);let events=available();if(!events.length){await new Promise(resolve=>{const timer=setTimeout(done,20000);function done(){clearTimeout(timer);r.pollWaiters.delete(done);resolve();}r.pollWaiters.add(done);req.on("close",done);});events=available();}json(res,200,{events,cursor:r.sequence});return true;}
  if(req.method==="POST"&&url.pathname==="/api/tv/control"){const auth=controller(req);if(!auth){json(res,401,{error:"Controller is not paired."});return true;}try{const input=await body(req);if(!isCommand(input)){json(res,400,{error:"Invalid StudyFlowTvControlCommand."});return true;}emit(auth.r,"command",input);json(res,202,{accepted:true,commandId:input.commandId});}catch(e){json(res,400,{error:e instanceof Error?e.message:String(e)});}return true;}
  if(req.method==="POST"&&url.pathname==="/api/tv/cast"){const auth=controller(req);if(!auth){json(res,401,{error:"Controller is not paired."});return true;}try{const input=await body(req);if(!isCast(input)){json(res,400,{error:"Invalid StudyFlowTvCastState."});return true;}emit(auth.r,"cast",input);json(res,202,{accepted:true,castId:input.castId});}catch(e){json(res,400,{error:e instanceof Error?e.message:String(e)});}return true;}
  if(req.method==="POST"&&url.pathname==="/api/tv/revoke"){const auth=controller(req);if(!auth){json(res,401,{error:"Controller is not paired."});return true;}auth.r.revoked=true;auth.r.paired=false;controllers.delete(auth.r.controllerToken);emit(auth.r,"pairing",{state:"REVOKED",deviceName:auth.r.deviceName});json(res,200,{revoked:true});return true;}
  return false;
 }
 return{handle,stats:()=>({receivers:receivers.size,controllers:controllers.size})};
}
