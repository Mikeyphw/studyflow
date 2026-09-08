# Android TV roadmap and working-rules contract

This verifier keeps the TV overlay chain aligned with the complete roadmap and the user's working
preferences.

Required guarantees:

- the full TVOV00-TVOV20 roadmap is checked in;
- TVOV00-TVOV14 are marked implemented after the TVOV13/14 overlay;
- TVOV15 remains the next planned overlay;
- overlays are explicitly structured implementation units, not light placeholder patches;
- TV work targets `studyflow_tv` and uses `devtool --copy apply overlay`;
- paired-phone control, cast-state boundaries, and the TV dependency budget remain documented;
- TV Schedule and Library stay read-only/presentation-only surfaces in `:app-tv`.
