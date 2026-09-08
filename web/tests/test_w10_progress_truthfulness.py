from pathlib import Path
import json, subprocess, textwrap, unittest
ROOT=Path(__file__).resolve().parents[1]
class W10ProgressTests(unittest.TestCase):
    def test_progress_ui_and_truthfulness_copy_exist(self):
        ui=(ROOT/"src/react/App.tsx").read_text()
        for token in ("Readiness", "Study this week", "Accuracy", "Coverage", "Evidence Timeline", "Content Integrity", "No data"):
            self.assertIn(token,ui)

    def test_progress_metrics_keep_unknown_distinct_from_measured_zero(self):
        script=textwrap.dedent('''
            import { createInitialStudyState, deriveProgressMetrics, createEvidence } from './runtime/domain.js';
            const now='2026-09-07T00:00:00.000Z'; const state=createInitialStudyState({now});
            let m=deriveProgressMetrics(state,{at:now});
            if(m.weeklyStudyMinutes!==0) throw new Error('study zero must be measured zero');
            if(m.accuracy!==null) throw new Error('accuracy without graded evidence must be no-data');
            if(m.topicCoverage!==0) throw new Error('existing active topics with no evidence must measure zero coverage');
            const topicIds=state.topics.slice(0,2).map(x=>x.id);
            for(let i=0;i<5;i++) state.evidence.push(createEvidence({planId:state.activePlanId,topicId:topicIds[i%2],questionId:state.questions[0].id,kind:'question-attempt',observedAt:now,provenance:'question',value:{gradable:true,correct:i<4},createdAt:now,updatedAt:now}));
            m=deriveProgressMetrics(state,{at:now});
            if(m.readiness!==null||m.accuracy!==0.8||m.coveredTopics!==2) throw new Error('five attempts must remain insufficient for readiness: '+JSON.stringify(m));
            for(let i=5;i<30;i++) state.evidence.push(createEvidence({planId:state.activePlanId,topicId:topicIds[i%2],questionId:state.questions[0].id,kind:'question-attempt',observedAt:now,provenance:'question',value:{gradable:true,correct:true},createdAt:now,updatedAt:now}));
            m=deriveProgressMetrics(state,{at:now});
            if(m.readiness===null||m.coveredTopics!==2) throw new Error('readiness must become available after the minimum evidence gate: '+JSON.stringify(m));
        ''')
        subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,check=True)
    def test_goal_progress_and_integrity_are_derived(self):
        script=textwrap.dedent('''
            import { createInitialStudyState, createGoal, deriveGoalProgress, buildContentIntegrityReport } from './runtime/domain.js';
            const now='2026-09-07T00:00:00.000Z'; const state=createInitialStudyState({now});
            const goal=createGoal({planId:state.activePlanId,name:'Weekly minutes',metric:'study-minutes',target:120,period:'weekly',createdAt:now,updatedAt:now});
            const p=deriveGoalProgress(state,goal,{at:now}); if(p.actual!==0||p.progress!==0) throw new Error('goal zero must be measured');
            if(!buildContentIntegrityReport(state).valid) throw new Error('seed should be valid');
            state.activities[0].planId='missing'; if(buildContentIntegrityReport(state).valid) throw new Error('broken references must be visible');
        ''')
        subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,check=True)
if __name__=='__main__': unittest.main()
