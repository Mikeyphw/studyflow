from pathlib import Path
import re

_VERSION_PATTERN = re.compile(
    r'versionName\s*=\s*"0\.(?P<minor>\d+)\.0-phase0?(?P<phase>\d+)"',
)


def phase_version_at_least(root: Path, minimum_phase: int) -> bool:
    """Return true for the canonical StudyFlow phase version at or above the requested phase."""
    text = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    match = _VERSION_PATTERN.search(text)
    if match is None:
        return False
    minor = int(match.group("minor"))
    phase = int(match.group("phase"))
    return minor == phase and phase >= minimum_phase
