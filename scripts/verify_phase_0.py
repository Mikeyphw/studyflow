#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_FILES = {
    "docs/product/PRODUCT_CONTRACT.md": ["complete, self-contained workspace", "Offline-first behavior"],
    "docs/product/NAVIGATION_MAP.md": ["Compact width", "Expanded width"],
    "docs/product/OFFLINE_FIRST_AND_PORTABILITY.md": ["Export guarantees", "Failure guarantees"],
    "docs/product/LOCALIZATION_GLOSSARY.md": ["Portuguese (Brazil)", "StudyPlan"],
    "docs/product/SCOPE_BOUNDARIES.md": ["Phase 1 foundation", "post-1.0"],
    "docs/product/ACCESSIBILITY_BASELINE.md": ["48 dp", "200% font scale"],
    "docs/architecture/ADR-0001-study-plan-root.md": ["aggregate root", "studyPlanId"],
    "docs/architecture/ADR-0002-offline-first.md": ["local database", "authoritative"],
    "docs/architecture/ADR-0003-code-language-and-localization.md": ["English", "Brazilian Portuguese"],
    "docs/quality/PHASE_0_ACCEPTANCE.md": ["Phase 0 is complete"],
}

failures = []
for relative_path, required_terms in REQUIRED_FILES.items():
    path = ROOT / relative_path
    if not path.is_file():
        failures.append(f"missing {relative_path}")
        continue
    content = path.read_text(encoding="utf-8")
    for term in required_terms:
        if term not in content:
            failures.append(f"{relative_path}: missing contract term {term!r}")

if failures:
    print("PHASE_0_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print(f"PHASE_0_CONTRACT: PASSED ({len(REQUIRED_FILES)} documents)")
