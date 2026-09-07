import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root=resolve(new URL("..",import.meta.url).pathname); const tracked=join(root,"runtime"); const temp=mkdtempSync(join(tmpdir(),"studyflow-ts-"));
function files(dir,base=dir){const out=[];for(const name of readdirSync(dir)){const path=join(dir,name);if(statSync(path).isDirectory())out.push(...files(path,base));else if(path.endsWith(".js"))out.push(relative(base,path));}return out.sort();}
try{
 const result=spawnSync("tsc",["-p",join(root,"tsconfig.json"),"--outDir",temp,"--pretty","false"],{cwd:root,encoding:"utf8"});
 if(result.status!==0){process.stderr.write(result.stderr||result.stdout||"TypeScript compilation failed\n");process.exit(result.status??1)}
 const generated=files(temp); const committed=files(tracked); if(JSON.stringify(generated)!==JSON.stringify(committed)){console.error("Tracked runtime file list differs from TypeScript output",{generated,committed});process.exit(1)}
 for(const rel of generated){const a=readFileSync(join(temp,rel));const b=readFileSync(join(tracked,rel));if(!a.equals(b)){console.error(`Tracked runtime is stale: ${rel}`);process.exit(1)}}
 console.log(`TypeScript runtime parity OK (${generated.length} files)`);
} finally { rmSync(temp,{recursive:true,force:true}); }
