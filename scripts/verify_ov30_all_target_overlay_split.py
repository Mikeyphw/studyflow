#!/usr/bin/env python3
from __future__ import annotations
import json, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
TARGETS=[
  {
    "step": "30a",
    "target": "studyflow_android",
    "task": "verifyOv30StudyflowAndroidTargetSeal"
  },
  {
    "step": "30b",
    "target": "studyflow_desktop",
    "task": "verifyOv30StudyflowDesktopTargetSeal"
  },
  {
    "step": "30c",
    "target": "studyflow_web",
    "task": "verifyOv30StudyflowWebTargetSeal"
  },
  {
    "step": "30d",
    "target": "studyflow_tv",
    "task": "verifyOv30StudyflowTvTargetSeal"
  },
  {
    "step": "30e",
    "target": "studyflow_release",
    "task": "verifyOv30StudyflowReleaseTargetSeal"
  },
  {
    "step": "30f",
    "target": "studyflow_tv_release",
    "task": "verifyOv30StudyflowTvReleaseTargetSeal"
  },
  {
    "step": "30g",
    "target": "studyflow_quality",
    "task": "verifyOv30StudyflowQualityTargetSeal"
  },
  {
    "step": "30h",
    "target": "studyflow_parity",
    "task": "verifyOv30StudyflowParityTargetSeal"
  }
]
FAIL=[]
def require(c,m):
    if not c: FAIL.append(m)

def values(block):
    out=[]
    for table in ('tasks',):
        for value in block.get(table,{}).values():
            if isinstance(value, list): out.extend(str(x) for x in value)
    phases=block.get('validation',{}).get('phases',{}) if isinstance(block.get('validation',{}), dict) else {}
    for value in phases.values():
        if isinstance(value, list): out.extend(str(x) for x in value)
    return out

def web_seal_order_ok(command):
    """Preserve both seals and their intentional pre-validation order.

    Both checks run before compilation, lint, tests, release checks, or packaging.
    A bare legacy seal, a reversed order, and omitted seals are all rejected.
    """
    if not isinstance(command, str):
        return False
    segments = [part.strip() for part in command.split('&&')]
    return len(segments) >= 3 and segments[:2] == [
        'npm run ov30r2:proper-validation-seal',
        'npm run ov30:target-seal',
    ] and all(segments)

def main():
    devtool=tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8'))
    pkg=json.loads((ROOT/'web/package.json').read_text(encoding='utf-8'))
    ledger=json.loads((ROOT/'docs/roadmap/OV30_TARGET_OVERLAY_LEDGER.json').read_text(encoding='utf-8'))
    require(ledger.get('roadmapOverlay')=='30 of 30','ledger overlay number mismatch')
    require(ledger.get('splitPolicy')=='one_overlay_per_devtool_target','ledger split policy missing')
    entries=ledger.get('targets',[])
    require(len(entries)==len(TARGETS),'ledger target count mismatch')
    by_target={e.get('target'): e for e in entries}
    targets=devtool.get('targets',{})
    for item in TARGETS:
        target=item['target']; task=item['task']
        require(target in targets, f'{target} missing from devtool')
        require((ROOT/f'scripts/verify_ov30_{target}_target_seal.py').is_file(), f'{target} verifier missing')
        require((ROOT/f'scripts/validate_ov30_{target}_target_seal.sh').is_file(), f'{target} validator script missing')
        require((ROOT/f'docs/remediation/OV30_{target.upper()}_TARGET_SEAL.md').is_file(), f'{target} doc missing')
        require(target in by_target, f'{target} missing from ledger')
        if target == 'studyflow_web':
            scripts=pkg.get('scripts',{})
            require('ov30:target-seal' in scripts, 'web target seal npm script missing')
            for operation in ('build', 'test', 'lint', 'release:check'):
                require(web_seal_order_ok(scripts.get(operation, '')),
                        f'web {operation} must run pinned OV30R2 and OV30 target seals first')
        else:
            require(task in values(targets[target]), f'{target} does not reference own OV30 task')
    parity=targets.get('studyflow_parity',{})
    require('verifyOv30AllTargetOverlaySplit' in values(parity), 'parity target must include aggregate split verifier')
    build=(ROOT/'build.gradle.kts').read_text(encoding='utf-8')
    require('tasks.register<Exec>("verifyOv30AllTargetOverlaySplit")' in build, 'aggregate Gradle task missing')
    require('runRelFinalExhaustiveRoadmapAudit' not in '\n'.join(values(parity)), 'parity target must not run recursive audit by default')
    if FAIL:
        print('OV30_TARGET_OVERLAY_SPLIT: FAIL')
        for f in FAIL: print('-', f)
        raise SystemExit(1)
    print('OV30_TARGET_OVERLAY_SPLIT: PASS (' + str(len(TARGETS)) + ' targets)')
if __name__ == '__main__': main()
