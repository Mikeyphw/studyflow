# TVOV30 — Android TV D-pad and SFLP delegated search

TVOV30 adds a native search overlay built only from the current typed TV presentation. KEYCODE_SEARCH opens it, D-pad
up/down moves deterministically through ranked results, and selection routes to the existing TV destination. Text entry
is accepted through the existing SFLP input-delegation reducer using TEXT_REPLACE/TEXT_COMMIT events from a trusted
delegation. Android TV advertises keyboard-input acceptance but still does not gain workspace.write or repository access.
