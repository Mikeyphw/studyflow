#!/usr/bin/env bash
set -euo pipefail
umask 077
CONFIG_PATH="${STUDYFLOW_ANDROID_SIGNING_PROPERTIES:-$HOME/.config/studyflow/android-signing.properties}"
KEYSTORE_PATH="${STUDYFLOW_ANDROID_KEYSTORE:-$HOME/.config/studyflow/studyflow-personal.p12}"
KEY_ALIAS="${STUDYFLOW_ANDROID_KEY_ALIAS:-studyflow}"
fail() { printf 'StudyFlow signing setup failed: %s\n' "$*" >&2; exit 1; }
command -v keytool >/dev/null 2>&1 || fail "keytool was not found; install/use a JDK first"
command -v python3 >/dev/null 2>&1 || fail "python3 was not found; it is required to safely write Java signing properties"
case "$CONFIG_PATH" in /*) ;; *) fail "STUDYFLOW_ANDROID_SIGNING_PROPERTIES must be an absolute path" ;; esac
case "$KEYSTORE_PATH" in /*) ;; *) fail "STUDYFLOW_ANDROID_KEYSTORE must be an absolute path" ;; esac
mkdir -p "$(dirname "$CONFIG_PATH")" "$(dirname "$KEYSTORE_PATH")"
if [[ -e "$CONFIG_PATH" || -e "$KEYSTORE_PATH" ]]; then
  printf 'Existing signing material was not modified.\nProperties: %s\nKeystore:   %s\n' "$CONFIG_PATH" "$KEYSTORE_PATH"
  printf 'Move/delete them yourself only if you intentionally want a new app identity.\n'; exit 2
fi
printf 'Create the permanent StudyFlow Android signing key.\nThis key must be kept for every future StudyFlow APK update.\n\n'
while :; do
  IFS= read -r -s -p 'Keystore password (at least 12 characters): ' password; printf '\n'
  [[ ${#password} -ge 12 ]] || { printf 'Use at least 12 characters.\n' >&2; continue; }
  IFS= read -r -s -p 'Confirm password: ' confirm; printf '\n'
  [[ "$password" == "$confirm" ]] || { printf 'Passwords do not match.\n' >&2; continue; }; break
done
unset confirm
export STUDYFLOW_SIGNING_SETUP_PASSWORD="$password"
keytool -genkeypair -keystore "$KEYSTORE_PATH" -storetype PKCS12 -alias "$KEY_ALIAS" -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=StudyFlow Personal, OU=Personal, O=StudyFlow, C=BR' -storepass:env STUDYFLOW_SIGNING_SETUP_PASSWORD -keypass:env STUDYFLOW_SIGNING_SETUP_PASSWORD >/dev/null
# Java .properties has its own escaping rules. Write through Python so passwords/paths
# containing backslashes, separators, leading spaces, or control characters round-trip safely.
export STUDYFLOW_SIGNING_CONFIG_PATH="$CONFIG_PATH"
export STUDYFLOW_SIGNING_KEYSTORE_PATH="$KEYSTORE_PATH"
export STUDYFLOW_SIGNING_KEY_ALIAS="$KEY_ALIAS"
python3 - <<'PYPROPS'
import os
from pathlib import Path

def esc(value: str) -> str:
    out = []
    for i, ch in enumerate(value):
        if ch == "\\": out.append("\\\\")
        elif ch == "\n": out.append("\\n")
        elif ch == "\r": out.append("\\r")
        elif ch == "\t": out.append("\\t")
        elif ch in "=:": out.append("\\" + ch)
        elif ch == " " and i == 0: out.append("\\ ")
        elif ord(ch) > 0x7f:
            # Properties.load(InputStream) is ISO-8859-1 based. Emit Java Unicode
            # escapes so non-ASCII passwords, aliases and paths round-trip exactly.
            cp = ord(ch)
            if cp <= 0xffff:
                out.append(f"\\u{cp:04x}")
            else:
                cp -= 0x10000
                hi = 0xd800 + (cp >> 10)
                lo = 0xdc00 + (cp & 0x3ff)
                out.append(f"\\u{hi:04x}\\u{lo:04x}")
        else: out.append(ch)
    return "".join(out)

values = {
    "storeFile": os.environ["STUDYFLOW_SIGNING_KEYSTORE_PATH"],
    "storePassword": os.environ["STUDYFLOW_SIGNING_SETUP_PASSWORD"],
    "keyAlias": os.environ["STUDYFLOW_SIGNING_KEY_ALIAS"],
    "keyPassword": os.environ["STUDYFLOW_SIGNING_SETUP_PASSWORD"],
}
Path(os.environ["STUDYFLOW_SIGNING_CONFIG_PATH"]).write_text(
    "".join(f"{k}={esc(v)}\n" for k, v in values.items()), encoding="utf-8"
)
PYPROPS
unset STUDYFLOW_SIGNING_CONFIG_PATH STUDYFLOW_SIGNING_KEYSTORE_PATH STUDYFLOW_SIGNING_KEY_ALIAS
chmod 600 "$CONFIG_PATH" "$KEYSTORE_PATH"
printf '\nPermanent StudyFlow signing material created.\nProperties: %s\nKeystore:   %s\nAlias:      %s\n\nCertificate fingerprints:\n' "$CONFIG_PATH" "$KEYSTORE_PATH" "$KEY_ALIAS"
keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass:env STUDYFLOW_SIGNING_SETUP_PASSWORD | grep -E 'SHA1:|SHA256:' || true
unset STUDYFLOW_SIGNING_SETUP_PASSWORD password
printf '\nBack up BOTH files privately. Losing this keystore prevents in-place updates of installed StudyFlow apps.\n'
printf 'Next: run Gradle :app:studyFlowSigningStatus and :app:assemblePersonalDebug or :app:assemblePersonalRelease.\n'
