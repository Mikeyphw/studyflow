from pathlib import Path
import re

_PHASE_PATTERN = re.compile(
    r'versionName\s*=\s*"0\.(?P<minor>\d+)\.0-phase0?(?P<phase>\d+)"',
)
_VERSION_CODE_PATTERN = re.compile(r'versionCode\s*=\s*(?P<code>\d+)')
_RELEASE_CANDIDATE_PATTERN = re.compile(r'versionName\s*=\s*"1\.0\.0-rc(?P<rc>\d+)"')


def phase_version_at_least(root: Path, minimum_phase: int) -> bool:
    """Return true for a StudyFlow phase or release-candidate version at/above a phase.

    Phase 36 moves the product away from phase-style version names. For source-contract
    validators, the release-candidate versionCode is the monotonic delivery phase marker.
    """
    text = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    phase_match = _PHASE_PATTERN.search(text)
    if phase_match is not None:
        minor = int(phase_match.group("minor"))
        phase = int(phase_match.group("phase"))
        return minor == phase and phase >= minimum_phase
    if _RELEASE_CANDIDATE_PATTERN.search(text) is not None:
        code_match = _VERSION_CODE_PATTERN.search(text)
        if code_match is None:
            return False
        version_code = int(code_match.group("code"))
        return version_code >= minimum_phase and version_code >= 36
    return False
