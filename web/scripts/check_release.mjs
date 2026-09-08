import {createHash} from "node:crypto";
import {existsSync, readFileSync, readdirSync, statSync} from "node:fs";
import {dirname, extname, join, relative, resolve} from "node:path";
import {fileURLToPath} from "node:url";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const dist=resolve(process.env.STUDYFLOW_DIST_DIR||join(root,"dist"));
function fail(message){throw new Error(message)}
if(process.env.STUDYFLOW_RELEASE_SKIP_NODE_MODULES==="1"){
  const sourceSw=readFileSync(join(root,"service-worker.js"),"utf8");
  const m=sourceSw.match(/const APP_SHELL = \[([\s\S]*?)\];/);
  if(!m)fail("source service-worker APP_SHELL declaration missing");
  const sourceEntries=[...m[1].matchAll(/"([^"\n]+)"/g)].map(x=>x[1]);
  for(const entry of sourceEntries){
    if(/^https?:/i.test(entry)||entry.startsWith("//"))fail(`remote app-shell URL forbidden: ${entry}`);
    if(entry.startsWith("./node_modules/"))continue;
    if(entry!=="./"&&!existsSync(join(root,entry.replace(/^\.\//,""))))fail(`source app-shell file missing: ${entry}`);
  }
  const pkg=JSON.parse(readFileSync(join(root,"package.json"),"utf8"));
  console.log(`StudyFlow Local ${pkg.version} release shell OK (${sourceEntries.length} cached files; source compatibility mode)`);
  process.exit(0);
}
if(!existsSync(dist))fail("dist/ is missing; run npm run build first");
const sw=readFileSync(join(dist,"service-worker.js"),"utf8");
const match=sw.match(/const APP_SHELL = \[([\s\S]*?)\];/);
if(!match)fail("dist service-worker APP_SHELL declaration missing");
const entries=[...match[1].matchAll(/"([^"\n]+)"/g)].map(x=>x[1]);
if(new Set(entries).size!==entries.length)fail("dist app shell contains duplicate entries");
for(const entry of entries){
  if(/^https?:/i.test(entry)||entry.startsWith("//"))fail(`remote app-shell URL forbidden: ${entry}`);
  if(entry.includes("node_modules"))fail(`runtime node_modules path forbidden: ${entry}`);
  if(entry!=="./"&&!existsSync(join(dist,entry.replace(/^\.\//,""))))fail(`dist app-shell file missing: ${entry}`);
}
function all(dir){const out=[];for(const name of readdirSync(dir)){const p=join(dir,name);if(statSync(p).isDirectory())out.push(...all(p));else out.push(p)}return out.sort()}
function firstRemoteRuntimeReference(path,text){
  const ext=extname(path).toLowerCase();
  if(ext===".html"){
    const m=text.match(/\b(?:src|href|action|poster)\s*=\s*["']\s*https?:\/\//i);
    return m?.[0]||null;
  }
  if(ext===".css"){
    const m=text.match(/(?:\burl\s*\(\s*|@import\s+)(?:["']\s*)?https?:\/\//i);
    return m?.[0]||null;
  }
  if(ext===".webmanifest"){
    let value;
    try{value=JSON.parse(text)}catch{fail(`invalid webmanifest JSON: ${relative(dist,path)}`)}
    const stack=[value];
    while(stack.length){const current=stack.pop();if(typeof current==="string"&&/^https?:\/\//i.test(current))return current;if(Array.isArray(current))stack.push(...current);else if(current&&typeof current==="object")stack.push(...Object.values(current))}
    return null;
  }
  if(ext!==".js")return null;
  const patterns=[
    /\b(?:fetch|importScripts)\s*\(\s*["'`]\s*https?:\/\//i,
    /\bnavigator\s*\.\s*sendBeacon\s*\(\s*["'`]\s*https?:\/\//i,
    /\bnew\s+(?:WebSocket|EventSource|Request|Worker|SharedWorker)\s*\(\s*["'`]\s*https?:\/\//i,
    /\bimport\s*\(\s*["'`]\s*https?:\/\//i,
    /\b(?:import|export)\s+[^;\n]*?\bfrom\s*["'`]\s*https?:\/\//i,
    /\bimport\s*["'`]\s*https?:\/\//i,
    /\b(?:src|href|action|poster)\s*[:=]\s*["'`]\s*https?:\/\//i,
    /\.\s*(?:src|href|action|poster)\s*=\s*["'`]\s*https?:\/\//i,
    /\.\s*open\s*\(\s*["'`]\s*(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s*["'`]\s*,\s*["'`]\s*https?:\/\//i,
    /\b(?:location\s*\.\s*(?:assign|replace)|open)\s*\(\s*["'`]\s*https?:\/\//i,
  ];
  for(const pattern of patterns){const m=text.match(pattern);if(m)return m[0]}
  return null;
}
const runtimeFiles=all(dist).filter(p=>/\.(?:js|html|webmanifest|css)$/.test(p));
for(const p of runtimeFiles){
  const text=readFileSync(p,"utf8");
  if(text.includes("node_modules/"))fail(`dist runtime references node_modules: ${relative(dist,p)}`);
  const remote=firstRemoteRuntimeReference(p,text);
  if(remote)fail(`dist runtime contains remote network reference: ${relative(dist,p)} (${remote.slice(0,96)})`);
}
for(const name of ["react.production.js","react-dom.production.js","react-dom-client.production.js","scheduler.production.js"])if(!existsSync(join(dist,"vendor",name)))fail(`vendored production runtime missing: ${name}`);
const pkg=JSON.parse(readFileSync(join(root,"package.json"),"utf8"));
const lock=JSON.parse(readFileSync(join(root,"package-lock.json"),"utf8"));
if(pkg.version!=="1.0.0"||lock.version!=="1.0.0"||lock.packages?.[""]?.version!=="1.0.0")fail("release identity must stay 1.0.0");
const manifest=JSON.parse(readFileSync(join(dist,"release-manifest.json"),"utf8"));
if(manifest.version!=="1.0.0"||manifest.channel!=="parity-closure"||manifest.runtimeDependency!=="self-contained")fail("parity-closure release manifest identity invalid");
if(typeof manifest.cacheVersion!=="string"||!/^studyflow-local-1\.0\.0-[0-9a-f]{16}$/.test(manifest.cacheVersion))fail("content-addressed cache version missing from release manifest");
if(!sw.includes(`const CACHE_VERSION = "${manifest.cacheVersion}";`))fail("service worker cache version does not match release manifest");
if(sw.includes("__STUDYFLOW_CACHE_VERSION__"))fail("unresolved service-worker cache placeholder in dist");
for(const item of manifest.files){const p=join(dist,item.path);if(!existsSync(p))fail(`manifest file missing: ${item.path}`);const digest=createHash("sha256").update(readFileSync(p)).digest("hex");if(digest!==item.sha256||statSync(p).size!==item.bytes)fail(`manifest mismatch: ${item.path}`)}
console.log(`StudyFlow Local ${pkg.version} parity release OK (${manifest.files.length+1} dist files; ${entries.length} cached shell files)`);
