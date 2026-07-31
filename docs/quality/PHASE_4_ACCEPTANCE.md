# Phase 4 acceptance contract

Phase 4 is accepted when:

1. full, privacy-stripped, structure-only, and template exports share one versioned codec;
2. manifest and payload SHA-256 checksums are verified before import;
3. unsafe, duplicate, unknown, oversized, incomplete, or schema-incompatible ZIPs are rejected;
4. import preview reports identity, mode, row count, and annex limitations;
5. the package table inventory exactly matches the declared export mode and annex capability;
6. create-new, replace, and compatible-merge strategies remap every imported identifier;
7. replacement preserves existing snapshot metadata and replace/merge preserve a private pre-import restore point;
8. manual snapshots can be created, listed, restored, and deleted;
9. snapshot and import writes are transactionally committed and private files use canonical paths;
10. the Storage Access Framework is used for package selection and destination creation;
11. physical annex bytes are never falsely marked as included;
12. English and pt-BR resources remain aligned;
13. Phase 3 warning cleanup leaves no deprecated directional icons, boxed integer state, modifier
    parameter ordering warning, or missing Portuguese `many` quantities;
14. Phase 0 through Phase 3 contracts remain green.
