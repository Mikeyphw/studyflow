#!/bin/sh
set -eu
cd "$(dirname "$0")/.."

# Resolve every declared main-build project through the built-in dependency report so Gradle can
# write lock state/checksums without coupling integrity generation to compilation.
MAIN_DEPENDENCY_TASKS=$(python3 - <<'PY'
import re
from pathlib import Path
source = Path("settings.gradle.kts").read_text(encoding="utf-8")
modules = re.findall(r'"(:[A-Za-z0-9:_-]+)"', source.split('include(', 1)[1].split(')', 1)[0])
print("dependencies " + " ".join(f"{module}:dependencies" for module in modules))
PY
)
# shellcheck disable=SC2086
./gradlew --write-locks --write-verification-metadata sha256 $MAIN_DEPENDENCY_TASKS

# Included builds own independent lock and verification metadata.
./gradlew -p build-logic --write-locks --write-verification-metadata sha256 :convention:dependencies

python3 scripts/verify_gradle_dependency_integrity.py --require-materialized
