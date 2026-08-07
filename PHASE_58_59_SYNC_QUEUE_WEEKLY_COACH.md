# Phase 58-59: Cloudless Sync Queue and Weekly Coach Review

Phase 58 and Phase 59 build on the landed post-V2 release seal without adding any
cloud account, network dependency, or silent plan mutation.

## Phase 58: Cloudless Sync/Backup Queue

The sync foundation now exposes a local-only backup queue generated from the
existing checkpoint state and local-change summary. The queue is deterministic and
user-visible:

- prepare a fresh local checkpoint when the change token has moved;
- queue a local backup export when unsaved local changes exist;
- request a freshness verification when the last checkpoint is stale;
- surface manual conflict-policy review for multi-plan local data;
- block safely when checkpoint state is blocked;
- wait quietly when there is no local study data yet.

The queue deliberately stays cloudless. It does not create accounts, does not
transmit data, and does not promise background execution that the app cannot
verify locally.

## Phase 59: Study Coach Weekly Review

The Progress workspace now synthesizes readiness, adaptive reschedule preview,
analytics, and evidence timeline into a weekly coach review. It highlights:

- stable readiness and consistency wins;
- readiness risk, review backlog, accuracy risk, schedule drift, and weak mock
  evidence;
- safe reschedule actions that are already available for preview/apply;
- empty-evidence weeks where the learner should complete one visible action.

All visible text is localized through workspace resources; domain objects expose
kinds, scores, and counts instead of UI prose.

## Warning cleanup bundled with the phase

This overlay also closes the warning hygiene backlog from Phase 57:

- route enums used by typed navigation are annotated with `@Keep`;
- deprecated `ScrollableTabRow` is replaced by `PrimaryScrollableTabRow`;
- the private Compose resource collision `in_progress` is renamed;
- Today's minute ticker uses primitive `mutableLongStateOf`;
- an obsolete `SDK_INT < 23` branch is removed from reminder reliability;
- intentional compatibility strings use explicit resource-lint suppression
  boundaries.


## Phase 58-59 r2 audit closure

- Scoped lint hygiene update: resource warning suppressions are now attached only to known compatibility strings instead of the entire resource file.
