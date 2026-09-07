from __future__ import annotations
import subprocess, textwrap, unittest
from pathlib import Path
WEB=Path(__file__).resolve().parents[1]

def node(source:str):
    p=subprocess.run(["node","--input-type=module","-e",textwrap.dedent(source)],cwd=WEB,text=True,capture_output=True)
    if p.returncode: raise AssertionError(p.stderr or p.stdout)

class Pc02PlanContentImportTest(unittest.TestCase):
    def test_markdown_preview_is_pure_and_apply_builds_nested_topics(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>"2026-09-07T04:00:00.000Z"});const before=JSON.stringify(store.getState());
        const preview=store.previewPlanContentImport("# Mathematics\\n## Algebra\\n### Linear equations\\n## Geometry",{format:"markdown",mode:"merge"});assert.equal(preview.ready,true);assert.equal(preview.summary.subjectsAdd,1);assert.equal(preview.summary.topicsAdd,3);assert.equal(JSON.stringify(store.getState()),before);store.applyPlanContentImportPreview(preview);
        const state=store.getState(),subject=state.subjects.find(x=>x.name==="Mathematics"),parent=state.topics.find(x=>x.name==="Algebra"),child=state.topics.find(x=>x.name==="Linear equations");assert.ok(subject&&parent&&child);assert.equal(child.parentTopicId,parent.id);assert.equal(child.subjectId,subject.id);
        ''')

    def test_csv_and_tsv_fields_normalize_into_canonical_entities(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js"; import {parsePlanContentImport} from "./runtime/content_import.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>"2026-09-07T04:00:00.000Z"});const csv='subject,path,priority,status,weight\\nPortuguese,Grammar > Syntax,high,learning,3\\n';const preview=store.previewPlanContentImport(csv,{format:"csv"});assert.equal(preview.ready,true);store.applyPlanContentImportPreview(preview);const state=store.getState(),subject=state.subjects.find(x=>x.name==="Portuguese"),grammar=state.topics.find(x=>x.name==="Grammar"&&x.subjectId===subject.id),syntax=state.topics.find(x=>x.name==="Syntax"&&x.subjectId===subject.id);assert.equal(subject.weight,3);assert.equal(syntax.parentTopicId,grammar.id);assert.equal(syntax.priority,"high");assert.equal(syntax.status,"learning");
        const parsed=parsePlanContentImport('subject\\ttopic\\tpriority\\nLogic\\tPropositions\\thigh\\n',{format:"tsv"});assert.equal(parsed.issues.length,0);assert.equal(parsed.records[0].subject,"Logic");
        ''')

    def test_nested_json_and_flat_json_rows_are_supported(self):
        node('''
        import assert from "node:assert/strict"; import {parsePlanContentImport} from "./runtime/content_import.js";
        let parsed=parsePlanContentImport(JSON.stringify({subjects:[{name:"Law",weight:2,topics:[{name:"Constitution",children:[{name:"Rights",priority:"high"}]}]}]}),{format:"json"});assert.equal(parsed.issues.length,0);assert.equal(parsed.records.some(r=>r.topicPath.join("/")==="Constitution/Rights"),true);
        parsed=parsePlanContentImport(JSON.stringify([{subject:"IT",path:"Networks > TCP",status:"reviewing"}]),{format:"json"});assert.equal(parsed.records[0].topicPath.join("/"),"Networks/TCP");assert.equal(parsed.records[0].status,"reviewing");
        ''')

    def test_duplicate_rows_are_reported_and_not_multiplied(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const preview=store.previewPlanContentImport("Logic > Propositions\\nLogic > Propositions",{format:"txt"});assert.equal(preview.ready,true);assert.equal(preview.summary.duplicate,1);assert.equal(preview.summary.subjectsAdd,1);assert.equal(preview.summary.topicsAdd,1);store.applyPlanContentImportPreview(preview);const state=store.getState();assert.equal(state.subjects.filter(x=>x.name==="Logic").length,1);assert.equal(state.topics.filter(x=>x.name==="Propositions").length,1);
        ''')

    def test_ambiguous_existing_matches_and_invalid_rows_block_apply(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});store.addSubject({name:"Duplicate"});store.addSubject({name:"duplicate"});let preview=store.previewPlanContentImport("Duplicate > Topic",{format:"txt"});assert.equal(preview.ready,false);assert.equal(preview.summary.ambiguous,1);assert.throws(()=>store.applyPlanContentImportPreview(preview));preview=store.previewPlanContentImport('subject,topic,weight\\nMath,Algebra,-3\\n',{format:"csv"});assert.equal(preview.ready,false);assert.ok(preview.summary.invalid>=1);
        ''')

    def test_reconcile_archives_missing_content_but_preserves_history(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>"2026-09-07T04:00:00.000Z"});const historyBefore=JSON.stringify({reviews:store.getState().reviews,evidence:store.getState().evidence,sessions:store.getState().sessions});const preview=store.previewPlanContentImport("Portuguese > Grammar",{format:"txt",mode:"reconcile"});assert.ok(preview.summary.archive>0);store.applyPlanContentImportPreview(preview);const state=store.getState();assert.equal(state.subjects.find(x=>x.name==="Constitutional Law").archived,true);assert.equal(state.topics.find(x=>x.name==="Text interpretation").status,"archived");assert.equal(state.topics.find(x=>x.name==="Grammar").status,"not-started");assert.equal(JSON.stringify({reviews:state.reviews,evidence:state.evidence,sessions:state.sessions}),historyBefore);
        ''')

    def test_preview_is_revision_guarded(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage})});const preview=store.previewPlanContentImport("Logic > Propositions",{format:"txt"});store.addSubject({name:"Changed after preview"});assert.throws(()=>store.applyPlanContentImportPreview(preview),/stale/i);
        ''')

    def test_import_can_explicitly_create_a_new_plan(self):
        node('''
        import assert from "node:assert/strict"; import {createBrowserRepository} from "./runtime/storage.js"; import {createStudyStore} from "./runtime/state.js";
        const storage={getItem(){return null},setItem(){},removeItem(){}};const store=createStudyStore({repository:createBrowserRepository({storage}),now:()=>"2026-09-07T04:00:00.000Z"});const original=store.getState().activePlanId;const preview=store.previewPlanContentImport("Algorithms > Graphs > BFS\\nAlgorithms > Dynamic programming",{format:"txt",target:"new-plan",planName:"Computer Science",planMode:"PROGRAMMING_COURSE"});assert.equal(preview.ready,true);assert.equal(store.getState().activePlanId,original);store.applyPlanContentImportPreview(preview);const state=store.getState(),plan=state.plans.find(x=>x.name==="Computer Science");assert.ok(plan);assert.equal(plan.mode,"PROGRAMMING_COURSE");assert.equal(state.activePlanId,plan.id);assert.equal(state.subjects.filter(x=>x.planId===plan.id).length,1);assert.equal(state.topics.filter(x=>x.planId===plan.id).length,3);
        ''')

if __name__=="__main__": unittest.main()
