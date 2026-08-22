# CSOV08 Conflict review UI

StudyFlow now has a conservative conflict review surface for optional cloud sync.

The merge engine still refuses to silently apply both-sided or delete-vs-change conflicts.
The presenter converts those blocked merge decisions into review items, and the sync UI exposes
three explicit choices: keep local, use remote, or keep both.

Default behavior remains safe: if conflict review items exist, StudyFlow does nothing until the
user chooses a resolution.
