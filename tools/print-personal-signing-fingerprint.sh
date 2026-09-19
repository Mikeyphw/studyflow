#!/usr/bin/env bash
set -euo pipefail
CONFIG_PATH="${STUDYFLOW_ANDROID_SIGNING_PROPERTIES:-$HOME/.config/studyflow/android-signing.properties}"
[[ -f "$CONFIG_PATH" ]] || { echo "StudyFlow signing properties not found: $CONFIG_PATH" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 was not found; it is required to decode Java signing properties" >&2; exit 1; }
command -v keytool >/dev/null 2>&1 || { echo "keytool was not found; install/use a JDK first" >&2; exit 1; }

# Decode the exact Java-properties escaping emitted by setup-personal-signing.sh.
# Never source/eval the properties file: credentials are data, not shell code.
mapfile -d '' -t SIGNING_FIELDS < <(python3 - "$CONFIG_PATH" <<'PYPROPS'
import sys
from pathlib import Path

path = Path(sys.argv[1])
raw = path.read_bytes().decode("iso-8859-1")

def unescape(value: str) -> str:
    out = []
    i = 0
    while i < len(value):
        if value[i] != "\\":
            out.append(value[i]); i += 1; continue
        i += 1
        if i >= len(value):
            out.append("\\"); break
        ch = value[i]; i += 1
        simple = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}
        if ch in simple:
            out.append(simple[ch])
        elif ch == "u":
            if i + 4 > len(value): raise SystemExit("invalid Java Unicode escape in signing properties")
            out.append(chr(int(value[i:i+4], 16))); i += 4
        else:
            out.append(ch)
    # Java may encode supplementary characters as UTF-16 surrogate pairs.
    return "".join(out).encode("utf-16", "surrogatepass").decode("utf-16")

props = {}
for line in raw.splitlines():
    if not line or line[0] in "#!":
        continue
    escaped = False
    split = None
    for idx, ch in enumerate(line):
        if escaped:
            escaped = False; continue
        if ch == "\\":
            escaped = True; continue
        if ch in "=:":
            split = idx; break
    if split is None:
        continue
    props[unescape(line[:split])] = unescape(line[split+1:])

for key in ("storeFile", "keyAlias", "storePassword"):
    value = props.get(key)
    if value is None or value == "":
        raise SystemExit(f"StudyFlow signing property is missing: {key}")
    if "\x00" in value:
        raise SystemExit(f"StudyFlow signing property contains NUL: {key}")
    sys.stdout.buffer.write(value.encode("utf-8") + b"\0")
PYPROPS
)
[[ ${#SIGNING_FIELDS[@]} -eq 3 ]] || { echo "StudyFlow signing properties are incomplete or invalid" >&2; exit 1; }
KEYSTORE_PATH="${SIGNING_FIELDS[0]}"
KEY_ALIAS="${SIGNING_FIELDS[1]}"
STORE_PASSWORD="${SIGNING_FIELDS[2]}"
[[ -f "$KEYSTORE_PATH" ]] || { echo "StudyFlow keystore not found: $KEYSTORE_PATH" >&2; exit 1; }
export STUDYFLOW_SIGNING_FINGERPRINT_PASSWORD="$STORE_PASSWORD"
keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass:env STUDYFLOW_SIGNING_FINGERPRINT_PASSWORD | grep -E 'SHA1:|SHA256:'
unset STUDYFLOW_SIGNING_FINGERPRINT_PASSWORD STORE_PASSWORD KEYSTORE_PATH KEY_ALIAS SIGNING_FIELDS
