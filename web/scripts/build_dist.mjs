import {createHash} from "node:crypto";
import {cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync} from "node:fs";
import {dirname, join, relative, resolve} from "node:path";
import {fileURLToPath} from "node:url";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const dist=resolve(process.env.STUDYFLOW_DIST_DIR||join(root,"dist"));
const vendorRoot=resolve(process.env.STUDYFLOW_VENDOR_ROOT||root);
const pkg=JSON.parse(readFileSync(join(root,"package.json"),"utf8"));
const expected={react:"19.2.8","react-dom":"19.2.8",scheduler:"0.27.0"};
const vendorFiles={react:"cjs/react.production.js","react-dom":"cjs/react-dom.production.js","react-dom-client":"cjs/react-dom-client.production.js",scheduler:"cjs/scheduler.production.js"};
const packageFor={react:"react","react-dom":"react-dom","react-dom-client":"react-dom",scheduler:"scheduler"};
function sha(path){return createHash("sha256").update(readFileSync(path)).digest("hex")}
function files(dir){const out=[];for(const name of readdirSync(dir)){const p=join(dir,name);if(statSync(p).isDirectory())out.push(...files(p));else out.push(p)}return out.sort()}
function sourcePackage(name){return join(vendorRoot,"node_modules",name)}
for(const [name,version] of Object.entries(expected)){const pp=join(sourcePackage(name),"package.json");if(!existsSync(pp))throw new Error(`Pinned production dependency missing: ${name}@${version}`);const actual=JSON.parse(readFileSync(pp,"utf8")).version;if(actual!==version)throw new Error(`Pinned production dependency mismatch: ${name} ${actual} != ${version}`)}
rmSync(dist,{recursive:true,force:true});mkdirSync(dist,{recursive:true});
for(const name of ["index.html","styles.css","manifest.webmanifest"]){cpSync(join(root,name),join(dist,name))}
for(const dir of ["runtime","icons"]){cpSync(join(root,dir),join(dist,dir),{recursive:true})}
mkdirSync(join(dist,"vendor"),{recursive:true});
for(const [destKey,rel] of Object.entries(vendorFiles)){const pkgName=packageFor[destKey];const src=join(sourcePackage(pkgName),rel);if(!existsSync(src))throw new Error(`Production runtime file missing: ${pkgName}/${rel}`);cpSync(src,join(dist,"vendor",`${destKey}.production.js`))}
let app=readFileSync(join(root,"app.js"),"utf8");
app=app.replace('./node_modules/react/cjs/react.production.js','./vendor/react.production.js').replace('./node_modules/react-dom/cjs/react-dom.production.js','./vendor/react-dom.production.js').replace('./node_modules/react-dom/cjs/react-dom-client.production.js','./vendor/react-dom-client.production.js').replace('./node_modules/scheduler/cjs/scheduler.production.js','./vendor/scheduler.production.js');
writeFileSync(join(dist,"app.js"),app);
let sw=readFileSync(join(root,"service-worker.js"),"utf8");
sw=sw.replaceAll('./node_modules/react/cjs/react.production.js','./vendor/react.production.js').replaceAll('./node_modules/react-dom/cjs/react-dom.production.js','./vendor/react-dom.production.js').replaceAll('./node_modules/react-dom/cjs/react-dom-client.production.js','./vendor/react-dom-client.production.js').replaceAll('./node_modules/scheduler/cjs/scheduler.production.js','./vendor/scheduler.production.js');
const cacheSeed=files(dist).map(p=>`${relative(dist,p).replaceAll("\\","/")}\0${sha(p)}`).join("\n");
const cacheVersion=`studyflow-local-${pkg.version}-${createHash("sha256").update(cacheSeed).digest("hex").slice(0,16)}`;
if(!sw.includes('__STUDYFLOW_CACHE_VERSION__'))throw new Error('service-worker cache-version placeholder missing');
sw=sw.replace('__STUDYFLOW_CACHE_VERSION__',cacheVersion);
writeFileSync(join(dist,"service-worker.js"),sw);
let notices=`StudyFlow Local ${pkg.version} third-party notices\n\n`;
for(const [name,version] of Object.entries(expected)){const lic=join(sourcePackage(name),"LICENSE");notices+=`${name} ${version} — MIT\n`;if(existsSync(lic))notices+=readFileSync(lic,"utf8").trim()+"\n";notices+="\n"}
writeFileSync(join(dist,"THIRD_PARTY_NOTICES.txt"),notices);
const manifestFiles=files(dist).filter(p=>!p.endsWith("release-manifest.json")).map(p=>({path:relative(dist,p).replaceAll("\\","/"),sha256:sha(p),bytes:statSync(p).size}));
writeFileSync(join(dist,"release-manifest.json"),JSON.stringify({product:"StudyFlow Local",version:pkg.version,channel:"parity-closure",runtimeDependency:"self-contained",cacheVersion,files:manifestFiles},null,2)+"\n");
console.log(`StudyFlow dist built: ${dist} (${manifestFiles.length+1} files)`);
