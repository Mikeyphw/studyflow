import fs from "node:fs";
import assert from "node:assert/strict";
import { runSemanticGolden } from "../runtime/semantic_golden.js";
const fixture=new URL("../../protocol/semantic/v1/golden-vectors.json",import.meta.url);
const data=JSON.parse(fs.readFileSync(fixture,"utf8"));
let count=0;
for(const vector of data.vectors){
  const actual=runSemanticGolden(vector.kind,vector.input);
  if(vector.kind==="srs")actual.easeFactor=Number(actual.easeFactor.toFixed(2));
  assert.deepEqual(actual,vector.expected,vector.id);
  count++;
}
console.log(`WEB20_TYPESCRIPT_GOLDEN_VECTORS: PASS (${count})`);
