#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
failures = []

def require(path, *tokens):
    text = (root / path).read_text()
    for token in tokens:
        if token not in text:
            failures.append(f"{path}: missing {token!r}")

require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt",
    "class DesktopWorkspacePersistence",
    "workspace-v1.properties",
    "StandardCopyOption.ATOMIC_MOVE",
    "STUDYFLOW_DESKTOP_DATA_DIR",
    "DesktopWorkspaceSnapshot",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppBootstrap.kt",
    "DesktopWorkspacePersistence.open",
    "defaultDesktopWorkspaceRoot()",
    "workspacePersistence = workspacePersistence",
)
require(
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/InMemoryStudyPlanRepository.kt",
    "private val onChanged",
    "fun currentSettings()",
    "persist()",
)
require(
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspacePersistenceTest.kt",
    "AcrossRestart",
    "workspace-v1.properties",
)
require(
    ".devtool.toml",
    '"verifyDesktopDov08PersistentWorkspace"',
)

if failures:
    raise SystemExit("DOV08 persistent workspace violations:\n- " + "\n- ".join(failures))
print("DESKTOP_DOV08_PERSISTENT_WORKSPACE: PASS")
