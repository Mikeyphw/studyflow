import {createReadStream, existsSync, statSync} from "node:fs";
import {createServer} from "node:http";
import {networkInterfaces} from "node:os";
import {extname, join, normalize, resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {createTvRelay} from "./tv_relay.mjs";

const root=resolve(fileURLToPath(new URL("../dist/",import.meta.url)));
const argv=process.argv.slice(2);
function value(flag,fallback){const index=argv.indexOf(flag);return index>=0&&argv[index+1]?argv[index+1]:fallback;}
const host=value("--host",process.env.STUDYFLOW_TV_HOST||"0.0.0.0");
const port=Number(value("--port",process.env.STUDYFLOW_TV_PORT||"8000"));
if(!Number.isInteger(port)||port<1||port>65535)throw new Error(`Invalid TV server port: ${port}`);
if(!existsSync(join(root,"index.html")))throw new Error("web/dist is missing. Run npm run build first.");

const mime={".html":"text/html; charset=utf-8",".css":"text/css; charset=utf-8",".js":"text/javascript; charset=utf-8",".json":"application/json; charset=utf-8",".webmanifest":"application/manifest+json; charset=utf-8",".png":"image/png",".txt":"text/plain; charset=utf-8"};
function sendFile(path,res){res.writeHead(200,{"Content-Type":mime[extname(path)]||"application/octet-stream","Cache-Control":"no-cache","X-Content-Type-Options":"nosniff"});createReadStream(path).pipe(res);}
function remotePage(){return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>StudyFlow TV Remote</title><style>body{font:16px system-ui,sans-serif;max-width:720px;margin:0 auto;padding:24px;background:#111;color:#f5f5f5}input,select,button{font:inherit;padding:12px;margin:4px;border-radius:8px;border:1px solid #666;background:#222;color:inherit}button{cursor:pointer}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.card{border:1px solid #444;border-radius:12px;padding:16px;margin:12px 0}.ok{color:#9f9}</style></head><body><h1>StudyFlow TV Remote</h1><div class="card"><label>Pairing code <input id="code" inputmode="numeric" maxlength="6" pattern="[0-9]{6}"></label><button id="pair">Pair</button><p id="status">Enter the six-digit code shown on TV.</p></div><div class="card"><h2>Open surface</h2><div class="grid" id="surfaces"></div></div><div class="card"><h2>Controls</h2><div class="grid" id="actions"></div></div><script>
let controllerId='';
let receiverId='';
let controllerAuthKey=null;
let currentSurface='TODAY';
const status=document.getElementById('status');
const EMPTY_HASH='e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
const utf8=value=>new TextEncoder().encode(value);
const hex=buffer=>[...new Uint8Array(buffer)].map(value=>value.toString(16).padStart(2,'0')).join('');
const unhex=value=>{if(value.length%2||!/^[a-f0-9]+$/.test(value))throw new Error('Invalid relay key');const out=new Uint8Array(value.length/2);for(let i=0;i<out.length;i++)out[i]=parseInt(value.slice(i*2,i*2+2),16);return out};
function nonce(){if(!crypto?.getRandomValues)throw new Error('Secure WebCrypto is required');const out=new Uint8Array(16);crypto.getRandomValues(out);return hex(out)}
async function derive(privateKey,relayPublicHex,context){const pub=await crypto.subtle.importKey('spki',unhex(relayPublicHex),{name:'ECDH',namedCurve:'P-256'},false,[]);const shared=new Uint8Array(await crypto.subtle.deriveBits({name:'ECDH',public:pub},privateKey,256));const ctx=utf8(context);const joined=new Uint8Array(shared.length+ctx.length);joined.set(shared);joined.set(ctx,shared.length);const digest=await crypto.subtle.digest('SHA-256',joined);return crypto.subtle.importKey('raw',digest,{name:'HMAC',hash:'SHA-256'},false,['sign'])}
async function signedHeaders(method,path,body){if(!controllerAuthKey||!controllerId)throw new Error('Pair the remote first.');const ts=Date.now();const authNonce=nonce();const bodyHash=body?hex(await crypto.subtle.digest('SHA-256',utf8(body))):EMPTY_HASH;const canonical=[method.toUpperCase(),path,controllerId,String(ts),authNonce,bodyHash].join('\n');const proof=hex(await crypto.subtle.sign('HMAC',controllerAuthKey,utf8(canonical)));return {'X-StudyFlow-Controller-Id':controllerId,'X-StudyFlow-Auth-Timestamp':String(ts),'X-StudyFlow-Auth-Nonce':authNonce,'X-StudyFlow-Auth-Proof':proof}}
async function request(path,options={},authenticated=true){const body=options.body||'';const headers={'Content-Type':'application/json',...(options.headers||{})};if(authenticated)Object.assign(headers,await signedHeaders(options.method||'GET',path,body));const res=await fetch(path,{...options,headers});const data=await res.json().catch(()=>({}));if(!res.ok)throw new Error(data.error||('HTTP '+res.status));return data}
async function withPlane(path,payload,operation){if(!controllerId||!receiverId)throw new Error('Pair the remote first.');const now=Date.now();const bodyHash=hex(await crypto.subtle.digest('SHA-256',utf8(JSON.stringify(payload))));return {...payload,planeEnvelope:{messageId:payload.commandId||payload.castId||('webremote-'+now),protocolVersion:1,operation,sessionId:controllerId,receiverId,castScopeId:payload.action==='STOP_CAST'?(payload.targetId||payload.surface):payload.studyPlanId,presentationSnapshotId:payload.castId,issuedAtEpochMillis:now,expiresAtEpochMillis:now+30000,bodySha256Hex:bodyHash}}}
function command(surface,action,extra={}){const payload={commandId:'webremote-'+Date.now()+'-'+nonce(),protocolVersion:1,surface,action,issuedAtEpochMillis:Date.now(),requiresUserPresence:true,...extra};return withPlane('/api/tv/control',payload,'COMMAND').then(wrapped=>request('/api/tv/control',{method:'POST',body:JSON.stringify(wrapped)})).then(()=>status.textContent=action+' sent').catch(e=>status.textContent=e.message)}
document.getElementById('pair').onclick=async()=>{try{if(!crypto?.subtle)throw new Error('WebCrypto is required for secure pairing.');const keys=await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'},false,['deriveBits']);const publicHex=hex(await crypto.subtle.exportKey('spki',keys.publicKey));const data=await request('/api/tv/pair',{method:'POST',body:JSON.stringify({pairingCode:document.getElementById('code').value.trim(),controllerKeyAgreementPublicKeyHex:publicHex})},false);controllerId=data.controllerId;receiverId=data.receiverId;controllerAuthKey=await derive(keys.privateKey,data.relayKeyAgreementPublicKeyHex,'StudyFlow-TV-Controller-V2|'+controllerId);status.textContent='Paired securely with '+data.deviceName;status.className='ok'}catch(e){controllerId='';receiverId='';controllerAuthKey=null;status.textContent=e.message}};
const surfaces={Today:'TODAY',Session:'STUDY_SESSION',Questions:'QUESTION_SET',Reviews:'REVIEW','Mock Exam':'MOCK_EXAM',Progress:'PROGRESS',Schedule:'SCHEDULE',Library:'LIBRARY',Settings:'SETTINGS'};for(const [label,surface] of Object.entries(surfaces)){const b=document.createElement('button');b.textContent=label;b.onclick=()=>{currentSurface=surface;command(surface,'OPEN')};document.getElementById('surfaces').appendChild(b)}
const actions=['START','PAUSE','RESUME','PREVIOUS','NEXT','SHOW_EXPLANATION','MARK_FOR_REVIEW','SUBMIT','REVIEW_RESULTS','COMPLETE','BACK','STOP_CAST'];for(const action of actions){const b=document.createElement('button');b.textContent=action.replaceAll('_',' ');b.onclick=()=>command(currentSurface,action);document.getElementById('actions').appendChild(b)}
for(let index=0;index<4;index++){const b=document.createElement('button');b.textContent='Choose '+String.fromCharCode(65+index);b.onclick=()=>command(currentSurface==='MOCK_EXAM'?'MOCK_EXAM':'QUESTION_SET','SELECT_ANSWER',{optionId:'choice-'+index});document.getElementById('actions').appendChild(b)}
for(const mode of [['Review queue','review-reviews'],['Error notebook','review-errors']]){const b=document.createElement('button');b.textContent=mode[0];b.onclick=()=>command('REVIEW','OPEN',{targetId:mode[1]});document.getElementById('actions').appendChild(b)}
for(const rating of ['again','hard','good','easy']){const b=document.createElement('button');b.textContent='Review '+rating;b.onclick=()=>command('REVIEW','COMPLETE',{optionId:'rating-'+rating});document.getElementById('actions').appendChild(b)}
for(const category of ['content-gap','forgotten','concept-confusion','interpretation','distraction','calculation','time-pressure','disputed','other']){const b=document.createElement('button');b.textContent='Error: '+category.replaceAll('-',' ');b.onclick=()=>command('REVIEW','MARK_FOR_REVIEW',{optionId:'category-'+category});document.getElementById('actions').appendChild(b)}
{const b=document.createElement('button');b.textContent='Resolve current error';b.onclick=()=>command('REVIEW','COMPLETE',{optionId:'resolve-error'});document.getElementById('actions').appendChild(b)}
for(const item of [['Progress: Today','PROGRESS','progress-today','TODAY'],['Progress: Questions','PROGRESS','progress-questions','QUESTION_SET'],['Progress: Reviews','PROGRESS','progress-reviews','REVIEW'],['Progress: Mock Exam','PROGRESS','progress-mock','MOCK_EXAM'],['Progress: Schedule','PROGRESS','progress-schedule','SCHEDULE'],['Schedule: Today','SCHEDULE','schedule-today','SCHEDULE'],['Schedule: This week','SCHEDULE','schedule-week','SCHEDULE'],['Schedule: Progress','SCHEDULE','schedule-progress','PROGRESS'],['Library: All','LIBRARY','library-all','LIBRARY'],['Library: Local files','LIBRARY','library-local','LIBRARY'],['Library: Web references','LIBRARY','library-reference','LIBRARY']]){const b=document.createElement('button');b.textContent=item[0];b.onclick=()=>command(item[1],'OPEN',{targetId:item[2]}).then(()=>{currentSurface=item[3]});document.getElementById('actions').appendChild(b)}
</script></body></html>`;}
const relay=createTvRelay();
const server=createServer(async(req,res)=>{
 const requestUrl=new URL(req.url||"/",`http://${req.headers.host||"localhost"}`);
 if(await relay.handle(req,res,requestUrl))return;
 if(requestUrl.pathname==="/tv"||requestUrl.pathname==="/tv/"){res.writeHead(302,{Location:"/?tv=1","Cache-Control":"no-store"});res.end();return;}
 if(requestUrl.pathname==="/remote"||requestUrl.pathname==="/remote/"){const html=remotePage();res.writeHead(200,{"Content-Type":"text/html; charset=utf-8","Cache-Control":"no-store","Content-Length":Buffer.byteLength(html)});res.end(html);return;}
 let pathname=decodeURIComponent(requestUrl.pathname);if(pathname==="/")pathname="/index.html";
 const relative=normalize(pathname).replace(/^([/\\])+/,'');const path=resolve(root,relative);
 if(path!==root&&!path.startsWith(root+"/")){res.writeHead(403);res.end("Forbidden");return;}
 if(existsSync(path)&&statSync(path).isFile()){sendFile(path,res);return;}
 if(req.method==="GET"&&req.headers.accept?.includes("text/html")){sendFile(join(root,"index.html"),res);return;}
 res.writeHead(404,{"Content-Type":"text/plain; charset=utf-8"});res.end("Not found");
});

server.listen(port,host,()=>{
 const addresses=[];for(const entries of Object.values(networkInterfaces()))for(const info of entries||[])if(info.family==="IPv4"&&!info.internal)addresses.push(info.address);
 console.log("StudyFlow TV-browser server ready");
 console.log(`  Local: http://127.0.0.1:${port}/tv`);
 console.log(`  Remote: http://127.0.0.1:${port}/remote`);
 for(const address of [...new Set(addresses)]){console.log(`  TV:     http://${address}:${port}/tv`);console.log(`  Phone:  http://${address}:${port}/remote`);}
 console.log("Open /tv on the television, then /remote on a phone and enter the six-digit pairing code.");
 console.log("Pairing is in-memory, revocable and expires after 120 seconds before pairing; no StudyFlow database rows are relayed.");
});
