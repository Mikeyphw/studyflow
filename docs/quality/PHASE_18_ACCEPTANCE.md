# Phase 18 Acceptance

- Materials, videos, and annexes have launch plans in `StudyResourceLaunchPlanner`.
- App-private annex files open through `FileProvider` and never expose raw private file paths.
- `AndroidManifest.xml` declares package visibility queries for viewer/playback handoff.
- The library screen exposes open actions for materials, videos, and annexes.
- `scripts/verify_phase_18.py` is wired into `scripts/validate_repo.sh`.
