#!/usr/bin/env python3
from pathlib import Path

path = Path("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/SafManualFolderSyncStorage.kt")
text = path.read_text(encoding="utf-8")
required = [
    "import android.os.Build",
    "require(isTreeUriCompat(uri))",
    "private fun isTreeUriCompat(uri: Uri): Boolean =",
    "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)",
    "DocumentsContract.isTreeUri(uri)",
    "runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess",
]
missing = [token for token in required if token not in text]
if missing:
    raise SystemExit("OV20 SAF API23 lint hotfix missing: " + ", ".join(missing))

# The API-24 call must no longer be the direct validation expression.
if 'require(DocumentsContract.isTreeUri(uri))' in text:
    raise SystemExit("OV20 SAF API23 lint hotfix regressed to an unguarded API-24 isTreeUri call")

# Keep minSdk compatibility explicit; do not trade the bug for a global lint suppression.
for forbidden in ('@Suppress("NewApi")', '@SuppressLint("NewApi")'):
    if forbidden in text:
        raise SystemExit("OV20 SAF API23 lint hotfix must not suppress NewApi")

print("OV20_SAF_API23_LINT_HOTFIX: PASS")
