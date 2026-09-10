# TVOV28 — Android TV goal projection and actions

TVOV28 adds current goal progress to the native-TV Progress surface. Evidence-backed goals are evaluated from canonical
session/test/mock/topic evidence before projection. Custom goals can be completed explicitly; active goals can be paused
and paused goals resumed. All mutations are emitted as TV domain actions and committed by the paired data-owning phone.
The TV APK does not gain a progress repository or database dependency.
