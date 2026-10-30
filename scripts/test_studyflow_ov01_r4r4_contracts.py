#!/usr/bin/env python3
"""R4R4 source-bound adverse tests. Never invokes Gradle or downloads packages."""
from pathlib import Path
import importlib.util
import subprocess
import tempfile
import tomllib
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]

def main():
    c=tomllib.loads((ROOT/'.devtool.toml').read_text())
    d=c['targets']['studyflow_desktop']
    plans=[d['tasks']['test'],d['validation']['phases']['unit-test']]
    expected=[f'verifyDesktopDov{i:02d}'+suffix for i,suffix in [
       (8,'PersistentWorkspace'),(9,'PlanContentLifecycle'),(10,'PlannerAdaptiveWorkflows'),
       (11,'SessionRuntime'),(12,'QuestionPracticeExecution'),(13,'MockExamStudio'),
       (14,'ReviewsSrsErrorNotebook'),(15,'LibraryFilesMaterialViewers'),
       (16,'WritingWorkspace'),(17,'ContentModesExecution'),(18,'PublicExamParity'),
       (19,'ReadinessCoachingReports'),(20,'PortabilityBackupMigration'),
       (21,'EnPtBrLocalization'),(22,'SystemLightDarkAppearance'),
       (23,'NativeDesktopIntegration'),(24,'FullApplicableParitySeal')]]
    for plan in plans:
        assert all(plan.count(t)==1 for t in expected), 'Missing/duplicate DOV08-DOV24 plan task'
    for n in range(8,25):
        script=next(iter(sorted((ROOT/'scripts').glob(f'verify_desktop_dov{n:02d}_*.py'))),None)
        assert script, f'missing desktop DOV{n:02d} verifier'
        proc=subprocess.run(['python3',str(script)],cwd=ROOT,text=True,capture_output=True,timeout=40)
        assert proc.returncode==0, f'{script.name}: {proc.stdout} {proc.stderr}'
    workflow=(ROOT/'.github/workflows/studyflow-ci.yml').read_text()
    assert "matrix.profile == 'android-contracts' || matrix.profile == 'parity-contracts'" in workflow
    assert 'Install pinned TypeScript environment for native/Web contracts' in workflow
    assert 'working-directory: web\n        run: npm ci' in workflow
    assert 'working-directory: web\n        run: npm run build' in workflow
    package=(ROOT/'web/package.json').read_text()
    assert '"typescript": "5.8.3"' in package
    assert (ROOT/'web/package-lock.json').is_file()
    spec=importlib.util.spec_from_file_location('ci_profile_r4r4',ROOT/'scripts/studyflow_ci_gradle_profile.py')
    p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
    plan=p.profile_plan('parity-contracts')
    assert plan['operation']=='validation.phases.preflight'
    assert plan['tasks']==c['targets']['studyflow_parity']['validation']['phases']['preflight']
    assert plan['qualified_release'] is False
    assert 'verifyRelFinalTerminalSeal' not in plan['tasks']
    assert 'verifyRelFinalTerminalSeal' in c['targets']['studyflow_parity']['tasks']['build']
    assert 'verifyRelFinalTerminalSeal' in c['targets']['studyflow_parity']['tasks']['test']
    assert 'verifyRelFinalTerminalSeal' in c['targets']['studyflow_parity']['validation']['phases']['unit-test']
    # Declared preflight must not accidentally pull in signed/package output tasks.
    assert not any('assembleRelease' in t or 'bundleRelease' in t or 'signRelease' in t for t in plan['tasks'])
    with tempfile.TemporaryDirectory(prefix='r4r4-parity-negative-') as tmp:
        testroot=Path(tmp);cfg=(ROOT/'.devtool.toml').read_text(); f=testroot/'.devtool.toml'
        original='  "verifyRelFinalRoadmapPromiseAudit",'
        # Insert a known release-qualified terminal task into preflight, not parity build.
        start=cfg.index('[targets.studyflow_parity.validation.phases]')
        lhs,rhs=cfg[:start],cfg[start:]
        assert rhs.count(original)==1
        f.write_text(lhs+rhs.replace(original, original+'\n  "verifyRelFinalTerminalSeal",',1))
        with patch.object(p,'ROOT',testroot):
            try:p.profile_plan('parity-contracts')
            except ValueError as e: assert 'release-qualified' in str(e)
            else: raise AssertionError('unsafe parity release seal escaped negative gate')
    print('PASS_R4R4_SOURCE_AND_NEGATIVE_TESTS_NO_GRADLE')

if __name__=='__main__':main()
