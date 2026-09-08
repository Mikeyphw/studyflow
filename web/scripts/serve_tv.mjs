import {createReadStream, existsSync, statSync} from "node:fs";
import {createServer} from "node:http";
import {networkInterfaces} from "node:os";
import {extname, join, normalize, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const root=resolve(fileURLToPath(new URL("../dist/",import.meta.url)));
const argv=process.argv.slice(2);
function value(flag,fallback){const index=argv.indexOf(flag);return index>=0&&argv[index+1]?argv[index+1]:fallback;}
const host=value("--host",process.env.STUDYFLOW_TV_HOST||"0.0.0.0");
const port=Number(value("--port",process.env.STUDYFLOW_TV_PORT||"8000"));
if(!Number.isInteger(port)||port<1||port>65535)throw new Error(`Invalid TV server port: ${port}`);
if(!existsSync(join(root,"index.html")))throw new Error("web/dist is missing. Run npm run build first.");

const mime={".html":"text/html; charset=utf-8",".css":"text/css; charset=utf-8",".js":"text/javascript; charset=utf-8",".json":"application/json; charset=utf-8",".webmanifest":"application/manifest+json; charset=utf-8",".png":"image/png",".txt":"text/plain; charset=utf-8"};
function sendFile(path,res){res.writeHead(200,{"Content-Type":mime[extname(path)]||"application/octet-stream","Cache-Control":"no-cache","X-Content-Type-Options":"nosniff"});createReadStream(path).pipe(res);}
const server=createServer((req,res)=>{
 const requestUrl=new URL(req.url||"/",`http://${req.headers.host||"localhost"}`);
 if(requestUrl.pathname==="/tv"||requestUrl.pathname==="/tv/"){res.writeHead(302,{Location:"/?tv=1","Cache-Control":"no-store"});res.end();return;}
 let pathname=decodeURIComponent(requestUrl.pathname);if(pathname==="/")pathname="/index.html";
 const relative=normalize(pathname).replace(/^([/\\])+/,"");const path=resolve(root,relative);
 if(path!==root&&!path.startsWith(root+"/")){res.writeHead(403);res.end("Forbidden");return;}
 if(existsSync(path)&&statSync(path).isFile()){sendFile(path,res);return;}
 if(req.method==="GET"&&req.headers.accept?.includes("text/html")){sendFile(join(root,"index.html"),res);return;}
 res.writeHead(404,{"Content-Type":"text/plain; charset=utf-8"});res.end("Not found");
});

server.listen(port,host,()=>{
 const addresses=[];for(const entries of Object.values(networkInterfaces()))for(const info of entries||[])if(info.family==="IPv4"&&!info.internal)addresses.push(info.address);
 console.log("StudyFlow TV-browser server ready");
 console.log(`  Local: http://127.0.0.1:${port}/tv`);
 for(const address of [...new Set(addresses)])console.log(`  TV:    http://${address}:${port}/tv`);
 console.log("Open /tv on the television. ?tv=1 forces TV mode; ?tv=0 disables it.");
 console.log("LAN HTTP is intentionally supported; secure-context-only PWA/encryption capabilities are reported separately by the app.");
});
