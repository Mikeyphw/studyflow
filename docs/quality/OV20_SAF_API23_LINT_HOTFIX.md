# OV20 SAF API-23 lint hotfix

The ordinary full-project lint gate exposed an API compatibility defect in
`SafManualFolderSyncStorage.requireTreeUri`: `DocumentsContract.isTreeUri(Uri)` is API 24,
while StudyFlow keeps `minSdk = 23`.

The fix keeps the existing SAF-tree validation semantics without raising `minSdk` or
suppressing `NewApi`:

- API 24+: use `DocumentsContract.isTreeUri(uri)`.
- API 23: validate the tree URI by calling the API-21 `DocumentsContract.getTreeDocumentId(uri)`
  and treating `IllegalArgumentException` as an invalid tree URI.

After applying this hotfix, rerun `devtool --copy -r ~/Code/studyflow --target studyflow_android lint`.
