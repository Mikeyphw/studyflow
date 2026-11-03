#!/usr/bin/env python3
"""SFDT-03: verify eight-target explicit ownership. Source-only, no Gradle.

This checks the REVIEWED input policy, not remote CI acceptance. Optional
--verify-devtool exercises the actual Devtool affected engine when installed.
"""
from __future__ import annotations
import argparse, copy, fnmatch, json, pathlib, posixpath, sys, tomllib

ROOT = pathlib.Path(__file__).resolve().parents[1]
ROOTS = {'studyflow_android': '.', 'studyflow_desktop': '.', 'studyflow_web': 'web', 'studyflow_tv': '.', 'studyflow_tv_release': '.', 'studyflow_release': '.', 'studyflow_quality': '.', 'studyflow_parity': '.'}
BASE_INPUTS = {'studyflow_android': ['.devtool.toml', 'settings.gradle.kts', 'build.gradle.kts', 'gradle/**', 'build-logic/**', 'app/**', 'core/**', 'feature/**', 'protocol/**', 'tools/**'], 'studyflow_desktop': ['.devtool.toml', 'settings.gradle.kts', 'build.gradle.kts', 'gradle/**', 'build-logic/**', 'desktopApp/**', 'core/**', 'feature/**', 'protocol/**'], 'studyflow_web': ['src/**', 'tests/**', 'scripts/**', 'package.json', 'package-lock.json', 'requirements-test.txt', 'tsconfig.json', 'sw.js'], 'studyflow_tv': ['.devtool.toml', 'settings.gradle.kts', 'build.gradle.kts', 'gradle/**', 'build-logic/**', 'app-tv/**', 'core/**', 'feature/**', 'protocol/**'], 'studyflow_tv_release': ['.devtool.toml', 'settings.gradle.kts', 'build.gradle.kts', 'gradle/**', 'build-logic/**', 'app-tv/**', 'core/**', 'feature/**', 'protocol/**', 'docs/release/**'], 'studyflow_release': ['.devtool.toml', 'settings.gradle.kts', 'build.gradle.kts', 'gradle/**', 'build-logic/**', 'app/**', 'core/**', 'feature/**', 'protocol/**', 'docs/release/**'], 'studyflow_quality': ['.devtool.toml', 'settings.gradle.kts', 'build.gradle.kts', 'gradle/**', 'build-logic/**', 'app/**', 'app-tv/**', 'desktopApp/**', 'core/**', 'feature/**', 'protocol/**', 'docs/quality/**', 'scripts/**', 'tools/**'], 'studyflow_parity': ['.devtool.toml', 'protocol/**', 'app/**', 'app-tv/**', 'desktopApp/**', 'web/**', 'docs/validation/**']}
ADDITIONS = {'studyflow_android': ['gradlew', 'gradle.properties', 'scripts/**', '.github/workflows/*.yml'], 'studyflow_desktop': ['gradlew', 'gradle.properties', 'scripts/**', '.github/workflows/*.yml'], 'studyflow_tv': ['gradlew', 'gradle.properties', 'scripts/**', '.github/workflows/*.yml'], 'studyflow_tv_release': ['gradlew', 'gradle.properties', 'scripts/**', '.github/workflows/*.yml'], 'studyflow_release': ['gradlew', 'gradle.properties', 'scripts/**', '.github/workflows/*.yml'], 'studyflow_quality': ['gradlew', 'gradle.properties', '.github/workflows/*.yml'], 'studyflow_parity': ['gradlew', 'gradle.properties', 'scripts/**', '.github/workflows/*.yml', 'core/**', 'feature/**', 'build-logic/**', 'gradle/**', 'settings.gradle.kts', 'build.gradle.kts'], 'studyflow_web': ['../.devtool.toml', '../protocol/**', '../.github/workflows/*.yml']}
TARGETS = tuple(ROOTS)
GRADLE_TARGETS = tuple(t for t in TARGETS if t != 'studyflow_web')
MATRIX = (
    ('app/src/main/java/App.kt', ('studyflow_android','studyflow_release','studyflow_quality','studyflow_parity')),
    ('app-tv/src/main/java/Tv.kt', ('studyflow_tv','studyflow_tv_release','studyflow_quality','studyflow_parity')),
    ('desktopApp/src/main/kotlin/Window.kt', ('studyflow_desktop','studyflow_quality','studyflow_parity')),
    ('web/src/main.ts', ('studyflow_web','studyflow_parity')),
    ('web/package.json', ('studyflow_web','studyflow_parity')),
    ('core/domain/src/main/kotlin/Model.kt', GRADLE_TARGETS),
    ('feature/workspace/src/main/kotlin/A.kt', GRADLE_TARGETS),
    ('protocol/schema.json', TARGETS),
    ('gradle/wrapper/gradle-wrapper.properties', GRADLE_TARGETS),
    ('build-logic/convention/build.gradle.kts', GRADLE_TARGETS),
    ('.github/workflows/studyflow-ci.yml', TARGETS),
    ('.devtool.toml', TARGETS),
    ('docs/quality/rules.md', ('studyflow_quality',)),
    ('docs/validation/parity.md', ('studyflow_parity',)),
    ('README.md', TARGETS),  # unowned => conservative candidate-wide fallback
)

class OwnershipError(ValueError):
    pass

def require(cond: bool, message: str) -> None:
    if not cond:
        raise OwnershipError(message)

def normalized_pattern(root: str, pattern: str) -> str:
    value = posixpath.normpath(posixpath.join('' if root == '.' else root, pattern))
    require(value != '..' and not value.startswith('../'), 'Ownership pattern escapes repo: '+pattern)
    return value

def owns(pattern: str, path: str) -> bool:
    return fnmatch.fnmatchcase(path, pattern) or path == pattern or path.startswith(pattern.rstrip('/')+'/')

def projected_direct(targets: dict, path: str) -> tuple[str,...]:
    # Advisory projection of explicitly declared target.inputs only; Devtool
    # also includes referenced job.inputs and reverse workflow dependencies.
    found = []
    for name in TARGETS:
        target=targets[name]
        if any(owns(normalized_pattern(ROOTS[name], pattern),path) for pattern in target['inputs']):
            found.append(name)
    return tuple(found)

def audit_config(config: dict) -> dict:
    targets = config.get('targets', {})
    require(isinstance(targets,dict) and set(targets)==set(TARGETS),'The eight target identities changed')
    for name in TARGETS:
        target = targets[name]
        require(isinstance(target, dict) and target.get('root')==ROOTS[name], f'Execution root changed: {name}')
        require(target.get('affected_ownership')=='inputs-only', f'Implicit root ownership still active: {name}')
        original=BASE_INPUTS[name]
        expected=original+ADDITIONS[name]
        inputs=target.get('inputs')
        require(isinstance(inputs,list) and inputs==expected, f'Unreviewed or missing ownership inputs: {name}')
        require(len(inputs)==len(set(inputs)),f'Duplicate input patterns: {name}')
        for pattern in inputs:
            require(isinstance(pattern,str),f'Non-string input: {name}')
            normalized_pattern(ROOTS[name],pattern)
    for path, expected in MATRIX:
        owned=projected_direct(targets,path)
        if not owned:
            owned=TARGETS
        require(set(owned)==set(expected), f'Impact projection mismatch {path}: {owned} != {expected}')
    # No CI workflow or Gradle tasks are removed to make affected selection pass.
    require(set(config.get('workspace',{}).get('groups',{})) >= {'clients','mobile','release','quality','everything'},
            'Workspace groups missing')
    assert targets['studyflow_android']['execution_environment']=='native-termux'
    return {'schema':'studyflow.sfdt03.ownership.v1','status':'SOURCE_ONLY_PASS',
            'mode':'advisory-no-ci-skip','targets':len(TARGETS),'matrix_cases':len(MATRIX),
            'qualified_release':False,'gradle_executed':False,'requires_upstream_sfdt02':True}

def verify_devtool(root: pathlib.Path) -> None:
    from devtool.config import load_project_config
    from devtool.affected import analyze_affected_targets
    loaded = load_project_config(str(root), None, allow_ambiguous_target=True)
    for name in TARGETS:
        require(loaded.targets[name].affected_ownership=='inputs-only',
                f'Devtool does not expose SFDT-02 ownership mode: {name}')
    for path, expected in MATRIX:
        selected=analyze_affected_targets(loaded,changed_paths=[path])
        require(set(selected.direct_targets)==set(expected),
                f'ACTUAL Devtool direct impact mismatch for {path}: {selected.direct_targets} != {expected}')
        require(set(selected.targets)==set(expected),
                f'ACTUAL Devtool transitive impact mismatch for {path}: {selected.targets} != {expected}')
        if path=='README.md':
            require(selected.uncertain_paths==(path,), 'Unknown-path safety fallback lost')

def main(argv=None) -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--check',action='store_true')
    ap.add_argument('--json',action='store_true')
    ap.add_argument('--verify-devtool',action='store_true',help='run the installed actual Devtool engine (source-only)')
    args=ap.parse_args(argv)
    try:
        payload=audit_config(tomllib.loads((ROOT/'.devtool.toml').read_text(encoding='utf-8')))
        if args.verify_devtool:
            verify_devtool(ROOT)
            payload['real_devtool_matrix']='PASS'
        else:
            payload['real_devtool_matrix']='NOT_RUN'
    except (OwnershipError, OSError, KeyError, ImportError, SystemExit, AttributeError, TypeError, ValueError) as e:
        print('SFDT03_OWNERSHIP_REFUSED:',e,file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(payload,sort_keys=True,indent=2))
    else:
        print(f'SFDT03_OWNERSHIP_SOURCE_PASS: {payload["targets"]} targets, {payload["matrix_cases"]} matrices, actual={payload["real_devtool_matrix"]}')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
