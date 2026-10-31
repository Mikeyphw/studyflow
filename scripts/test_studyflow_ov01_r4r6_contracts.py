#!/usr/bin/env python3
"""R4R6: source-only hosted regression. Never executes Gradle or network requests."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import subprocess
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def source_check(script: str) -> None:
    out = subprocess.run([sys.executable, str(ROOT / 'scripts' / script)],
                         cwd=ROOT, capture_output=True, text=True, timeout=75)
    require(out.returncode == 0,
            f'{script}: {out.stdout[-2000:]} {out.stderr[-2000:]}')


def desktop_owners(build: str) -> list[str]:
    start = 'tasks.register("verifyStudyFlowDesktopAllContracts")'
    end = 'tasks.register("verifyStudyFlowTvAllContracts")'
    require(start in build and end in build, 'Desktop aggregate delimiters missing')
    block = build.split(start, 1)[1].split(end, 1)[0]
    result = re.findall(r'"(verify[A-Za-z0-9_]+)"', block)
    require(len(result) == len(set(result)) and len(result) >= 22,
            'Desktop aggregate appears truncated or duplicated')
    return result


def validate_ownership(owners: list[str], plan: list[str], label: str) -> None:
    for name in owners:
        require(plan.count(name) == 1,
                f'{label}: missing/duplicate Desktop aggregate dependency: {name}')


def main() -> None:
    ci = (ROOT / '.github/workflows/studyflow-ci.yml').read_text()
    require('python3 scripts/test_studyflow_ov01_r4r6_contracts.py' in ci,
            'GitHub source job does not execute this regression')
    model = (ROOT / 'core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlanning.kt').read_text()
    fixture = (ROOT / 'app/src/test/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetActionPolicyOv14Test.kt').read_text()
    require(re.search(r'val notes:\s*String[\s,)]', model) is not None,
            'StudySession.notes signature changed; re-evaluate fixture')
    require('notes = "",' in fixture and 'notes = null,' not in fixture,
            'Widget OV14 test has illegal nullable notes fixture')
    gradle = (ROOT / 'app/build.gradle.kts').read_text()
    require('dependsOn(verifyPhoneReleaseSigningPreflight)' in gradle and
            'name == "assembleRelease"' in gradle and 'name == "bundleRelease"' in gradle and
            'name.startsWith("signRelease")' in gradle,
            'Release APK/AAB signing preflight was weakened')
    config = tomllib.loads((ROOT / '.devtool.toml').read_text())
    desktop = config['targets']['studyflow_desktop']
    owners = desktop_owners((ROOT / 'build.gradle.kts').read_text())
    require({'verifyStudyFlowDesktopFoundation','verifyStudyFlowDesktopBootstrapScreens',
             'verifyStudyFlowDesktopShellContracts','verifyStudyFlowDesktopWorkflows',
             'verifyAtt04DesktopAttentionContracts'}.issubset(owners),
            'Desktop aggregate lost historical owners')
    for label, plan in (('tasks.test', desktop['tasks']['test']),
                        ('validation.phases.unit-test', desktop['validation']['phases']['unit-test'])):
        validate_ownership(owners, plan, label)
        # Negative probes catch the old one-at-a-time missing registration failure.
        for missing in ('verifyStudyFlowDesktopShellContracts',
                        'verifyStudyFlowDesktopWorkflows',
                        'verifyAtt04DesktopAttentionContracts'):
            mutated = plan.copy()
            mutated.remove(missing)
            try:
                validate_ownership(owners, mutated, label)
            except AssertionError:
                pass
            else:
                raise AssertionError(f'Negative test failed to catch missing {missing}')
    path = ROOT / 'scripts/verify_ov30_all_target_overlay_split.py'
    spec = importlib.util.spec_from_file_location('studyflow_ov30_split_r4r6', path)
    require(spec is not None and spec.loader is not None, 'OV30 verifier module missing')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    import json
    web = json.loads((ROOT / 'web/package.json').read_text())['scripts']
    for name in ('build','test','lint','release:check'):
        command = web[name]
        require(verifier.web_seal_order_ok(command), f'OV30 {name} must preserve both seals')
        for broken in (
            command.replace('npm run ov30r2:proper-validation-seal && ', '', 1),
            command.replace('npm run ov30:target-seal && ', '', 1),
            command.replace('npm run ov30r2:proper-validation-seal && npm run ov30:target-seal',
                            'npm run ov30:target-seal && npm run ov30r2:proper-validation-seal', 1),
        ):
            require(not verifier.web_seal_order_ok(broken),
                    f'OV30 {name} improperly accepted missing/reordered seals')
    for script in ('verify_studyflow_desktop_foundation.py',
                   'verify_studyflow_desktop_bootstrap_screens.py',
                   'verify_studyflow_desktop_shell_contracts.py',
                   'verify_studyflow_desktop_workflows.py',
                   'verify_ov30_all_target_overlay_split.py',
                   'verify_ov30_studyflow_web_target_seal.py'):
        source_check(script)
    print(f'STUDYFLOW_OV01_R4R6: PASS ({len(owners)} desktop owners, signed release gate preserved, both web seals, widget fixture)')


if __name__ == '__main__':
    main()
