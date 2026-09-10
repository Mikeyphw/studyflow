"use strict";
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const parity=require(path.resolve(__dirname,"../runtime/tv_semantic_parity.js"));
const fixture=path.resolve(__dirname,"../../protocol/tv-parity/v1/semantic-fixtures.tsv");
const lines=fs.readFileSync(fixture,"utf8").split(/\r?\n/).filter(line=>line&& !line.startsWith("#"));
for(const line of lines){
 const p=line.split("\t"),kind=p[0];
 if(kind==="viewer-page")assert.equal(parity.tvParityPage(+p[1],+p[2],+p[3]),+p[4]);
 else if(kind==="viewer-zoom")assert.equal(parity.tvParityZoom(+p[1],+p[2]),+p[3]);
 else if(kind==="goal-actions")assert.deepEqual(parity.tvParityGoalActions({completed:p[2]==="true",paused:p[3]==="true",canComplete:p[4]==="true"}),p[5]?p[5].split(","):[]);
 else if(kind==="error-category")assert.equal(parity.tvParityNextErrorCategory(p[1]),p[2]);
 else if(kind==="error-resolve")assert.equal(parity.tvParityErrorResolutionAction(p[1]==="true"),p[2]);
 else if(kind==="search-score")assert.equal(parity.tvParitySearchScore(p[1],p[2],p[3]),+p[4]);
 else throw new Error(`Unknown fixture kind ${kind}`);
}
console.log(`TVOV31_WEB_TV_COMMON_FIXTURES: PASS (${lines.length})`);
