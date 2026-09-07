import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const sw=readFileSync(join(root,"service-worker.js"),"utf8");
const match=sw.match(/const APP_SHELL = \[([\s\S]*?)\];/);
if(!match)throw new Error("service-worker APP_SHELL declaration missing");
const entries=[...match[1].matchAll(/"([^"\n]+)"/g)].map((item)=>item[1]);
if(new Set(entries).size!==entries.length)throw new Error("service-worker APP_SHELL contains duplicate entries");
for(const entry of entries){if(/^https?:/i.test(entry)||entry.startsWith("//"))throw new Error(`remote app-shell URL is forbidden: ${entry}`);}
function jsFiles(dir){const out=[];for(const name of readdirSync(dir)){const path=join(dir,name);if(statSync(path).isDirectory())out.push(...jsFiles(path));else if(path.endsWith(".js"))out.push(`./${relative(root,path).replaceAll("\\","/")}`);}return out.sort();}
for(const runtime of jsFiles(join(root,"runtime"))){if(!entries.includes(runtime))throw new Error(`runtime file missing from offline app shell: ${runtime}`);}
const skipNodeModules=process.env.STUDYFLOW_RELEASE_SKIP_NODE_MODULES==="1";
for(const entry of entries){
  if(entry==="./")continue;
  const local=join(root,entry.replace(/^\.\//,""));
  if(entry.startsWith("./node_modules/")&&skipNodeModules)continue;
  if(!existsSync(local))throw new Error(`offline app-shell file missing: ${entry}`);
}
const pkg=JSON.parse(readFileSync(join(root,"package.json"),"utf8"));
const lock=JSON.parse(readFileSync(join(root,"package-lock.json"),"utf8"));
if(pkg.version!=="1.0.0"||lock.version!=="1.0.0"||lock.packages?.[""]?.version!=="1.0.0")throw new Error("release identity must be 1.0.0 in package metadata and lockfile");
console.log(`StudyFlow Local ${pkg.version} release shell OK (${entries.length} cached files)`);
